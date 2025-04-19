package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Rendering of the armour slot "window".
 */
public class ViewArmour implements IView
{
	public static final float ARMOUR_SLOT_SCALE = 0.1f;
	public static final float ARMOUR_SLOT_SPACING = 0.05f;
	public static final float ARMOUR_SLOT_RIGHT_EDGE = 0.95f;
	public static final float ARMOUR_SLOT_TOP_EDGE = 0.95f;

	public static final Rect LOCATION = new Rect(ARMOUR_SLOT_RIGHT_EDGE - ARMOUR_SLOT_SCALE, ARMOUR_SLOT_TOP_EDGE - (4.0f * ARMOUR_SLOT_SCALE + 3.0f * ARMOUR_SLOT_SPACING), ARMOUR_SLOT_RIGHT_EDGE, ARMOUR_SLOT_TOP_EDGE);

	private final Binding<NonStackableItem[]> _binding;
	private final Binding<ItemTuple<BodyPart>> _innerBinding;
	private final IView _itemView;

	public ViewArmour(GlUi ui, Binding<NonStackableItem[]> binding, Consumer<BodyPart> eventHoverBodyPart)
	{
		_binding = binding;
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
		// We use a fake inner binding.
		_innerBinding = new Binding<>();
		_itemView = new ComplexItemView<>(ui, _innerBinding, options);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		Environment env = Environment.getShared();
		IAction action = null;
		NonStackableItem[] armourSlots = _binding.get();
		float nextTopSlot = location.topY();
		for (int i = 0; i < 4; ++i)
		{
			float left = location.leftX();
			float bottom = nextTopSlot - ARMOUR_SLOT_SCALE;
			float right = location.rightX();
			float top = nextTopSlot;
			NonStackableItem armour = armourSlots[i];
			BodyPart thisPart = BodyPart.values()[i];
			ItemTuple<BodyPart> tuple = ItemTuple.commonFromItems(env, null, armour, thisPart);
			
			// We use our composed view.
			_innerBinding.set(tuple);
			IAction thisAction = _itemView.render(new Rect(left, bottom, right, top), cursor);
			if (null != thisAction)
			{
				action = thisAction;
			}
			
			nextTopSlot -= ARMOUR_SLOT_SCALE + ARMOUR_SLOT_SPACING;
		}
		
		return action;
	}
}
