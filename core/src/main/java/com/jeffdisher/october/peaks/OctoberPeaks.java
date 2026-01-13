package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.persistence.MutablePreferences;
import com.jeffdisher.october.peaks.scene.BlockRenderer;
import com.jeffdisher.october.peaks.scene.EntityRenderer;
import com.jeffdisher.october.peaks.scene.PassiveRenderer;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.utils.Assert;


public class OctoberPeaks extends ApplicationAdapter
{
	public static final String ENV_VAR_OCTOBER_PEAKS_ROOT = "OCTOBER_PEAKS_ROOT";

	private final WindowListener _windowListener;

	private GL20 _gl;
	private Environment _environment;
	private File _localStorageDirectory;
	private LoadedResources _resources;
	private InputManager _input;
	private UiStateManager _uiState;

	public OctoberPeaks(WindowListener windowListener)
	{
		_windowListener = windowListener;
	}

	@Override
	public void create()
	{
		_initializeCommonResources();
		
		_initializeLocalStorage();
		MutablePreferences prefs = new MutablePreferences(_localStorageDirectory);
		_initializeInputAndState(prefs);
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
		
		// Set common GL functionality for the view.
		_gl.glEnable(GL20.GL_BLEND);
		_gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		_gl.glEnable(GL20.GL_CULL_FACE);
		
		// Create the long-lived resources.
		try
		{
			_environment = Environment.createSharedInstance();
			ItemTextureAtlas itemAtlas = TextureHelpers.loadAtlasForItems(_gl
					, _environment.items.ITEMS_BY_TYPE
					, "missing_texture.png"
			);
			BlockRenderer.Resources blockRenderer = new BlockRenderer.Resources(_environment, _gl, itemAtlas);
			BlockRenderer.ItemSlotResources blockItemSlotRenderer = new BlockRenderer.ItemSlotResources(_gl, itemAtlas);
			EntityRenderer.Resources entityRenderer = new EntityRenderer.Resources(_environment, _gl);
			SkyBox.Resources skyBox = new SkyBox.Resources(_gl);
			EyeEffect.Resources eyeEffect = new EyeEffect.Resources(_gl);
			GlUi.Resources glui = new GlUi.Resources(_gl, itemAtlas);
			AudioManager.Resources audioManager = new AudioManager.Resources();
			PassiveRenderer.Resources passive = new PassiveRenderer.Resources(_environment, _gl, itemAtlas);
			_resources = new LoadedResources(itemAtlas
				, blockRenderer
				, blockItemSlotRenderer
				, entityRenderer
				, skyBox
				, eyeEffect
				, glui
				, audioManager
				, passive
			);
		}
		catch (IOException | TabListReader.TabListException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	private void _initializeLocalStorage()
	{
		String envVar = System.getenv(ENV_VAR_OCTOBER_PEAKS_ROOT);
		// We will just use external storage, which should put this in home, unless our env var is specified.
		_localStorageDirectory = (null != envVar)
			? new File(envVar)
			: new File(new File(Gdx.files.getExternalStoragePath()), "OctoberPeaks")
		;
		Assert.assertTrue(_localStorageDirectory.isDirectory() || _localStorageDirectory.mkdirs());
	}

	private void _initializeInputAndState(MutablePreferences mutablePreferences)
	{
		// Create the input manager and connect the UI state manager to the relevant parts of the system.
		MutableControls mutableControls = new MutableControls(_localStorageDirectory);
		_input = new InputManager(mutableControls);
		_windowListener.setInputManager(_input);
		_uiState = new UiStateManager(_environment, _gl, _localStorageDirectory, _resources, mutableControls, mutablePreferences, new UiStateManager.ICallouts() {
			@Override
			public void shouldCaptureMouse(boolean setCapture)
			{
				_windowListener.setLogicMouseCaptureState(setCapture);
			}
		});
		
		// Install an exception handler and send it to the state manager.
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
		{
			@Override
			public void uncaughtException(Thread t, Throwable e)
			{
				// We will print the information to the console but also enter the error state.
				String title = "Fatal error in thread: " + t;
				System.err.println(title);
				e.printStackTrace(System.err);
				
				StackTraceElement[] stack = e.getStackTrace();
				String[] payload = new String[stack.length + 1];
				payload[0] = title;
				for (int i = 0; i < stack.length; ++i)
				{
					payload[i + 1] = stack[i].toString();
				}
				_uiState.enterErrorState(payload);
			}
		});
	}
}
