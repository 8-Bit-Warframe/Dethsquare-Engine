package com.ezardlabs.dethsquare;

import com.ezardlabs.dethsquare.Collider.Collision;

/**
 * Base class for everything attached to {@link GameObject GameObjects}
 */
public class Component {
	/**
	 * The {@link GameObject} this component is attached to. A component is always attached to a
	 * {@link GameObject}
	 */
	public GameObject gameObject;
	/**
	 * The {@link Transform} attached to the {@link GameObject} that this {@link Component} is also
	 * attached to (null if there is none attached)
	 */
	public Transform transform;

	protected void destroy() {
	}

	public void start() {
	}

	public void onTriggerEnter(Collider other) {
	}

	public void onCollision(Collider other, Collision collision) {
	}
}
