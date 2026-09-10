package com.jeffdisher.october.peaks.graphics;

import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;


public class TestSubBlockMesh
{
	private static Environment ENV;
	private static Block STONE_STAIR;
	private static Block STONE_SLAB;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE_STAIR = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_stair"));
		STONE_SLAB = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void stair() throws Throwable
	{
		SubBlockMesh mesh = SubBlockMesh.builder(ENV.blocks.getSubBlocks(STONE_STAIR, false));
		List<SubBlockMesh.Face> north = mesh.getFaces(FacingDirection.UP, FacingDirection.NORTH);
		Assert.assertEquals(4, north.size());
		Assert.assertArrayEquals(new float[] {0.0f, 0.0f, 0.5f}, north.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.25f, 0.5f}, north.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.25f, 0.5f}, north.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.5f, 0.5f}, north.get(1).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.5f, 1.0f}, north.get(2).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.75f, 1.0f}, north.get(2).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.75f, 1.0f}, north.get(3).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 1.0f, 1.0f}, north.get(3).edge3, 0.01f);
		
		List<SubBlockMesh.Face> west = mesh.getFaces(FacingDirection.UP, FacingDirection.WEST);
		Assert.assertEquals(4, west.size());
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 0.5f}, west.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.75f, 1.0f, 0.5f}, west.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.75f, 0.0f, 0.5f}, west.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.5f, 1.0f, 0.5f}, west.get(1).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.5f, 0.0f, 1.0f}, west.get(2).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.25f, 1.0f, 1.0f}, west.get(2).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.25f, 0.0f, 1.0f}, west.get(3).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 1.0f, 1.0f}, west.get(3).edge3, 0.01f);
		
		List<SubBlockMesh.Face> flippedEast = mesh.getFaces(FacingDirection.UP, FacingDirection.FLIPPED_EAST);
		Assert.assertEquals(4, flippedEast.size());
		Assert.assertArrayEquals(new float[] {0.0f, 1.0f, 1.0f}, flippedEast.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.25f, 0.0f, 1.0f}, flippedEast.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.25f, 1.0f, 1.0f}, flippedEast.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.5f, 0.0f, 1.0f}, flippedEast.get(1).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.5f, 1.0f, 1.0f}, flippedEast.get(2).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.75f, 0.0f, 1.0f}, flippedEast.get(2).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.75f, 1.0f, 1.0f}, flippedEast.get(3).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 1.0f}, flippedEast.get(3).edge3, 0.01f);
		
		List<SubBlockMesh.Face> flippedEastFromSouth = mesh.getFaces(FacingDirection.SOUTH, FacingDirection.FLIPPED_EAST);
		Assert.assertEquals(4, flippedEastFromSouth.size());
		Assert.assertArrayEquals(new float[] {0.0f, 0.0f, 1.0f}, flippedEastFromSouth.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 0.75f}, flippedEastFromSouth.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.0f, 0.75f}, flippedEastFromSouth.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 0.5f}, flippedEastFromSouth.get(1).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.5f, 0.0f, 0.5f}, flippedEastFromSouth.get(2).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 0.25f}, flippedEastFromSouth.get(2).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.5f, 0.0f, 0.25f}, flippedEastFromSouth.get(3).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 0.0f}, flippedEastFromSouth.get(3).edge3, 0.01f);
	}

	@Test
	public void slab() throws Throwable
	{
		SubBlockMesh mesh = SubBlockMesh.builder(ENV.blocks.getSubBlocks(STONE_SLAB, false));
		List<SubBlockMesh.Face> north = mesh.getFaces(FacingDirection.UP, FacingDirection.NORTH);
		Assert.assertEquals(2, north.size());
		Assert.assertArrayEquals(new float[] {0.0f, 0.5f, 1.0f}, north.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.75f, 1.0f}, north.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.75f, 1.0f}, north.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 1.0f, 1.0f}, north.get(1).edge3, 0.01f);
		
		List<SubBlockMesh.Face> west = mesh.getFaces(FacingDirection.UP, FacingDirection.WEST);
		Assert.assertEquals(2, west.size());
		Assert.assertArrayEquals(new float[] {0.5f, 0.0f, 1.0f}, west.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.25f, 1.0f, 1.0f}, west.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.25f, 0.0f, 1.0f}, west.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 1.0f, 1.0f}, west.get(1).edge3, 0.01f);
		
		List<SubBlockMesh.Face> up = mesh.getFaces(FacingDirection.UP, FacingDirection.UP);
		Assert.assertEquals(4, up.size());
		Assert.assertArrayEquals(new float[] {0.0f, 1.0f, 1.0f}, up.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.75f, 1.0f}, up.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.75f, 1.0f}, up.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.5f, 1.0f}, up.get(1).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.5f, 1.0f}, up.get(2).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.25f, 1.0f}, up.get(2).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.25f, 1.0f}, up.get(3).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 1.0f}, up.get(3).edge3, 0.01f);
		
		List<SubBlockMesh.Face> down = mesh.getFaces(FacingDirection.DOWN, FacingDirection.UP);
		Assert.assertEquals(4, down.size());
		Assert.assertArrayEquals(new float[] {0.0f, 1.0f, 0.5f}, down.get(0).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.75f, 0.5f}, down.get(0).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.75f, 0.5f}, down.get(1).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.5f, 0.5f}, down.get(1).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.5f, 0.5f}, down.get(2).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.25f, 0.5f}, down.get(2).edge3, 0.01f);
		Assert.assertArrayEquals(new float[] {0.0f, 0.25f, 0.5f}, down.get(3).base3, 0.01f);
		Assert.assertArrayEquals(new float[] {1.0f, 0.0f, 0.5f}, down.get(3).edge3, 0.01f);
	}
}
