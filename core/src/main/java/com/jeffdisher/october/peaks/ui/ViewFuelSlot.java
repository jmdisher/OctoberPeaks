package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.types.Item;

/**
 * A simple view for drawing the fuel slot in the furnace view (since it shouldn't overload the other view uses).
 */
public class ViewFuelSlot implements IView
{
	private final Binding<FuelTuple> _bottomWindowFuelBinding;
	private final StatelessViewItemTuple<FuelTuple> _stateless;

	public ViewFuelSlot(GlUi ui
		, Binding<FuelTuple> bottomWindowFuelBinding
	)
	{
		_bottomWindowFuelBinding = bottomWindowFuelBinding;
		
		Function<FuelTuple, Item> typeValueTransformer = (FuelTuple desc) -> desc.type;
		ToIntFunction<FuelTuple> numberLabelValueTransformer = (FuelTuple ignored) -> 0;
		StatelessViewItemTuple.ToFloatFunction<FuelTuple> progressBarValueTransformer = (FuelTuple desc) -> desc.remaining;
		_stateless = new StatelessViewItemTuple<>(ui
			// We always just show the same background for fuel.
			, (FuelTuple ignored) -> ui.pixelLightGrey
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
		FuelTuple tuple = _bottomWindowFuelBinding.get();
		return _stateless.render(location, cursor, tuple);
	}


	public static record FuelTuple(Item type, float remaining) {}
}
