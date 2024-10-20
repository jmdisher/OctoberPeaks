package com.jeffdisher.october.peaks;

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

	public static Vector fromEntity(EntityLocation entity, float horizontalOffsets, float zOffset)
	{
		return new Vector(entity.x() + horizontalOffsets, entity.y() + horizontalOffsets, entity.z() + zOffset);
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


	private float _magnitude()
	{
		return (float)Math.sqrt((this.x * this.x) + (this.y * this.y) + (this.z * this.z));
	}
}
