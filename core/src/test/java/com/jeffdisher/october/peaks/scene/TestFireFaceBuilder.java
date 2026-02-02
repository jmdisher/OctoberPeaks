package com.jeffdisher.october.peaks.scene;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.logic.SparseByteCube;


public class TestFireFaceBuilder
{
	@Test
	public void empty() throws Throwable
	{
		FireFaceBuilder builder = new FireFaceBuilder();
		SparseByteCube cube = builder.extractNonEmptyCollection();
		Assert.assertNull(cube);
	}

	@Test
	public void singleEdges() throws Throwable
	{
		FireFaceBuilder builder = new FireFaceBuilder();
		builder.setBit((byte)1, (byte)2, (byte)3, FireFaceBuilder.FACE_NORTH);
		builder.setBit((byte)2, (byte)3, (byte)4, FireFaceBuilder.FACE_EAST);
		builder.setBit((byte)1, (byte)2, (byte)3, FireFaceBuilder.FACE_NORTH);
		SparseByteCube cube = builder.extractNonEmptyCollection();
		Assert.assertEquals(FireFaceBuilder.FACE_NORTH, cube.get(1, 2, 3));
		Assert.assertEquals(FireFaceBuilder.FACE_EAST, cube.get(2, 3, 4));
	}

	@Test
	public void multiEdges() throws Throwable
	{
		FireFaceBuilder builder = new FireFaceBuilder();
		builder.setBit((byte)1, (byte)2, (byte)3, FireFaceBuilder.FACE_NORTH);
		builder.setBit((byte)2, (byte)3, (byte)4, FireFaceBuilder.FACE_EAST);
		builder.setBit((byte)1, (byte)2, (byte)3, FireFaceBuilder.FACE_SOUTH);
		builder.setBit((byte)1, (byte)2, (byte)3, FireFaceBuilder.FACE_EAST);
		builder.setBit((byte)1, (byte)2, (byte)3, FireFaceBuilder.FACE_DOWN);
		SparseByteCube cube = builder.extractNonEmptyCollection();
		Assert.assertEquals(FireFaceBuilder.FACE_NORTH
			| FireFaceBuilder.FACE_SOUTH
			| FireFaceBuilder.FACE_EAST
			| FireFaceBuilder.FACE_DOWN
		, cube.get(1, 2, 3));
		Assert.assertEquals(FireFaceBuilder.FACE_EAST, cube.get(2, 3, 4));
	}

	@Test
	public void checkBits() throws Throwable
	{
		byte value = FireFaceBuilder.FACE_NORTH | FireFaceBuilder.FACE_EAST;
		Assert.assertTrue(FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_NORTH));
		Assert.assertTrue(FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_EAST));
		Assert.assertFalse(FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_WEST));
		Assert.assertFalse(FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_UP));
	}
}
