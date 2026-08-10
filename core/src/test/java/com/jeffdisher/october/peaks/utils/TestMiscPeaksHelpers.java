package com.jeffdisher.october.peaks.utils;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;


public class TestMiscPeaksHelpers
{
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicDirection() throws Throwable
	{
		Block log = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		Block stair = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_stair"));
		Block slab = ENV.blocks.fromItem(ENV.items.getItemById("op.stone_brick_slab"));
		Block door = ENV.blocks.fromItem(ENV.items.getItemById("op.door"));
		
		// We will click against the down block but face West.
		AbsoluteLocation emptyBlock = new AbsoluteLocation(5, 6, 7);
		AbsoluteLocation solidBlock = emptyBlock.getRelative(0, 0, -1);
		byte yaw = OrientationHelpers.YAW_WEST;
		
		Assert.assertNull(MiscPeaksHelpers.findBlockPlacementDirection(ENV, solidBlock, emptyBlock, yaw, log));
		Assert.assertEquals(FacingDirection.WEST, MiscPeaksHelpers.findBlockPlacementDirection(ENV, solidBlock, emptyBlock, yaw, stair));
		Assert.assertEquals(FacingDirection.DOWN, MiscPeaksHelpers.findBlockPlacementDirection(ENV, solidBlock, emptyBlock, yaw, slab));
		Assert.assertEquals(FacingDirection.WEST, MiscPeaksHelpers.findBlockPlacementDirection(ENV, solidBlock, emptyBlock, yaw, door));
	}
}
