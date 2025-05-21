package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;
import java.util.function.IntSupplier;


/**
 * A view class for displaying text data.  The hover and binding can be manipulated externally in order to use this for
 * text input.
 */
public class ViewTextField<T> implements IView
{
	public static final float ROW_HEIGHT = 0.1f;

	private final GlUi _ui;
	private final Binding<T> _binding;
	private final Function<T, String> _valueTransformer;
	private final IntSupplier _outlineSupplier;
	private final Runnable _hoverAction;

	public ViewTextField(GlUi ui
			, Binding<T> binding
			, Function<T, String> valueTransformer
			, IntSupplier outlineSupplier
			, Runnable hoverAction
	)
	{
		_ui = ui;
		_binding = binding;
		_valueTransformer = valueTransformer;
		_outlineSupplier = outlineSupplier;
		_hoverAction = hoverAction;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		int outlineTexture = _outlineSupplier.getAsInt();
		T object = _binding.get();
		String text = _valueTransformer.apply(object);
		boolean didClick = location.containsPoint(cursor);
		UiIdioms.drawOutlineColour(_ui, location, outlineTexture, didClick);
		UiIdioms.drawTextCentred(_ui, location, text);
		return didClick
				? new _Action()
				: null
		;
	}


	private class _Action implements IAction
	{
		@Override
		public void renderHover(Point cursor)
		{
			// No hover.
		}
		@Override
		public void takeAction()
		{
			_hoverAction.run();
		}
	}
}
