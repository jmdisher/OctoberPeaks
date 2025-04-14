package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;

import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Rendering of the armour slot "window".
 */
public class WindowArmour
{
	public static final float ARMOUR_SLOT_SCALE = 0.1f;
	public static final float ARMOUR_SLOT_SPACING = 0.05f;
	public static final float ARMOUR_SLOT_RIGHT_EDGE = 0.95f;
	public static final float ARMOUR_SLOT_TOP_EDGE = 0.95f;

	public static final Rect LOCATION = new Rect(ARMOUR_SLOT_RIGHT_EDGE - ARMOUR_SLOT_SCALE, ARMOUR_SLOT_TOP_EDGE - (4.0f * ARMOUR_SLOT_SCALE + 3.0f * ARMOUR_SLOT_SPACING), ARMOUR_SLOT_RIGHT_EDGE, ARMOUR_SLOT_TOP_EDGE);

	public static IView<Entity> buildRenderer(GlUi ui, Consumer<BodyPart> eventHoverBodyPart)
	{
		return (Rect location, Binding<Entity> binding, Point cursor) -> {
			NonStackableItem[] armourSlots = binding.data.armourSlots();
			float nextTopSlot = location.topY();
			for (int i = 0; i < 4; ++i)
			{
				float left = location.leftX();
				float bottom = nextTopSlot - ARMOUR_SLOT_SCALE;
				float right = location.rightX();
				float top = nextTopSlot;
				boolean isMouseOver = UiIdioms.isMouseOver(left, bottom, right, top, cursor);
				
				// See if there is an item for this slot.
				NonStackableItem armour = armourSlots[i];
				
				if (null != armour)
				{
					// Draw this item.
					UiIdioms.renderNonStackableItem(ui, left, bottom, right, top, ui.pixelLightGrey, armour, isMouseOver);
				}
				else
				{
					// Just draw the background.
					int backgroundTexture = isMouseOver
							? ui.pixelLightGrey
							: ui.pixelDarkGreyAlpha
					;
					
					UiIdioms.drawOverlayFrame(ui, backgroundTexture, ui.pixelLightGrey, left, bottom, right, top);
				}
				if (isMouseOver)
				{
					eventHoverBodyPart.accept(BodyPart.values()[i]);
				}
				
				nextTopSlot -= ARMOUR_SLOT_SCALE + ARMOUR_SLOT_SPACING;
			}
		};
	}
}
