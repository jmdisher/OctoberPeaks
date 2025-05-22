package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;


public class StatelessMultiLineButton<T> implements IStatelessView<T>
{
	private final GlUi _ui;
	private final ITransformer<T> _transformer;
	private final Consumer<T> _actionConsumer;
	private final int _lineCount;

	public StatelessMultiLineButton(GlUi ui
			, ITransformer<T> transformer
			, Consumer<T> actionConsumer
	)
	{
		_ui = ui;
		_transformer = transformer;
		_actionConsumer = actionConsumer;
		_lineCount = transformer.getLineCount();
	}

	@Override
	public IAction render(Rect bounds, Point cursor, T data)
	{
		int outlineTexture = _transformer.getOutlineTexture(data);
		boolean shouldHighlight = bounds.containsPoint(cursor);
		UiIdioms.drawOutlineColour(_ui, bounds, outlineTexture, shouldHighlight);
		float leftX = bounds.leftX() + UiIdioms.OUTLINE_SIZE;
		float rightX = bounds.rightX() - UiIdioms.OUTLINE_SIZE;
		float lineHeight = bounds.getHeight() / (float)_lineCount;
		float nextTop = bounds.topY();
		for (int i = 0; i < _lineCount; ++i)
		{
			float bottom = nextTop - lineHeight;
			Rect lineRect = new Rect(leftX, bottom, rightX, nextTop);
			String line = _transformer.getLine(data, i);
			UiIdioms.drawTextLeft(_ui, lineRect, line);
			nextTop = bottom;
		}
		
		// If the cursor is over this and this instance can perform actions, return an action object.
		return (shouldHighlight && (null != _actionConsumer))
				? new _Action(data)
				: null
		;
	}


	public static interface ITransformer<T>
	{
		public int getLineCount();
		public int getOutlineTexture(T data);
		public String getLine(T data, int line);
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
