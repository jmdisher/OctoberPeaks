package com.jeffdisher.october.peaks;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input.Keys;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Handles input events and state.
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

	public InputManager(MovementControl movement, ClientWrapper client)
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
				return true;
			}
			@Override
			public boolean mouseMoved(int screenX, int screenY)
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
				return true;
			}
			@Override
			public boolean keyDown(int keycode)
			{
				switch(keycode)
				{
				case Keys.ESCAPE:
					// This just disables the interface.
					Gdx.input.setCursorCatched(false);
					Gdx.input.setInputProcessor(null);
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
		});
		Gdx.input.setCursorCatched(true);
	}

	public boolean shouldUpdateSceneRunningEvents(PartialEntity entity, AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
	{
		// See if we need to jump/swim.
		boolean didJump = false;
		if (_jumpSwim)
		{
			didJump = _client.jumpOrSwim();
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
			}
		}
		boolean shouldUpdate = _rotationDidUpdate;
		_rotationDidUpdate = false;
		return shouldUpdate;
	}
}
