package com.jeffdisher.october.peaks;

import org.junit.Assert;
import org.junit.Test;


public class TestMatrix
{
	@Test
	public void identity() throws Throwable
	{
		Matrix identity = Matrix.identity();
		float[] vec4 = new float[] {1.0f, 2.0f, 3.0f, 1.0f};
		float[] transformed = identity.multiplyVector(vec4);
		_vectorEquals(vec4, transformed);
	}

	@Test
	public void rotations() throws Throwable
	{
		// We will just use the 90 degree rotation so everything stays on the axes.
		float radians = (float)Math.PI / 2.0f;
		Matrix rotX = Matrix.rotateX(radians);
		Matrix rotY = Matrix.rotateY(radians);
		Matrix rotZ = Matrix.rotateZ(radians);
		float[] vec4 = new float[] {1.0f, 2.0f, 3.0f, 1.0f};
		float[] aroundX = rotX.multiplyVector(vec4);
		float[] aroundY = rotY.multiplyVector(vec4);
		float[] aroundZ = rotZ.multiplyVector(vec4);
		_vectorEquals(new float[] { 1.0f, -3.0f, 2.0f, 1.0f}, aroundX);
		_vectorEquals(new float[] { 3.0f, 2.0f, -1.0f, 1.0f}, aroundY);
		_vectorEquals(new float[] { -2.0f, 1.0f, 3.0f, 1.0f}, aroundZ);
	}

	@Test
	public void lookAt() throws Throwable
	{
		// We want to look down the negative Y to invert the X-axis and make the Z-axis the up.
		Vector eye = new Vector(0.0f, 0.0f, 0.0f);
		Vector target = new Vector(0.0f, -1.0f, 0.0f);
		Vector up = new Vector(0.0f, 0.0f, 1.0f);
		Matrix view = Matrix.lookAt(eye, target, up);
		
		_vectorEquals(new float[] {-1.0f, 3.0f, 2.0f, 1.0f}, view.multiplyVector(new float[] {1.0f, 2.0f, 3.0f, 1.0f}));
		_vectorEquals(new float[] {1.0f, 3.0f, 2.0f, 1.0f}, view.multiplyVector(new float[] {-1.0f, 2.0f, 3.0f, 1.0f}));
	}


	private static void _vectorEquals(float[] expected, float[] test)
	{
		Assert.assertEquals(expected[0], test[0], 0.01f);
		Assert.assertEquals(expected[1], test[1], 0.01f);
		Assert.assertEquals(expected[2], test[2], 0.01f);
		Assert.assertEquals(expected[3], test[3], 0.01f);
	}
}
