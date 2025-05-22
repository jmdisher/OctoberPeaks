package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;
import java.util.function.Function;


public class StatelessViewTextButton<T> implements IStatelessView<T>
{
	private final GlUi _ui;
	private final Function<T, String> _valueTransformer;
	private final Consumer<T> _actionConsumer;

	public StatelessViewTextButton(GlUi ui
			, Function<T, String> valueTransformer
			, Consumer<T> actionConsumer
	)
	{
		_ui = ui;
		_valueTransformer = valueTransformer;
		_actionConsumer = actionConsumer;
	}

	@Override
	public IAction render(Rect bounds, Point cursor, T data)
	{
		boolean shouldHighlight = bounds.containsPoint(cursor);
		UiIdioms.drawOutline(_ui, bounds, shouldHighlight);
		String transformed = _valueTransformer.apply(data);
		UiIdioms.drawTextCentred(_ui, bounds, transformed);
		
		// If the cursor is over this and this instance can perform actions, return an action object.
		return (shouldHighlight && (null != _actionConsumer))
				? new _Action(data)
				: null
		;
	}


	private class _Action implements IAction
	{
		private final T _data;
		public _Action(T data)
		{
			_data = data;
		}
		@Override
		public void renderHover(Point cursor)
		{
			// Do nothing.
		}
		@Override
		public void takeAction()
		{
			_actionConsumer.accept(_data);
		}
	}
}
