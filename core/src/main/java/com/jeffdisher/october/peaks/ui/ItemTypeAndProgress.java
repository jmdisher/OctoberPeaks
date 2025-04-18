package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Item;


/**
 * A simple tuple used in some basic rendering operations.
 */
public record ItemTypeAndProgress(Item type, float progress)
{
}
