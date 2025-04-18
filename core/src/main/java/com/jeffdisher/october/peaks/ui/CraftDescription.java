package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;


public record CraftDescription(Craft craft
		, Items output
		, ItemRequirement[] input
		, float progress
		, boolean canBeSelected
)
{
	public static record ItemRequirement(Item type
			, int required
			, int available
	) {}
}
