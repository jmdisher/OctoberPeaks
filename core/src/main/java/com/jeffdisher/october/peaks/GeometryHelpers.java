package com.jeffdisher.october.peaks;


public class GeometryHelpers
{
	public static Vector computeFacingVector(float yawRadians, float pitchRadians)
	{
		// We will assume that we are looking at (0, 1, 0) when at 0 rotation.
		float lookX = -(float)Math.sin(yawRadians);
		float lookY = (float)Math.cos(yawRadians);
		float lookZ = (float)Math.sin(pitchRadians);
		float distanceZ = (float)Math.cos(pitchRadians);
		return new Vector(lookX * distanceZ, lookY * distanceZ, lookZ).normalize();
	}

}
