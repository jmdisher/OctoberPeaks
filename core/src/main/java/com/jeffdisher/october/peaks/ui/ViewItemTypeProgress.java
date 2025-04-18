package com.jeffdisher.october.peaks.ui;


/**
 * Renders an item type with a progress bar.  Used for things like fuel slots.
 */
public class ViewItemTypeProgress implements IView<ItemTypeAndProgress>
{
	private final GlUi _ui;
	private final Binding<ItemTypeAndProgress> _binding;

	public ViewItemTypeProgress(GlUi ui
			, Binding<ItemTypeAndProgress> binding
	)
	{
		_ui = ui;
		_binding = binding;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// We only render anything at all if there is data in the binding.
		ItemTypeAndProgress data = _binding.get();
		if (null != data)
		{
			// Then we just render the type with a progress bar overlay.
			int green = _ui.pixelGreen;
			int noCount = 0;
			UiIdioms.renderItem(_ui, location.leftX(), location.bottomY(), location.rightX(), location.topY(), green, data.type(), noCount, data.progress(), false);
		}
		
		// No action in this case.
		return null;
	}
}
