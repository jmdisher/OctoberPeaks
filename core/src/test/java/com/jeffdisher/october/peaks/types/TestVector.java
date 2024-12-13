package com.jeffdisher.october.peaks.types;

import org.junit.Assert;
import org.junit.Test;


public class TestVector
{
	@Test
	public void crossProduct() throws Throwable
	{
		Vector xAxis = new Vector(1.0f, 0.0f, 0.0f);
		Vector xAxisN = new Vector(-1.0f, 0.0f, 0.0f);
		Vector yAxis = new Vector(0.0f, 1.0f, 0.0f);
		Vector yAxisN = new Vector(0.0f, -1.0f, 0.0f);
		Vector zAxis = new Vector(0.0f, 0.0f, 1.0f);
		Vector zAxisN = new Vector(0.0f, 0.0f, -1.0f);
		
		_vectorEquals(zAxis, Vector.cross(xAxis, yAxis));
		_vectorEquals(zAxisN, Vector.cross(yAxis, xAxis));
		_vectorEquals(xAxis, Vector.cross(yAxis, zAxis));
		_vectorEquals(xAxisN, Vector.cross(zAxis, yAxis));
		_vectorEquals(yAxis, Vector.cross(zAxis, xAxis));
		_vectorEquals(yAxisN, Vector.cross(xAxis, zAxis));
	}

	@Test
	public void normalize() throws Throwable
	{
		Vector xAxis = new Vector(3.0f, 4.0f, 0.0f);
		Assert.assertEquals(5.0f, xAxis.magnitude(), 0.01f);
		_vectorEquals(new Vector(0.6f, 0.8f, 0.0f), xAxis.normalize());
	}


	private static void _vectorEquals(Vector expected, Vector test)
	{
		Assert.assertEquals(expected.x(), test.x(), 0.01f);
		Assert.assertEquals(expected.y(), test.y(), 0.01f);
		Assert.assertEquals(expected.z(), test.z(), 0.01f);
	}
}
