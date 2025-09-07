package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Rendering of the hotbar "window".
 */
public class ViewHotbar implements IView
{
	public static final float HOTBAR_ITEM_SCALE = 0.1f;
	public static final float HOTBAR_ITEM_SPACING = 0.05f;
	public static final float HOTBAR_BOTTOM_Y = -0.95f;
	public static final float HOTBAR_WIDTH = ((float)Entity.HOTBAR_SIZE * HOTBAR_ITEM_SCALE) + ((float)(Entity.HOTBAR_SIZE - 1) * HOTBAR_ITEM_SPACING);

	public static final Rect LOCATION = new Rect(- HOTBAR_WIDTH / 2.0f, HOTBAR_BOTTOM_Y, HOTBAR_WIDTH / 2.0f, HOTBAR_BOTTOM_Y + HOTBAR_ITEM_SCALE);

	private final Binding<Entity> _binding;
	private final StatelessViewItemTuple<ItemTuple<Boolean>> _stateless;

	public ViewHotbar(GlUi ui
			, Binding<Entity> binding
	)
	{
		_binding = binding;
		
		// Note that we will pre-process the data before invoking stateless, based on entity context, so this is in terms of Boolean.
		ToIntFunction<ItemTuple<Boolean>> outlineTextureValueTransformer = (ItemTuple<Boolean> desc) -> desc.context() ? ui.pixelGreen : ui.pixelLightGrey;
		Function<ItemTuple<Boolean>, Item> typeValueTransformer = (ItemTuple<Boolean> desc) -> desc.type();
		ToIntFunction<ItemTuple<Boolean>> numberLabelValueTransformer = (ItemTuple<Boolean> desc) -> desc.count();
		StatelessViewItemTuple.ToFloatFunction<ItemTuple<Boolean>> progressBarValueTransformer = (ItemTuple<Boolean> desc) -> desc.durability();
		_stateless = new StatelessViewItemTuple<>(ui
			, outlineTextureValueTransformer
			, typeValueTransformer
			, numberLabelValueTransformer
			, progressBarValueTransformer
			, null
			, null
		);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		Environment env = Environment.getShared();
		float nextLeftButton = location.leftX();
		Entity entity = _binding.get();
		Inventory entityInventory = _getEntityInventory(entity);
		int[] hotbarKeys = entity.hotbarItems();
		int activeIndex = entity.hotbarIndex();
		for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
		{
			boolean isActive = (activeIndex == i);
			int thisKey = hotbarKeys[i];
			ItemTuple<Boolean> innerValue;
			if (0 == thisKey)
			{
				// No item so just draw the frame.
				innerValue = new ItemTuple<>(null, 0, 0.0f, isActive);
			}
			else
			{
				// There is something here so render it.
				Items stack = entityInventory.getStackForKey(thisKey);
				NonStackableItem nonStack = entityInventory.getNonStackableForKey(thisKey);
				innerValue = ItemTuple.commonFromItems(env, stack, nonStack, isActive);
			}
			
			// Use the composed item - we ignore the response since it doesn't do anything.
			_stateless.render(new Rect(nextLeftButton, location.bottomY(), nextLeftButton + HOTBAR_ITEM_SCALE, location.topY()), cursor, innerValue);
			
			nextLeftButton += HOTBAR_ITEM_SCALE + HOTBAR_ITEM_SPACING;
		}
		
		// No hover or action.
		return null;
	}


	private static Inventory _getEntityInventory(Entity entity)
	{
		Inventory inventory = entity.isCreativeMode()
				? CreativeInventory.fakeInventory()
				: entity.inventory()
		;
		return inventory;
	}
}
