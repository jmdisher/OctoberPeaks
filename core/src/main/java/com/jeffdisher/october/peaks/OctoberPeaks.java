package com.jeffdisher.october.peaks;

import java.io.IOException;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;


public class OctoberPeaks extends ApplicationAdapter
{
	private GL20 _gl;
	private SceneRenderer _scene;

	@Override
	public void create()
	{
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
					_scene.rotate(deltaX, deltaY);
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
				float magnitude = 0.25f;
				switch (keycode)
				{
				case Keys.W:
					_scene.translate(0.0f, magnitude, 0.0f);
					didCall = true;
					break;
				case Keys.A:
					_scene.translate(-magnitude, 0.0f, 0.0f);
					didCall = true;
					break;
				case Keys.S:
					_scene.translate(0.0f, -magnitude, 0.0f);
					didCall = true;
					break;
				case Keys.D:
					_scene.translate(magnitude, 0.0f, 0.0f);
					didCall = true;
					break;
				case Keys.SPACE:
					_scene.translate(0.0f, 0.0f, magnitude);
					didCall = true;
					break;
				case Keys.SHIFT_LEFT:
					_scene.translate(0.0f, 0.0f, -magnitude);
					didCall = true;
					break;
					default:
						didCall = false;
				}
				return didCall;
			}
		});
		Gdx.input.setCursorCatched(true);
	}

	@Override
	public void render()
	{
		_gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		_gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		_gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		
		_scene.render();
	}

	@Override
	public void dispose()
	{
	}
}
