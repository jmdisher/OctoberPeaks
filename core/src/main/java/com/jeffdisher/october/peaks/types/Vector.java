package com.jeffdisher.october.peaks.types;

import com.jeffdisher.october.types.EntityLocation;


public record Vector(float x, float y, float z)
{
	public static Vector cross(Vector left, Vector right)
	{
		return new Vector(
				(left.y * right.z) - (left.z * right.y),
				(left.z * right.x) - (left.x * right.z),
				(left.x * right.y) - (left.y * right.x)
		);
	}

	public static Vector delta(Vector start, Vector end)
	{
		return new Vector(end.x - start.x, end.y - start.y, end.z - start.z);
	}

	/**
	 * These types are meant for different purposes but contain the same data and sometimes one needs to be converted
	 * into another for use elsewhere so this helper does the direct conversion.
	 * 
	 * @param entity The entity location to convert into a Vector object.
	 * @return The new Vector object.
	 */
	public static Vector fromEntityLocation(EntityLocation entity)
	{
		return new Vector(entity.x(), entity.y(), entity.z());
	}


	public float magnitude()
	{
		return _magnitude();
	}

	public Vector normalize()
	{
		float magnitude = _magnitude();
		return new Vector(this.x / magnitude, this.y / magnitude, this.z / magnitude);
	}

	public Vector scale(float scale)
	{
		return new Vector(this.x * scale, this.y * scale, this.z * scale);
	}

	public Vector add(Vector other)
	{
		return new Vector(this.x + other.x, this.y + other.y, this.z + other.z);
	}


	private float _magnitude()
	{
		return (float)Math.sqrt((this.x * this.x) + (this.y * this.y) + (this.z * this.z));
	}
}
