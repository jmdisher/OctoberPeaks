package com.jeffdisher.october.peaks;

import org.junit.Assert;
import org.junit.Test;


public class TestGeometryHelpers
{
	@Test
	public void facingVector() throws Throwable
	{
		float halfPi = (float)(Math.PI / 2.0f);
		
		// Test facing forward.
		float yaw = 0.0f;
		float pitch = 0.0f;
		Vector forward = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, 1.0f, 0.0f), forward);
		
		// Left - rotation of half pi.
		yaw = halfPi;
		Vector left = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(-1.0f, 0.0f, 0.0f), left);
		
		// Backward, rotation of full pi.
		yaw = (float)Math.PI;
		Vector back = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, -1.0f, 0.0f), back);
		
		// Back and up.
		yaw = (float)Math.PI;
		pitch = 0.2f;
		Vector backUp = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, -0.98f, 0.2f), backUp);
	}


	private static void _vectorEquals(Vector expected, Vector test)
	{
		Assert.assertEquals(expected.x(), test.x(), 0.01f);
		Assert.assertEquals(expected.y(), test.y(), 0.01f);
		Assert.assertEquals(expected.z(), test.z(), 0.01f);
	}
}
