package com.jeffdisher.october.peaks.ui;


/**
 * This is a basic string label written into the centre of its bounds.
 * The value is pulled from bindings.
 */
public class ViewTextLabel implements IView
{
	private final GlUi _ui;
	private final Binding<String> _dataBinding;

	public ViewTextLabel(GlUi ui
			, Binding<String> dataBinding
	)
	{
		_ui = ui;
		_dataBinding = dataBinding;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		String text = _dataBinding.get();
		UiIdioms.drawTextCentred(_ui, location, text);
		return null;
	}
}
