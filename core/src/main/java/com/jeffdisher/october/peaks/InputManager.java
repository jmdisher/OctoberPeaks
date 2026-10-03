package com.jeffdisher.october.peaks;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.ui.Point;
import com.badlogic.gdx.Input.Keys;


/**
 * Handles input events and the corresponding state machine to make sense of these low-level events in order to
 * synthesize the higher-level meaningful events to feed into the rest of the system.
 */
public class InputManager
{
	// The mapping we use to check key codes.
	private final MutableControls _controls;
	private final InputCapture _inputCapture;

	public InputManager(MutableControls mutableControls, InputCapture inputCapture)
	{
		_controls = mutableControls;
		_inputCapture = inputCapture;
		
		Gdx.input.setInputProcessor(new InputAdapter() {
			@Override
			public boolean touchDragged(int screenX, int screenY, int pointer)
			{
				_commonMouse(screenX, screenY);
				return true;
			}
			@Override
			public boolean mouseMoved(int screenX, int screenY)
			{
				_commonMouse(screenX, screenY);
				return true;
			}
			@Override
			public boolean keyTyped(char character)
			{
				// Note that this technique might mean that we drop characters when typing too quickly (more than one char per frame).
				_inputCapture.typedCharacter = character;
				return true;
			}
			@Override
			public boolean keyDown(int keycode)
			{
				switch(keycode)
				{
				case Keys.SHIFT_LEFT:
					_inputCapture.leftShiftDown = true;
					break;
				case Keys.CONTROL_LEFT:
					_inputCapture.leftCtrlDown = true;
					break;
				}
				
				// See if one of our dynamic controls matches this.
				MutableControls.Control control = _controls.getCodeForKey(keycode);
				if (null != control)
				{
					// We can actually do something here.
					if (!control.isClickOnly)
					{
						_inputCapture.activeControls[control.ordinal()] = true;
					}
				}
				return true;
			}
			@Override
			public boolean keyUp(int keycode)
			{
				switch(keycode)
				{
				case Keys.ESCAPE:
					// We just capture the click.
					_inputCapture.didHandleKeyEsc = false;
					break;
				case Keys.NUM_1:
					_inputCapture.lastPressedNumber = 1;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_2:
					_inputCapture.lastPressedNumber = 2;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_3:
					_inputCapture.lastPressedNumber = 3;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_4:
					_inputCapture.lastPressedNumber = 4;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_5:
					_inputCapture.lastPressedNumber = 5;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_6:
					_inputCapture.lastPressedNumber = 6;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_7:
					_inputCapture.lastPressedNumber = 7;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_8:
					_inputCapture.lastPressedNumber = 8;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.NUM_9:
					_inputCapture.lastPressedNumber = 9;
					_inputCapture.didHandlePressedNumber = false;
					break;
				case Keys.SHIFT_LEFT:
					_inputCapture.leftShiftDown = false;
					break;
				case Keys.CONTROL_LEFT:
					_inputCapture.leftCtrlDown = false;
					break;
				}
				
				// See if one of our dynamic controls matches this.
				MutableControls.Control control = _controls.getCodeForKey(keycode);
				if (null != control)
				{
					// Click only is only triggered on key up while hold are only set on key down and always cleared on up.
					_inputCapture.activeControls[control.ordinal()] = control.isClickOnly;
				}
				_inputCapture.lastKeyUp = keycode;
				return true;
			}
			@Override
			public boolean touchDown(int screenX, int screenY, int pointer, int button)
			{
				switch(button)
				{
				case 0:
					_inputCapture.buttonDown0 = true;
					_inputCapture.didHandleButton0 = false;
					break;
				case 1:
					_inputCapture.buttonDown1 = true;
					_inputCapture.didHandleButton1 = false;
					break;
				}
				return true;
			}
			@Override
			public boolean touchUp(int screenX, int screenY, int pointer, int button)
			{
				switch(button)
				{
				case 0:
					_inputCapture.buttonDown0 = false;
					break;
				case 1:
					_inputCapture.buttonDown1 = false;
					break;
				}
				return true;
			}
			private void _commonMouse(int screenX, int screenY)
			{
				// We only want to handle the mouse movements if capturing them.
				if (_inputCapture.shouldCaptureMouseMovements)
				{
					// If we just enabled the mouse movements, the first event tends to snap us jarringly.
					if (!_inputCapture.didInitializeMouse)
					{
						_inputCapture.didInitializeMouse = true;
						_inputCapture.lastReportedMouseX = screenX;
						_inputCapture.lastReportedMouseY = screenY;
					}
					_inputCapture.mouseX = screenX;
					_inputCapture.mouseY = screenY;
				}
				else
				{
					// We want to return the 2D location of the cursor, in GL coordinates.
					// (screen coordinates are from the top-left and from 0-count whereas the scene is from bottom left and from -1.0 to 1.0).
					float screenWidth = Gdx.graphics.getWidth();
					float x = (2.0f * screenX / screenWidth) - 1.0f;
					
					float screenHeight = Gdx.graphics.getHeight();
					// (screen coordinates are from the top-left and from 0-count whereas the scene is from bottom left and from -1.0 to 1.0).
					float y = (2.0f * (screenHeight - screenY) / screenHeight) - 1.0f;
					_inputCapture.glCursorLocation = new Point(x, y);
				}
			}
		});
		
		_inputCapture.didHandlePressedNumber = true;
		_inputCapture.didHandleButton0 = true;
		_inputCapture.didHandleButton1 = true;
		_inputCapture.didHandleKeyEsc = true;
	}

	public void enterCaptureState(boolean state)
	{
		_enterCaptureState(state);
	}


	private void _enterCaptureState(boolean state)
	{
		_inputCapture.shouldCaptureMouseMovements = state;
		_inputCapture.didInitializeMouse = false;
		Gdx.input.setCursorCatched(state);
	}
}
