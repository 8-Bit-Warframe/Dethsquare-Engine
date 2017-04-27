package com.ezardlabs.dethsquare;

/**
 * Representation of 2D vectors and points
 */
public final class Vector2 {
	public float x;
	public float y;
	private Vector2ChangeListener listener;

	/**
	 * Shorthand for writing Vector2(0, 0)
	 */
	public Vector2() {
	}

	/**
	 * Creates a new vector with the given x and y components
	 */
	public Vector2(float x, float y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Creates a new vector that has the same x and y values as the source vector
	 */
	public Vector2(Vector2 source) {
		this.x = source.x;
		this.y = source.y;
	}

	/**
	 * Sets the x and y components of the vector
	 *
	 * @param x the new x component
	 * @param y the new y component
	 */
	public void set(float x, float y) {
		if (listener != null) listener.onVector2Changed(x - this.x, y - this.y);
		this.x = x;
		this.y = y;
	}

	/**
	 * Sets the x and y components of the vector to be the same as teh given source vector
	 *
	 * @param source the source vector to copy x and y component values from
	 */
	public void set(Vector2 source) {
		if (listener != null) listener.onVector2Changed(source.x - x, source.y - y);
		x = source.x;
		y = source.y;
	}

	/**
	 * Returns a new {@link Vector2} that has been offset by the given parameters
	 *
	 * @param x The amount to offset the x coordinate by
	 * @param y The amount to offset the y coordinate by
	 * @return The offset {@link Vector2}
	 */
	public Vector2 offset(float x, float y) {
		return new Vector2(this.x + x, this.y + y);
	}

	/**
	 * Returns the length of this vector
	 * <p>
	 * If you want to compare the magnitudes of 2 vectors then it is advisable to compare their
	 * square magnitudes({@link #sqrMagnitude()}) as they are faster to compute
	 *
	 * @return The length of this vector
	 */
	public double magnitude() {
		return Math.sqrt(x * x + y * y);
	}

	/**
	 * Returns the squared length of this vector
	 *
	 * @return The squared length of this vector
	 */
	public float sqrMagnitude() {
		return x * x + y * y;
	}

	/**
	 * Makes this vector have a magnitude of 1
	 * <p>
	 * Note that this method modifies the vector it is called on; if you want to keep the vector
	 * unchanged, then use {@link #normalised()} instead
	 */
	public void normalise() {
		double length = Math.sqrt(x * x + y * y);
		if (length != 0) {
			x /= length;
			y /= length;
		}
	}

	/**
	 * Returns this vector with a magnitude of 1
	 * <p>
	 * Not that this method does not modify the vector it is called on; if you want to modify the
	 * vector then use {@link #normalise()} instead
	 *
	 * @return The normalised version of this vector
	 */
	public Vector2 normalised() {
		Vector2 v = new Vector2(this);
		v.normalise();
		return v;
	}

	/**
	 * Multiplies the vector by the given value
	 * <p>
	 * Note that this method modifies the vector it is called on; if you want to keep the vector
	 * unchanged, then use {@link #multipliedBy(float)} instead
	 *
	 * @param multiplier The amount to multiply the vector by
	 */
	public void multiplyBy(float multiplier) {
		x *= multiplier;
		y *= multiplier;
	}

	/**
	 * Returns this vector multiplied by the given value
	 * <p>
	 * Not that this method does not modify the vector it is called on; if you want to modify the
	 * vector then use {@link #multiplyBy(float)} instead
	 *
	 * @param multiplier The amount to multiply the vector by
	 * @return A new vector, equal to the vector this method was called on multiplied by the given value
	 */
	public Vector2 multipliedBy(float multiplier) {
		return new Vector2(x * multiplier, y * multiplier);
	}

	/**
	 * Calculates the distance between 2 vector points
	 *
	 * @param a the first point
	 * @param b the second point
	 * @return The distance between the 2 points
	 */
	public static double distance(Vector2 a, Vector2 b) {
		return Math.sqrt(Math.pow(b.x - a.x, 2) + Math.pow(b.y - a.y, 2));
	}

	@Override
	public String toString() {
		return "Vector2(" + x + ", " + y + ")";
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Vector2 && ((Vector2) o).x == x && ((Vector2) o).y == y;
	}

	void setVector2ChangeListener(Vector2ChangeListener listener) {
		this.listener = listener;
	}

	interface Vector2ChangeListener {
		void onVector2Changed(float xDiff, float yDiff);
	}
}