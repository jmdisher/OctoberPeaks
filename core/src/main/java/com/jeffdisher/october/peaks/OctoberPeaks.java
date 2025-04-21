package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.peaks.scene.BlockRenderer;
import com.jeffdisher.october.peaks.scene.EntityRenderer;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.types.MutableControls;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.utils.Assert;


public class OctoberPeaks extends ApplicationAdapter
{
	private final Environment _environment;
	private final String _clientName;
	private final InetSocketAddress _serverSocketAddress;

	private GL20 _gl;
	private LoadedResources _resources;
	private InputManager _input;
	private UiStateManager _uiState;

	// Variables which only exist over the course of a single game session.
	private GameSession _currentGameSession;

	public OctoberPeaks(Options options)
	{
		_environment = Environment.createSharedInstance();
		_clientName = options.clientName();
		_serverSocketAddress = options.serverAddress();
	}

	@Override
	public void create()
	{
		_gl = Gdx.graphics.getGL20();
		
		// Set common GL functionality for the view.
		_gl.glEnable(GL20.GL_BLEND);
		_gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		_gl.glEnable(GL20.GL_CULL_FACE);
		
		// Create the long-lived resources.
		TextureAtlas<ItemVariant> itemAtlas;
		try
		{
			itemAtlas = TextureHelpers.loadAtlasForItems(_gl
					, _environment.items.ITEMS_BY_TYPE
					, "missing_texture.png"
			);
			BlockRenderer.Resources blockRenderer = new BlockRenderer.Resources(_environment, _gl, itemAtlas);
			EntityRenderer.Resources entityRenderer = new EntityRenderer.Resources(_environment, _gl);
			SkyBox.Resources skyBox = new SkyBox.Resources(_gl);
			EyeEffect.Resources eyeEffect = new EyeEffect.Resources(_gl);
			GlUi.Resources glui = new GlUi.Resources(_gl, itemAtlas);
			AudioManager.Resources audioManager = new AudioManager.Resources();
			_resources = new LoadedResources(itemAtlas
					, blockRenderer
					, entityRenderer
					, skyBox
					, eyeEffect
					, glui
					, audioManager
			);
		}
		catch (IOException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		
		GlUi ui = new GlUi(_gl, _resources);
		
		// Create the input manager and connect the UI state manager to the relevant parts of the system.
		MutableControls mutableControls = new MutableControls();
		_input = new InputManager(mutableControls);
		_uiState = new UiStateManager(_environment, ui, mutableControls, new UiStateManager.ICallouts() {
			@Override
			public void shouldCaptureMouse(boolean setCapture)
			{
				_input.enterCaptureState(setCapture);
			}
		});
		
		// Immediately transition into playing state.  This will become more complex later.
		_currentGameSession = new GameSession(_environment, _gl, _resources, _clientName, _serverSocketAddress, _uiState);
		boolean onServer = (null != _serverSocketAddress);
		_uiState.startPlay(_currentGameSession, onServer);
		
		// Finish the rest of the startup now that the pieces are in place.
		_currentGameSession.finishStartup();
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	@Override
	public void resize(int width, int height)
	{
		_uiState.handleScreenResize(width, height);
	}

	@Override
	public void render()
	{
		// Flush any captured input events.
		_input.flushEventsToStateManager(_uiState);
		
		// Reset the screen so we can draw this frame.
		_gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		_gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		_gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		_uiState.renderFrame();
	}

	@Override
	public void dispose()
	{
		if (null != _currentGameSession)
		{
			_currentGameSession.shutdown();
		}
		
		// Shut down the long-lived resources.
		_resources.shutdown(_gl);
		
		// Tear-down the shared environment.
		Environment.clearSharedInstance();
	}
}
