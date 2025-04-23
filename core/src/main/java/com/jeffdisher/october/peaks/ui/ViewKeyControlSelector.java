package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;

import com.badlogic.gdx.Input.Keys;
import com.jeffdisher.october.peaks.types.MutableControls;


/**
 * A view class which is specially tailored to displaying and modifying MutableControls.
 * In the future, this will probably be refactored into smaller view types in a larger composition.
 */
public class ViewKeyControlSelector implements IView
{
	public static final float ROW_HEIGHT = 0.1f;

	private final GlUi _ui;
	private final MutableControls _controls;
	private final Consumer<MutableControls.Control> _hoverAction;

	public ViewKeyControlSelector(GlUi ui
			, MutableControls controls
			, Consumer<MutableControls.Control> hoverAction
	)
	{
		_ui = ui;
		_controls = controls;
		_hoverAction = hoverAction;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// We want to draw this as a 2-column table where the title takes up 75% of the space, right-justified names, and the keycode can be shown on the right side in a selector button.
		float fourthWidth = location.getWidth() / 4.0f;
		float split = location.rightX() - fourthWidth;
		float nextItemTop = location.topY();
		float nameCentreX = (location.leftX() + split) / 2.0f;
		_Action action = null;
		for (MutableControls.Control control : MutableControls.Control.values())
		{
			float bottom = nextItemTop - ROW_HEIGHT;
			Rect keyRect = new Rect(split, bottom, location.rightX(), nextItemTop);
			String key = Keys.toString(_controls.getKeyCode(control));
			
			UiIdioms.drawRawTextCentredAtTop(_ui, nameCentreX, nextItemTop, control.description);
			boolean didClick = keyRect.containsPoint(cursor);
			UiIdioms.drawOutline(_ui, keyRect, didClick);
			UiIdioms.drawTextCentred(_ui, keyRect, key);
			if (didClick)
			{
				action = new _Action(control);
			}
			nextItemTop -= ROW_HEIGHT;
		}
		return action;
	}


	private class _Action implements IAction
	{
		private final MutableControls.Control _object;
		public _Action(MutableControls.Control object)
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
