package com.jeffdisher.october.peaks.ui;


/**
 * A view which displays a large chunk of wrapped text over a simple background.
 */
public class ViewTextBox implements IView
{
	private final GlUi _ui;
	private final Binding<String> _binding;

	public ViewTextBox(GlUi ui
		, Binding<String> binding
	)
	{
		_ui = ui;
		_binding = binding;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		String text = _binding.get();
		UiIdioms.drawWrappedTextOnBackground(_ui, location, text);
		return null;
	}
}
