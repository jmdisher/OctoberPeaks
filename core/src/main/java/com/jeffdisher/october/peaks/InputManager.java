package com.jeffdisher.october.peaks;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input.Keys;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Handles input events and state.
 * Note that this class handles the high-level UI state but this will likely change later:  The UI state will be split
 * out.
 */
public class InputManager
{
	private final MovementControl _movement;
	private final ClientWrapper _client;

	// These are the key states - we capture them in events and decode them when polling for a frame.
	private boolean _moveUp;
	private boolean _moveDown;
	private boolean _moveRight;
	private boolean _moveLeft;
	private boolean _jumpSwim;
	private boolean _rotationDidUpdate;
	private boolean _buttonDown0;
	private boolean _buttonDown1;
	@SuppressWarnings("unused")
	private boolean _didHandleButton0;
	private boolean _didHandleButton1;

	// Data specifically related to high-level UI state (will likely be pulled out, later).
	private _UiState _uiState;

	public InputManager(MovementControl movement, ClientWrapper client, WindowManager windowManager)
	{
		_movement = movement;
		_client = client;
		Gdx.input.setInputProcessor(new InputAdapter() {
			boolean _didInitialize = false;
			int _x;
			int _y;
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
					// We will just use this to toggle mode.
					if (_UiState.PLAY ==_uiState)
					{
						// Release the cursor.
						Gdx.input.setCursorCatched(false);
						_uiState = _UiState.MENU;
					}
					else
					{
						// Capture the cursor.
						Gdx.input.setCursorCatched(true);
						_uiState = _UiState.PLAY;
						_didInitialize = false;
					}
					break;
				case Keys.I:
					// We want to tell the window manager to toggle the inventory mode.
					boolean isInventoryVisible = windowManager.toggleInventoryMode();
					if (isInventoryVisible)
					{
						// Disable capture.
						Gdx.input.setCursorCatched(false);
						_uiState = _UiState.MENU;
					}
					else
					{
						// Enable capture.
						Gdx.input.setCursorCatched(true);
						_uiState = _UiState.PLAY;
						_didInitialize = false;
					}
					break;
				case Keys.SPACE:
					_jumpSwim = true;
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
				// We only want to use the mouse to pan the screen if we are in play mode.
				if (_UiState.PLAY == _uiState)
				{
					if (_didInitialize)
					{
						int deltaX = screenX - _x;
						int deltaY = screenY - _y;
						_movement.rotate(deltaX, deltaY);
						_rotationDidUpdate = true;
					}
					else if ((0 != screenX) && (0 != screenY))
					{
						_didInitialize = true;
					}
					_x = screenX;
					_y = screenY;
				}
			}
		});
		_uiState = _UiState.PLAY;
		Gdx.input.setCursorCatched(true);
	}

	public boolean shouldUpdateSceneRunningEvents(PartialEntity entity, AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
	{
		boolean didTakeAction = false;
		if (_UiState.PLAY == _uiState)
		{
			didTakeAction = _handlePlayMode(stopBlock, preStopBlock);
		}
		
		// If we took no action, just tell the client to pass time.
		if (!didTakeAction)
		{
			_client.doNothing();
		}
		
		// Return whether or not we changed the rotation.
		boolean shouldUpdate = _rotationDidUpdate;
		_rotationDidUpdate = false;
		return shouldUpdate;
	}


	private boolean _handlePlayMode(AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
	{
		boolean didTakeAction = false;
		// See if we need to jump/swim.
		boolean didJump = false;
		if (_jumpSwim)
		{
			didJump = _client.jumpOrSwim();
			didTakeAction = true;
		}
		if (!didJump)
		{
			// We didn't, so see if we can move horizontally.
			EntityChangeMove.Direction direction;
			if (_moveUp)
			{
				direction = _movement.walk(true);
			}
			else if (_moveDown)
			{
				direction = _movement.walk(false);
			}
			else if (_moveRight)
			{
				direction = _movement.strafeRight(true);
			}
			else if (_moveLeft)
			{
				direction = _movement.strafeRight(false);
			}
			else
			{
				direction = null;
			}
			if (null != direction)
			{
				_client.stepHorizontal(direction);
				didTakeAction = true;
			}
		}
		
		// See if we need to act in response to buttons.
		if (_buttonDown0)
		{
			if (null != stopBlock)
			{
				// Returns false if the block wasn't valid.
				didTakeAction = _client.hitBlock(stopBlock);
			}
			_didHandleButton0 = true;
		}
		else if (_buttonDown1)
		{
			if (null != preStopBlock)
			{
				_client.runRightClickAction(stopBlock, preStopBlock, !_didHandleButton1);
				didTakeAction = true;
			}
			_didHandleButton1 = true;
		}
		return didTakeAction;
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
	}
}
