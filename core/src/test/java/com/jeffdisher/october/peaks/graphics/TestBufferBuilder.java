package com.jeffdisher.october.peaks.graphics;

import java.nio.BufferUnderflowException;
import java.nio.FloatBuffer;

import org.junit.Assert;
import org.junit.Test;


public class TestBufferBuilder
{
	@Test
	public void singleAttribute() throws Throwable
	{
		FloatBuffer buffer = FloatBuffer.allocate(64);
		BufferBuilder builder = new BufferBuilder(buffer, new Attribute[] { new Attribute("Position", 3) });
		builder.append(0, new float[] { 1.0f, 2.0f, 3.0f });
		builder.appendVertex(new float[] { 1.0f, 2.0f, 3.0f });
		BufferBuilder.Buffer frozen = builder.finishOne();
		Assert.assertEquals(2, frozen.vertexCount);
	}

	@Test
	public void streamBuffers() throws Throwable
	{
		FloatBuffer buffer = FloatBuffer.allocate(64);
		BufferBuilder builder = new BufferBuilder(buffer, new Attribute[] { new Attribute("Position", 3) });
		builder.appendVertex(new float[] { 1.0f, 2.0f, 3.0f });
		BufferBuilder.Buffer one = builder.finishOne();
		builder.appendVertex(new float[] { 4.0f, 5.0f, 6.0f });
		BufferBuilder.Buffer two = builder.finishOne();
		builder.appendVertex(new float[] { 7.0f, 8.0f, 9.0f });
		BufferBuilder.Buffer three = builder.finishOne();
		
		Assert.assertEquals(1, one.vertexCount);
		Assert.assertEquals(1, two.vertexCount);
		Assert.assertEquals(1, three.vertexCount);
		
		Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f }, one.testGetFloats(new float[3]), 0.01f);
		Assert.assertArrayEquals(new float[] { 4.0f, 5.0f, 6.0f }, two.testGetFloats(new float[3]), 0.01f);
		Assert.assertArrayEquals(new float[] { 7.0f, 8.0f, 9.0f }, three.testGetFloats(new float[3]), 0.01f);
		
		// We should fail to read any more.
		try
		{
			one.testGetFloats(new float[1]);
			Assert.fail();
		}
		catch (BufferUnderflowException e)
		{
			// Expected.
		}
		try
		{
			two.testGetFloats(new float[1]);
			Assert.fail();
		}
		catch (BufferUnderflowException e)
		{
			// Expected.
		}
		try
		{
			three.testGetFloats(new float[1]);
			Assert.fail();
		}
		catch (BufferUnderflowException e)
		{
			// Expected.
		}
	}
}
