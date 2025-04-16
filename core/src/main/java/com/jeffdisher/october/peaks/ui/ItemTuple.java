package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * This is just a convenient way of passing around an item which is either stackable or not.
 * Note that it is possible for both to be null if the context is only being used for some empty space event.
 */
public record ItemTuple<T>(Items stackable, NonStackableItem nonStackable, T context)
{
	public Item getItemType()
	{
		Item type;
		if (null != this.stackable)
		{
			type = this.stackable.type();
		}
		else if (null != this.nonStackable)
		{
			type = this.nonStackable.type();
		}
		else
		{
			type = null;
		}
		return type;
	}
}
