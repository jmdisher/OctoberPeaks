package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.types.Item;

/**
 * A simple view for drawing the fuel slot in the furnace view (since it shouldn't overload the other view uses).
 */
public class ViewFuelSlot implements IView
{
	private final Binding<ItemTuple<Void>> _bottomWindowFuelBinding;
	private final StatelessViewItemTuple<ItemTuple<Void>> _stateless;

	public ViewFuelSlot(GlUi ui
		, Binding<ItemTuple<Void>> bottomWindowFuelBinding
	)
	{
		_bottomWindowFuelBinding = bottomWindowFuelBinding;
		
		Function<ItemTuple<Void>, Item> typeValueTransformer = (ItemTuple<Void> desc) -> desc.type();
		ToIntFunction<ItemTuple<Void>> numberLabelValueTransformer = (ItemTuple<Void> desc) -> desc.count();
		StatelessViewItemTuple.ToFloatFunction<ItemTuple<Void>> progressBarValueTransformer = (ItemTuple<Void> desc) -> desc.durability();
		_stateless = new StatelessViewItemTuple<>(ui
			// We always just show the same background for fuel.
			, (ItemTuple<Void> ignored) -> ui.pixelLightGrey
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
		ItemTuple<Void> tuple = _bottomWindowFuelBinding.get();
		return _stateless.render(location, cursor, tuple);
	}
}
