package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;


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
	private final StatelessViewItemTuple<_BarTuple> _stateless;

	public ViewHotbar(GlUi ui
			, Binding<Entity> binding
	)
	{
		_binding = binding;
		
		// Note that we could just store the inventory keys and reach into the bound Entity on each call but these are
		// all always visible so we pre-process them in a single pass in render().
		ToIntFunction<_BarTuple> outlineTextureValueTransformer = (_BarTuple desc) -> desc.isActive ? ui.pixelGreen : ui.pixelLightGrey;
		Function<_BarTuple, Item> typeValueTransformer = (_BarTuple desc) -> desc.getType();
		ToIntFunction<_BarTuple> numberLabelValueTransformer = (_BarTuple desc) -> desc.getCount();
		StatelessViewItemTuple.ToFloatFunction<_BarTuple> progressBarValueTransformer = (_BarTuple desc) -> desc.getDurability();
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
		float nextLeftButton = location.leftX();
		Entity entity = _binding.get();
		Inventory entityInventory = _getEntityInventory(entity);
		int[] hotbarKeys = entity.hotbarItems();
		int activeIndex = entity.hotbarIndex();
		for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
		{
			boolean isActive = (activeIndex == i);
			int thisKey = hotbarKeys[i];
			_BarTuple innerValue;
			if (0 == thisKey)
			{
				// No item so just draw the frame.
				innerValue = new _BarTuple(null, isActive);
			}
			else
			{
				// There is something here so render it.
				ItemSlot slot = entityInventory.getSlotForKey(thisKey);
				innerValue = new _BarTuple(slot, isActive);
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


	private static record _BarTuple(ItemSlot slot, boolean isActive)
	{
		public Item getType()
		{
			Item type = null;
			if (null != this.slot)
			{
				type = (null != slot.stack) ? slot.stack.type() : slot.nonStackable.type();
			}
			return type;
		}
		public int getCount()
		{
			// Only called on non-null type.
			return (null != slot.stack) ? slot.stack.count() : 0;
		}
		public float getDurability()
		{
			// Only called on non-null type.
			float durability = 0.0f;
			if (null != slot.nonStackable)
			{
				Environment env = Environment.getShared();
				Item type = slot.nonStackable.type();
				durability = (float)PropertyHelpers.getDurability(slot.nonStackable) / (float)env.durability.getDurability(type);
			}
			return durability;
		}
	}
}
