package com.ezardlabs.dethsquare.util;

public class Utils {
	public static Platform PLATFORM;
	public static AudioUtils AUDIO;
	public static IOUtils IO;
	public static PrefUtils PREFS;
	public static RenderUtils RENDER;

	public enum Platform {
		ANDROID,
		DESKTOP
	}

	public static void init(Platform platform, AudioUtils audio, IOUtils io, PrefUtils prefs, RenderUtils render) {
		PLATFORM = platform;
		AUDIO = audio;
		IO = io;
		PREFS = prefs;
		RENDER = render;
	}
}