package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;
import java.util.function.Function;


/**
 * For now, at least, we will state the a radio button needs to be bound to an Enum, since that gives us a natural UI
 * split.
 */
public class StatelessViewRadioButton<T extends Enum<T>> implements IStatelessView<T>
{
	private final GlUi _ui;
	private final Function<T, String> _valueTransformer;
	private final Consumer<T> _actionConsumer;
	private final Class<T> _enumType;

	public StatelessViewRadioButton(GlUi ui
			, Function<T, String> valueTransformer
			, Consumer<T> actionConsumer
			, Class<T> enumType
	)
	{
		_ui = ui;
		_valueTransformer = valueTransformer;
		_actionConsumer = actionConsumer;
		_enumType = enumType;
	}

	@Override
	public IAction render(Rect bounds, Point cursor, T data)
	{
		// We want to split the bounds we were given horizontally, by the number of types.
		float masterWidth = bounds.getWidth();
		float instanceWidth = masterWidth / (float)_enumType.getEnumConstants().length;
		float thisLeft = bounds.leftX();
		T selected = null;
		for (T instance : _enumType.getEnumConstants())
		{
			boolean isSelected = (data == instance);
			int outlineTexture = isSelected ? _ui.pixelGreen : _ui.pixelLightGrey;
			float nextLeft = thisLeft + instanceWidth;
			Rect instanceRect = new Rect(thisLeft, bounds.bottomY(), nextLeft, bounds.topY());
			boolean shouldHighlight = instanceRect.containsPoint(cursor);
			UiIdioms.drawOutlineColour(_ui, instanceRect, outlineTexture, shouldHighlight);
			String text = _valueTransformer.apply(instance);
			UiIdioms.drawTextCentred(_ui, instanceRect, text);
			thisLeft = nextLeft;
			
			if (shouldHighlight)
			{
				selected = instance;
			}
		}
		
		// If the cursor is over any option this instance can perform actions, return an action object.
		return ((null != selected) && (null != _actionConsumer))
				? new _Action(selected)
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
