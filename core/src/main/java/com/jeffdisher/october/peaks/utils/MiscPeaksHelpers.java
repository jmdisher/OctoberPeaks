package com.jeffdisher.october.peaks.utils;

import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.subactions.EntitySubActionPlaceSelectedBlockGeneric;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.utils.Assert;


/**
 * Miscellaneous helper functions specific to OctoberPeaks.
 */
public class MiscPeaksHelpers
{
	public static String readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	public static FacingDirection findBlockPlacementDirection(Environment env
		, AbsoluteLocation solidBlock
		, AbsoluteLocation emptyBlock
		, byte yaw
		, Block placedBlock
	)
	{
		Assert.assertTrue(null != solidBlock);
		Assert.assertTrue(null != emptyBlock);
		Assert.assertTrue(null != placedBlock);
		
		// We prioritize facing the solidBlock but will fall back to yaw if the block requires a different orientation.
		FacingDirection direction;
		if (solidBlock.x() > emptyBlock.x())
		{
			direction = FacingDirection.EAST;
		}
		else if (solidBlock.x() < emptyBlock.x())
		{
			direction = FacingDirection.WEST;
		}
		else if (solidBlock.y() > emptyBlock.y())
		{
			direction = FacingDirection.NORTH;
		}
		else if (solidBlock.y() < emptyBlock.y())
		{
			direction = FacingDirection.SOUTH;
		}
		else if (solidBlock.z() > emptyBlock.z())
		{
			direction = FacingDirection.UP;
		}
		else if (solidBlock.z() < emptyBlock.z())
		{
			direction = FacingDirection.DOWN;
		}
		else
		{
			direction = null;
		}
		
		// See if we need to check yaw or if this is sufficient.
		if (!EntitySubActionPlaceSelectedBlockGeneric.isValidOrientationForBlock(env, placedBlock, direction))
		{
			direction = OrientationHelpers.getYawDirection(yaw);
			if (!EntitySubActionPlaceSelectedBlockGeneric.isValidOrientationForBlock(env, placedBlock, direction))
			{
				direction = null;
			}
		}
		return direction;
	}
}
