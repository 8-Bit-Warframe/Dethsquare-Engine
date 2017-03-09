package com.ezardlabs.dethsquare;

import com.ezardlabs.dethsquare.util.GameListeners;
import com.ezardlabs.dethsquare.util.GameListeners.UpdateListener;

import java.util.ArrayList;

public final class Collider extends BoundedComponent {
	static {
		GameListeners.addUpdateListener(new UpdateListener() {
			@Override
			public void onUpdate() {
				if (rebuildQuadTree) {
					qt.init(staticColliders.toArray(new Collider[staticColliders.size()]));
					rebuildQuadTree = false;
				}
			}
		});
	}

	public static final ArrayList<Collider> normalColliders = new ArrayList<>();
	public static final ArrayList<Collider> staticColliders = new ArrayList<>();
	public static final ArrayList<Collider> triggerColliders = new ArrayList<>();
	public static QuadTree<Collider> qt = new QuadTree<>(20);
	private static boolean inited = false;
	private static boolean rebuildQuadTree = false;
	public final RectF lastBounds = new RectF();
	private final float height;
	private final float width;
	ArrayList<Collider> possible = new ArrayList<>();
	private Collider[] triggers = new Collider[0];
	boolean isTrigger = false;

	public enum CollisionLocation {
		TOP,
		RIGHT,
		BOTTOM,
		LEFT
	}

	public class Collision {
		/**
		 * The {@link Collider} that was hit
		 */
		public final Collider collider;
		/**
		 * The {@link GameObject} of the {@link Collider} that was hit
		 */
		public final GameObject gameObject;
		/**
		 * The side of the {@link Collider} that was involved in the collision
		 */
		public final CollisionLocation location;
		/**
		 * The speed at which the collision occurred
		 */
		public final float speed;
		/**
		 * The {@link Transform} of the {@link Collider} that was hit
		 */
		public final Transform transform;
		/**
		 * The {@link Rigidbody} of the {@link Collider} that was hit
		 */
		public final Rigidbody rigidbody;

		Collision(Collider collider, GameObject gameObject, Transform transform,
				Rigidbody rigidbody, CollisionLocation location, float speed) {
			this.collider = collider;
			this.gameObject = gameObject;
			this.transform = transform;
			this.rigidbody = rigidbody;
			this.location = location;
			this.speed = speed;
		}
	}

	public Collider(float width, float height) {
		this(width, height, false);
	}

	public Collider(float width, float height, boolean isTrigger) {
		this.width = width;
		this.height = height;
		this.isTrigger = isTrigger;
	}

	public static void init() {
		qt.init(staticColliders.toArray(new Collider[staticColliders.size()]));
		inited = true;
	}

	static void clearAll() {
		normalColliders.clear();
		staticColliders.clear();
		triggerColliders.clear();
		qt = new QuadTree<>(20);
		inited = false;
	}

	public void start() {
		if (gameObject.isStatic) {
			staticColliders.add(this);
			if (inited) {
				rebuildQuadTree = true;
			}
		}
		if (isTrigger) addTrigger();
		if (!gameObject.isStatic && !isTrigger) normalColliders.add(this);
		if (gameObject.isStatic && !isTrigger) gameObject.setTag("solid");
		recalculateBounds();
	}

	private void addTrigger() {
		triggerColliders.add(this);
		triggers = new Collider[triggerColliders.size()];
	}

	private void removeTrigger() {
		triggerColliders.remove(this);
		triggers = new Collider[triggerColliders.size()];
	}

	public void destroy() {
		if (gameObject.isStatic) staticColliders.remove(this);
		if (isTrigger) removeTrigger();
		if (!gameObject.isStatic && !isTrigger) normalColliders.remove(this);
	}

	void move(float x, float y) {
		if (isTrigger) {
			transform.position.x += x;
			transform.position.y += y;
			recalculateBounds();
			triggerCheck();
			return;
		}
		possible.clear();

		lastBounds.left = bounds.left;
		lastBounds.top = bounds.top;
		lastBounds.right = bounds.right;
		lastBounds.bottom = bounds.bottom;
		if (x > 0) bounds.right += x;
		if (x < 0) bounds.left += x;
		if (y > 0) bounds.bottom += y;
		if (y < 0) bounds.top += y;

		QuadTree.retrieve(possible, qt, this);

		bounds.left = lastBounds.left;
		bounds.top = lastBounds.top;
		bounds.right = lastBounds.right;
		bounds.bottom = lastBounds.bottom;

		if (possible.isEmpty()) {
			transform.position.x += x;
			transform.position.y += y;
		} else {
			transform.position.y += y;
			recalculateBounds();
			Collider c;
			for (int i = 0; i < possible.size(); i++) {
				c = possible.get(i);
				if (c != this && c != null && !c.isTrigger && RectF.intersects(bounds, c.bounds)) {
					if (y > 0 && bounds.bottom > c.bounds.top) {
						transform.position.y = Math.round(c.bounds.top - bounds.height());
						gameObject.onCollision(
								new Collision(c, c.gameObject, c.transform, c.gameObject.rigidbody,
										CollisionLocation.BOTTOM, y));
					} else if (y < 0 && bounds.top < c.bounds.bottom) {
						transform.position.y = Math.round(c.bounds.bottom);
						if (transform.position.y != lastBounds.top) {
							gameObject.onCollision(new Collision(c, c.gameObject, c.transform,
									c.gameObject.rigidbody, CollisionLocation.TOP, y));
						}
					}
					if (gameObject.rigidbody != null) {
						gameObject.rigidbody.velocity.y = 0;
					}
					recalculateBounds();
				}
			}
			transform.position.x += x;
			recalculateBounds();
			for (int i = 0; i < possible.size(); i++) {
				c = possible.get(i);
				if (c != this && c != null && !c.isTrigger && RectF.intersects(bounds, c.bounds)) {
					if (x > 0 && bounds.right > c.bounds.left) {
						transform.position.x = Math.round(c.bounds.left - bounds.width());
						if (transform.position.x != lastBounds.left) {
							gameObject.onCollision(new Collision(c, c.gameObject, c.transform,
									c.gameObject.rigidbody, CollisionLocation.RIGHT, x));
						}
					} else if (x < 0 && bounds.left < c.bounds.right) {
						transform.position.x = Math.round(c.bounds.right);
						if (transform.position.x != lastBounds.left) {
							gameObject.onCollision(new Collision(c, c.gameObject, c.transform,
									c.gameObject.rigidbody, CollisionLocation.LEFT, x));
						}
					}
					if (gameObject.rigidbody != null) {
						gameObject.rigidbody.velocity.x = 0;
					}
					recalculateBounds();
				}
			}
		}
		recalculateBounds();
		triggerCheck();
	}

	public void recalculateBounds() {
		bounds.left = transform.position.x;
		bounds.top = transform.position.y;
		bounds.right = transform.position.x + width;
		bounds.bottom = transform.position.y + height;
	}

	public void triggerCheck() {
		if (isTrigger) {
			possible.clear();
			QuadTree.retrieve(possible, qt, this);
			for (BoundedComponent bc : possible) {
				if (RectF.intersects(bounds, bc.bounds)) {
					gameObject.onTriggerEnter((Collider) bc);
				}
			}
			for (Collider c : normalColliders) {
				if (c != this && RectF.intersects(bounds, c.bounds)) {
					gameObject.onTriggerEnter(c);
				}
			}
			for (Collider c : triggerColliders.toArray(triggers)) {
				if (c != this && c != null && RectF.intersects(bounds, c.bounds)) {
					c.gameObject.onTriggerEnter(this);
					gameObject.onTriggerEnter(c);
				}
			}
		} else {
			for (Collider c : triggerColliders) {
				if (c != this && RectF.intersects(bounds, c.bounds)) {
					c.gameObject.onTriggerEnter(this);
				}
			}
		}
	}
}
