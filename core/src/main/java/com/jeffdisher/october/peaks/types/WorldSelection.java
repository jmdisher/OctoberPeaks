package com.jeffdisher.october.peaks.types;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.PartialEntity;


// Either the entity or the blocks are non-null, never both.
public record WorldSelection(PartialEntity entity
		, AbsoluteLocation stopBlock
		, AbsoluteLocation preStopBlock
)
{
}
