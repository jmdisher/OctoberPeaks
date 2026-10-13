package com.jeffdisher.october.peaks.scene;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.types.CuboidAddress;


public class TestFrustumFilter
{
	@Test
	public void simple() throws Throwable
	{
		// A very basic test of the FrustumFilter.
		Matrix projection = Matrix.perspective(90.0f, 1.333f, 0.1f, 200.0f);
		Vector eye = new Vector(28.34f, -0.14f, 1.53f);
		Vector target = new Vector(29.210978f, -0.12465379f, 1.0389161f);
		Vector upVector = new Vector(0.49100775f, 0.00865133f, 0.8711123f);
		Matrix view = Matrix.lookAt(eye, target, upVector);
		FrustumFilter filter = new FrustumFilter(projection, view);
		
		// We should be able to see this cuboid we are standing one.
		Assert.assertTrue(filter.shouldIncludeCuboid(CuboidAddress.fromInt(0, -1, -1)));
		// But not the one behind the one we are standing on.
		Assert.assertFalse(filter.shouldIncludeCuboid(CuboidAddress.fromInt(-1, -1, -1)));
	}
}
