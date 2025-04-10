package com.jeffdisher.october.peaks;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input.Keys;


/**
 * Handles input events and the corresponding state machine to make sense of these low-level events in order to
 * synthesize the higher-level meaningful events to feed into the rest of the system.
 */
public class InputManager
{
	// Variables related to the higher-order state of the manager (enabling/disabling event filtering, etc).
	private boolean _shouldCaptureMouseMovements;
	private boolean _didInitializeMouse;

	// These are the key states - we capture them in events and decode them when polling for a frame.
	private boolean _moveUp;
	private boolean _moveDown;
	private boolean _moveRight;
	private boolean _moveLeft;
	private boolean _jumpSwim;
	private int _mouseX;
	private int _mouseY;
	private boolean _buttonDown0;
	private boolean _buttonDown1;
	private boolean _leftShiftDown;
	private int _lastPressedNumber;
	private boolean _didHandlePressedNumber;

	// These are records of whether we have handled single-action events based on keys or buttons.
	private int _lastReportedMouseX;
	private int _lastReportedMouseY;
	private boolean _didHandleButton0;
	private boolean _didHandleButton1;
	private boolean _didHandleKeyEsc;
	private boolean _didHandleKeyI;
	private boolean _didHandleKeyF;
	private boolean _didHandleEnterFullScreen;
	private boolean _didHandleEnterWindowMode;
	private boolean _didHandleKeyPlus;
	private boolean _didHandleKeyMinus;

	public InputManager()
	{
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
			public boolean keyDown(int keycode)
			{
				switch(keycode)
				{
				case Keys.ESCAPE:
					// We just capture the click.
					_didHandleKeyEsc = false;
					break;
				case Keys.NUM_1:
					_lastPressedNumber = 1;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_2:
					_lastPressedNumber = 2;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_3:
					_lastPressedNumber = 3;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_4:
					_lastPressedNumber = 4;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_5:
					_lastPressedNumber = 5;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_6:
					_lastPressedNumber = 6;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_7:
					_lastPressedNumber = 7;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_8:
					_lastPressedNumber = 8;
					_didHandlePressedNumber = false;
					break;
				case Keys.NUM_9:
					_lastPressedNumber = 9;
					_didHandlePressedNumber = false;
					break;
				case Keys.I:
					// We just capture the click.
					_didHandleKeyI = false;
					break;
				case Keys.F:
					// We just capture the click.
					_didHandleKeyF = false;
					break;
				case Keys.SPACE:
					_jumpSwim = true;
					break;
				case Keys.SHIFT_LEFT:
					_leftShiftDown = true;
					break;
				case Keys.W:
					_moveUp = true;
					break;
				case Keys.A:
					_moveLeft = true;
					break;
				case Keys.S:
					_moveDown = true;
					break;
				case Keys.D:
					_moveRight = true;
					break;
				case Keys.NUMPAD_ENTER:
					// Enter full screen.
					_didHandleEnterFullScreen = false;
					_didHandleEnterWindowMode = true;
					break;
				case Keys.NUMPAD_DOT:
					// Enter window mode.
					_didHandleEnterWindowMode = false;
					_didHandleEnterFullScreen = true;
					break;
				case Keys.NUMPAD_ADD:
					_didHandleKeyPlus = false;
					_didHandleKeyMinus = true;
					break;
				case Keys.NUMPAD_SUBTRACT:
					_didHandleKeyMinus = false;
					_didHandleKeyPlus = true;
					break;
				}
				return true;
			}
			@Override
			public boolean keyUp(int keycode)
			{
				switch(keycode)
				{
				case Keys.SPACE:
					_jumpSwim = false;
					break;
				case Keys.SHIFT_LEFT:
					_leftShiftDown = false;
					break;
				case Keys.W:
					_moveUp = false;
					break;
				case Keys.A:
					_moveLeft = false;
					break;
				case Keys.S:
					_moveDown = false;
					break;
				case Keys.D:
					_moveRight = false;
					break;
				}
				return true;
			}
			@Override
			public boolean touchDown(int screenX, int screenY, int pointer, int button)
			{
				switch(button)
				{
				case 0:
					_buttonDown0 = true;
					_didHandleButton0 = false;
					break;
				case 1:
					_buttonDown1 = true;
					_didHandleButton1 = false;
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
					_buttonDown0 = false;
					break;
				case 1:
					_buttonDown1 = false;
					break;
				}
				return true;
			}
			private void _commonMouse(int screenX, int screenY)
			{
				// We only want to handle the mouse movements if capturing them.
				if (_shouldCaptureMouseMovements)
				{
					// If we just enabled the mouse movements, the first event tends to snap us jarringly.
					if (!_didInitializeMouse)
					{
						_didInitializeMouse = true;
						_lastReportedMouseX = screenX;
						_lastReportedMouseY = screenY;
					}
					_mouseX = screenX;
					_mouseY = screenY;
				}
			}
		});
		
		// We are starting in the mouse capture state.
		_enterCaptureState(true);
		_didHandlePressedNumber = true;
		_didHandleButton0 = true;
		_didHandleButton1 = true;
		_didHandleKeyEsc = true;
		_didHandleKeyI = true;
		_didHandleKeyF = true;
		_didHandleEnterFullScreen = true;
		_didHandleEnterWindowMode = true;
		_didHandleKeyPlus = true;
		_didHandleKeyMinus = true;
	}

	public void flushEventsToStateManager(UiStateManager uiManager)
	{
		// We want to go down the list of things we might need to report and tell the UI Manager.
		
		// Firstly, we operate in a different mode, whether we are in capturing mode or not.
		if (_shouldCaptureMouseMovements)
		{
			// When we are capturing, the cursor is invisible and this is essentially a "yoke".
			int deltaX = _mouseX - _lastReportedMouseX;
			int deltaY = _mouseY - _lastReportedMouseY;
			_lastReportedMouseX = _mouseX;
			_lastReportedMouseY = _mouseY;
			uiManager.capturedMouseMoved(deltaX, deltaY);
			if (_buttonDown0)
			{
				uiManager.captureMouse0Down(!_didHandleButton0);
				_didHandleButton0 = true;
			}
			if (_buttonDown1)
			{
				uiManager.captureMouse1Down(!_didHandleButton1, _leftShiftDown);
				_didHandleButton1 = true;
			}
			
			// We will only pass one of the movement directions, for now.
			if (_moveUp)
			{
				uiManager.moveForward();
			}
			else if (_moveDown)
			{
				uiManager.moveBackward();
			}
			else if (_moveRight)
			{
				uiManager.strafeRight();
			}
			else if (_moveLeft)
			{
				uiManager.strafeLeft();
			}
			
			if (_jumpSwim)
			{
				uiManager.jumpOrSwim();
			}
		}
		else
		{
			// When we are not capturing, we are just interested in knowing where the mouse is and if there are any clicks.
			float glX = _getGlX();
			float glY = _getGlY();
			uiManager.normalMouseMoved(glX, glY);
			if (!_didHandleButton0)
			{
				uiManager.normalMouse0Clicked(_leftShiftDown);
				_didHandleButton0 = true;
			}
			if (!_didHandleButton1)
			{
				uiManager.normalMouse1Clicked(_leftShiftDown);
				_didHandleButton1 = true;
			}
		}
		
		// Now, we handle the special events related to specific keys which generally change UI state.
		if (!_didHandleKeyEsc)
		{
			uiManager.handleKeyEsc();
			_didHandleKeyEsc = true;
		}
		if (!_didHandlePressedNumber)
		{
			uiManager.handleHotbarIndex(_lastPressedNumber - 1);
			_didHandlePressedNumber = true;
		}
		if (!_didHandleKeyI)
		{
			uiManager.handleKeyI();
			_didHandleKeyI = true;
		}
		if (!_didHandleKeyF)
		{
			uiManager.handleKeyF();
			_didHandleKeyF = true;
		}
		if (!_didHandleEnterFullScreen)
		{
			uiManager.changeScreenMode(true);
			_didHandleEnterFullScreen = true;
		}
		else if (!_didHandleEnterWindowMode)
		{
			uiManager.changeScreenMode(false);
			_didHandleEnterWindowMode = true;
		}
		if (!_didHandleKeyPlus || !_didHandleKeyMinus)
		{
			boolean isIncrease = !_didHandleKeyPlus;
			uiManager.handleScaleChange(isIncrease ? 1 : -1);
			_didHandleKeyPlus = true;
			_didHandleKeyMinus = true;
		}
	}

	public void enterCaptureState(boolean state)
	{
		_enterCaptureState(state);
	}


	private void _enterCaptureState(boolean state)
	{
		_shouldCaptureMouseMovements = state;
		_didInitializeMouse = false;
		Gdx.input.setCursorCatched(state);
	}

	private static float _getGlX()
	{
		float screenWidth = Gdx.graphics.getWidth();
		float mouseX = (float)Gdx.input.getX();
		// (screen coordinates are from the top-left and from 0-count whereas the scene is from bottom left and from -1.0 to 1.0).
		return (2.0f * mouseX / screenWidth) - 1.0f;
	}

	private static float _getGlY()
	{
		float screenHeight = Gdx.graphics.getHeight();
		float mouseY = (float)Gdx.input.getY();
		// (screen coordinates are from the top-left and from 0-count whereas the scene is from bottom left and from -1.0 to 1.0).
		return (2.0f * (screenHeight - mouseY) / screenHeight) - 1.0f;
	}
}
