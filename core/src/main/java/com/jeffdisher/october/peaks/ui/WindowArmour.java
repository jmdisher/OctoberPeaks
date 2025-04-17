package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;

import com.jeffdisher.october.types.BodyPart;
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

	public static IView<NonStackableItem[]> buildRenderer(GlUi ui, Consumer<BodyPart> eventHoverBodyPart)
	{
		// The armour is composed over ComplexItemView, with no render hover.
		ComplexItemView.IBindOptions<BodyPart> options = new ComplexItemView.IBindOptions<BodyPart>()
		{
			@Override
			public int getOutlineTexture(ItemTuple<BodyPart> context)
			{
				// We always just use the light grey, no matter.
				return ui.pixelLightGrey;
			}
			@Override
			public void hoverRender(Point cursor, ItemTuple<BodyPart> context)
			{
				// Nothing.
			}
			@Override
			public void hoverAction(ItemTuple<BodyPart> context)
			{
				// We pass this back to our consumer, since it knows how to invoke an actual action.
				eventHoverBodyPart.accept(context.context());
			}
		};
		IView<ItemTuple<BodyPart>> itemView = ComplexItemView.buildRenderer(ui, options, false);
		Binding<ItemTuple<BodyPart>> innerBinding = new Binding<>();
		
		return (Rect location, Binding<NonStackableItem[]> binding, Point cursor) -> {
			IAction action = null;
			NonStackableItem[] armourSlots = binding.get();
			float nextTopSlot = location.topY();
			for (int i = 0; i < 4; ++i)
			{
				float left = location.leftX();
				float bottom = nextTopSlot - ARMOUR_SLOT_SCALE;
				float right = location.rightX();
				float top = nextTopSlot;
				NonStackableItem armour = armourSlots[i];
				
				// We use our composed view.
				innerBinding.set(new ItemTuple<>(null, armour, BodyPart.values()[i]));
				IAction thisAction = itemView.render(new Rect(left, bottom, right, top), innerBinding, cursor);
				if (null != thisAction)
				{
					action = thisAction;
				}
				
				nextTopSlot -= ARMOUR_SLOT_SCALE + ARMOUR_SLOT_SPACING;
			}
			
			return action;
		};
	}
}
