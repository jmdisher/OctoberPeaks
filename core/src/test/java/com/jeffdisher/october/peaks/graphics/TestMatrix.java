package com.jeffdisher.october.peaks.graphics;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.peaks.types.Vector;


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

	@Test
	public void lookAtMany() throws Throwable
	{
		Vector eye = new Vector(-2.0f, 2.0f, 2.0f);
		Vector target = new Vector(0.0f, 0.0f, 0.0f);
		Vector up = new Vector(0.0f, 0.0f, 1.0f);
		Matrix view = Matrix.lookAt(eye, target, up);
		
		float[] v0 = view.multiplyVector(new float[] {0.0f, 0.0f, 0.0f, 1.0f});
		float[] v1 = view.multiplyVector(new float[] {0.0f, 0.0f, 1.0f, 1.0f});
		float[] v2 = view.multiplyVector(new float[] {-1.5f, 0.0f, 0.0f, 1.0f});
		float[] v3 = view.multiplyVector(new float[] {0.0f, 2.0f, 0.0f, 1.0f});
		_vectorEquals(new float[] {0.0f, 0.0f, -3.46f, 1.0f}, v0);
		_vectorEquals(new float[] {0.0f, 0.82f, -2.89f, 1.0f}, v1);
		_vectorEquals(new float[] {1.06f, -0.61f, -2.6f, 1.0f}, v2);
		_vectorEquals(new float[] {-1.41f, -0.82f, -2.3f, 1.0f}, v3);
		
		Matrix proj = Matrix.perspective(90.0f, 1.0f, 0.1f, 100.0f);
		float[] p0 = proj.multiplyVector(v0);
		float[] p1 = proj.multiplyVector(v1);
		float[] p2 = proj.multiplyVector(v2);
		float[] p3 = proj.multiplyVector(v3);
		_vectorEquals(new float[] {0.0f, 0.0f, 3.27f, 3.46f}, p0);
		_vectorEquals(new float[] {0.0f, 0.82f, 2.69f, 2.89f}, p1);
		_vectorEquals(new float[] {1.06f, -0.61f, 2.4f, 2.6f}, p2);
		_vectorEquals(new float[] {-1.41f, -0.82f, 2.11f, 2.31f}, p3);
	}

	@Test
	public void scale() throws Throwable
	{
		Matrix scale = Matrix.scale(2.0f,  3.0f, 4.0f);
		float[] vec4 = new float[] {-1.0f, 1.0f, 0.5f, 1.0f};
		float[] scaled = scale.multiplyVector(vec4);
		_vectorEquals(new float[] {-2.0f, 3.0f, 2.0f, 1.0f}, scaled);
	}

	@Test
	public void scaleAndTranslate() throws Throwable
	{
		Matrix translate = Matrix.translate(10.0f, -20.0f, 0.1f);
		Matrix scale = Matrix.scale(2.0f,  3.0f, 4.0f);
		Matrix combine = Matrix.multiply(translate, scale);
		float[] vec4 = new float[] {-1.0f, 1.0f, 0.5f, 1.0f};
		float[] combined = combine.multiplyVector(vec4);
		_vectorEquals(new float[] {8.0f, -17.0f, 2.1f, 1.0f}, combined);
	}

	@Test
	public void rotateToFace() throws Throwable
	{
		float[] vec4 = new float[] {0.0f, 1.0f, 0.0f, 1.0f};
		Matrix northEast = Matrix.rotateToFace(new Vector(5.0f, 10.0f, 1.0f));
		Matrix northWest = Matrix.rotateToFace(new Vector(-5.0f, 10.0f, 1.0f));
		Matrix southEast = Matrix.rotateToFace(new Vector(5.0f, -10.0f, 1.0f));
		Matrix southWest = Matrix.rotateToFace(new Vector(-5.0f, -10.0f, 1.0f));
		
		_vectorEquals(new float[] {0.45f, 0.89f, 0.09f, 1.0f}, northEast.multiplyVector(vec4));
		_vectorEquals(new float[] {-0.45f, 0.89f, 0.09f, 1.0f}, northWest.multiplyVector(vec4));
		_vectorEquals(new float[] {0.45f, -0.89f, 0.09f, 1.0f}, southEast.multiplyVector(vec4));
		_vectorEquals(new float[] {-0.45f, -0.89f, 0.09f, 1.0f}, southWest.multiplyVector(vec4));
	}

	@Test
	public void scaleAndTranslateComponents() throws Throwable
	{
		Matrix translate = Matrix.translate(10.0f, -20.0f, 0.1f);
		Matrix scale = Matrix.scale(2.0f,  3.0f, 4.0f);
		Matrix combine = Matrix.multiply(translate, scale);
		float[] combined = combine.multiplyVectorComponents(-1.0f, 1.0f, 0.5f, 1.0f);
		_vectorEquals(new float[] {8.0f, -17.0f, 2.1f, 1.0f}, combined);
	}


	private static void _vectorEquals(float[] expected, float[] test)
	{
		Assert.assertEquals(expected[0], test[0], 0.01f);
		Assert.assertEquals(expected[1], test[1], 0.01f);
		Assert.assertEquals(expected[2], test[2], 0.01f);
		Assert.assertEquals(expected[3], test[3], 0.01f);
	}
}
