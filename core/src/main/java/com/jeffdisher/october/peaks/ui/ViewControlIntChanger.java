package com.jeffdisher.october.peaks.ui;

import java.util.function.BiConsumer;
import java.util.function.Function;


/**
 * A view which is actually a control to display and change a value - sends the callback whenever it is changed.
 */
public class ViewControlIntChanger implements IView
{
	private final GlUi _ui;
	private final Binding<Integer> _dataBinding;
	private final Function<Integer, String> _valueTransformer;
	private final BiConsumer<ViewControlIntChanger, Integer> _hoverAction;

	public ViewControlIntChanger(GlUi ui
			, Binding<Integer> dataBinding
			, Function<Integer, String> valueTransformer
			, BiConsumer<ViewControlIntChanger, Integer> hoverAction
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
		// For now, we will just make a segmented display of 3 buttons:  - <val> + by splitting the location into thirds by X.
		Integer object = _dataBinding.get();
		String text = _valueTransformer.apply(object);
		
		float thirdWidth = location.getWidth() / 3.0f;
		float firstSplit = location.leftX() + thirdWidth;
		float secondSplit = location.rightX() - thirdWidth;
		Rect minus = new Rect(location.leftX(), location.bottomY(), firstSplit, location.topY());
		Rect display = new Rect(firstSplit, location.bottomY(), secondSplit, location.topY());
		Rect plus = new Rect(secondSplit, location.bottomY(), location.rightX(), location.topY());
		
		boolean didClickMinus = minus.containsPoint(cursor);
		boolean didClickPlus = plus.containsPoint(cursor);
		
		UiIdioms.drawOutline(_ui, minus, didClickMinus);
		UiIdioms.drawTextCentred(_ui, minus, "-");
		
		UiIdioms.drawTextCentred(_ui, display, text);
		
		UiIdioms.drawOutline(_ui, plus, didClickPlus);
		UiIdioms.drawTextCentred(_ui, plus, "+");
		
		_Action action;
		if (didClickMinus)
		{
			action = new _Action(object - 1);
		}
		else if (didClickPlus)
		{
			action = new _Action(object + 1);
		}
		else
		{
			action = null;
		}
		return action;
	}


	private class _Action implements IAction
	{
		private final Integer _object;
		public _Action(Integer object)
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
			_hoverAction.accept(ViewControlIntChanger.this, _object);
		}
	}
}
