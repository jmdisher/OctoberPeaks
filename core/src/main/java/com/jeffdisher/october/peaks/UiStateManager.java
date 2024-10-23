package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Handles the current high-level state of the UI based on events from the InputManager.
 */
public class UiStateManager
{
	private final MovementControl _movement;
	private final ClientWrapper _client;

	private boolean _rotationDidUpdate;
	private boolean _didAccountForTimeInFrame;
	private boolean _mouseHeld0;
	private boolean _mouseHeld1;
	private boolean _mouseClicked1;
	private float _normalGlX;
	private float _normalGlY;

	// Data specifically related to high-level UI state (will likely be pulled out, later).
	private _UiState _uiState;

	public UiStateManager(MovementControl movement, ClientWrapper client)
	{
		_movement = movement;
		_client = client;
		
		// We start up in the play state.
		_uiState = _UiState.PLAY;
	}

	public boolean canSelectInScene()
	{
		// This just means whether or not we are in play mode.
		return _UiState.PLAY == _uiState;
	}

	public void drawRelevantWindows(WindowManager windowManager, AbsoluteLocation selectedBlock, PartialEntity selectedEntity)
	{
		if (_UiState.INVENTORY == _uiState)
		{
			// TODO:  Plumb in the relevant information for crafting, etc.
			// For now, we just provide some testing data.
			Environment env = Environment.getShared();
			Map<Integer, NonStackableItem> nonStack = new HashMap<>();
			Map<Integer, Items> stack = new HashMap<>();
			int count = 0;
			for (Item item : env.items.ITEMS_BY_TYPE)
			{
				if (env.durability.isStackable(item))
				{
					if (env.encumbrance.getEncumbrance(item) > 0)
					{
						stack.put(count, new Items(item, 2));
						count += 1;
					}
				}
				else
				{
					nonStack.put(count, new NonStackableItem(item, 10));
					count += 1;
				}
			}
			
			WindowManager.WindowData topLeft = new WindowManager.WindowData("Crafting"
					, 0
					, 0
					, 0
					, null
					, Map.of()
					, Map.of()
					, null
			);
			WindowManager.WindowData topRight = new WindowManager.WindowData("Inventory"
					, 4
					, 10
					, 0
					, (int page) -> System.out.println("PAGE: " + page)
					, nonStack
					, stack
					, (int key) -> System.out.println("KEY: " + key)
			);
			WindowManager.WindowData bottom = new WindowManager.WindowData("Floor"
					, 0
					, 20
					, 0
					, null
					, Map.of()
					, Map.of()
					, null
			);
			
			windowManager.drawActiveWindows(null, null, topLeft, topRight, bottom, _normalGlX, _normalGlY);
		}
		else
		{
			// In this case, just draw the common UI elements.
			windowManager.drawActiveWindows(selectedBlock, selectedEntity, null, null, null, _normalGlX, _normalGlY);
		}
	}

	public boolean didViewPerspectiveChange()
	{
		// Return whether or not we changed the rotation.
		boolean shouldUpdate = _rotationDidUpdate;
		_rotationDidUpdate = false;
		return shouldUpdate;
	}

	public void finalizeFrameEvents(PartialEntity entity, AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
	{
		// See if the click refers to anything selected.
		if (_mouseHeld0)
		{
			if (null != stopBlock)
			{
				_client.hitBlock(stopBlock);
			}
		}
		else if (_mouseHeld1)
		{
			if (null != preStopBlock)
			{
				_client.runRightClickAction(stopBlock, preStopBlock, _mouseClicked1);
			}
		}
		
		// If we took no action, just tell the client to pass time.
		if (!_didAccountForTimeInFrame)
		{
			_client.doNothing();
		}
		// And reset.
		_didAccountForTimeInFrame = false;
		_mouseHeld0 = false;
		_mouseHeld1 = false;
		_mouseClicked1 = false;
	}

	public void capturedMouseMoved(int deltaX, int deltaY)
	{
		_movement.rotate(deltaX, deltaY);
		_rotationDidUpdate = true;
	}

	public void captureMouse0Down(boolean justClicked)
	{
		_mouseHeld0 = true;
	}

	public void captureMouse1Down(boolean justClicked)
	{
		_mouseHeld1 = true;
		_mouseClicked1 = justClicked;
	}

	public void moveForward()
	{
		EntityChangeMove.Direction direction = _movement.walk(true);
		_client.stepHorizontal(direction);
		_didAccountForTimeInFrame = true;
	}

	public void moveBackward()
	{
		EntityChangeMove.Direction direction = _movement.walk(false);
		_client.stepHorizontal(direction);
		_didAccountForTimeInFrame = true;
	}

	public void strafeRight()
	{
		EntityChangeMove.Direction direction = _movement.strafeRight(true);
		_client.stepHorizontal(direction);
		_didAccountForTimeInFrame = true;
	}

	public void strafeLeft()
	{
		EntityChangeMove.Direction direction = _movement.strafeRight(false);
		_client.stepHorizontal(direction);
		_didAccountForTimeInFrame = true;
	}

	public void jumpOrSwim()
	{
		_client.jumpOrSwim();
	}

	public void normalMouseMoved(float glX, float glY)
	{
		_normalGlX = glX;
		_normalGlY = glY;
	}

	public void normalMouse0Clicked()
	{
		// TODO:  Implement (inventory/crafting mode).
	}

	public void normalMouse1Clicked()
	{
		// TODO:  Implement (inventory/crafting mode).
	}

	public void handleKeyEsc(IInputStateChanger captureState)
	{
		switch (_uiState)
		{
		case INVENTORY:
			_uiState = _UiState.PLAY;
			captureState.shouldCaptureMouse(true);
			break;
		case MENU:
			_uiState = _UiState.PLAY;
			captureState.shouldCaptureMouse(true);
			break;
		case PLAY:
			_uiState = _UiState.MENU;
			captureState.shouldCaptureMouse(false);
			break;
		}
	}

	public void handleKeyI(IInputStateChanger captureState)
	{
		switch (_uiState)
		{
		case INVENTORY:
			_uiState = _UiState.PLAY;
			captureState.shouldCaptureMouse(true);
			break;
		case MENU:
			// Just ignore this.
			break;
		case PLAY:
			_uiState = _UiState.INVENTORY;
			captureState.shouldCaptureMouse(false);
			break;
		}
	}


	/**
	 *  Represents the high-level state of the UI.  This will likely be split out into a class to specifically manage UI
	 *  state, later one.
	 */
	private static enum _UiState
	{
		/**
		 * The mode where play is normal.  Cursor is captured and there is no open window.
		 */
		PLAY,
		/**
		 * The mode where play is effectively "paused".  The cursor is released and buttons to change game setup will be
		 * presented.
		 * TODO:  Add a "pause" mode to the server, used here in single-player mode.
		 * TODO:  Determine the required buttons for control and add them.
		 */
		MENU,
		/**
		 * The mode where player control is largely disabled and the interface is mostly about clicking on buttons, etc.
		 */
		INVENTORY,
	}


	public static interface IInputStateChanger
	{
		public void shouldCaptureMouse(boolean setCapture);
	}
}
