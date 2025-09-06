package com.jeffdisher.october.peaks.ui;


/**
 * A simple view for drawing the fuel slot in the furnace view (since it shouldn't overload the other view uses).
 */
public class ViewFuelSlot implements IView
{
	private final Binding<ItemTuple<Void>> _bottomWindowFuelBinding;
	private final StatelessViewItemTuple<Void> _stateless;

	public ViewFuelSlot(GlUi ui
		, Binding<ItemTuple<Void>> bottomWindowFuelBinding
	)
	{
		_bottomWindowFuelBinding = bottomWindowFuelBinding;
		_stateless = new StatelessViewItemTuple<>(ui
			// We always just show the same background for fuel.
			, (Void ignored) -> ui.pixelLightGrey
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
