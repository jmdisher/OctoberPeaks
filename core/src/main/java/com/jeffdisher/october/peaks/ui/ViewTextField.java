package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;


/**
 * A view class for displaying text data.  The hover and binding can be manipulated externally in order to use this for
 * text input.
 */
public class ViewTextField implements IView
{
	public static final float ROW_HEIGHT = 0.1f;

	private final GlUi _ui;
	private final Binding<String> _binding;
	private final Consumer<ViewTextField> _hoverAction;

	public ViewTextField(GlUi ui
			, Binding<String> binding
			, Consumer<ViewTextField> hoverAction
	)
	{
		_ui = ui;
		_binding = binding;
		_hoverAction = hoverAction;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		String text = _binding.get();
		boolean didClick = location.containsPoint(cursor);
		UiIdioms.drawOutline(_ui, location, didClick);
		UiIdioms.drawTextCentred(_ui, location, text);
		return didClick
				? new _Action(this)
				: null
		;
	}


	private class _Action implements IAction
	{
		private final ViewTextField _object;
		public _Action(ViewTextField object)
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
			_hoverAction.accept(_object);
		}
	}
}
