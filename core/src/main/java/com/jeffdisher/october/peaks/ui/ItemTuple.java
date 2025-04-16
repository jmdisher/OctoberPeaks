package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * This is just a convenient way of passing around an item which is either stackable or not.
 * Note that it is possible for both to be null if the context is only being used for some empty space event.
 */
public record ItemTuple<T>(Items stackable, NonStackableItem nonStackable, T context)
{
}
