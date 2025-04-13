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
		return (Rect location, Binding<Entity> binding) -> {
			float nextLeftButton = location.leftX();
			Inventory entityInventory = _getEntityInventory(binding);
			int[] hotbarKeys = binding.data.hotbarItems();
			int activeIndex = binding.data.hotbarIndex();
			for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
			{
				int outline = (activeIndex == i)
						? ui.pixelGreen
						: ui.pixelLightGrey
				;
				int thisKey = hotbarKeys[i];
				if (0 == thisKey)
				{
					// No item so just draw the frame.
					UiIdioms.drawOverlayFrame(ui, ui.pixelDarkGreyAlpha, outline, nextLeftButton, location.bottomY(), nextLeftButton + HOTBAR_ITEM_SCALE, location.topY());
				}
				else
				{
					// There is something here so render it.
					Items stack = entityInventory.getStackForKey(thisKey);
					if (null != stack)
					{
						UiIdioms.renderStackableItem(ui, nextLeftButton, location.bottomY(), nextLeftButton + HOTBAR_ITEM_SCALE, location.topY(), outline, stack, false);
					}
					else
					{
						NonStackableItem nonStack = entityInventory.getNonStackableForKey(thisKey);
						UiIdioms.renderNonStackableItem(ui, nextLeftButton, location.bottomY(), nextLeftButton + HOTBAR_ITEM_SCALE, location.topY(), outline, nonStack, false);
					}
				}
				nextLeftButton += HOTBAR_ITEM_SCALE + HOTBAR_ITEM_SPACING;
			}
		};
	}


	private static Inventory _getEntityInventory(Binding<Entity> entityBinding)
	{
		Inventory inventory = entityBinding.data.isCreativeMode()
				? CreativeInventory.fakeInventory()
				: entityBinding.data.inventory()
		;
		return inventory;
	}
}
