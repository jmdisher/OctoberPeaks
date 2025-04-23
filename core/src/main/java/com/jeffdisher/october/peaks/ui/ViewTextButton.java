package com.jeffdisher.october.peaks.ui;

import java.util.function.BiConsumer;
import java.util.function.Function;


public class ViewTextButton<T> implements IView
{
	private final GlUi _ui;
	private final Binding<T> _dataBinding;
	private final Function<T, String> _valueTransformer;
	private final BiConsumer<ViewTextButton<T>, T> _hoverAction;

	public ViewTextButton(GlUi ui
			, Binding<T> dataBinding
			, Function<T, String> valueTransformer
			, BiConsumer<ViewTextButton<T>, T> hoverAction
	)
	{
		_ui = ui;
		_dataBinding = dataBinding;
		_valueTransformer = valueTransformer;
		_hoverAction = hoverAction;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		T object = _dataBinding.get();
		String text = _valueTransformer.apply(object);
		boolean didClick = location.containsPoint(cursor);
		UiIdioms.drawOutline(_ui, location, didClick);
		UiIdioms.drawTextCentred(_ui, location, text);
		return didClick
				? new _Action(object)
				: null
		;
	}


	private class _Action implements IAction
	{
		private final T _object;
		public _Action(T object)
		{
			_object = object;
		}
		@Override
		public void renderHover(Point cursor)
		{
			// No hover.
		}
		@Override
		public void takeAction()
		{
			_hoverAction.accept(ViewTextButton.this, _object);
		}
	}
}
