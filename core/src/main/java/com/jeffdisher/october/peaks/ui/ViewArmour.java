package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Item;
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
	private final StatelessViewItemTuple<_Tuple> _stateless;

	public ViewArmour(GlUi ui, Binding<NonStackableItem[]> binding, Consumer<BodyPart> eventHoverBodyPart)
	{
		_binding = binding;
		
		Environment env = Environment.getShared();
		// We always just use the light grey, no matter.
		ToIntFunction<_Tuple> outlineTextureValueTransformer = (_Tuple ignored) -> ui.pixelLightGrey;
		Function<_Tuple, Item> typeValueTransformer = (_Tuple tuple) ->  (null != tuple.item) ? tuple.item.type() : null;
		StatelessViewItemTuple.ToFloatFunction<_Tuple> progressBarValueTransformer = (_Tuple tuple) -> {
			return (float)PropertyHelpers.getDurability(tuple.item) / (float)env.durability.getDurability(tuple.item.type());
		};
		// We pass this back to our consumer, since it knows how to invoke an actual action.
		Consumer<_Tuple> actionConsumer = (_Tuple data) -> eventHoverBodyPart.accept(data.part);
		_stateless = new StatelessViewItemTuple<>(ui
			, outlineTextureValueTransformer
			, typeValueTransformer
			, null
			, progressBarValueTransformer
			, null
			, actionConsumer
		);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
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
			_Tuple tuple = new _Tuple(armour, thisPart);
			
			// Use the stateless to render this element.
			Rect bounds = new Rect(left, bottom, right, top);
			IAction thisAction = _stateless.render(bounds, cursor, tuple);
			if (null != thisAction)
			{
				action = thisAction;
			}
			
			nextTopSlot -= ARMOUR_SLOT_SCALE + ARMOUR_SLOT_SPACING;
		}
		
		return action;
	}


	private static record _Tuple(NonStackableItem item, BodyPart part) {}
}
