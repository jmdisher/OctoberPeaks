package com.jeffdisher.october.peaks;

import com.jeffdisher.october.peaks.ui.Point;


/**
 * A shared instance which stores the captured input state (mouse and keyboard) so that different layers of the UI stack
 * can easily read it or request that its mode change.
 * This tracks the state of keys, mouse buttons, but also the related meta-buttons since some of those modify the button
 * meaning.
 */
public class InputCapture
{
	public final ICallouts captureState;
	public Point cursor;
	public boolean mouseHeld0;
	public boolean mouseHeld1;
	public boolean mouseClicked0;
	public boolean mouseClicked1;
	public boolean leftClick;
	public boolean leftShiftClick;
	public boolean rightClick;

	// Some more complex input state.
	public boolean waitingForMouseRelease1;
	public boolean ctrlQPressed;
	public boolean qPressed;

	// NOTE:  This shouldn't really be here (it is a decision, not input) but it is an simple place to put it with the
	// correct sharing and lifecycle (since it does move around like input).
	public boolean didAccountForTimeInFrame;

	public InputCapture(ICallouts captureState)
	{
		this.captureState = captureState;
	}

	public void normalMouseMoved(Point cursor)
	{
		this.cursor = cursor;
	}

	public void captureMouse0Down(boolean justClicked)
	{
		this.mouseHeld0 = true;
		this.mouseClicked0 = justClicked;
	}

	public void captureMouse1Down(boolean justClicked, boolean leftShiftHeld)
	{
		this.mouseHeld1 = true;
		// We use the shift to allow us to set the "held" without "clicked".
		// In the future, this will likely be expanded but it isn't obvious where the interpretation of this key should
		// go (InputManager, where it can associated with key settings, or here where it is associated with the UI state).
		if (!leftShiftHeld)
		{
			this.mouseClicked1 = justClicked;
		}
	}

	public void normalMouse0Clicked(boolean leftShiftDown)
	{
		if (leftShiftDown)
		{
			this.leftShiftClick = true;
		}
		else
		{
			this.leftClick = true;
		}
	}

	public void normalMouse1Clicked(boolean leftShiftDown)
	{
		this.rightClick = true;
	}

	public void resetState()
	{
		this.mouseHeld0 = false;
		this.mouseHeld1 = false;
		this.mouseClicked0 = false;
		this.mouseClicked1 = false;
		this.leftClick = false;
		this.leftShiftClick = false;
		this.rightClick = false;
		
		// We keep waitingForMouseRelease1 since it crosses frames.
		this.ctrlQPressed = false;
		this.qPressed = false;
		
		this.didAccountForTimeInFrame = false;
	}


	/**
	 * Methods passed in from a higher-level component to control other aspects of the native window manager environment
	 * required by the internal logic.
	 */
	public static interface ICallouts
	{
		public void shouldCaptureMouse(boolean setCapture);
	}
}
