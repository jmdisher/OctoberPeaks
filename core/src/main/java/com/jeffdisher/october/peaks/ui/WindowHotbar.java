package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Rendering of the hotbar "window".
 */
public class WindowHotbar
{
	public static final float HOTBAR_ITEM_SCALE = 0.1f;
	public static final float HOTBAR_ITEM_SPACING = 0.05f;
	public static final float HOTBAR_BOTTOM_Y = -0.95f;
	public static final float HOTBAR_WIDTH = ((float)Entity.HOTBAR_SIZE * HOTBAR_ITEM_SCALE) + ((float)(Entity.HOTBAR_SIZE - 1) * HOTBAR_ITEM_SPACING);

	public static final Rect LOCATION = new Rect(- HOTBAR_WIDTH / 2.0f, HOTBAR_BOTTOM_Y, HOTBAR_WIDTH / 2.0f, HOTBAR_BOTTOM_Y + HOTBAR_ITEM_SCALE);

	public static IView<Entity> buildRenderer(GlUi ui)
	{
		// We only care about whether or not this is selected so we will pass in a boolean.
		ComplexItemView.IBindOptions<Boolean> options = new ComplexItemView.IBindOptions<Boolean>()
		{
			@Override
			public int getOutlineTexture(ItemTuple<Boolean> context)
			{
				return context.context()
						? ui.pixelGreen
						: ui.pixelLightGrey
				;
			}
			@Override
			public void hoverRender(Point cursor, ItemTuple<Boolean> context)
			{
				// Nothing.
			}
			@Override
			public void hoverAction(ItemTuple<Boolean> context)
			{
				// No action.
			}
		};
		IView<ItemTuple<Boolean>> itemView = ComplexItemView.buildRenderer(ui, options, false);
		Binding<ItemTuple<Boolean>> innerBinding = new Binding<>();
		
		return (Rect location, Binding<Entity> binding, Point cursor) -> {
			float nextLeftButton = location.leftX();
			Entity entity = binding.get();
			Inventory entityInventory = _getEntityInventory(entity);
			int[] hotbarKeys = entity.hotbarItems();
			int activeIndex = entity.hotbarIndex();
			for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
			{
				boolean isActive = (activeIndex == i);
				int thisKey = hotbarKeys[i];
				if (0 == thisKey)
				{
					// No item so just draw the frame.
					innerBinding.set(new ItemTuple<>(null, null, isActive));
				}
				else
				{
					// There is something here so render it.
					Items stack = entityInventory.getStackForKey(thisKey);
					NonStackableItem nonStack = entityInventory.getNonStackableForKey(thisKey);
					innerBinding.set(new ItemTuple<>(stack, nonStack, isActive));
				}
				
				// Use the composed item - we ignore the response since it doesn't do anything.
				itemView.render(new Rect(nextLeftButton, location.bottomY(), nextLeftButton + HOTBAR_ITEM_SCALE, location.topY()), innerBinding, cursor);
				
				nextLeftButton += HOTBAR_ITEM_SCALE + HOTBAR_ITEM_SPACING;
			}
			
			// No hover or action.
			return null;
		};
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
