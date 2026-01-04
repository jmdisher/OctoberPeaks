package com.jeffdisher.october.peaks.ui;

import java.util.function.BooleanSupplier;


/**
 * A button which can be changed on/off by clicking on it.  External users pass in the binding state of the button to
 * check the state or externally control it but this class also modifies the state in response to clicks.
 */
public class ViewToggleButton implements IView
{
	private final GlUi _ui;
	private final Binding<String> _nameBinding;
	private final Binding<Boolean> _toggleState;
	private final BooleanSupplier _didLeftClick;

	public ViewToggleButton(GlUi ui
		, Binding<String> nameBinding
		, Binding<Boolean> toggleState
		, BooleanSupplier didLeftClick
	)
	{
		_ui = ui;
		_nameBinding = nameBinding;
		_toggleState = toggleState;
		_didLeftClick = didLeftClick;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// We only honour the bottom-left of this location since we will size ourselves based on the binding value.
		String buttonTitle = _nameBinding.get();
		boolean currentState = _toggleState.get();
		int outlineColour = currentState
			? _ui.pixelGreen
			: _ui.pixelRed
		;
		boolean isHovering = UiIdioms.drawTextInColouredFrameWithHoverCheck(_ui, outlineColour, location.leftX(), location.bottomY(), buttonTitle, cursor);
		IAction validToggleAction = isHovering
			? new _ToggleAction()
			: null
		;
		return validToggleAction;
	}

	private class _ToggleAction implements IAction
	{
		@Override
		public void renderHover(Point cursor)
		{
			// Nothing to render in the hover.
		}
		@Override
		public void takeAction()
		{
			if (_didLeftClick.getAsBoolean())
			{
				_toggleState.set(!_toggleState.get());
			}
		}
	}
}
