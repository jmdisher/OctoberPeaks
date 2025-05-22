package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;
import java.util.function.Function;


public class StatelessViewTextButton implements IStatelessView<String>
{
	private final GlUi _ui;
	private final Function<String, String> _valueTransformer;
	private final Consumer<String> _actionConsumer;

	public StatelessViewTextButton(GlUi ui
			, Function<String, String> valueTransformer
			, Consumer<String> actionConsumer
	)
	{
		_ui = ui;
		_valueTransformer = valueTransformer;
		_actionConsumer = actionConsumer;
	}

	@Override
	public IAction render(Rect bounds, Point cursor, String data)
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
		private final String _data;
		public _Action(String data)
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
