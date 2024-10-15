package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.util.Set;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;


public class OctoberPeaks extends ApplicationAdapter
{
	private Environment _environment;
	private GL20 _gl;
	private SceneRenderer _scene;
	private MovementControl _movement;
	private ClientWrapper _client;

	@Override
	public void create()
	{
		_environment = Environment.createSharedInstance();
		_gl = Gdx.graphics.getGL20();
		
		// Set common GL functionality for the view.
		_gl.glEnable(GL20.GL_BLEND);
		_gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		
		try
		{
			_scene = new SceneRenderer(_gl);
		}
		catch (IOException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		_movement = new MovementControl();
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
					_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
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
				if (Keys.ESCAPE == keycode)
				{
					Gdx.input.setCursorCatched(false);
					Gdx.input.setInputProcessor(null);
				}
				return true;
			}
			@Override
			public boolean keyUp(int keycode)
			{
				boolean didCall;
				float magnitude = 5.0f;
				switch (keycode)
				{
				case Keys.W:
					_movement.walk(magnitude);
					didCall = true;
					break;
				case Keys.A:
					_movement.strafeRight(-magnitude);
					didCall = true;
					break;
				case Keys.S:
					_movement.walk(-magnitude);
					didCall = true;
					break;
				case Keys.D:
					_movement.strafeRight(magnitude);
					didCall = true;
					break;
				case Keys.SPACE:
					_movement.jump(magnitude);
					didCall = true;
					break;
				case Keys.SHIFT_LEFT:
					_movement.jump(-magnitude);
					didCall = true;
					break;
					default:
						didCall = false;
				}
				if (didCall)
				{
					_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
				}
				return didCall;
			}
		});
		Gdx.input.setCursorCatched(true);
		_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
		
		_client = new ClientWrapper(_environment
				, new ClientWrapper.ICuboidUpdateConsumer() {
					@Override
					public void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
					{
						_scene.setCuboid(cuboid);
					}
					@Override
					public void updateExisting(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
					{
						_scene.setCuboid(cuboid);
					}
					@Override
					public void unload(CuboidAddress address)
					{
						_scene.removeCuboid(address);
					}
				}
				, "Peaks_test_client"
				, null
		);
		_client.finishStartup();
	}

	@Override
	public void render()
	{
		_gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		_gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		_gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		
		_scene.render();
		
		// Handle any interactions with the client.
		_client.doNothing();
	}

	@Override
	public void dispose()
	{
		// Disconnect from the server.
		_client.disconnect();
		
		// Tear-down the shared environment.
		Environment.clearSharedInstance();
		_environment = null;
	}
}
