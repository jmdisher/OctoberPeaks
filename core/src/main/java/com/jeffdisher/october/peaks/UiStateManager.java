package com.jeffdisher.october.peaks;

import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.CraftAspect;
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
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.CraftDescription;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.SubBinding;
import com.jeffdisher.october.peaks.ui.ViewArmour;
import com.jeffdisher.october.peaks.ui.ViewCraftingPanel;
import com.jeffdisher.october.peaks.ui.ViewEntityInventory;
import com.jeffdisher.october.peaks.ui.ViewFuelSlot;
import com.jeffdisher.october.peaks.ui.ViewHotbar;
import com.jeffdisher.october.peaks.ui.ViewMetaData;
import com.jeffdisher.october.peaks.ui.ViewSelection;
import com.jeffdisher.october.peaks.ui.ViewTradeOffers;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.utils.GeometryHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the current high-level state of the UI based on events from the InputManager.
 */
public class UiStateManager implements GameSession.ICallouts
{
	public static final float RETICLE_SIZE = 0.05f;
	public static final Rect WINDOW_TOP_LEFT = new Rect(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final Rect WINDOW_TOP_RIGHT = new Rect(0.05f, 0.05f, ViewArmour.ARMOUR_SLOT_RIGHT_EDGE - ViewArmour.ARMOUR_SLOT_SCALE - ViewArmour.ARMOUR_SLOT_SPACING, 0.95f);
	public static final Rect WINDOW_BOTTOM = new Rect(-0.95f, -0.80f, 0.95f, -0.05f);
	public static final Rect WINDOW_LEFT = new Rect(-0.95f, -0.80f, -0.05f, 0.95f);
	public static final int MAX_WORLD_NAME = 16;
	public static final float CHARGE_BAR_WIDTH_MAX = 0.4f;
	public static final float CHARGE_BAR_LEFT = -0.2f;
	public static final float CHARGE_BAR_BOTTOM = -0.25f;
	public static final float CHARGE_BAR_TOP = -0.2f;

	private final Environment _env;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final EntityVolume _playerVolume;
	private final EntityType _villagerEntityType;
	private final ICallouts _captureState;
	private final GL20 _gl;
	private final LocalStorageManager _localStorageManager;
	private final LoadedResources _resources;
	private final ModeContainer _modeContainer;
	private final Map<Integer, String> _otherPlayersById;

	private boolean _rotationDidUpdate;
	private boolean _didAccountForTimeInFrame;
	private _AudibleMotion _audibleMotionInFrame;
	private boolean _waitingForMouseRelease1;
	private boolean _ctrlQPressed;
	private boolean _qPressed;

	// Data specifically related to high-level UI state.
	private boolean _isRunningOnServer;

	// Tracking related to orientation change updates.
	private boolean _orientationNeedsFlush;
	private float _yawRadians;
	private float _pitchRadians;

	// Bindings related to the game UI during a PLAY state (the in-game UI - not just menus, etc).
	private final Binding<WorldSelection> _selectionBinding;
	private final Binding<Entity> _entityBinding;
	private final Binding<Inventory> _thisEntityInventoryBinding;
	private final Binding<Inventory> _bottomWindowInventoryBinding;
	private final Binding<String> _bottomWindowTitleBinding;
	private final Binding<ViewFuelSlot.FuelTuple> _bottomWindowFuelBinding;
	private final Binding<String> _craftingPanelTitleBinding;
	private final Binding<List<CraftDescription>> _craftingPanelBinding;
	private final Binding<Integer> _currentTradingPartnerIdBinding;
	
	// Views for rendering parts of the UI in specific modes.
	private final Window _thisEntityInventoryWindow;
	private final Window _bottomInventoryWindow;
	private final Window _craftingWindow;
	private final Window _metaDataWindow;
	private final Window _hotbarWindow;
	private final Window _armourWindow;
	private final Window _selectionWindow;
	private final Window _leftTradingWindow;

	// Data related to the liquid overlay.
	private final Block _waterBlock;
	private final Block _lavaBlock;
	private AbsoluteLocation _eyeBlockLocation;

	public UiStateManager(Environment environment
		, GL20 gl
		, MouseState mouseState
		, File localStorageDirectory
		, LoadedResources resources
		, MutableControls mutableControls
		, MutablePreferences mutablePreferences
		, ICallouts captureState
	)
	{
		_env = environment;
		_ui = new GlUi(gl, resources);
		_mouseState = mouseState;
		_uiData = new UiData(localStorageDirectory, mutableControls, mutablePreferences);
		_playerVolume = environment.creatures.PLAYER.volume();
		_villagerEntityType = environment.creatures.getTypeById("op.villager");
		_captureState = captureState;
		_gl = gl;
		_localStorageManager = new LocalStorageManager(_uiData.worldListBinding, localStorageDirectory);
		_resources = resources;
		_modeContainer = new ModeContainer();
		_otherPlayersById = new HashMap<>();
		
		// Define all of our bindings.
		_selectionBinding = new Binding<>(null);
		_entityBinding = new Binding<>(null);
		_thisEntityInventoryBinding = new SubBinding<>(_entityBinding, (Entity entity) -> _getInventory(entity));
		Binding<NonStackableItem[]> armourBinding = new SubBinding<>(_entityBinding, (Entity entity) -> entity.armourSlots());
		_bottomWindowInventoryBinding = new Binding<>(null);
		_bottomWindowTitleBinding = new Binding<>(null);
		_bottomWindowFuelBinding = new Binding<>(null);
		_craftingPanelTitleBinding = new Binding<>(null);
		_craftingPanelBinding = new Binding<>(null);
		_currentTradingPartnerIdBinding = new Binding<>(0);
	
		// Create our views.
		IntConsumer mouseOverTopRightKeyConsumer = (int key) -> {
			Assert.assertTrue((_modeContainer.inventory == _modeContainer.currentMode) || (_modeContainer.trading == _modeContainer.currentMode));
			
			AbsoluteLocation openStation = (_modeContainer.inventory == _modeContainer.currentMode)
				? _modeContainer.inventory.openStationLocation
				: null
			;
			AbsoluteLocation relevantBlock;
			if (null != openStation)
			{
				relevantBlock = openStation;
			}
			else
			{
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_entityBinding.get(), _playerVolume);
				relevantBlock = feetBlock;
			}
			_handleHoverOverEntityInventoryItem(relevantBlock, key);
		};
		IntConsumer mouseOverBottomKeyConsumer = (int key) -> {
			Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
			AbsoluteLocation relevantBlock;
			if (null != _modeContainer.inventory.openStationLocation)
			{
				relevantBlock = _modeContainer.inventory.openStationLocation;
			}
			else
			{
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_entityBinding.get(), _playerVolume);
				relevantBlock = feetBlock;
			}
			_pullFromBlockToEntityInventory(relevantBlock, key);
		};
		Consumer<CraftDescription> craftHoverOverConsumer = (CraftDescription desc) -> {
			Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
			if (_modeContainer.inventory.isManualCraftingStation && (_mouseState.leftClick || _mouseState.leftShiftClick))
			{
				Craft craft = desc.craft();
				if (null != _modeContainer.inventory.openStationLocation)
				{
					_modeContainer.inventory.continuousInBlock = _mouseState.leftShiftClick ? craft : null;
					_modeContainer.inventory.currentGameSession.client.beginCraftInBlock(_modeContainer.inventory.openStationLocation, craft);
				}
				else
				{
					_modeContainer.inventory.continuousInInventory = _mouseState.leftShiftClick ? craft : null;
					_modeContainer.inventory.currentGameSession.client.beginCraftInInventory(craft);
				}
				_didAccountForTimeInFrame = true;
			}
		};
		
		BooleanSupplier isLeftClick = () -> _mouseState.leftClick;
		
		Binding<String> inventoryTitleBinding = new Binding<>("Inventory");
		ViewEntityInventory thisEntityInventoryView = new ViewEntityInventory(_ui, inventoryTitleBinding, _thisEntityInventoryBinding, null, mouseOverTopRightKeyConsumer, isLeftClick);
		_thisEntityInventoryWindow = new Window(WINDOW_TOP_RIGHT, thisEntityInventoryView);
		ViewFuelSlot fuelProgress = new ViewFuelSlot(_ui, _bottomWindowFuelBinding);
		ViewEntityInventory bottomInventoryView = new ViewEntityInventory(_ui, _bottomWindowTitleBinding, _bottomWindowInventoryBinding, fuelProgress, mouseOverBottomKeyConsumer, isLeftClick);
		_bottomInventoryWindow = new Window(WINDOW_BOTTOM, bottomInventoryView);
		ViewCraftingPanel craftingPanelView = new ViewCraftingPanel(_ui, _craftingPanelTitleBinding, _craftingPanelBinding, craftHoverOverConsumer, isLeftClick);
		_craftingWindow = new Window(WINDOW_TOP_LEFT, craftingPanelView);
		_metaDataWindow = new Window(ViewMetaData.LOCATION, new ViewMetaData(_ui, _entityBinding));
		_hotbarWindow = new Window(ViewHotbar.LOCATION, new ViewHotbar(_ui, _entityBinding));
		Consumer<BodyPart> eventHoverArmourBodyPart = (BodyPart hoverPart) -> {
			Assert.assertTrue((_modeContainer.inventory == _modeContainer.currentMode)
				|| (_modeContainer.trading == _modeContainer.currentMode)
			);
			if (_mouseState.leftClick)
			{
				// Note that we ignore the result since this will be reflected in the UI, if valid.
				GameSession currentGameSession = (_modeContainer.inventory == _modeContainer.currentMode)
					? _modeContainer.inventory.currentGameSession
					: _modeContainer.trading.currentGameSession
				;
				currentGameSession.client.swapArmour(hoverPart);
			}
		};
		_armourWindow = new Window(ViewArmour.LOCATION, new ViewArmour(_ui, armourBinding, eventHoverArmourBodyPart));
		_selectionWindow = new Window(ViewSelection.LOCATION, new ViewSelection(_ui, _env, _selectionBinding, (AbsoluteLocation location) -> {
			Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
			return _modeContainer.play.currentGameSession.blockLookup.readBlock(location);
		}, _otherPlayersById));
		Consumer<Item> tradeButtonConsumer = (Item tradeItem) -> {
			Assert.assertTrue(_modeContainer.trading == _modeContainer.currentMode);
			if (_mouseState.leftClick)
			{
				MinimalEntity villager = MinimalEntity.fromPartialEntity(_modeContainer.trading.currentGameSession.getEntityForId(_currentTradingPartnerIdBinding.get()));
				boolean didSend = _modeContainer.trading.currentGameSession.client.sendTrade(villager, tradeItem);
				if (!didSend)
				{
					// If we failed to send the trade, it means something went wrong (usually out of range) so exit trading mode.
					_modeContainer.trading.handleEscape();
				}
			}
		};
		ViewTradeOffers bottomTradingView = new ViewTradeOffers(_ui
			, _currentTradingPartnerIdBinding
			, (int villagerId) -> {
				Assert.assertTrue(_modeContainer.trading == _modeContainer.currentMode);
				PartialEntity partial = _modeContainer.trading.currentGameSession.getEntityForId(villagerId);
				return MinimalEntity.fromPartialEntity(partial);
			}, tradeButtonConsumer);
		_leftTradingWindow = new Window(WINDOW_LEFT, bottomTradingView);
		
		// Look up the liquid overlay types.
		_waterBlock = _env.blocks.fromItem(_env.items.getItemById("op.water_source"));
		_lavaBlock = _env.blocks.fromItem(_env.items.getItemById("op.lava_source"));
		
		// Build the modes (we add these late since they should be allowed to depend on arbitrary things here).
		_modeContainer.start = new ModeStart(_modeContainer
			, _localStorageManager
			, _ui
			, _mouseState
			, _uiData
		);
		_modeContainer.listSinglePlayer = new ModeListSinglePlayer(_modeContainer
			, _ui
			, _mouseState
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
			, _mouseState
			, _uiData
		);
		_modeContainer.newSinglePlayer =  new ModeNewSinglePlayer(_modeContainer
			, _ui
			, _mouseState
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
			, _mouseState
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
			, _mouseState
			, _uiData
		);
		_modeContainer.listForProfile = new ModeListForProfile(_modeContainer
			, _ui
			, _mouseState
			, _uiData
			, (ProfilingModes mode) -> {
				ProfilingSession session = new ProfilingSession(_env, _gl, _uiData.mutablePreferences.screenBrightness, _resources);
				mode.populate.accept(_env, session);
				return session;
			}
		);
		_modeContainer.options = new ModeOptions(_modeContainer
			, _ui
			, _mouseState
			, _uiData
			, (GameSession session) -> {
				_drawCommonPauseBackground(session);
			}
		);
		_modeContainer.keyBindings = new ModeKeyBindings(_modeContainer
			, _ui
			, _mouseState
			, _uiData
			, (GameSession session) -> {
				_drawCommonPauseBackground(session);
			}
		);
		_modeContainer.connecting = new ModeConnecting(_modeContainer
			, _ui
			, _mouseState
			, _uiData
		);
		_modeContainer.play = new ModePlay(_modeContainer
			, () -> {
				_captureState.shouldCaptureMouse(false);
			}
		);
		_modeContainer.inventory = new ModeInventory(_modeContainer
			, () -> {
				_captureState.shouldCaptureMouse(true);
			}
		);
		_modeContainer.pause = new ModePause(_modeContainer
			, _ui
			, _mouseState
			, _uiData
			, (GameSession session) -> {
				_drawCommonPauseBackground(session);
			}
			, () -> {
				_captureState.shouldCaptureMouse(true);
			}
		);
		_modeContainer.profile = new ModeProfile();
		_modeContainer.trading = new ModeTrading(_modeContainer
			, _currentTradingPartnerIdBinding
			, () -> {
				_captureState.shouldCaptureMouse(true);
			}
		);
		_modeContainer.error = new ModeError(_modeContainer
			, _ui
			, _mouseState
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
			_captureState.shouldCaptureMouse(false);
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
	public void otherClientJoined(int clientId, String name)
	{
		Object old = _otherPlayersById.put(clientId, name);
		Assert.assertTrue(null == old);
	}

	@Override
	public void otherClientLeft(int clientId)
	{
		Object old = _otherPlayersById.remove(clientId);
		Assert.assertTrue(null != old);
	}

	public void capturedMouseMoved(int deltaX, int deltaY)
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		if ((0 != deltaX) || (0 != deltaY))
		{
			_yawRadians = _modeContainer.play.currentGameSession.movement.rotateYaw(deltaX);
			_pitchRadians = _modeContainer.play.currentGameSession.movement.rotatePitch(deltaY);
			_orientationNeedsFlush = true;
		}
		_rotationDidUpdate = true;
	}

	public void walk(RelativeDirection relative)
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		boolean runningSpeed = false;
		_modeContainer.play.currentGameSession.client.accelerateHorizontal(relative, runningSpeed);
		_didAccountForTimeInFrame = true;
		_audibleMotionInFrame = _AudibleMotion.WALK;
	}

	public void run(RelativeDirection relative)
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		boolean runningSpeed = true;
		_modeContainer.play.currentGameSession.client.accelerateHorizontal(relative, runningSpeed);
		_didAccountForTimeInFrame = true;
		_audibleMotionInFrame = _AudibleMotion.RUN;
	}

	public void sneak(RelativeDirection relative)
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		_modeContainer.play.currentGameSession.client.sneak(relative);
		_didAccountForTimeInFrame = true;
		
		// We will say that sneaking is silent.
		_audibleMotionInFrame = null;
	}

	public void ascendOrJumpOrSwim()
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		_modeContainer.play.currentGameSession.client.ascendOrJumpOrSwim();
	}

	public void tryDescend()
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		_modeContainer.play.currentGameSession.client.tryDescend();
	}

	public void handleKeyEsc()
	{
		_modeContainer.currentMode.handleEscape();
		
		// Any meaning of "back" should stop text input.
		_uiData.typingCapture = null;
	}

	public void handleHotbarIndex(int hotbarIndex)
	{
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
	}

	public void handleKeyI()
	{
		// This only matters if we are playing or in the inventory screen.
		if (_modeContainer.inventory == _modeContainer.currentMode)
		{
			_modeContainer.setActive(_modeContainer.play.becomeActive(_modeContainer.inventory.currentGameSession));
			_captureState.shouldCaptureMouse(true);
		}
		else if (_modeContainer.play == _modeContainer.currentMode)
		{
			_modeContainer.setActive(_modeContainer.inventory.becomeActive(_modeContainer.play.currentGameSession, null));
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_captureState.shouldCaptureMouse(false);
		}
	}

	public void handleKeyF()
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
	}

	public void handleKeyQ(boolean isCtrlPressed)
	{
		if (isCtrlPressed)
		{
			_ctrlQPressed = true;
		}
		else
		{
			_qPressed = true;
		}
	}

	public void toggleCreativeFlight()
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		_modeContainer.play.currentGameSession.client.toggleCreativeFlight();
	}

	public void keyCodeUp(int lastKeyUp)
	{
		if ((_modeContainer.keyBindings == _modeContainer.currentMode) && (null != _uiData.currentlyChangingControl.get()))
		{
			boolean didSet = _uiData.mutableControls.setKeyForControl(_uiData.currentlyChangingControl.get(), lastKeyUp);
			if (didSet)
			{
				_uiData.currentlyChangingControl.set(null);
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
		// Find the selection, if the mode supports this.
		WorldSelection selection = null;
		PartialEntity entity = null;
		AbsoluteLocation stopBlock = null;
		FacingDirection stopBlockOrientation = null;
		Block stopBlockType = null;
		AbsoluteLocation preStopBlock = null;
		if (_modeContainer.play == _modeContainer.currentMode)
		{
			// See if the perspective changed.
			if (_rotationDidUpdate)
			{
				_rotationDidUpdate = false;
				Vector eye = _modeContainer.play.currentGameSession.movement.computeEye();
				Vector target = _modeContainer.play.currentGameSession.movement.computeTarget();
				Vector upVector = _modeContainer.play.currentGameSession.movement.computeUpVector();
				_modeContainer.play.currentGameSession.selectionManager.updatePosition(eye, target);
				_modeContainer.play.currentGameSession.scene.updatePosition(eye, target, upVector);
				_eyeBlockLocation = GeometryHelpers.locationFromVector(eye);
			}
			
			// Capture whatever is selected.
			selection = _modeContainer.play.currentGameSession.selectionManager.findSelection();
			if (null != selection)
			{
				entity = selection.entity();
				stopBlock = selection.stopBlock();
				BlockProxy proxy = (null != stopBlock)
					? _modeContainer.play.currentGameSession.blockLookup.readBlock(stopBlock)
					: null
				;
				if (null != proxy)
				{
					stopBlockType = proxy.getBlock();
					stopBlockOrientation = proxy.getOrientation();
				}
				else
				{
					// Note that the stopBlock can also point at the first not loaded block (since it is a "stop point"), but there is no point in drawing that.
					stopBlock = null;
				}
				preStopBlock = selection.preStopBlock();
			}
		}
		_selectionBinding.set(selection);
		
		// Draw the relevant windows on top of this scene (passing in any information describing the UI state).
		_drawRelevantWindows(entity, stopBlock, stopBlockType, stopBlockOrientation);
		
		_handleEndOfFrameEvents(entity, stopBlock, preStopBlock);
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
	}

	private void _handleEndOfFrameEvents(PartialEntity entity, AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
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
				_captureState.shouldCaptureMouse(true);
			}
		}
		else if (_modeContainer.currentMode == _modeContainer.play)
		{
			// Finalizing frame events can change the game session so capture that, now.
			GameSession currentGameSession = _modeContainer.play.currentGameSession;
			
			// This is the most common mode where events matter since it is where most of them start and passive events still need to be applied, in the background.
			// Finalize the event processing with this selection and accounting for inter-frame time.
			// Note that this must be last since we deliver some events while drawing windows, etc, when we discover click locations, etc.
			_finalizeFrameEvents(entity, stopBlock, preStopBlock);
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

	public void keyTyped(char typedCharacter)
	{
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
	}

	public void enterErrorState(String[] payload)
	{
		_modeContainer.setActive(_modeContainer.error.becomeActive(payload));
		_captureState.shouldCaptureMouse(false);
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
		if (_mouseState.leftClick)
		{
			// Select this in the hotbar (this will clear if already set).
			currentGameSession.client.setSelectedItemKeyOrClear(entityInventoryKey);
		}
		else if (_mouseState.rightClick)
		{
			currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, viewingFuelInventory);
		}
		else if (_mouseState.leftShiftClick)
		{
			currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, viewingFuelInventory);
		}
		else if (_qPressed || _ctrlQPressed)
		{
			// If we are holding ctrl, drop the entire stack.
			currentGameSession.client.dropItemSlot(entityInventoryKey, _ctrlQPressed);
			_qPressed = false;
			_ctrlQPressed = false;
		}
	}

	private void _pullFromBlockToEntityInventory(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
		
		// Note that we ignore the result since this will be reflected in the UI, if valid.
		if (_mouseState.rightClick)
		{
			_modeContainer.inventory.currentGameSession.client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _modeContainer.inventory.viewingFuelInventory);
		}
		else if (_mouseState.leftShiftClick)
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
			_captureState.shouldCaptureMouse(false);
			didOpen = true;
		}
		return didOpen;
	}

	private IAction _drawInventoryStateWindows()
	{
		Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
		
		// We are in inventory mode but we will need to handle station/floor cases differently.
		Inventory relevantInventory = null;
		Inventory inventoryToCraftFrom = null;
		List<Craft> validCrafts = null;
		CraftOperation currentOperation = null;
		String stationName = "Floor";
		ViewFuelSlot.FuelTuple fuelSlot = null;
		boolean isAutomaticCrafting = false;
		if (null != _modeContainer.inventory.openStationLocation)
		{
			// We are in station mode so check this block's inventory and crafting (potentially clearing it if it is no longer a station).
			BlockProxy stationBlock = _modeContainer.inventory.currentGameSession.blockLookup.readBlock(_modeContainer.inventory.openStationLocation);
			Block stationType = stationBlock.getBlock();
			
			if (_env.stations.getNormalInventorySize(stationType) > 0)
			{
				Inventory stationInventory = stationBlock.getInventory();
				inventoryToCraftFrom = stationInventory;
				// If we are viewing the fuel inventory, we want to use that, instead.
				FuelState fuel = stationBlock.getFuel();
				if (null != fuel)
				{
					if (_modeContainer.inventory.viewingFuelInventory)
					{
						stationInventory = fuel.fuelInventory();
					}
					Item currentFuel = fuel.currentFuel();
					if (null != currentFuel)
					{
						long totalFuel = _env.fuel.millisOfFuel(currentFuel);
						long remainingFuel = fuel.millisFuelled();
						float fuelRemaining = (float)remainingFuel / (float) totalFuel;
						fuelSlot = new ViewFuelSlot.FuelTuple(currentFuel, fuelRemaining);
					}
				}
				else
				{
					// This is invalid so just clear it.
					_modeContainer.inventory.viewingFuelInventory = false;
				}
				
				// Find the crafts for this station type.
				Set<String> classifications = _env.stations.getCraftingClasses(stationType);
				
				relevantInventory = stationInventory;
				validCrafts = _env.crafting.craftsForClassifications(classifications);
				// We will convert these into CraftOperation instances so we can splice in the current craft.
				currentOperation = stationBlock.getCrafting();
				if (0 == _env.stations.getManualMultiplier(stationType))
				{
					isAutomaticCrafting = true;
				}
				stationName = stationType.item().name();
				if (_modeContainer.inventory.viewingFuelInventory)
				{
					stationName += " Fuel";
				}
			}
			else
			{
				// This is no longer a station.
				_modeContainer.inventory.openStationLocation = null;
				_modeContainer.inventory.continuousInInventory = null;
				_modeContainer.inventory.continuousInBlock = null;
			}
		}
		
		Inventory entityInventory = _thisEntityInventoryBinding.get();
		if (null == _modeContainer.inventory.openStationLocation)
		{
			// We are just looking at the floor at our feet.
			Entity thisEntity = _entityBinding.get();
			
			inventoryToCraftFrom = entityInventory;
			// We are just looking at the entity inventory so find the built-in crafting recipes.
			validCrafts = _env.crafting.craftsForClassifications(Set.of(CraftAspect.BUILT_IN));
			// We will convert these into CraftOperation instances so we can splice in the current craft.
			currentOperation = thisEntity.ephemeralShared().localCraftOperation();
		}
		
		Inventory finalInventoryToCraftFrom = inventoryToCraftFrom;
		final CraftOperation finalCraftOperation = currentOperation;
		Craft currentCraft = (null != currentOperation) ? currentOperation.selectedCraft() : null;
		boolean canBeManuallySelected = !isAutomaticCrafting;
		List<CraftDescription> convertedCrafts = validCrafts.stream()
				.map((Craft craft) -> {
					long progressMillis = 0L;
					if (craft == currentCraft)
					{
						progressMillis = finalCraftOperation.completedMillis();
					}
					float progress = (float)progressMillis / (float)craft.millisPerCraft;
					CraftDescription.ItemRequirement[] requirements = Arrays.stream(craft.input)
							.map((Items input) -> {
								Item type = input.type();
								int available = finalInventoryToCraftFrom.getCount(type);
								return new CraftDescription.ItemRequirement(type, input.count(), available);
							})
							.toArray((int size) -> new CraftDescription.ItemRequirement[size])
					;
					// Note that we are assuming that there is only one output type.
					return new CraftDescription(craft
							, new Items(craft.output[0], craft.output.length)
							, requirements
							, progress
							, canBeManuallySelected
					);
				})
				.toList()
		;
		
		String craftingType = isAutomaticCrafting
				? "Automatic Crafting"
				: "Manual Crafting"
		;
		
		// We need to update our bindings BEFORE rendering anything.
		_bottomWindowInventoryBinding.set(relevantInventory);
		_bottomWindowTitleBinding.set(stationName);
		_bottomWindowFuelBinding.set(fuelSlot);
		_craftingPanelTitleBinding.set(craftingType);
		_craftingPanelBinding.set(convertedCrafts);
		_modeContainer.inventory.isManualCraftingStation = canBeManuallySelected;
		
		// Now, do the actual drawing.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter(_modeContainer.inventory.currentGameSession);
		
		// This is a window mode so draw the usual.
		IAction action = _drawCommonWindowModeElements();
		
		// We will show the crafting panel as long as there are any valid crafts.
		if (!convertedCrafts.isEmpty())
		{
			IAction hover = _craftingWindow.doRender(_mouseState.cursor);
			if (null != hover)
			{
				action = hover;
			}
		}
		IAction hover = _thisEntityInventoryWindow.doRender(_mouseState.cursor);
		if (null != hover)
		{
			action = hover;
		}
		hover = _bottomInventoryWindow.doRender(_mouseState.cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action)
		{
			action.renderHover(_mouseState.cursor);
		}
		
		// Return any action so that the caller can run the action now that rendering is finished.
		return action;
	}

	private IAction _drawPlayStateWindows()
	{
		// In this case, just draw the common UI elements.
		_ui.enterUiRenderMode();
		
		// We also use this helper for profling where this doesn't do anything so just skip it in that case.
		if (_modeContainer.play == _modeContainer.currentMode)
		{
			_handleEyeFilter(_modeContainer.play.currentGameSession);
		}
		
		_drawCommonPlayModeElements();
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _selectionWindow.doRender(_mouseState.cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		Entity entity = _entityBinding.get();
		if (null != entity)
		{
			int chargeMillis = entity.ephemeralShared().chargeMillis();
			if (chargeMillis > 0)
			{
				// We want to show the weapon charge as a horizontal progress bar, from left to right.
				int key = entity.hotbarItems()[entity.hotbarIndex()];
				// If we have nothing selected, we should have cleared the charge.
				Assert.assertTrue(0 != key);
				int maxCharge = _env.tools.getChargeMillis(_getInventory(entity).getSlotForKey(key).getType());
				// If we have a charge, we must be charging something.
				Assert.assertTrue(maxCharge > 0);
				float progress = (float)chargeMillis / (float)maxCharge;
				float left = CHARGE_BAR_LEFT;
				float bottom = CHARGE_BAR_BOTTOM;
				float right = progress * CHARGE_BAR_WIDTH_MAX + CHARGE_BAR_LEFT;
				float top = CHARGE_BAR_TOP;
				_ui.drawWholeTextureRect(_ui.pixelGreenAlpha, left, bottom, right, top);
			}
		}
		
		return null;
	}

	private IAction _drawTradingStateWindows()
	{
		_ui.enterUiRenderMode();
		
		_handleEyeFilter(_modeContainer.trading.currentGameSession);
		
		// This is a window mode so draw the usual.
		IAction action = _drawCommonWindowModeElements();
		
		// The trading window is the interesting part of this view.
		IAction hover = _leftTradingWindow.doRender(_mouseState.cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		hover = _thisEntityInventoryWindow.doRender(_mouseState.cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action)
		{
			action.renderHover(_mouseState.cursor);
		}
		
		// Return any action so that the caller can run the action now that rendering is finished.
		return action;
	}

	private void _drawCommonPauseBackground(GameSession currentGameSession)
	{
		if (null != currentGameSession)
		{
			// We don't use these actual parameters unless in the proper play mode.
			PartialEntity selectedEntity = null;
			AbsoluteLocation selectedBlock = null;
			Block selectedType = null;
			FacingDirection orientation = null;
			currentGameSession.scene.render(selectedEntity, selectedBlock, selectedType, orientation);
			currentGameSession.eyeEffect.drawEyeEffect();
		}
		
		// Draw whatever is common to states where we draw interactive buttons on top.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter(currentGameSession);
		
		_drawCommonPlayModeElements();
		
		// Draw the overlay to dim the window.
		_ui.drawWholeTextureRect(_ui.pixelDarkGreyAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
	}

	private IAction _drawCommonWindowModeElements()
	{
		// Draw the other common elements (inventory, armour, hotbar, etc).
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_mouseState.cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_mouseState.cursor);
			Assert.assertTrue(null == noAction);
		}
		return _armourWindow.doRender(_mouseState.cursor);
	}

	private void _drawCommonPlayModeElements()
	{
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_mouseState.cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_mouseState.cursor);
			Assert.assertTrue(null == noAction);
		}
	}

	private void _handleEyeFilter(GameSession currentGameSession)
	{
		// If our eye is under a liquid, draw the liquid over the screen (we do this here since it is part of the orthographic plane and not logically part of the scene).
		if (null != _eyeBlockLocation)
		{
			BlockProxy eyeProxy = currentGameSession.blockLookup.readBlock(_eyeBlockLocation);
			if (null != eyeProxy)
			{
				Block blockType = eyeProxy.getBlock();
				if (_waterBlock == blockType)
				{
					_ui.drawWholeTextureRect(_ui.pixelBlueAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
				}
				else if (_lavaBlock == blockType)
				{
					_ui.drawWholeTextureRect(_ui.pixelOrangeLava, -1.0f, -1.0f, 1.0f, 1.0f);
				}
			}
		}
	}

	private void _drawRelevantWindows(PartialEntity selectedEntity, AbsoluteLocation selectedBlock, Block stopBlockType, FacingDirection stopBlockOrientation)
	{
		// Perform state-specific drawing.
		IAction action = null;
		if (_modeContainer.currentMode == _modeContainer.start)
		{
			action = _modeContainer.start.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.listSinglePlayer)
		{
			action = _modeContainer.listSinglePlayer.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.confirmDeleteSinglePlayer)
		{
			action = _modeContainer.confirmDeleteSinglePlayer.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.newSinglePlayer)
		{
			action = _modeContainer.newSinglePlayer.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.listMultiPlayer)
		{
			action = _modeContainer.listMultiPlayer.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.newMultiPlayer)
		{
			action = _modeContainer.newMultiPlayer.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.listForProfile)
		{
			action = _modeContainer.listForProfile.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.options)
		{
			action = _modeContainer.options.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.keyBindings)
		{
			action = _modeContainer.keyBindings.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.connecting)
		{
			action = _modeContainer.connecting.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.play)
		{
			_modeContainer.play.currentGameSession.scene.render(selectedEntity, selectedBlock, stopBlockType, stopBlockOrientation);
			_modeContainer.play.currentGameSession.eyeEffect.drawEyeEffect();
			action = _drawPlayStateWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.inventory)
		{
			_modeContainer.inventory.currentGameSession.scene.render(selectedEntity, selectedBlock, stopBlockType, stopBlockOrientation);
			_modeContainer.inventory.currentGameSession.eyeEffect.drawEyeEffect();
			action = _drawInventoryStateWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.pause)
		{
			action = _modeContainer.pause.drawRelevantWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.profile)
		{
			_modeContainer.profile.profilingSession.scene.render(selectedEntity, selectedBlock, stopBlockType, stopBlockOrientation);
			_modeContainer.profile.profilingSession.eyeEffect.drawEyeEffect();
			action = _drawPlayStateWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.trading)
		{
			_modeContainer.trading.currentGameSession.scene.render(selectedEntity, selectedBlock, stopBlockType, stopBlockOrientation);
			_modeContainer.trading.currentGameSession.eyeEffect.drawEyeEffect();
			action = _drawTradingStateWindows();
		}
		else if (_modeContainer.currentMode == _modeContainer.error)
		{
			action = _modeContainer.error.drawRelevantWindows();
		}
		else
		{
			// Every state needs drawing support.
			throw Assert.unreachable();
		}
		
		// Run any actions based on clicking on the UI.
		if (null != action)
		{
			action.takeAction();
		}
	}

	private void _finalizeFrameEvents(PartialEntity entity, AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
	{
		Assert.assertTrue(_modeContainer.play == _modeContainer.currentMode);
		
		// See if we need to update our orientation.
		if (_orientationNeedsFlush)
		{
			_modeContainer.play.currentGameSession.client.setOrientation(_yawRadians, _pitchRadians);
			_orientationNeedsFlush = false;
		}
		
		// See if the click refers to anything selected.
		boolean didAct = false;
		if (_mouseState.mouseHeld0)
		{
			if (null != stopBlock)
			{
				didAct = _modeContainer.play.currentGameSession.client.hitBlock(stopBlock);
			}
			else if (null != entity)
			{
				if (_mouseState.mouseClicked0)
				{
					_modeContainer.play.currentGameSession.client.hitEntity(entity);
					didAct = true;
				}
			}
		}
		else if (_mouseState.mouseHeld1)
		{
			// We want to treat things like a bow as the highest priority, so we will handle that first, whether or not
			// the mouse button is held or clicked (although these priorities may be reconsidered).
			didAct = _modeContainer.play.currentGameSession.client.holdRightClickOnSelf();
			if (didAct)
			{
				_waitingForMouseRelease1 = true;
			}
			
			if (null != stopBlock)
			{
				// First, see if we need to change the UI state if this is a station we just clicked on.
				if (!didAct && _mouseState.mouseClicked1)
				{
					didAct = _didOpenStationInventory(stopBlock);
				}
			}
			else if (null != entity)
			{
				if (!didAct && _mouseState.mouseClicked1)
				{
					// Check if this is a villager and then switch into the trading UI mode.
					if ((entity.type() == _villagerEntityType) && (null != ((ExtensionVillager.Data)entity.extendedData()).profession()))
					{
						// This is a villager with a profession so switch to our trading UI mode.
						_modeContainer.setActive(_modeContainer.trading.becomeActive(_modeContainer.play.currentGameSession));
						_captureState.shouldCaptureMouse(false);
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
			if (!didAct && _mouseState.mouseClicked1 && (null != stopBlock))
			{
				didAct = _modeContainer.play.currentGameSession.client.runRightClickOnBlock(stopBlock, preStopBlock);
			}
			if (!didAct && _mouseState.mouseClicked1)
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
		else if (_waitingForMouseRelease1)
		{
			didAct = _modeContainer.play.currentGameSession.client.releasedRightClickOnSelf();
			if (didAct)
			{
				// If we failed to send the release, just wait for our next frame (usually means that there is still a "charge" in the current accumulation).
				_waitingForMouseRelease1 = false;
			}
		}
		
		// If we were in the normal play mode, we still want to be able to drop from the hotbar.
		if (!didAct)
		{
			if (_qPressed || _ctrlQPressed)
			{
				// If we are holding ctrl, drop the entire stack.
				Entity thisEntity = _entityBinding.get();
				int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
				if (Entity.NO_SELECTION != selectedKey)
				{
					_modeContainer.play.currentGameSession.client.dropItemSlot(selectedKey, _ctrlQPressed);
					didAct = true;
				}
				_qPressed = false;
				_ctrlQPressed = false;
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
		if (!_didAccountForTimeInFrame)
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
		
		_didAccountForTimeInFrame = false;
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

	private static Inventory _getInventory(Entity entity)
	{
		Inventory inventory = entity.isCreativeMode()
			? CreativeInventory.fakeInventory()
			: entity.inventory()
		;
		return inventory;
	}


	private static enum _AudibleMotion
	{
		WALK,
		RUN,
	}


	/**
	 * Methods passed in from a higher-level component to control other aspects of the native window manager environment
	 * required by the internal logic.
	 */
	public static interface ICallouts
	{
		public void shouldCaptureMouse(boolean setCapture);
	}
}
