package com.jeffdisher.october.peaks;

import com.badlogic.gdx.Input.Keys;
import com.jeffdisher.october.peaks.persistence.MutableControls;
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
	// (note that the mouse "held" is if the button is currently down while "pressed" means it was pressed in this frame
	// and "released" means it was released in this frame)
	public boolean mouseHeld0;
	public boolean mouseHeld1;
	public boolean mousePressed0;
	public boolean mousePressed1;
	public boolean mouseReleased0;
	public boolean mouseReleased1;

	// Since shift and ctrl are meta-keys, we just record if they are currently held.
	public boolean leftShiftHeld;
	public boolean leftCtrlHeld;

	// This is an array for each MutableControls by ordinal for "held" currently or "released" in this frame.
	public final boolean[] controlHeld;
	public final boolean[] controlReleased;
	// (Keys.UNKNOWN means "no lastKeyCodeReleased")
	public int lastKeyCodeReleased;
	public boolean didReleaseEsc;

	// Variables related to the higher-order state of the manager (enabling/disabling event filtering, etc).
	public boolean shouldCaptureMouseMovements;
	public boolean didInitializeMouse;
	public int mouseX;
	public int mouseY;
	public Point glCursorLocation;
	public int lastReportedMouseX;
	public int lastReportedMouseY;

	// State related to more open-ended uses (number keys or general typing).
	// '\0' means "no typedCharacter" and -1 means "no lastPressedNumber"
	public char typedCharacter;
	public int lastPressedNumber;

	// NOTE:  This shouldn't really be here (it is a decision, not input) but it is an simple place to put it with the
	// correct sharing and lifecycle (since it does move around like input).
	public boolean didAccountForTimeInFrame;

	public InputCapture(ICallouts captureState)
	{
		this.captureState = captureState;
		
		this.controlHeld = new boolean[MutableControls.Control.values().length];
		this.controlReleased = new boolean[MutableControls.Control.values().length];
		this.lastKeyCodeReleased = Keys.UNKNOWN;
		
		this.typedCharacter = '\0';
		this.lastPressedNumber = -1;
	}

	/**
	 * Most users of the receiver must directly clear the relevant flags but this is the general case to reset misc
	 * "release" states which can be used by a component which wants to begin reading the receiver but doesn't know
	 * any residual state ignored by a previous user.
	 */
	public void clearReleaseState()
	{
		this.mousePressed0 = false;
		this.mousePressed1 = false;
		this.mouseReleased0 = false;
		this.mouseReleased1 = false;
		
		for (int i = 0; i < this.controlReleased.length; ++i)
		{
			this.controlReleased[i] = false;
		}
		this.lastKeyCodeReleased = Keys.UNKNOWN;
		this.didReleaseEsc = false;
		
		this.typedCharacter = '\0';
		this.lastPressedNumber = -1;
		
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
