package com.jeffdisher.october.peaks.types;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Input.Keys;


/**
 * Defines the shared state of what key codes the InputManager uses to implement the shared controls in the system.
 * This allows the key binding UI to directly share the state with the InputManager.
 */
public class MutableControls
{
	public static enum Control
	{
		MOVE_FORWARD(Keys.W, false, "Move Forward"),
		MOVE_RIGHT(Keys.D, false, "Move Right"),
		MOVE_LEFT(Keys.A, false, "Move Left"),
		MOVE_BACKWARD(Keys.S, false, "Move Backward"),
		MOVE_JUMP(Keys.SPACE, false, "Jump/Swim"),
		MOVE_INVENTORY(Keys.I, true, "Open/Close Inventory"),
		MOVE_FUEL(Keys.F, true, "Toggle Fuel Inventory"),
		;
		
		private int keyCode;
		public final boolean isClickOnly;
		public final String description;
		private Control(int defaultCode, boolean isClickOnly, String description)
		{
			// The key code will be modified as we run but starts as default.
			this.keyCode = defaultCode;
			this.isClickOnly = isClickOnly;
			this.description = description;
		}
	}


	private final Map<Integer, Control> _keyCodeToCommand;

	public MutableControls()
	{
		_keyCodeToCommand = new HashMap<>();
		
		// Populate this with defaults.
		for (Control control : Control.values())
		{
			_keyCodeToCommand.put(control.keyCode, control);
		}
	}

	public Control getCodeForKey(int keyCode)
	{
		return _keyCodeToCommand.get(keyCode);
	}
}
