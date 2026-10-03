package com.jeffdisher.october.peaks;

import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.client.RelativeDirection;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
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
import com.jeffdisher.october.peaks.ui.CraftDescription;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the current high-level state of the UI based on events from the InputManager.
 */
public class UiStateManager implements GameSession.ICallouts
{
	public static final int MAX_WORLD_NAME = 16;

	private final Environment _env;
	private final GlUi _ui;
	private final InputCapture _inputCapture;
	private final UiData _uiData;
	private final EntityVolume _playerVolume;
	private final EntityType _villagerEntityType;
	private final GL20 _gl;
	private final LocalStorageManager _localStorageManager;
	private final LoadedResources _resources;
	private final ModeContainer _modeContainer;

	private _AudibleMotion _audibleMotionInFrame;

	// Data specifically related to high-level UI state.
	private boolean _isRunningOnServer;

	// Tracking related to orientation change updates.
	private boolean _orientationNeedsFlush;
	private float _yawRadians;
	private float _pitchRadians;

	// Bindings related to the game UI during a PLAY state (the in-game UI - not just menus, etc).
	private final Binding<Entity> _entityBinding;
	private final Binding<Integer> _currentTradingPartnerIdBinding;

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
		_playerVolume = environment.creatures.PLAYER.volume();
		_villagerEntityType = environment.creatures.getTypeById("op.villager");
		_gl = gl;
		_localStorageManager = new LocalStorageManager(_uiData.worldListBinding, localStorageDirectory);
		_resources = resources;
		_modeContainer = new ModeContainer();
		
		// Define all of our bindings.
		_entityBinding = new Binding<>(null);
		_currentTradingPartnerIdBinding = new Binding<>(0);
	
		// Create our views.
		IntConsumer mouseOverTopRightKeyConsumer = (int key) -> {
			Assert.assertTrue((_modeContainer.inventory == _modeContainer.currentMode) || (_modeContainer.trading == _modeContainer.currentMode));
			
			AbsoluteLocation openStation = (_modeContainer.inventory == _modeContainer.currentMode)
				? _modeContainer.inventory.openStationLocation
				: null
			;
			_handleHoverOverEntityInventoryItem(openStation, key);
		};
		IntConsumer mouseOverBottomKeyConsumer = (int key) -> {
			Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
			AbsoluteLocation relevantBlock = _modeContainer.inventory.openStationLocation;
			_pullFromBlockToEntityInventory(relevantBlock, key);
		};
		Consumer<CraftDescription> craftHoverOverConsumer = (CraftDescription desc) -> {
			Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
			if (_modeContainer.inventory.isManualCraftingStation && (_inputCapture.mouseReleased0))
			{
				Craft craft = desc.craft();
				if (null != _modeContainer.inventory.openStationLocation)
				{
					_modeContainer.inventory.continuousInBlock = _inputCapture.leftShiftHeld ? craft : null;
					_modeContainer.inventory.currentGameSession.client.beginCraftInBlock(_modeContainer.inventory.openStationLocation, craft);
				}
				else
				{
					_modeContainer.inventory.continuousInInventory = _inputCapture.leftShiftHeld ? craft : null;
					_modeContainer.inventory.currentGameSession.client.beginCraftInInventory(craft);
				}
				_inputCapture.didAccountForTimeInFrame = true;
			}
		};
		
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
				GameSession session = _createSinglePlayerSession(_gl, _resources, directoryName, null, null, null, 0);
				_isRunningOnServer = false;
				_uiData.isRunningOnServerBinding.set(_isRunningOnServer);
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
				GameSession session = _createSinglePlayerSession(_gl, _resources, directoryName, worldGeneratorName, defaultPlayerMode, difficulty, basicWorldGeneratorSeed);
				_isRunningOnServer = false;
				_uiData.isRunningOnServerBinding.set(_isRunningOnServer);
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
					_isRunningOnServer = true;
					_uiData.isRunningOnServerBinding.set(_isRunningOnServer);
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
				ProfilingSession session = new ProfilingSession(_env, _gl, _uiData.mutablePreferences.screenBrightness, _resources);
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
		);
		_modeContainer.inventory = new ModeInventory(_modeContainer
			, _ui
			, _inputCapture
			, _entityBinding
			, mouseOverTopRightKeyConsumer
			, mouseOverBottomKeyConsumer
			, craftHoverOverConsumer
		);
		_modeContainer.pause = new ModePause(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
		);
		_modeContainer.profile = new ModeProfile();
		_modeContainer.trading = new ModeTrading(_modeContainer
			, _ui
			, _inputCapture
			, _currentTradingPartnerIdBinding
			, _entityBinding
			, mouseOverTopRightKeyConsumer
		);
		_modeContainer.error = new ModeError(_modeContainer
			, _ui
			, _inputCapture
			, _uiData
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
		
		// If we were in the active play state, release the mouse capture (this check just makes the transition more explicit).
		if (_modeContainer.play == _modeContainer.currentMode)
		{
			_inputCapture.captureState.shouldCaptureMouse(false);
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

	/**
	 * Called after clearing the framebuffer in order to render the frame with whatever is required for in the current
	 * UI state.
	 * Internally, this is also an opportunity for the state manager to act on, flush, or reset any input events it has
	 * received since the last frame.
	 */
	public void renderFrame()
	{
		// Flush any captured input events.
		_flushInputEvents();
		
		// Find the selection, if the mode supports this.
		if (_modeContainer.play == _modeContainer.currentMode)
		{
			// We will reach into this mode in the other cases where this is needed.
			_modeContainer.play.updateSelection();
		}
		
		// Draw the relevant windows on top of this scene (passing in any information describing the UI state).
		_drawRelevantWindows();
		
		_handleEndOfFrameEvents();
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
		_inputCapture.clearReleaseState();
	}


	private void _handleEndOfFrameEvents()
	{
		if (_modeContainer.currentMode == _modeContainer.options)
		{
			// We can be in this state while running or while at the main menu.
			if (null != _modeContainer.options.currentGameSession)
			{
				// This mode is also accessible from the pause menu so check if we are on a server.
				if (_isRunningOnServer)
				{
					_passTimeWhileRunning(_modeContainer.options.currentGameSession);
				}
				else
				{
					_modeContainer.options.currentGameSession.client.passTimeWhilePaused();
				}
			}
		}
		else if (_modeContainer.currentMode == _modeContainer.keyBindings)
		{
			// We can be in this state while running or while at the main menu.
			if (null != _modeContainer.keyBindings.currentGameSession)
			{
				// This mode is also accessible from the pause menu so check if we are on a server.
				if (_isRunningOnServer)
				{
					_passTimeWhileRunning(_modeContainer.keyBindings.currentGameSession);
				}
				else
				{
					_modeContainer.keyBindings.currentGameSession.client.passTimeWhilePaused();
				}
			}
		}
		else if (_modeContainer.currentMode == _modeContainer.connecting)
		{
			// This is a bit of a hack but we can easily poll for state change here instead of coming up with a cross-
			// thread callback mechanism (some kind of message queue)just for this.
			if (_modeContainer.connecting.pendingGameSession.isConnectionReady())
			{
				_modeContainer.setActive(_modeContainer.play.becomeActive(_modeContainer.connecting.pendingGameSession));
				_inputCapture.captureState.shouldCaptureMouse(true);
			}
		}
		else if (_modeContainer.currentMode == _modeContainer.play)
		{
			// Finalizing frame events can change the game session so capture that, now.
			GameSession currentGameSession = _modeContainer.play.currentGameSession;
			
			// This is the most common mode where events matter since it is where most of them start and passive events still need to be applied, in the background.
			// Finalize the event processing with this selection and accounting for inter-frame time.
			// Note that this must be last since we deliver some events while drawing windows, etc, when we discover click locations, etc.
			_finalizeFrameEvents();
			_passTimeWhileRunning(currentGameSession);
		}
		else if (_modeContainer.currentMode == _modeContainer.inventory)
		{
			// This is similar to PLAY but only passive events are relevant here since any active events come from actions in the UI.
			_passTimeWhileRunning(_modeContainer.inventory.currentGameSession);
		}
		else if (_modeContainer.currentMode == _modeContainer.pause)
		{
			if (_isRunningOnServer)
			{
				_passTimeWhileRunning(_modeContainer.pause.currentGameSession);
			}
			else
			{
				_modeContainer.pause.currentGameSession.client.passTimeWhilePaused();
			}
		}
		else if (_modeContainer.currentMode == _modeContainer.trading)
		{
			// This is similar to PLAY but only passive events are relevant here since any active events come from actions in the UI.
			_passTimeWhileRunning(_modeContainer.trading.currentGameSession);
		}
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
		_inputCapture.captureState.shouldCaptureMouse(false);
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


	private void _handleHoverOverEntityInventoryItem(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		Assert.assertTrue((_modeContainer.inventory == _modeContainer.currentMode)
			|| (_modeContainer.trading == _modeContainer.currentMode)
		);
		GameSession currentGameSession = (_modeContainer.inventory == _modeContainer.currentMode)
			? _modeContainer.inventory.currentGameSession
			: _modeContainer.trading.currentGameSession
		;
		boolean viewingFuelInventory = (_modeContainer.inventory == _modeContainer.currentMode) && _modeContainer.inventory.viewingFuelInventory;
		
		// This is the helper called when looking at the player's own inventory.
		if (_inputCapture.mouseReleased0 && !_inputCapture.leftShiftHeld)
		{
			// Select this in the hotbar (this will clear if already set).
			currentGameSession.client.setSelectedItemKeyOrClear(entityInventoryKey);
		}
		else if ((null != targetBlock) && _inputCapture.mouseReleased1)
		{
			currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, viewingFuelInventory);
		}
		else if ((null != targetBlock) && (_inputCapture.mouseReleased0 && _inputCapture.leftShiftHeld))
		{
			currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, viewingFuelInventory);
		}
		else if (_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()])
		{
			// If we are holding ctrl, drop the entire stack.
			currentGameSession.client.dropItemSlot(entityInventoryKey, _inputCapture.leftCtrlHeld);
			_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()] = false;
		}
	}

	private void _pullFromBlockToEntityInventory(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
		
		// Note that we ignore the result since this will be reflected in the UI, if valid.
		if (_inputCapture.mouseReleased1)
		{
			_modeContainer.inventory.currentGameSession.client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _modeContainer.inventory.viewingFuelInventory);
		}
		else if (_inputCapture.leftShiftHeld && _inputCapture.mouseReleased0)
		{
			_modeContainer.inventory.currentGameSession.client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, _modeContainer.inventory.viewingFuelInventory);
		}
	}

	private boolean _didOpenStationInventory(AbsoluteLocation blockLocation)
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		// See if there is an inventory we can open at the given block location.
		// NOTE:  We don't use this mechanism to talk about air blocks (or other empty blocks with ad-hoc inventories), only actual blocks.
		BlockProxy proxy = _modeContainer.play.currentGameSession.blockLookup.readBlock(blockLocation);
		boolean didOpen = false;
		Block block = proxy.getBlock();
		if (_env.stations.getNormalInventorySize(block) > 0)
		{
			// We are at least some kind of station with an inventory.
			_modeContainer.setActive(_modeContainer.inventory.becomeActive(_modeContainer.play.currentGameSession, blockLocation));
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_inputCapture.captureState.shouldCaptureMouse(false);
			didOpen = true;
		}
		return didOpen;
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

	private void _finalizeFrameEvents()
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		PartialEntity entity = _modeContainer.play.selectedEntity;
		AbsoluteLocation stopBlock = _modeContainer.play.selectedBlock;
		AbsoluteLocation preStopBlock = _modeContainer.play.preSelectedBlock;
		
		// See if we need to update our orientation.
		if (_orientationNeedsFlush)
		{
			_modeContainer.play.currentGameSession.client.setOrientation(_yawRadians, _pitchRadians);
			_orientationNeedsFlush = false;
		}
		
		// See if the click refers to anything selected.
		boolean didAct = false;
		if (_inputCapture.mouseHeld0)
		{
			if (null != stopBlock)
			{
				didAct = _modeContainer.play.currentGameSession.client.hitBlock(stopBlock);
			}
			else if (null != entity)
			{
				if (_inputCapture.mousePressed0)
				{
					_modeContainer.play.currentGameSession.client.hitEntity(entity);
					didAct = true;
				}
			}
		}
		else if (_inputCapture.mouseHeld1)
		{
			// We want to treat things like a bow as the highest priority, so we will handle that first, whether or not
			// the mouse button is held or clicked (although these priorities may be reconsidered).
			didAct = _modeContainer.play.currentGameSession.client.holdRightClickOnSelf();
			if (didAct)
			{
				_modeContainer.play.isWaitingForRightClickRelease = true;
			}
			
			if (null != stopBlock)
			{
				// First, see if we need to change the UI state if this is a station we just clicked on.
				if (!didAct && _inputCapture.mousePressed1)
				{
					didAct = _didOpenStationInventory(stopBlock);
				}
			}
			else if (null != entity)
			{
				if (!didAct && _inputCapture.mousePressed1)
				{
					// Check if this is a villager and then switch into the trading UI mode.
					if ((entity.type() == _villagerEntityType) && (null != ((ExtensionVillager.Data)entity.extendedData()).profession()))
					{
						// This is a villager with a profession so switch to our trading UI mode.
						_modeContainer.setActive(_modeContainer.trading.becomeActive(_modeContainer.play.currentGameSession));
						_inputCapture.captureState.shouldCaptureMouse(false);
						_currentTradingPartnerIdBinding.set(entity.id());
					}
					else
					{
						// Otherwise, try to apply the current item to the entity.
						_modeContainer.play.currentGameSession.client.applyToEntity(entity);
					}
					// As long as we attempted either of these, we consider the action complete.
					didAct = true;
				}
			}
			
			// If we still didn't do anything, try clicks on the block or self.
			if (!didAct && _inputCapture.mousePressed1 && (null != stopBlock))
			{
				didAct = _modeContainer.play.currentGameSession.client.runRightClickOnBlock(stopBlock, preStopBlock);
			}
			if (!didAct && _inputCapture.mousePressed1)
			{
				didAct = _modeContainer.play.currentGameSession.client.runRightClickOnSelf();
			}
			if (!didAct && (null != stopBlock) && (null != preStopBlock))
			{
				// In this case, we either want to place a block or repair a block.
				didAct = _modeContainer.play.currentGameSession.client.runPlaceBlock(stopBlock, preStopBlock);
				if (!didAct)
				{
					didAct = _modeContainer.play.currentGameSession.client.runRepairBlock(stopBlock);
				}
			}
		}
		else if (_modeContainer.play.isWaitingForRightClickRelease)
		{
			didAct = _modeContainer.play.currentGameSession.client.releasedRightClickOnSelf();
			if (didAct)
			{
				// If we failed to send the release, just wait for our next frame (usually means that there is still a "charge" in the current accumulation).
				_modeContainer.play.isWaitingForRightClickRelease = false;
			}
		}
		
		// If we were in the normal play mode, we still want to be able to drop from the hotbar.
		if (!didAct)
		{
			if (_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()])
			{
				// If we are holding ctrl, drop the entire stack.
				Entity thisEntity = _entityBinding.get();
				int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
				if (Entity.NO_SELECTION != selectedKey)
				{
					_modeContainer.play.currentGameSession.client.dropItemSlot(selectedKey, _inputCapture.leftCtrlHeld);
					didAct = true;
				}
				_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()] = false;
			}
		}
		
		// We may have changed mode above so check that.
		GameSession currentGameSession = _modeContainer.play.currentGameSession;
		if (didAct)
		{
			if (_modeContainer.inventory == _modeContainer.currentMode)
			{
				currentGameSession = _modeContainer.inventory.currentGameSession;
			}
			else if (_modeContainer.trading == _modeContainer.currentMode)
			{
				currentGameSession = _modeContainer.trading.currentGameSession;
			}
		}
		
		ViscosityReader reader = new ViscosityReader(_env, currentGameSession.blockLookup);
		if ((null != _audibleMotionInFrame) && SpatialHelpers.isStandingOnGround(reader, _entityBinding.get().location(), _playerVolume))
		{
			switch (_audibleMotionInFrame)
			{
			case WALK:
				currentGameSession.audioManager.setWalking();
				break;
			case RUN:
				currentGameSession.audioManager.setRunning();
				break;
			}
		}
		else
		{
			currentGameSession.audioManager.setStanding();
		}
	}

	/**
	 * Continues any active operations and completes accounting for time in a frame where the game is active and not
	 * paused.
	 */
	private void _passTimeWhileRunning(GameSession currentGameSession)
	{
		// Complete any of the idle operations and account for time passing.
		// If we took no action, just tell the client to pass time.
		if (!_inputCapture.didAccountForTimeInFrame)
		{
			Craft rescheduleInInventory = null;
			AbsoluteLocation openStationLocation = null;
			Craft rescheduleInBlock = null;
			
			if (_modeContainer.inventory == _modeContainer.currentMode)
			{
				// Check to see if our continuous crafting operations are still valid.
				if (null != _modeContainer.inventory.continuousInInventory)
				{
					boolean isValid = currentGameSession.client.isCraftInInventoryValid(_modeContainer.inventory.continuousInInventory);
					if (!isValid)
					{
						// We can't continue this so drop it.
						_modeContainer.inventory.continuousInInventory = null;
					}
				}
				if (null != _modeContainer.inventory.continuousInBlock)
				{
					boolean isValid = currentGameSession.client.isCraftInBlockValid(_modeContainer.inventory.openStationLocation, _modeContainer.inventory.continuousInBlock);
					if (!isValid)
					{
						// We can't continue this so drop it.
						_modeContainer.inventory.continuousInBlock = null;
					}
				}
				rescheduleInInventory = _modeContainer.inventory.continuousInInventory;
				openStationLocation = _modeContainer.inventory.openStationLocation;
				rescheduleInBlock = _modeContainer.inventory.continuousInBlock;
			}
			currentGameSession.client.passTimeWhileRunning(rescheduleInInventory, openStationLocation, rescheduleInBlock);
		}
		
		_inputCapture.didAccountForTimeInFrame = false;
		_audibleMotionInFrame = null;
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

	private void _flushInputEvents()
	{
		// We want to go down the list of things we might need to report and tell the UI Manager.
		
		// Firstly, we operate in a different mode, whether we are in capturing mode or not.
		if (_inputCapture.shouldCaptureMouseMovements)
		{
			Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
			
			// When we are capturing, the cursor is invisible and this is essentially a "yoke".
			if ((_inputCapture.mouseX != _inputCapture.lastReportedMouseX) || (_inputCapture.mouseY != _inputCapture.lastReportedMouseY))
			{
				int deltaX = _inputCapture.mouseX - _inputCapture.lastReportedMouseX;
				int deltaY = _inputCapture.mouseY - _inputCapture.lastReportedMouseY;
				_inputCapture.lastReportedMouseX = _inputCapture.mouseX;
				_inputCapture.lastReportedMouseY = _inputCapture.mouseY;
				Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
				
				// Something has to change for us to get this call.
				Assert.assertTrue((0 != deltaX) || (0 != deltaY));
				_yawRadians = _modeContainer.play.currentGameSession.movement.rotateYaw(deltaX);
				_pitchRadians = _modeContainer.play.currentGameSession.movement.rotatePitch(deltaY);
				_orientationNeedsFlush = true;
			}
			
			// Check out movement controls.
			RelativeDirection relativeMove = _getCurrentMove();
			if (null != relativeMove)
			{
				if (_inputCapture.controlHeld[MutableControls.Control.MOVE_SNEAK.ordinal()])
				{
					_modeContainer.play.currentGameSession.client.sneak(relativeMove);
					_inputCapture.didAccountForTimeInFrame = true;
					
					// We will say that sneaking is silent.
					_audibleMotionInFrame = null;
				}
				else if (_inputCapture.controlHeld[MutableControls.Control.MOVE_SPRINT.ordinal()])
				{
					boolean runningSpeed = true;
					_modeContainer.play.currentGameSession.client.accelerateHorizontal(relativeMove, runningSpeed);
					_inputCapture.didAccountForTimeInFrame = true;
					_audibleMotionInFrame = _AudibleMotion.RUN;
				}
				else
				{
					boolean runningSpeed = false;
					_modeContainer.play.currentGameSession.client.accelerateHorizontal(relativeMove, runningSpeed);
					_inputCapture.didAccountForTimeInFrame = true;
					_audibleMotionInFrame = _AudibleMotion.WALK;
				}
			}
			
			// See if we want to jump or try descending a ladder.
			if (_inputCapture.controlHeld[MutableControls.Control.MOVE_JUMP.ordinal()])
			{
				_modeContainer.play.currentGameSession.client.ascendOrJumpOrSwim();
			}
			else if (_inputCapture.controlHeld[MutableControls.Control.MOVE_SNEAK.ordinal()])
			{
				_modeContainer.play.currentGameSession.client.tryDescend();
			}
			if (_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FLIGHT.ordinal()])
			{
				_modeContainer.play.currentGameSession.client.toggleCreativeFlight();
				_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FLIGHT.ordinal()] = false;
			}
		}
		else
		{
			// We also only capture the raw text input when not capturing movements since this would just be noise.
			if ('\0' != _inputCapture.typedCharacter)
			{
				char typedCharacter = _inputCapture.typedCharacter;
				// If we have a binding capturing keys, make sure that this is one of our whitelist character types and then append it.
				if (null != _uiData.typingCapture)
				{
					String string = _uiData.typingCapture.get();
					int nameLength = string.length();
					if (('\b' == typedCharacter) && (nameLength > 0))
					{
						// Backspace is a special case.
						_uiData.typingCapture.set(string.substring(0, string.length() - 1));
					}
					else if (nameLength < MAX_WORLD_NAME)
					{
						int type = Character.getType(typedCharacter);
						switch (type)
						{
						case Character.LOWERCASE_LETTER:
						case Character.UPPERCASE_LETTER:
						case Character.DECIMAL_DIGIT_NUMBER:
							_uiData.typingCapture.set(string + typedCharacter);
							break;
							default:
								// Special-case whitelist.
								switch (typedCharacter)
								{
								case '.':
								case ':':
								case '-':
								case '_':
								case ' ':
									_uiData.typingCapture.set(string + typedCharacter);
									break;
								default:
									// Ignored.
								}
						}
					}
				}
				_inputCapture.typedCharacter = '\0';
			}
		}
		
		// Now, we handle the special events related to specific keys which generally change UI state.
		if (_inputCapture.didReleaseEsc)
		{
			_modeContainer.currentMode.handleEscape();
			
			// Any meaning of "back" should stop text input.
			_uiData.typingCapture = null;
			_inputCapture.didReleaseEsc = false;
		}
		if (-1 != _inputCapture.lastPressedNumber)
		{
			int hotbarIndex = _inputCapture.lastPressedNumber - 1;
			// We need an active session and not paused but logically this means play or inventory.
			if (_modeContainer.play == _modeContainer.currentMode)
			{
				_modeContainer.play.currentGameSession.client.changeHotbarIndex(hotbarIndex);
			}
			else if (_modeContainer.inventory == _modeContainer.currentMode)
			{
				_modeContainer.inventory.currentGameSession.client.changeHotbarIndex(hotbarIndex);
			}
			else if (_modeContainer.trading == _modeContainer.currentMode)
			{
				_modeContainer.trading.currentGameSession.client.changeHotbarIndex(hotbarIndex);
			}
			_inputCapture.lastPressedNumber = -1;
		}
		if (_inputCapture.controlReleased[MutableControls.Control.TOGGLE_INVENTORY.ordinal()])
		{
			// This only matters if we are playing or in the inventory screen.
			if (_modeContainer.inventory == _modeContainer.currentMode)
			{
				_modeContainer.setActive(_modeContainer.play.becomeActive(_modeContainer.inventory.currentGameSession));
				_inputCapture.captureState.shouldCaptureMouse(true);
			}
			else if (_modeContainer.play == _modeContainer.currentMode)
			{
				_modeContainer.setActive(_modeContainer.inventory.becomeActive(_modeContainer.play.currentGameSession, null));
				// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
				_inputCapture.captureState.shouldCaptureMouse(false);
			}
			_inputCapture.controlReleased[MutableControls.Control.TOGGLE_INVENTORY.ordinal()] = false;
		}
		if (_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FUEL.ordinal()])
		{
			if (_modeContainer.inventory == _modeContainer.currentMode)
			{
				_modeContainer.inventory.viewingFuelInventory = !_modeContainer.inventory.viewingFuelInventory;
				if (_modeContainer.inventory.viewingFuelInventory)
				{
					// Make sure that this actually has a fuel slot.
					if (null == _modeContainer.inventory.openStationLocation)
					{
						_modeContainer.inventory.viewingFuelInventory = false;
					}
					else
					{
						BlockProxy stationBlock = _modeContainer.inventory.currentGameSession.blockLookup.readBlock(_modeContainer.inventory.openStationLocation);
						_modeContainer.inventory.viewingFuelInventory = (null != stationBlock.getFuel());
					}
				}
				_modeContainer.inventory.continuousInInventory = null;
				_modeContainer.inventory.continuousInBlock = null;
			}
			_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FUEL.ordinal()] = false;
		}
		
		if (Keys.UNKNOWN != _inputCapture.lastKeyCodeReleased)
		{
			if ((_modeContainer.keyBindings == _modeContainer.currentMode) && (null != _uiData.currentlyChangingControl.get()))
			{
				boolean didSet = _uiData.mutableControls.setKeyForControl(_uiData.currentlyChangingControl.get(), _inputCapture.lastKeyCodeReleased);
				if (didSet)
				{
					_uiData.currentlyChangingControl.set(null);
				}
			}
			_inputCapture.lastKeyCodeReleased = Keys.UNKNOWN;
		}
	}

	private RelativeDirection _getCurrentMove()
	{
		// Given that we can mix directions (forward + right, for example), this function handles that combination logic.
		int forward = 0;
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_FORWARD.ordinal()])
		{
			forward += 1;
		}
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_BACKWARD.ordinal()])
		{
			forward -= 1;
		}
		int right = 0;
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_RIGHT.ordinal()])
		{
			right += 1;
		}
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_LEFT.ordinal()])
		{
			right -= 1;
		}
		
		RelativeDirection relative;
		if (forward > 0)
		{
			if (right > 0)
			{
				relative = RelativeDirection.FORWARD_RIGHT;
			}
			else if (right < 0)
			{
				relative = RelativeDirection.FORWARD_LEFT;
			}
			else
			{
				relative = RelativeDirection.FORWARD;
			}
		}
		else if (forward < 0)
		{
			if (right > 0)
			{
				relative = RelativeDirection.BACKWARD_RIGHT;
			}
			else if (right < 0)
			{
				relative = RelativeDirection.BACKWARD_LEFT;
			}
			else
			{
				relative = RelativeDirection.BACKWARD;
			}
		}
		else
		{
			if (right > 0)
			{
				relative = RelativeDirection.RIGHT;
			}
			else if (right < 0)
			{
				relative = RelativeDirection.LEFT;
			}
			else
			{
				relative = null;
			}
		}
		return relative;
	}


	private static enum _AudibleMotion
	{
		WALK,
		RUN,
	}
}
