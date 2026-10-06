package com.jeffdisher.october.peaks;

import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.peaks.modes.ModeChat;
import com.jeffdisher.october.peaks.modes.ModeConfirmDeleteSinglePlayer;
import com.jeffdisher.october.peaks.modes.ModeConnecting;
import com.jeffdisher.october.peaks.modes.ModeContainer;
import com.jeffdisher.october.peaks.modes.ModeError;
import com.jeffdisher.october.peaks.modes.ModeInventory;
import com.jeffdisher.october.peaks.modes.ModeKeyBindings;
import com.jeffdisher.october.peaks.modes.ModeListForProfile;
import com.jeffdisher.october.peaks.modes.ModeListMultiPlayer;
import com.jeffdisher.october.peaks.modes.ModeListSinglePlayer;
import com.jeffdisher.october.peaks.modes.ModeNewMultiPlayer;
import com.jeffdisher.october.peaks.modes.ModeNewSinglePlayer;
import com.jeffdisher.october.peaks.modes.ModeOptions;
import com.jeffdisher.october.peaks.modes.ModePause;
import com.jeffdisher.october.peaks.modes.ModePlay;
import com.jeffdisher.october.peaks.modes.ModeProfile;
import com.jeffdisher.october.peaks.modes.ModeStart;
import com.jeffdisher.october.peaks.modes.ModeTrading;
import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.persistence.MutablePreferences;
import com.jeffdisher.october.peaks.profiling.ProfilingModes;
import com.jeffdisher.october.peaks.profiling.ProfilingSession;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the current high-level state of the UI based on events from the InputManager.
 */
public class UiStateManager implements GameSession.ICallouts
{
	private final Environment _env;
	private final GlUi _ui;
	private final InputCapture _inputCapture;
	private final UiData _uiData;
	private final LocalStorageManager _localStorageManager;
	private final ModeContainer _modeContainer;

	// Bindings related to the game UI during a PLAY state (the in-game UI - not just menus, etc).
	private final Binding<Entity> _entityBinding;

	public UiStateManager(Environment environment
		, GL20 gl
		, InputCapture inputCapture
		, File localStorageDirectory
		, LoadedResources resources
		, MutableControls mutableControls
		, MutablePreferences mutablePreferences
	)
	{
		_env = environment;
		_ui = new GlUi(gl, resources);
		_inputCapture = inputCapture;
		_uiData = new UiData(localStorageDirectory, mutableControls, mutablePreferences);
		_localStorageManager = new LocalStorageManager(_uiData.worldListBinding, localStorageDirectory);
		_modeContainer = new ModeContainer();
		
		// Define all of our bindings.
		_entityBinding = new Binding<>(null);
		Binding<Integer> currentTradingPartnerIdBinding = new Binding<>(0);
	
		// Build the modes (we add these late since they should be allowed to depend on arbitrary things here).
		_modeContainer.start = new ModeStart(_modeContainer
			, _localStorageManager
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.listSinglePlayer = new ModeListSinglePlayer(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
			, (String directoryName) -> {
				// We just pass nulls for our new game options.
				GameSession session = _createSinglePlayerSession(gl, resources, directoryName, null, null, null, 0);
				_uiData.isRunningOnServerBinding.set(session.isOnServer);
				return session;
			}
		);
		_modeContainer.confirmDeleteSinglePlayer = new ModeConfirmDeleteSinglePlayer(_modeContainer
			, _localStorageManager
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.newSinglePlayer =  new ModeNewSinglePlayer(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
			, (String directoryName
				, WorldConfig.WorldGeneratorName worldGeneratorName
				, WorldConfig.DefaultPlayerMode defaultPlayerMode
				, Difficulty difficulty
				, Integer basicWorldGeneratorSeed
			) -> {
				GameSession session = _createSinglePlayerSession(gl, resources, directoryName, worldGeneratorName, defaultPlayerMode, difficulty, basicWorldGeneratorSeed);
				_uiData.isRunningOnServerBinding.set(session.isOnServer);
				return session;
			}
		);
		_modeContainer.listMultiPlayer = new ModeListMultiPlayer(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
			, (String clientName, int startingViewDistance, InetSocketAddress serverAddress) -> {
				GameSession session;
				try
				{
					session = new GameSession(_env
						, gl
						, _uiData.mutablePreferences.screenBrightness
						, resources
						, clientName
						, startingViewDistance
						, serverAddress
						, null
						, null
						, null
						, null
						, null
						, this
					);
				}
				catch (ConnectException e)
				{
					// Something went wrong, so don't change state, but we can log this (might want somewhere in the UI to show this, later).
					e.printStackTrace();
					session = null;
				}
				
				if (null != session)
				{
					// This was a success, so change state.
					_uiData.isRunningOnServerBinding.set(session.isOnServer);
				}
				return session;
			}
		);
		_modeContainer.newMultiPlayer = new ModeNewMultiPlayer(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.listForProfile = new ModeListForProfile(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
			, (ProfilingModes mode) -> {
				ProfilingSession session = new ProfilingSession(_env, gl, _uiData.mutablePreferences.screenBrightness, resources);
				mode.populate.accept(_env, session);
				return session;
			}
		);
		_modeContainer.options = new ModeOptions(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.keyBindings = new ModeKeyBindings(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.connecting = new ModeConnecting(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.play = new ModePlay(_modeContainer
			, _ui
			, _inputCapture
			, _entityBinding
			, currentTradingPartnerIdBinding
		);
		_modeContainer.inventory = new ModeInventory(_modeContainer
			, _ui
			, _inputCapture
			, _entityBinding
		);
		_modeContainer.pause = new ModePause(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.profile = new ModeProfile(_modeContainer
			, _inputCapture
		);
		_modeContainer.trading = new ModeTrading(_modeContainer
			, _ui
			, _inputCapture
			, _entityBinding
			, currentTradingPartnerIdBinding
		);
		_modeContainer.error = new ModeError(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.chat = new ModeChat(_modeContainer
			, _ui
			, _inputCapture
		);
		_modeContainer.currentMode = _modeContainer.start.becomeActive();
	}

	@Override
	public void didConnect(int currentViewDistance)
	{
		// We just use this to initialize our preferences.
		_uiData.mutablePreferences.preferredViewDistance.set(currentViewDistance);
	}

	@Override
	public void didDisconnect()
	{
		// This is called when the server unexpectedly disconnects us so we want to change top-level state.
		// It can only happen if we are in the PLAY state or CONNECTING state (that is, we haven't completed the handshake).
		if (_modeContainer.play == _modeContainer.currentMode)
		{
			Assert.assertTrue(null == _modeContainer.connecting.pendingGameSession);
			_modeContainer.play.currentGameSession.shutdown();
		}
		else if (_modeContainer.connecting == _modeContainer.currentMode)
		{
			_modeContainer.connecting.pendingGameSession.shutdown();
		}
		else
		{
			// How did we disconnect when not playing or connecting?
			throw Assert.unreachable();
		}
		
		_modeContainer.setActive(_modeContainer.start.becomeActive());
	}

	@Override
	public void thisEntityUpdated(Entity projectedEntity)
	{
		_entityBinding.set(projectedEntity);
		
		// Make sure that we close the inventory if it is now too far away (can happen if falling or respawning).
		if ((_modeContainer.inventory == _modeContainer.currentMode) && (null != _modeContainer.inventory.openStationLocation))
		{
			EntityLocation eyeLocation = SpatialHelpers.getEyeLocation(projectedEntity.location(), _env.creatures.PLAYER.volume());
			float distance = SpatialHelpers.distanceFromLocationToBlockSurface(eyeLocation, _modeContainer.inventory.openStationLocation);
			boolean isLocationClose = (distance <= MiscConstants.REACH_BLOCK);
			if (!isLocationClose)
			{
				_modeContainer.inventory.openStationLocation = null;
			}
		}
	}

	@Override
	public void appendChatLog(String chatLogString)
	{
		// Pass this off to the play game mode (the chat log is a "passive window").
		// Using "conduit methods" like this isn't ideal (it should be some kind of notification the ModePlay could
		// listen for) but this is a relatively simple and special case.
		_modeContainer.play.appendChatLog(chatLogString);
	}

	/**
	 * Called after clearing the framebuffer in order to render the frame with whatever is required for in the current
	 * UI state.
	 * Internally, this is also an opportunity for the state manager to act on, flush, or reset any input events it has
	 * received since the last frame.
	 */
	public void renderFrame()
	{
		// Handle the user events at the beginning of the frame.
		if (_inputCapture.didReleaseEsc)
		{
			// The escape is the only common logic we have, so just apply it here.
			_modeContainer.currentMode.handleEscape();
			
			// Any meaning of "back" should stop text input.
			_inputCapture.textCapture = null;
			_inputCapture.didReleaseEsc = false;
		}
		_modeContainer.currentMode.handleUserEvents();
		
		// Draw the relevant windows on top of this scene (passing in any information describing the UI state).
		_drawRelevantWindows();
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
		_inputCapture.clearReleaseState();
	}

	public void handleScreenResize(int width, int height)
	{
		// If we are in a state which has a projection to rebuild, call it.
		if (_modeContainer.options == _modeContainer.currentMode)
		{
			if (null != _modeContainer.options.currentGameSession)
			{
				_modeContainer.options.currentGameSession.scene.rebuildProjection(width, height);
			}
		}
		else if (_modeContainer.keyBindings == _modeContainer.currentMode)
		{
			if (null != _modeContainer.keyBindings.currentGameSession)
			{
				_modeContainer.keyBindings.currentGameSession.scene.rebuildProjection(width, height);
			}
		}
		else if (_modeContainer.play == _modeContainer.currentMode)
		{
			_modeContainer.play.currentGameSession.scene.rebuildProjection(width, height);
		}
		else if (_modeContainer.inventory == _modeContainer.currentMode)
		{
			_modeContainer.inventory.currentGameSession.scene.rebuildProjection(width, height);
		}
		else if (_modeContainer.pause == _modeContainer.currentMode)
		{
			_modeContainer.pause.currentGameSession.scene.rebuildProjection(width, height);
		}
		else if (_modeContainer.profile == _modeContainer.currentMode)
		{
			_modeContainer.profile.profilingSession.scene.rebuildProjection(width, height);
		}
		else if (_modeContainer.trading == _modeContainer.currentMode)
		{
			_modeContainer.trading.currentGameSession.scene.rebuildProjection(width, height);
		}
	}

	public void enterErrorState(String[] payload)
	{
		_modeContainer.setActive(_modeContainer.error.becomeActive(payload));
	}

	public void shutdown()
	{
		// If we are in a state which has a game session, shut it down.
		if (_modeContainer.options == _modeContainer.currentMode)
		{
			if (null != _modeContainer.options.currentGameSession)
			{
				_modeContainer.options.currentGameSession.shutdown();
			}
		}
		else if (_modeContainer.keyBindings == _modeContainer.currentMode)
		{
			if (null != _modeContainer.keyBindings.currentGameSession)
			{
				_modeContainer.keyBindings.currentGameSession.scene.shutdown();
			}
		}
		else if (_modeContainer.play == _modeContainer.currentMode)
		{
			_modeContainer.play.currentGameSession.scene.shutdown();
		}
		else if (_modeContainer.inventory == _modeContainer.currentMode)
		{
			_modeContainer.inventory.currentGameSession.scene.shutdown();
		}
		else if (_modeContainer.pause == _modeContainer.currentMode)
		{
			_modeContainer.pause.currentGameSession.scene.shutdown();
		}
		else if (_modeContainer.profile == _modeContainer.currentMode)
		{
			_modeContainer.profile.profilingSession.scene.shutdown();
		}
		else if (_modeContainer.trading == _modeContainer.currentMode)
		{
			_modeContainer.trading.currentGameSession.scene.shutdown();
		}
		_uiData.serverList.shutdown();
	}


	private void _drawRelevantWindows()
	{
		// Perform state-specific drawing.
		IAction action = _modeContainer.currentMode.drawRelevantWindows();
		
		// Run any actions based on clicking on the UI.
		if (null != action)
		{
			action.takeAction();
		}
	}

	private GameSession _createSinglePlayerSession(GL20 gl
		, LoadedResources resources
		, String directoryName
		, WorldConfig.WorldGeneratorName worldGeneratorName
		, WorldConfig.DefaultPlayerMode defaultPlayerMode
		, Difficulty difficulty
		, Integer basicWorldGeneratorSeed
	)
	{
		GameSession pendingGameSession;
		File localWorldDirectory = _localStorageManager.getWorldDirectory(directoryName);
		try
		{
			pendingGameSession = new GameSession(_env
				, gl
				, _uiData.mutablePreferences.screenBrightness
				, resources
				, "Local"
				, _uiData.mutablePreferences.preferredViewDistance.get()
				, null
				, localWorldDirectory
				, worldGeneratorName
				, defaultPlayerMode
				, difficulty
				, basicWorldGeneratorSeed
				, this
			);
		}
		catch (ConnectException e)
		{
			// There are no connections in this case.
			throw Assert.unexpected(e);
		}
		return pendingGameSession;
	}
}
