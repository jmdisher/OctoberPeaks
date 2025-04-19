package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;


public class ViewTextButton implements IView
{
	private final GlUi _ui;
	private final Binding<String> _titleBinding;
	private final Consumer<ViewTextButton> _hoverAction;

	public ViewTextButton(GlUi ui
			, Binding<String> titleBinding
			, Consumer<ViewTextButton> hoverAction
	)
	{
		_ui = ui;
		_titleBinding = titleBinding;
		_hoverAction = hoverAction;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		String text = _titleBinding.get();
		boolean didClick = UiIdioms.drawButtonRootedAtTop(_ui, location.leftX(), location.topY(), text, cursor);
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
			_hoverAction.accept(ViewTextButton.this);
		}
	}
}
