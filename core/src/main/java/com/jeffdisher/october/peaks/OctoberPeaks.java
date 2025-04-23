package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
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
	public static final String ENV_VAR_OCTOBER_PEAKS_ROOT = "OCTOBER_PEAKS_ROOT";

	private final Environment _environment;
	private final String _clientName;
	private final InetSocketAddress _serverSocketAddress;

	private GL20 _gl;
	private File _localStorageDirectory;
	private LoadedResources _resources;
	private InputManager _input;
	private UiStateManager _uiState;

	public OctoberPeaks(Options options)
	{
		_environment = Environment.createSharedInstance();
		if (null != options)
		{
			// We were told to start up in an explicit mode so use that.
			String givenName = options.clientName();
			_clientName = (null != givenName)
					? givenName
					: "Local"
			;
			_serverSocketAddress = options.serverAddress();
		}
		else
		{
			// We were told to start up with an interactive UI to choose the mode.
			_clientName = null;
			_serverSocketAddress = null;
		}
	}

	@Override
	public void create()
	{
		_gl = Gdx.graphics.getGL20();
		
		// We will just use external storage, which should put this in home, unless our env var is specified.
		String envVar = System.getenv(ENV_VAR_OCTOBER_PEAKS_ROOT);
		_localStorageDirectory = (null != envVar)
				? new File(envVar)
				: new File(new File(Gdx.files.getExternalStoragePath()), "OctoberPeaks")
		;
		Assert.assertTrue(_localStorageDirectory.isDirectory() || _localStorageDirectory.mkdirs());
		
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
		
		// Create the input manager and connect the UI state manager to the relevant parts of the system.
		MutableControls mutableControls = new MutableControls(_localStorageDirectory);
		_input = new InputManager(mutableControls, (null != _clientName));
		_uiState = new UiStateManager(_environment, _gl, _localStorageDirectory, _resources, mutableControls, new UiStateManager.ICallouts() {
			@Override
			public void shouldCaptureMouse(boolean setCapture)
			{
				_input.enterCaptureState(setCapture);
			}
		});
		
		if (null != _clientName)
		{
			// Immediately transition into playing state.  This will become more complex later.
			// We will just store the world in the current directory.
			File localWorldDirectory = new File("world");
			GameSession currentGameSession;
			try
			{
				currentGameSession = new GameSession(_environment, _gl, _resources, _clientName, _serverSocketAddress, localWorldDirectory, _uiState);
			}
			catch (ConnectException e)
			{
				// In this start-up mode, we just want to print the error and exit.
				System.err.println("Failed to connect to server: " + _serverSocketAddress);
				e.printStackTrace();
				System.exit(1);
				throw Assert.unreachable();
			}
			boolean onServer = (null != _serverSocketAddress);
			_uiState.startPlay(currentGameSession, onServer);
			
			// Finish the rest of the startup now that the pieces are in place.
			currentGameSession.finishStartup();
			Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		}
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
		_uiState.shutdown();
		
		// Shut down the long-lived resources.
		_resources.shutdown(_gl);
		
		// Tear-down the shared environment.
		Environment.clearSharedInstance();
	}
}
