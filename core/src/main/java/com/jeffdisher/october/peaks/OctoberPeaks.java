package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.persistence.MutablePreferences;
import com.jeffdisher.october.peaks.scene.BlockRenderer;
import com.jeffdisher.october.peaks.scene.EntityRenderer;
import com.jeffdisher.october.peaks.scene.PassiveRenderer;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


public class OctoberPeaks extends ApplicationAdapter
{
	public static final String ENV_VAR_OCTOBER_PEAKS_ROOT = "OCTOBER_PEAKS_ROOT";

	private final WindowListener _windowListener;

	// Note that these "no UI" ivars are only set when running in the testing mode where there is no top-level UI or local preferences.
	private final String _noUiClientName;
	private final InetSocketAddress _noUiServerSocketAddress;

	private GL20 _gl;
	private Environment _environment;
	private File _localStorageDirectory;
	private LoadedResources _resources;
	private InputManager _input;
	private UiStateManager _uiState;

	public OctoberPeaks(Options options, WindowListener windowListener)
	{
		_windowListener = windowListener;
		if (null != options)
		{
			// We were told to start up in an explicit (no UI) mode so use that.
			String givenName = options.clientName();
			_noUiClientName = (null != givenName)
					? givenName
					: "Local"
			;
			_noUiServerSocketAddress = options.serverAddress();
		}
		else
		{
			// We were told to start up with an interactive UI to choose the mode.
			_noUiClientName = null;
			_noUiServerSocketAddress = null;
		}
	}

	@Override
	public void create()
	{
		_initializeCommonResources();
		
		// Normally, these "no-UI" ivars are left null but some testing modes explicitly set them to bypass normal top-level UI.
		boolean isNoUiMode = (null != _noUiClientName);
		_initializeLocalStorage(isNoUiMode);
		MutablePreferences prefs = new MutablePreferences(_localStorageDirectory);
		_initializeInputAndState(isNoUiMode, prefs);
		if (isNoUiMode)
		{
			// In this testing mode, we start directly in the game, never creating the top-level UI.
			File localWorldDirectory = new File(_localStorageDirectory, "world");
			GameSession currentGameSession;
			try
			{
				// In this running mode, we always just use the defaults so pass nulls or local/ephemeral bindings.
				Binding<Float> screenBrightness = prefs.screenBrightness;
				int startingViewDistance = prefs.preferredViewDistance.get();
				WorldConfig.WorldGeneratorName worldGeneratorName = null;
				WorldConfig.DefaultPlayerMode defaultPlayerMode = null;
				Difficulty difficulty = null;
				Integer basicWorldGeneratorSeed = null;
				currentGameSession = new GameSession(_environment
					, _gl
					, screenBrightness
					, _resources
					, _noUiClientName
					, startingViewDistance
					, _noUiServerSocketAddress
					, localWorldDirectory
					, worldGeneratorName
					, defaultPlayerMode
					, difficulty
					, basicWorldGeneratorSeed
					, _uiState
				);
			}
			catch (ConnectException e)
			{
				// In this start-up mode, we just want to print the error and exit.
				System.err.println("Failed to connect to server: " + _noUiServerSocketAddress);
				e.printStackTrace();
				System.exit(1);
				throw Assert.unreachable();
			}
			boolean onServer = (null != _noUiServerSocketAddress);
			_uiState.startPlay(currentGameSession, onServer);
			
			// Finish the rest of the startup now that the pieces are in place.
			currentGameSession.finishStartup();
		}
		else
		{
			// In this normal mode, we start at the top-level UI, so the UI state is managed internally to the _uiState.
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


	private void _initializeCommonResources()
	{
		// This is called during "create()" to establish all the resources which are common for both normal runs and testing no-UI runs.
		_gl = Gdx.graphics.getGL20();
		_environment = Environment.createSharedInstance();
		
		// Set common GL functionality for the view.
		_gl.glEnable(GL20.GL_BLEND);
		_gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		_gl.glEnable(GL20.GL_CULL_FACE);
		
		// Create the long-lived resources.
		try
		{
			ItemTextureAtlas itemAtlas = TextureHelpers.loadAtlasForItems(_gl
					, _environment.items.ITEMS_BY_TYPE
					, "missing_texture.png"
			);
			BlockRenderer.Resources blockRenderer = new BlockRenderer.Resources(_environment, _gl, itemAtlas);
			EntityRenderer.Resources entityRenderer = new EntityRenderer.Resources(_environment, _gl);
			SkyBox.Resources skyBox = new SkyBox.Resources(_gl);
			EyeEffect.Resources eyeEffect = new EyeEffect.Resources(_gl);
			GlUi.Resources glui = new GlUi.Resources(_gl, itemAtlas);
			AudioManager.Resources audioManager = new AudioManager.Resources();
			PassiveRenderer.Resources passive = new PassiveRenderer.Resources(_environment, _gl, itemAtlas);
			_resources = new LoadedResources(itemAtlas
					, blockRenderer
					, entityRenderer
					, skyBox
					, eyeEffect
					, glui
					, audioManager
					, passive
			);
		}
		catch (IOException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	private void _initializeLocalStorage(boolean isNoUiMode)
	{
		String envVar = System.getenv(ENV_VAR_OCTOBER_PEAKS_ROOT);
		if (isNoUiMode)
		{
			// In no-UI mode, just put this in /tmp (this is only for testing unless over-ridden).
			_localStorageDirectory = new File("/tmp/OctoberPeaks");
		}
		else
		{
			// We will just use external storage, which should put this in home, unless our env var is specified.
			_localStorageDirectory = (null != envVar)
					? new File(envVar)
					: new File(new File(Gdx.files.getExternalStoragePath()), "OctoberPeaks")
			;
		}
		Assert.assertTrue(_localStorageDirectory.isDirectory() || _localStorageDirectory.mkdirs());
	}

	private void _initializeInputAndState(boolean isNoUiMode, MutablePreferences mutablePreferences)
	{
		// Create the input manager and connect the UI state manager to the relevant parts of the system.
		MutableControls mutableControls = new MutableControls(_localStorageDirectory);
		_input = new InputManager(mutableControls, isNoUiMode);
		_windowListener.setInputManager(_input);
		_uiState = new UiStateManager(_environment, _gl, _localStorageDirectory, _resources, mutableControls, mutablePreferences, new UiStateManager.ICallouts() {
			@Override
			public void shouldCaptureMouse(boolean setCapture)
			{
				_input.enterCaptureState(setCapture);
			}
		});
	}
}
