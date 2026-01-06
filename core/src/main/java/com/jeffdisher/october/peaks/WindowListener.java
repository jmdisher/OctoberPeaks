package com.jeffdisher.october.peaks;

import com.jeffdisher.october.utils.Assert;


/**
 * This class is just an adapter which is called from the window listener, at the top-level of the application
 * configuration, in order to convert into a call to the input manager in response to window events.
 * This class also contains the "logical cursor capture state" set by UiStateManager and it will internally convert that
 * into events into the InputManager to enable/disable the "physical cursor capture state" based on this logical state
 * and the current state of focus in the window manager.
 */
public class WindowListener
{
	private InputManager _inputManager;
	private boolean _windowDoesHaveFocus;
	private boolean _logicalCaptureState;

	public void setInputManager(InputManager input)
	{
		Assert.assertTrue(null == _inputManager);
		_inputManager = input;
	}

	public void setLogicMouseCaptureState(boolean shouldCaptureMouse)
	{
		_logicalCaptureState = shouldCaptureMouse;
		if (_windowDoesHaveFocus)
		{
			_inputManager.enterCaptureState(_logicalCaptureState);
		}
	}
	public void focusLost()
	{
		_windowDoesHaveFocus = false;
		
		// If we should have this captured, we need to disable it.
		if (_logicalCaptureState)
		{
			_inputManager.enterCaptureState(false);
		}
	}

	public void focusGained()
	{
		_windowDoesHaveFocus = true;
		
		// When regaining focus, set the capture state to whatever it logically should be.
		if (_logicalCaptureState)
		{
			_inputManager.enterCaptureState(_logicalCaptureState);
		}
	}
}
