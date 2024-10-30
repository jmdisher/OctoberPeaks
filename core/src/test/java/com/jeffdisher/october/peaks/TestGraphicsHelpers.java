package com.jeffdisher.october.peaks;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.junit.Assert;
import org.junit.Test;


public class TestGraphicsHelpers
{
	public static int MiB = 1024 * 1024;

	@Test
	public void unitCube() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(MiB);
		FloatBuffer floats = buffer.asFloatBuffer();
		float[] base = new float[] {0.0f, 0.0f, 0.0f};
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float textureSize = 1.0f;
		GraphicsHelpers.drawCube(floats, base, (byte)1, uvBase, textureSize);
		floats.flip();
		// We should see the 6 cube faces, each composed of a single quad (2 triangles or 6 vertices).
		Assert.assertEquals(6 * 2 * 3 * GraphicsHelpers.FLOATS_PER_VERTEX, floats.remaining());
	}

	@Test
	public void doubleCube() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(MiB);
		FloatBuffer floats = buffer.asFloatBuffer();
		float[] base = new float[] {0.0f, 0.0f, 0.0f};
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float textureSize = 1.0f;
		GraphicsHelpers.drawCube(floats, base, (byte)2, uvBase, textureSize);
		floats.flip();
		// We should see the 6 cube faces, each composed of 4 quad tiles (2 triangles or 6 vertices).
		Assert.assertEquals(6 * 4 * 2 * 3 * GraphicsHelpers.FLOATS_PER_VERTEX, floats.remaining());
	}

	@Test
	public void quadCube() throws Throwable
	{
		ByteBuffer buffer = ByteBuffer.allocate(MiB);
		FloatBuffer floats = buffer.asFloatBuffer();
		float[] base = new float[] {0.0f, 0.0f, 0.0f};
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float textureSize = 1.0f;
		GraphicsHelpers.drawCube(floats, base, (byte)4, uvBase, textureSize);
		floats.flip();
		// We should see the 6 cube faces, each composed of 16 quad tiles (2 triangles or 6 vertices).
		Assert.assertEquals(6 * 16 * 2 * 3 * GraphicsHelpers.FLOATS_PER_VERTEX, floats.remaining());
	}
}
