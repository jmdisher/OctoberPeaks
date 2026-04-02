package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;


/**
 * Similar to ViewTextLabel but allows the data binding to be anything, so long as there is string transformer.
 * This is a basic string label written into the centre of its bounds.
 * The value is pulled from bindings.
 */
public class ViewGenericLabel<T> implements IView
{
	private final GlUi _ui;
	private final Binding<T> _dataBinding;
	private final Function<T, String> _valueTransformer;

	public ViewGenericLabel(GlUi ui
		, Binding<T> dataBinding
		, Function<T, String> valueTransformer
	)
	{
		_ui = ui;
		_dataBinding = dataBinding;
		_valueTransformer = valueTransformer;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		T object = _dataBinding.get();
		String text = _valueTransformer.apply(object);
		UiIdioms.drawTextCentred(_ui, location, text);
		return null;
	}
}
