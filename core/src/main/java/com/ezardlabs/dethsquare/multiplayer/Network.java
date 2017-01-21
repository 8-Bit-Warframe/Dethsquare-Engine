package com.ezardlabs.dethsquare.multiplayer;

import com.ezardlabs.dethsquare.GameObject;
import com.ezardlabs.dethsquare.Vector2;
import com.ezardlabs.dethsquare.multiplayer.Network.NetworkStateChangeListener.State;
import com.ezardlabs.dethsquare.prefabs.PrefabManager;
import com.ezardlabs.dethsquare.util.GameListeners;
import com.ezardlabs.dethsquare.util.GameListeners.UpdateListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

public class Network {
	private static UpdateListener updateListener;
	private static NetworkStateChangeListener listener;

	private static UDPWriter udpOut;
	private static UDPReader udpIn;
	private static final TCPWriter[] tcpOut = new TCPWriter[4];

	private static final int START_PORT = 2828;
	private static DatagramSocket datagramSocket;
	private static ServerSocket serverSocket;
	private static int udpPort = -1;
	private static int tcpPort = -1;

	private static int playerId = 0;
	private static boolean host = true;

	private static int networkIdCounter = 0;

	private static final long UPDATES_PER_SECOND = 60;
	private static long lastUpdate = 0;

	private static final String DIVIDER = "|";
	private static final String SPLIT_DIVIDER = Pattern.quote(DIVIDER);
	private static final String INSTANTIATE = "instantiate";
	private static final String DESTROY = "destroy";

	public enum Protocol {
		UDP,
		TCP
	}

	static void init() {
		datagramSocket = getNewDatagramSocket();
		udpPort = datagramSocket.getLocalPort();
		serverSocket = getNewServerSocket(udpPort + 1);
		tcpPort = serverSocket.getLocalPort();

		UPnPManager.discover();
		UPnPManager.addPortMapping(udpPort, Protocol.UDP, "Lost Sector UDP " + udpPort);
		UPnPManager.addPortMapping(tcpPort, Protocol.TCP, "Lost Sector TCP " + tcpPort);

		new TCPServer(serverSocket).start();
	}

	static DatagramSocket getNewDatagramSocket() {
		return getNewDatagramSocket(START_PORT);
	}

	static DatagramSocket getNewDatagramSocket(int startPort) {
		int port = startPort;
		DatagramSocket ds;
		while (true) {
			try {
				ds = new DatagramSocket(port);
				break;
			} catch (SocketException ignored) {
			}
			port++;
		}
		return ds;
	}

	static ServerSocket getNewServerSocket() {
		return getNewServerSocket(START_PORT);
	}

	static ServerSocket getNewServerSocket(int startPort) {
		int port = startPort;
		ServerSocket ss;
		while (true) {
			try {
				ss = new ServerSocket(port);
				break;
			} catch (IOException ignored) {
			}
		}
		return ss;
	}

	static int getUdpPort() {
		return udpPort;
	}

	static int getTcpPort() {
		return tcpPort;
	}

	public static int getPlayerId() {
		return playerId;
	}

	static int getNewNetworkId() {
		return networkIdCounter++;
	}

	static void setHost(boolean host) {
		Network.host = host;
	}

	public static boolean isHost() {
		return host;
	}

	public static void findGame(NetworkStateChangeListener listener) {
		System.out.println("Doing UPnP discovery stuff");
		UPnPManager.discover();
		System.out.println("Discovery stuff done");
		System.out.println("Adding UDP port mapping");
		UPnPManager.addPortMapping(udpPort, Protocol.UDP, "Lost Sector UDP " + udpPort);
		System.out.println("Adding TCP port mapping");
		UPnPManager.addPortMapping(tcpPort, Protocol.TCP, "Lost Sector TCP " + tcpPort);
		System.out.println("Port mappings done");
		Network.listener = listener;
		new TCPServer(serverSocket).start();
		MatchmakingThread mt = new MatchmakingThread();
		updateListener = () -> checkIfGameFound(mt);
		GameListeners.addUpdateListener(updateListener);
		mt.start();
		listener.onNetworkStateChanged(State.MATCHMAKING_SEARCHING);
	}

	private static void checkIfGameFound(MatchmakingThread mt) {
		if (mt.data != null) {
			listener.onNetworkStateChanged(State.MATCHMAKING_FOUND);
			JSONObject data = new JSONObject(mt.data);
			mt.data = null;
			playerId = data.getInt("id");
			host = data.getBoolean("host");

			JSONArray peers = data.getJSONArray("peers");

			InetAddress[] addresses = new InetAddress[peers.length()];
			int[] ports = new int[peers.length()];
			InetSocketAddress[] socketAddresses = new InetSocketAddress[peers.length()];

			listener.onNetworkStateChanged(State.GAME_CONNECTING);

			for (int i = 0; i < peers.length(); i++) {
				JSONObject player = peers.getJSONObject(i);
				try {
					addresses[i] = InetAddress.getByName(player.getString("address"));
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				ports[i] = player.getInt("port");

				socketAddresses[i] = new InetSocketAddress(addresses[i], ports[i]);

				try {
					tcpOut[i] = new TCPWriter(new Socket(addresses[i], ports[i] + 1));
					tcpOut[i].start();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			udpOut = new UDPWriter(datagramSocket, socketAddresses);
			udpOut.start();
			udpIn = new UDPReader(datagramSocket);
			udpIn.start();

			networkIdCounter = playerId * (Integer.MAX_VALUE / 4) + 1;

			listener.onNetworkStateChanged(State.GAME_CONNECTED);

			GameListeners.removeUpdateListener(updateListener);
			GameListeners.addUpdateListener(Network::update);
		}
	}

	private static void update() {
		if (System.currentTimeMillis() >= lastUpdate + 1000 / UPDATES_PER_SECOND) {
			lastUpdate = System.currentTimeMillis();
			ByteBuffer data = ByteBuffer.allocate(NetworkBehaviour.totalSize + (NetworkBehaviour
					.myNetworkBehaviours.size() * 8));
			for (NetworkBehaviour nb : NetworkBehaviour.myNetworkBehaviours.values()) {
				data.putInt(nb.getNetworkId());
				data.putShort(nb.getSize());
				data.put(nb.onSend());
			}
			udpOut.sendMessage(data.array());
		}
		synchronized (udpIn.udpMessages) {
			while (!udpIn.udpMessages.isEmpty()) {
				int count = 0;
				ByteBuffer data = ByteBuffer.wrap(udpIn.udpMessages.remove(0));
				NetworkBehaviour nb;
				while (count < data.capacity()) {
					data.position(count);
					int networkId = data.getInt(count);
					if (networkId == 0) break;
					int size = data.getShort(count + 4);
					nb = NetworkBehaviour.otherNetworkBehaviours.get(networkId);
					if (nb != null) {
						nb.onReceive(data, count + 6);
					}
					count += size + 6;
				}
			}
		}
	}

	private static class MatchmakingThread extends Thread {
		private String data;

		MatchmakingThread() {
			super("MatchmakingThread");
		}

		@Override
		public void run() {
			byte[] buffer = String.valueOf(udpPort).getBytes();
			try (DatagramSocket s = new DatagramSocket(udpPort)) {
				s.send(new DatagramPacket(buffer, buffer.length,
						InetAddress.getByName("8bitwarframe.com"), 3000));
				DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
				s.receive(p);
				data = new String(p.getData());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static class UDPReader extends Thread {
		private final DatagramSocket socket;
		final ArrayList<byte[]> udpMessages = new ArrayList<>();

		UDPReader(DatagramSocket socket) {
			super("UDPReader");
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
				while (true) {
					socket.receive(packet);
					synchronized (udpMessages) {
						udpMessages.add(packet.getData());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static class UDPWriter extends Thread {
		private final DatagramSocket socket;
		private final ArrayList<byte[]> messages = new ArrayList<>();
		private final DatagramPacket[] packets;

		UDPWriter(DatagramSocket socket, SocketAddress[] addresses) {
			super("UDPWriter");
			this.socket = socket;
			packets = new DatagramPacket[addresses.length];
			for (int i = 0; i < packets.length; i++) {
				packets[i] = new DatagramPacket(new byte[0], 0, addresses[i]);
			}
		}

		@Override
		public void run() {
			try {
				while (true) {
					synchronized (messages) {
						messages.wait();
						while (!messages.isEmpty()) {
							byte[] message = messages.remove(0);
							for (int i = 0; i < packets.length; i++) {
								packets[i].setData(message);
								socket.send(packets[i]);
							}
						}
					}
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		void sendMessage(byte[] message) {
			synchronized (messages) {
				messages.add(message);
				messages.notify();
			}
		}
	}

	private static class TCPServer extends Thread {
		private final ServerSocket serverSocket;

		TCPServer(ServerSocket serverSocket) {
			super("TCPServer");
			this.serverSocket = serverSocket;
		}

		@Override
		public void run() {
			try {
				while (true) {
					new TCPReader(serverSocket.accept()).start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static class TCPReader extends Thread {
		private final Socket socket;

		TCPReader(Socket socket) {
			super("TCPReader");
			this.socket = socket;
		}

		@Override
		public void run() {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(socket.getInputStream()))) {
				socket.setKeepAlive(true);
				while (socket.isConnected()) {
					String command = in.readLine();
					if (command != null) {
						switch (command) {
							case INSTANTIATE:
								processInstantiation(in.readLine());
								break;
							case DESTROY:
								processDestruction(in.readLine());
								break;
							default:
								System.out.println("Unknown command:" + command);
								break;
						}
					}
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static class TCPWriter extends Thread {
		private final Socket socket;
		private final ArrayList<String[]> messages = new ArrayList<>();

		TCPWriter(Socket socket) {
			super("TCPWriter");
			this.socket = socket;
		}

		@Override
		public void run() {
			try (BufferedWriter out = new BufferedWriter(
					new OutputStreamWriter(socket.getOutputStream()))) {
				while (true) {
					synchronized (messages) {
						messages.wait();
						while (!messages.isEmpty()) {
							String[] s = messages.remove(0);
							out.write(s[0]);
							out.newLine();
							out.write(s[1]);
							out.newLine();
							out.flush();
						}
					}
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}

		void sendMessage(String command, String message) {
			if (message.contains("\n") || message.contains("\r")) {
				throw new IllegalArgumentException("Message cannot contain newline characters");
			}
			synchronized (messages) {
				messages.add(new String[]{command,
						message});
				messages.notify();
			}
		}
	}

	private static boolean isSolo() {
		for (TCPWriter writer : tcpOut) {
			if (writer != null) {
				return false;
			}
		}
		return true;
	}

	public static GameObject instantiate(String prefabName, Vector2 position) {
		GameObject gameObject = PrefabManager.loadPrefab(prefabName);
		gameObject.networkId = getNewNetworkId();
		if (!isSolo()) {
			List<NetworkBehaviour> networkBehaviours = gameObject.getComponentsOfType(NetworkBehaviour.class);
			HashMap<String, Integer> networkIds = new HashMap<>();
			for (NetworkBehaviour nb : networkBehaviours) {
				networkIds.put(nb.getClass().getCanonicalName(), nb.getNetworkId());
			}
			StringBuilder sb = new StringBuilder();
			if (PrefabManager.prefabExists(prefabName + "_other")) {
				sb.append(prefabName).append("_other").append(DIVIDER);
			} else {
				sb.append(prefabName).append(DIVIDER);
			}
			sb.append(gameObject.networkId).append(DIVIDER);
			sb.append(position.x).append(DIVIDER);
			sb.append(position.y).append(DIVIDER);
			sb.append(playerId).append(DIVIDER);
			for (String key : networkIds.keySet()) {
				sb.append(key).append(DIVIDER).append(networkIds.get(key)).append(DIVIDER);
			}
			String message = sb.toString();
			message = message.substring(0, message.length() - 1);
			for (TCPWriter writer : tcpOut) {
				if (writer != null) {
					writer.sendMessage(INSTANTIATE, message);
				}
			}
		}
		return GameObject.instantiate(gameObject, position);
	}

	private static void processInstantiation(String message) throws IOException {
		String[] split = message.split(SPLIT_DIVIDER);
		GameObject gameObject = PrefabManager.loadPrefab(split[0]);
		gameObject.networkId = Integer.parseInt(split[1]);
		Vector2 position = new Vector2(Float.parseFloat(split[2]),
				Float.parseFloat(split[3]));
		int playerId = Integer.parseInt(split[4]);
		List<NetworkBehaviour> networkBehaviours = gameObject
				.getComponentsOfType(NetworkBehaviour.class);
		HashMap<String, Integer> networkIds = new HashMap<>();
		for (int i = 0; i < networkBehaviours.size(); i++) {
			networkIds.put(split[5 + (i * 2)], Integer.parseInt(split[6 + (i * 2)]));
		}
		for (NetworkBehaviour nb : networkBehaviours) {
			nb.setPlayerId(playerId);
			nb.setNetworkId(networkIds.get(nb.getClass().getCanonicalName()));
		}
		GameObject.instantiate(gameObject, position);
	}

	public static void destroy(GameObject gameObject) {
		GameObject.destroy(gameObject);
		if (!isSolo()) {
			for (TCPWriter writer : tcpOut) {
				if (writer != null) {
					writer.sendMessage(DESTROY, String.valueOf(gameObject.networkId));
				}
			}
		}
	}

	public static void destroy(GameObject gameObject, long delay) {
		new Timer().schedule(new TimerTask() {
			@Override
			public void run() {
				destroy(gameObject);
			}
		}, delay);
	}

	private static void processDestruction(String message) throws IOException {
		GameObject.destroy(Integer.parseInt(message));
	}

	public interface NetworkStateChangeListener {
		enum State {
			MATCHMAKING_SEARCHING,
			MATCHMAKING_FOUND,
			GAME_CONNECTING,
			GAME_CONNECTED
		}

		void onNetworkStateChanged(State state);
	}
}