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
	private final boolean[] _activeControls;
	private int _lastKeyUp;

	// Variables related to the higher-order state of the manager (enabling/disabling event filtering, etc).
	private boolean _shouldCaptureMouseMovements;
	private boolean _didInitializeMouse;

	// State we need to capture from the input processor.
	private int _mouseX;
	private int _mouseY;
	private boolean _buttonDown0;
	private boolean _buttonDown1;
	private char _typedCharacter;
	private boolean _leftShiftDown;
	private boolean _leftCtrlDown;
	private int _lastPressedNumber;
	private boolean _didHandlePressedNumber;

	// These are records of whether we have handled single-action events based on keys or buttons.
	private int _lastReportedMouseX;
	private int _lastReportedMouseY;
	private boolean _didHandleButton0;
	private boolean _didHandleButton1;
	private boolean _didHandleKeyEsc;
	private boolean _didHandleQ;

	public InputManager(MutableControls mutableControls, boolean startInCaptureMode)
	{
		_controls = mutableControls;
		_activeControls = new boolean[MutableControls.Control.values().length];
		_lastKeyUp = Keys.UNKNOWN;
		
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
				_typedCharacter = character;
				return true;
			}
			@Override
			public boolean keyDown(int keycode)
			{
				switch(keycode)
				{
				case Keys.SHIFT_LEFT:
					_leftShiftDown = true;
					break;
				case Keys.CONTROL_LEFT:
					_leftCtrlDown = true;
					break;
				}
				
				// See if one of our dynamic controls matches this.
				MutableControls.Control control = _controls.getCodeForKey(keycode);
				if (null != control)
				{
					// We can actually do something here.
					if (!control.isClickOnly)
					{
						_activeControls[control.ordinal()] = true;
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
				case Keys.SHIFT_LEFT:
					_leftShiftDown = false;
					break;
				case Keys.CONTROL_LEFT:
					_leftCtrlDown = false;
					break;
				case Keys.Q:
					// TODO:  Generalize this with the other keys (once we decide how to add in ctrl).
					_didHandleQ = false;
					break;
				}
				
				// See if one of our dynamic controls matches this.
				MutableControls.Control control = _controls.getCodeForKey(keycode);
				if (null != control)
				{
					// Click only is only triggered on key up while hold are only set on key down and always cleared on up.
					_activeControls[control.ordinal()] = control.isClickOnly;
				}
				_lastKeyUp = keycode;
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
		
		_enterCaptureState(startInCaptureMode);
		_didHandlePressedNumber = true;
		_didHandleButton0 = true;
		_didHandleButton1 = true;
		_didHandleKeyEsc = true;
		_didHandleQ = true;
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
			
			// Check out movement controls.
			UiStateManager.WalkType walk = _activeControls[MutableControls.Control.MOVE_SNEAK.ordinal()]
				? UiStateManager.WalkType.SNEAK
				: (_activeControls[MutableControls.Control.MOVE_SPRINT.ordinal()] ? UiStateManager.WalkType.RUN : UiStateManager.WalkType.WALK)
			;
			if (_activeControls[MutableControls.Control.MOVE_FORWARD.ordinal()])
			{
				uiManager.moveForward(walk);
			}
			else if (_activeControls[MutableControls.Control.MOVE_BACKWARD.ordinal()])
			{
				uiManager.moveBackward(walk);
			}
			else if (_activeControls[MutableControls.Control.MOVE_RIGHT.ordinal()])
			{
				uiManager.strafeRight(walk);
			}
			else if (_activeControls[MutableControls.Control.MOVE_LEFT.ordinal()])
			{
				uiManager.strafeLeft(walk);
			}
			
			// See if we want to jump or try descending a ladder.
			if (_activeControls[MutableControls.Control.MOVE_JUMP.ordinal()])
			{
				uiManager.ascendOrJumpOrSwim();
			}
			else if (_activeControls[MutableControls.Control.MOVE_SNEAK.ordinal()])
			{
				uiManager.tryDescend();
			}
		}
		else
		{
			// When we are not capturing, we are just interested in knowing where the mouse is and if there are any clicks.
			Point cursor = _getGlCursor();
			uiManager.normalMouseMoved(cursor);
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
			
			// We also only capture the raw text input when not capturing movements since this would just be noise.
			if ('\0' != _typedCharacter)
			{
				uiManager.keyTyped(_typedCharacter);
				_typedCharacter = '\0';
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
		if (_activeControls[MutableControls.Control.MOVE_INVENTORY.ordinal()])
		{
			uiManager.handleKeyI();
			_activeControls[MutableControls.Control.MOVE_INVENTORY.ordinal()] = false;
		}
		if (_activeControls[MutableControls.Control.MOVE_FUEL.ordinal()])
		{
			uiManager.handleKeyF();
			_activeControls[MutableControls.Control.MOVE_FUEL.ordinal()] = false;
		}
		if (!_didHandleQ)
		{
			uiManager.handleKeyQ(_leftCtrlDown);
			_didHandleQ = true;
		}
		
		if (Keys.UNKNOWN != _lastKeyUp)
		{
			uiManager.keyCodeUp(_lastKeyUp);
			_lastKeyUp = Keys.UNKNOWN;
		}
	}

	public void enterCaptureState(boolean state)
	{
		_enterCaptureState(state);
	}

	/**
	 * Disables the capture state, if active, returning whether or not mouse movements were being captured before this
	 * call.
	 * 
	 * @return True if capturing was active (so it can be reenabled by the caller, later).
	 */
	public boolean suspendCaptureState()
	{
		boolean wasCapturing = _shouldCaptureMouseMovements;
		if (wasCapturing)
		{
			_enterCaptureState(false);
		}
		return wasCapturing;
	}


	private void _enterCaptureState(boolean state)
	{
		_shouldCaptureMouseMovements = state;
		_didInitializeMouse = false;
		Gdx.input.setCursorCatched(state);
	}

	private static Point _getGlCursor()
	{
		// We want to return the 2D location of the cursor, in GL coordinates.
		// (screen coordinates are from the top-left and from 0-count whereas the scene is from bottom left and from -1.0 to 1.0).
		float screenWidth = Gdx.graphics.getWidth();
		float mouseX = (float)Gdx.input.getX();
		float x = (2.0f * mouseX / screenWidth) - 1.0f;
		
		float screenHeight = Gdx.graphics.getHeight();
		float mouseY = (float)Gdx.input.getY();
		// (screen coordinates are from the top-left and from 0-count whereas the scene is from bottom left and from -1.0 to 1.0).
		float y = (2.0f * (screenHeight - mouseY) / screenHeight) - 1.0f;
		return new Point(x, y);
	}
}
