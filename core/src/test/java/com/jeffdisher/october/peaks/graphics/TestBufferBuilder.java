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
		BufferBuilder builder = new BufferBuilder(buffer
			, new Attribute("Position", 0, 3)
			, null
			, null
			, null
			, null
			, null
		);
		builder.position(new float[] { 1.0f, 2.0f, 3.0f });
		builder.position(new float[] { 1.0f, 2.0f, 3.0f });
		BufferBuilder.Buffer frozen = builder.finishOne();
		Assert.assertEquals(2, frozen.vertexCount);
	}

	@Test
	public void streamBuffers() throws Throwable
	{
		FloatBuffer buffer = FloatBuffer.allocate(64);
		BufferBuilder builder = new BufferBuilder(buffer
			, new Attribute("Position", 0, 3)
			, null
			, null
			, null
			, null
			, null
		);
		builder.position(new float[] { 1.0f, 2.0f, 3.0f });
		BufferBuilder.Buffer one = builder.finishOne();
		builder.position(new float[] { 4.0f, 5.0f, 6.0f });
		BufferBuilder.Buffer two = builder.finishOne();
		builder.position(new float[] { 7.0f, 8.0f, 9.0f });
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

	@Test
	public void removedAttribute() throws Throwable
	{
		FloatBuffer buffer = FloatBuffer.allocate(64);
		BufferBuilder builder = new BufferBuilder(buffer
			, new Attribute("Position", 0, 3)
			, new Attribute("removed", -1, 3)
			, null
			, null
			, null
			, null
		);
		builder.position(new float[] { 1.0f, 2.0f, 3.0f });
		builder.normal(new float[] { 0.0f, -1.0f, 0.0f });
		builder.position(new float[] { 1.0f, 2.0f, 3.0f });
		builder.normal(new float[] { 0.0f, -1.0f, 0.0f });
		
		BufferBuilder.Buffer frozen = builder.finishOne();
		Assert.assertEquals(2, frozen.vertexCount);
		float[] raw = new float[6];
		frozen.testGetFloats(raw);
		Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f
			, 1.0f, 2.0f, 3.0f
		}, raw, 0.01f);
	}

	@Test
	public void chainedBuilders() throws Throwable
	{
		// Show that we can use multiple builders on the same buffer, so long as we stop using the first.
		FloatBuffer buffer = FloatBuffer.allocate(128);
		BufferBuilder builder = new BufferBuilder(buffer
			, new Attribute("Position", 0, 3)
			, null
			, new Attribute("texture", 1, 2)
			, null
			, null
			, null
		);
		builder.position(new float[] { 1.0f, 2.0f, 3.0f });
		builder.texture0(new float[] { 1.0f, 0.5f });
		BufferBuilder.Buffer one = builder.finishOne();
		
		BufferBuilder otherBuilder = new BufferBuilder(buffer
			, new Attribute("Position", 0, 3)
			, null
			, new Attribute("colour", 1, 4)
			, null
			, null
			, null
		);
		otherBuilder.position(new float[] { 1.0f, 2.0f, 3.0f });
		otherBuilder.texture0(new float[] { 0.8f, 0.5f, 0.1f, 0.9f });
		otherBuilder.position(new float[] { 3.0f, 2.1f, 1.1f });
		otherBuilder.texture0(new float[] { 0.8f, 0.5f, 0.2f, 0.9f });
		BufferBuilder.Buffer two = otherBuilder.finishOne();
		
		Assert.assertEquals(1, one.vertexCount);
		Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f, 1.0f, 0.5f }, one.testGetFloats(new float[5]), 0.01f);
		
		Assert.assertEquals(2, two.vertexCount);
		Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f, 0.8f, 0.5f, 0.1f, 0.9f
			, 3.0f, 2.1f, 1.1f, 0.8f, 0.5f, 0.2f, 0.9f
		}, two.testGetFloats(new float[14]), 0.01f);
	}
}
