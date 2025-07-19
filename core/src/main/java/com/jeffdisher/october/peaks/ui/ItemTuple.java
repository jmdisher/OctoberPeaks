package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * Used to pass around information for describing how to render an item tile in the UI:
 * -type: Can be null but normally is the type (either stackable or not)
 * -count: The count of items (if 0, this shouldn't be drawn)
 * -durability: A [0.0..1.0] value to show durability or a progress bar (if 0.0, not drawn).
 */
public record ItemTuple<T>(Item type, int count, float durability, T context)
{
	public static <T> ItemTuple<T> commonFromItems(Environment env, Items stack, NonStackableItem nonStack, T context)
	{
		ItemTuple<T> tuple;
		if (null != stack)
		{
			Assert.assertTrue(null == nonStack);
			tuple = new ItemTuple<>(stack.type(), stack.count(), 0.0f, context);
		}
		else if (null != nonStack)
		{
			Item type = nonStack.type();
			float durability = (float)PropertyHelpers.getDurability(nonStack) / (float)env.durability.getDurability(type);
			tuple = new ItemTuple<>(type, 0, durability, context);
		}
		else
		{
			tuple = new ItemTuple<>(null, 0, 0.0f, context);
			
		}
		return tuple;
	}
}
