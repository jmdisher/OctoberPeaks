package com.jeffdisher.october.peaks;

import com.jeffdisher.october.utils.Assert;


/**
 * This class is just an adapter which is called from the window listener, at the top-level of the application
 * configuration, in order to convert into a call to the input manager in response to window events.
 */
public class WindowListener
{
	private InputManager _inputManager;
	private boolean _shouldRestoreCaptureState;

	public void setInputManager(InputManager input)
	{
		Assert.assertTrue(null == _inputManager);
		_inputManager = input;
	}

	public void focusLost()
	{
		_shouldRestoreCaptureState = _inputManager.suspendCaptureState();
	}

	public void focusGained()
	{
		if (_shouldRestoreCaptureState)
		{
			_inputManager.enterCaptureState(true);
			_shouldRestoreCaptureState = false;
		}
	}
}
