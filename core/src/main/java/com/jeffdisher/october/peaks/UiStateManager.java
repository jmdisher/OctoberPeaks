package com.jeffdisher.october.peaks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.ComplexItemView;
import com.jeffdisher.october.peaks.ui.CraftDescription;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.IView;
import com.jeffdisher.october.peaks.ui.ItemTuple;
import com.jeffdisher.october.peaks.ui.Point;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.SubBinding;
import com.jeffdisher.october.peaks.ui.UiIdioms;
import com.jeffdisher.october.peaks.ui.ViewArmour;
import com.jeffdisher.october.peaks.ui.ViewControlIntChanger;
import com.jeffdisher.october.peaks.ui.ViewCraftingPanel;
import com.jeffdisher.october.peaks.ui.ViewEntityInventory;
import com.jeffdisher.october.peaks.ui.ViewHotbar;
import com.jeffdisher.october.peaks.ui.ViewMetaData;
import com.jeffdisher.october.peaks.ui.ViewSelection;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.utils.GeometryHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the current high-level state of the UI based on events from the InputManager.
 */
public class UiStateManager
{
	/**
	 * In order to avoid cases like placing blocks too quickly or aggressively breaking a block behind a target, we will
	 * delay an action on a new block by this many milliseconds.
	 * This will also be applied to things like right-click actions on entities/blocks.
	 */
	public static final long MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS = 200L;
	public static final float RETICLE_SIZE = 0.05f;
	public static final Rect WINDOW_TOP_LEFT = new Rect(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final Rect WINDOW_TOP_RIGHT = new Rect(0.05f, 0.05f, ViewArmour.ARMOUR_SLOT_RIGHT_EDGE - ViewArmour.ARMOUR_SLOT_SCALE - ViewArmour.ARMOUR_SLOT_SPACING, 0.95f);
	public static final Rect WINDOW_BOTTOM = new Rect(-0.95f, -0.80f, 0.95f, -0.05f);

	private final Environment _env;
	private final GlUi _ui;
	private final EntityVolume _playerVolume;
	private final MovementControl _movement;
	private final ClientWrapper _client;
	private final AudioManager _audioManager;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final IInputStateChanger _captureState;
	private final Map<Integer, String> _otherPlayersById;

	private boolean _rotationDidUpdate;
	private boolean _didAccountForTimeInFrame;
	private boolean _didWalkInFrame;
	private boolean _mouseHeld0;
	private boolean _mouseHeld1;
	private boolean _mouseClicked0;
	private boolean _mouseClicked1;
	private Point _cursor;

	// Variables related to the window overlay mode.
	private boolean _leftClick;
	private boolean _leftShiftClick;
	private boolean _rightClick;

	// Data specifically related to high-level UI state.
	private _UiState _uiState;
	private boolean _isRunningOnServer;
	private AbsoluteLocation _openStationLocation;
	private boolean _viewingFuelInventory;
	private Craft _continuousInInventory;
	private Craft _continuousInBlock;
	private boolean _isManualCraftingStation;

	// Tracking related to delayed actions when switching targets.
	private AbsoluteLocation _lastActionBlock;
	private long _lastActionMillis;

	// Tracking related to orientation change updates.
	private boolean _orientationNeedsFlush;
	private float _yawRadians;
	private float _pitchRadians;
	private boolean _shouldPause;
	private boolean _shouldResume;

	// Bindings defined/owned here and referenced by various UI components.
	private final Binding<WorldSelection> _selectionBinding;
	private final Binding<Entity> _entityBinding;
	private final Binding<Inventory> _thisEntityInventoryBinding;
	private final Binding<Inventory> _bottomWindowInventoryBinding;
	private final Binding<String> _bottomWindowTitleBinding;
	private final Binding<ItemTuple<Void>> _bottomWindowFuelBinding;
	private final Binding<String> _craftingPanelTitleBinding;
	private final Binding<List<CraftDescription>> _craftingPanelBinding;
	
	// Views for rendering parts of the UI in specific modes.
	private final Window<Inventory> _thisEntityInventoryWindow;
	private final Window<Inventory> _bottomInventoryWindow;
	private final Window<List<ItemTuple<CraftDescription>>> _craftingWindow;
	private final Window<Entity> _metaDataWindow;
	private final Window<Entity> _hotbarWindow;
	private final Window<NonStackableItem[]> _armourWindow;
	private final Window<WorldSelection> _selectionWindow;

	// UI for rendering the controls in the pause state.
	private final Binding<String> _quitButtonBinding;
	private final ViewTextButton<String> _quitButton;
	private final ViewTextButton<String> _optionsButton;
	private final ViewTextButton<String> _returnToGameButton;

	// UI for rendering the options state.
	private final Binding<Boolean> _fullScreenBinding;
	private final ViewTextButton<Boolean> _fullScreenButton;
	private final Binding<Integer> _viewDistanceBinding;
	private final ViewControlIntChanger _viewDistanceControl;
	private final ViewTextButton<String> _returnToPauseButton;

	// Data related to the liquid overlay.
	private final Set<Block> _waterBlockTypes;
	private final Set<Block> _lavaBlockTypes;
	private AbsoluteLocation _eyeBlockLocation;

	public UiStateManager(Environment environment
			, GlUi ui
			, MovementControl movement
			, ClientWrapper client
			, AudioManager audioManager
			, Function<AbsoluteLocation, BlockProxy> blockLookup
			, IInputStateChanger captureState
	)
	{
		_env = environment;
		_ui = ui;
		_playerVolume = environment.creatures.PLAYER.volume();
		_movement = movement;
		_client = client;
		_audioManager = audioManager;
		_blockLookup = blockLookup;
		_captureState = captureState;
		_otherPlayersById = new HashMap<>();
		
		// The UI state is fairly high-level, deciding what is on screen and how we handle inputs.
		_uiState = _UiState.START;
		
		// Define all of our bindings.
		_selectionBinding = new Binding<>();
		_entityBinding = new Binding<>();
		_thisEntityInventoryBinding = new SubBinding<>(_entityBinding, (Entity entity) -> {
			Inventory inventory = entity.isCreativeMode()
					? CreativeInventory.fakeInventory()
					: entity.inventory()
			;
			return inventory;
		});
		Binding<NonStackableItem[]> armourBinding = new SubBinding<>(_entityBinding, (Entity entity) -> entity.armourSlots());
		_bottomWindowInventoryBinding = new Binding<>();
		_bottomWindowTitleBinding = new Binding<>();
		_bottomWindowFuelBinding = new Binding<>();
		_craftingPanelTitleBinding = new Binding<>();
		_craftingPanelBinding = new Binding<>();
		
		// Create our views.
		IntConsumer mouseOverTopRightKeyConsumer = (int key) -> {
			AbsoluteLocation relevantBlock;
			if (null != _openStationLocation)
			{
				relevantBlock = _openStationLocation;
			}
			else
			{
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_entityBinding.get(), _playerVolume);
				relevantBlock = feetBlock;
			}
			_handleHoverOverEntityInventoryItem(relevantBlock, key);
		};
		IntConsumer mouseOverBottomKeyConsumer = (int key) -> {
			AbsoluteLocation relevantBlock;
			if (null != _openStationLocation)
			{
				relevantBlock = _openStationLocation;
			}
			else
			{
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_entityBinding.get(), _playerVolume);
				relevantBlock = feetBlock;
			}
			_pullFromBlockToEntityInventory(relevantBlock, key);
		};
		Consumer<CraftDescription> craftHoverOverConsumer = (CraftDescription desc) -> {
			if (_isManualCraftingStation && (_leftClick || _leftShiftClick))
			{
				Craft craft = desc.craft();
				if (null != _openStationLocation)
				{
					_continuousInBlock = _leftShiftClick ? craft : null;
					_client.beginCraftInBlock(_openStationLocation, craft);
				}
				else
				{
					_continuousInInventory = _leftShiftClick ? craft : null;
					_client.beginCraftInInventory(craft);
				}
				_didAccountForTimeInFrame = true;
			}
		};
		
		BooleanSupplier commonPageChangeCheck = () -> {
			return _leftClick;
		};
		
		Binding<String> inventoryTitleBinding = new Binding<>();
		inventoryTitleBinding.set("Inventory");
		ViewEntityInventory thisEntityInventoryView = new ViewEntityInventory(ui, inventoryTitleBinding, _thisEntityInventoryBinding, null, mouseOverTopRightKeyConsumer, commonPageChangeCheck);
		ComplexItemView.IBindOptions<Void> fuelViewOptions = new ComplexItemView.IBindOptions<Void>()
		{
			@Override
			public int getOutlineTexture(ItemTuple<Void> context)
			{
				// We always just show the same background for fuel.
				return ui.pixelLightGrey;
			}
			@Override
			public void hoverRender(Point cursor, ItemTuple<Void> context)
			{
				// No fuel slot hover.
			}
			@Override
			public void hoverAction(ItemTuple<Void> context)
			{
				// There is no fuel slot action.
			}
		};
		_thisEntityInventoryWindow = new Window<>(WINDOW_TOP_RIGHT, thisEntityInventoryView);
		ComplexItemView<Void> fuelProgress = new ComplexItemView<>(ui, _bottomWindowFuelBinding, fuelViewOptions);
		ViewEntityInventory bottomInventoryView = new ViewEntityInventory(ui, _bottomWindowTitleBinding, _bottomWindowInventoryBinding, fuelProgress, mouseOverBottomKeyConsumer, commonPageChangeCheck);
		_bottomInventoryWindow = new Window<>(WINDOW_BOTTOM, bottomInventoryView);
		ViewCraftingPanel craftingPanelView = new ViewCraftingPanel(ui, _craftingPanelTitleBinding, _craftingPanelBinding, craftHoverOverConsumer, commonPageChangeCheck);
		_craftingWindow = new Window<>(WINDOW_TOP_LEFT, craftingPanelView);
		_metaDataWindow = new Window<>(ViewMetaData.LOCATION, new ViewMetaData(_ui, _entityBinding));
		_hotbarWindow = new Window<>(ViewHotbar.LOCATION, new ViewHotbar(_ui, _entityBinding));
		Consumer<BodyPart> eventHoverArmourBodyPart = (BodyPart hoverPart) -> {
			if (_leftClick)
			{
				// Note that we ignore the result since this will be reflected in the UI, if valid.
				_client.swapArmour(hoverPart);
			}
		};
		_armourWindow = new Window<>(ViewArmour.LOCATION, new ViewArmour(_ui, armourBinding, eventHoverArmourBodyPart));
		_selectionWindow = new Window<>(ViewSelection.LOCATION, new ViewSelection(_ui, _env, _selectionBinding, _blockLookup, _otherPlayersById));
		
		// Pause state controls.
		_quitButtonBinding = new Binding<>();
		_quitButton = new ViewTextButton<>(_ui, _quitButtonBinding
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				// This will need to be updated, later, to return to start state.
				if (_leftClick)
				{
					Gdx.app.exit();
				}
		});
		_optionsButton = new ViewTextButton<>(_ui, _inlineBinding("Game Options")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					_uiState = _UiState.OPTIONS;
				}
		});
		_returnToGameButton = new ViewTextButton<>(_ui, _inlineBinding("Return to Game")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					_uiState = _UiState.PLAY;
					_captureState.shouldCaptureMouse(true);
					_shouldResume = true;
				}
		});
		
		// Options state controls.
		_fullScreenBinding = new Binding<>();
		_fullScreenBinding.set(Gdx.graphics.isFullscreen());
		_fullScreenButton = new ViewTextButton<>(_ui, _fullScreenBinding
			, (Boolean isFullScreen) -> isFullScreen ? "Change to Windowed" : "Change to Full Screen"
			, (ViewTextButton<Boolean> button, Boolean isFullScreen) -> {
				if (_leftClick)
				{
					// We will toggle the full screen and update the binding data.
					boolean newFullScreen = !isFullScreen;
					if (newFullScreen)
					{
						// We will just use the full screen of the current display mode.
						Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
					}
					else
					{
						// For now, we will always use this same default window size.
						Gdx.graphics.setWindowedMode(1280, 960);
					}
					_fullScreenBinding.set(newFullScreen);
				}
		});
		_viewDistanceBinding = new Binding<>();
		_viewDistanceBinding.set(MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		_viewDistanceControl = new ViewControlIntChanger(_ui, _viewDistanceBinding
			, (Integer distance) -> distance + " cuboids"
			, (ViewControlIntChanger button, Integer newDistance) -> {
				if (_leftClick)
				{
					// We try changing this in the client and it will return the updated value.
					int finalValue = _client.trySetViewDistance(newDistance);
					_viewDistanceBinding.set(finalValue);
				}
		});
		_returnToPauseButton = new ViewTextButton<>(_ui, _inlineBinding("Back")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					_uiState = _UiState.PAUSE;
				}
		});
		
		// Look up the liquid overlay types.
		_waterBlockTypes = Set.of(_env.blocks.fromItem(_env.items.getItemById("op.water_source"))
				, _env.blocks.fromItem(_env.items.getItemById("op.water_strong"))
				, _env.blocks.fromItem(_env.items.getItemById("op.water_weak"))
		);
		_lavaBlockTypes = Set.of(_env.blocks.fromItem(_env.items.getItemById("op.lava_source"))
				, _env.blocks.fromItem(_env.items.getItemById("op.lava_strong"))
				, _env.blocks.fromItem(_env.items.getItemById("op.lava_weak"))
		);
	}

	public void startPlay(boolean onServer)
	{
		Assert.assertTrue(_UiState.START == _uiState);
		_uiState = _UiState.PLAY;
		_isRunningOnServer = onServer;
		_quitButtonBinding.set(_isRunningOnServer ? "Disconnect" : "Quit");
	}

	public boolean canSelectInScene()
	{
		// This just means whether or not we are in play mode.
		return _UiState.PLAY == _uiState;
	}

	public void drawRelevantWindows(WorldSelection selection)
	{
		// Update common bindings.
		_selectionBinding.set(selection);
		
		// Perform state-specific drawing.
		IAction action;
		switch (_uiState)
		{
		case INVENTORY:
			action = _drawInventoryStateWindows();
			break;
		case PAUSE:
			action = _drawPauseStateWindows();
			break;
		case PLAY:
			action = _drawPlayStateWindows();
			break;
		case OPTIONS:
			action = _drawOptionsStateWindows();
			break;
		default:
			throw Assert.unreachable();
		}
		
		// Run any actions based on clicking on the UI.
		if (null != action)
		{
			action.takeAction();
		}
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
	}

	public boolean didViewPerspectiveChange()
	{
		// Return whether or not we changed the rotation.
		boolean shouldUpdate = _rotationDidUpdate;
		_rotationDidUpdate = false;
		return shouldUpdate;
	}

	public void finalizeFrameEvents(PartialEntity entity, AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
	{
		// Make sure the client is in the right state.
		if (_shouldResume)
		{
			_captureState.trySetPaused(false);
			_shouldResume = false;
		}
		
		// See if we need to update our orientation.
		if (_orientationNeedsFlush)
		{
			_client.setOrientation(_yawRadians, _pitchRadians);
			_orientationNeedsFlush = false;
		}
		
		// See if the click refers to anything selected.
		if (_mouseHeld0)
		{
			if (null != stopBlock)
			{
				if (_canAct(stopBlock))
				{
					_client.hitBlock(stopBlock);
					_didAccountForTimeInFrame = true;
				}
			}
			else if (null != entity)
			{
				if (_mouseClicked0)
				{
					_client.hitEntity(entity);
					_updateLastActionMillis();
				}
			}
		}
		else if (_mouseHeld1)
		{
			boolean didAct = false;
			if (null != stopBlock)
			{
				// First, see if we need to change the UI state if this is a station we just clicked on.
				if (_mouseClicked1)
				{
					didAct = _didOpenStationInventory(stopBlock);
				}
			}
			else if (null != entity)
			{
				if (_mouseClicked1)
				{
					// Try to apply the selected item to the entity (we consider this an action even if it did nothing).
					_client.applyToEntity(entity);
					_updateLastActionMillis();
					didAct = true;
				}
			}
			
			// If we still didn't do anything, try clicks on the block or self.
			if (!didAct && _mouseClicked1 && (null != stopBlock) && (null != preStopBlock))
			{
				didAct = _client.runRightClickOnBlock(stopBlock, preStopBlock);
				if (didAct)
				{
					_updateLastActionMillis();
				}
			}
			if (!didAct && _mouseClicked1)
			{
				didAct = _client.runRightClickOnSelf();
				if (didAct)
				{
					_updateLastActionMillis();
				}
			}
			if (!didAct && (null != stopBlock) && (null != preStopBlock))
			{
				if (_canAct(stopBlock))
				{
					// In this case, we either want to place a block or repair a block.
					didAct = _client.runPlaceBlock(stopBlock, preStopBlock);
					if (!didAct)
					{
						didAct = _client.runRepairBlock(stopBlock);
					}
				}
				else
				{
					didAct = false;
				}
			}
		}
		
		// See if we should continue any in-progress crafting operation.
		if (!_didAccountForTimeInFrame && (null != _openStationLocation))
		{
			// The common code doesn't know we are looking at this block so it can't apply this for us (as it does for in-inventory crafting).
			_client.beginCraftInBlock(_openStationLocation, _continuousInBlock);
			// We don't account for time here since this usually doesn't do anything.
		}
		
		// If we took no action, just tell the client to pass time.
		if (!_didAccountForTimeInFrame)
		{
			// See if we are doing continuous in-inventory crafting.
			if (null != _continuousInInventory)
			{
				_client.beginCraftInInventory(_continuousInInventory);
			}
			else
			{
				_client.doNothing();
			}
		}
		
		// We handle resume and pause distinctly since we need to put the client into the right state before/after any actions.
		if (_shouldPause)
		{
			_captureState.trySetPaused(true);
			_shouldPause = false;
		}
		if (_didWalkInFrame && SpatialHelpers.isStandingOnGround(_blockLookup, _entityBinding.get().location(), _playerVolume))
		{
			_audioManager.setWalking();
		}
		else
		{
			_audioManager.setStanding();
		}
		
		// And reset.
		_didAccountForTimeInFrame = false;
		_didWalkInFrame = false;
		_mouseHeld0 = false;
		_mouseHeld1 = false;
		_mouseClicked0 = false;
		_mouseClicked1 = false;
		
		_leftClick = false;
		_leftShiftClick = false;
		_rightClick = false;
	}

	public void setThisEntity(Entity projectedEntity)
	{
		_entityBinding.set(projectedEntity);
	}

	public void capturedMouseMoved(int deltaX, int deltaY)
	{
		if ((0 != deltaX) || (0 != deltaY))
		{
			_yawRadians = _movement.rotateYaw(deltaX);
			_pitchRadians = _movement.rotatePitch(deltaY);
			_orientationNeedsFlush = true;
		}
		_rotationDidUpdate = true;
	}

	public void captureMouse0Down(boolean justClicked)
	{
		_mouseHeld0 = true;
		_mouseClicked0 = justClicked;
	}

	public void captureMouse1Down(boolean justClicked, boolean leftShiftHeld)
	{
		_mouseHeld1 = true;
		// We use the shift to allow us to set the "held" without "clicked".
		// In the future, this will likely be expanded but it isn't obvious where the interpretation of this key should
		// go (InputManager, where it can associated with key settings, or here where it is associated with the UI state).
		if (!leftShiftHeld)
		{
			_mouseClicked1 = justClicked;
		}
	}

	public void moveForward()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.FORWARD);
		_didAccountForTimeInFrame = true;
		_didWalkInFrame = true;
	}

	public void moveBackward()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.BACKWARD);
		_didAccountForTimeInFrame = true;
		_didWalkInFrame = true;
	}

	public void strafeRight()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.RIGHT);
		_didAccountForTimeInFrame = true;
		_didWalkInFrame = true;
	}

	public void strafeLeft()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.LEFT);
		_didAccountForTimeInFrame = true;
		_didWalkInFrame = true;
	}

	public void jumpOrSwim()
	{
		_client.jumpOrSwim();
	}

	public void normalMouseMoved(Point cursor)
	{
		_cursor = cursor;
	}

	public void normalMouse0Clicked(boolean leftShiftDown)
	{
		if (leftShiftDown)
		{
			_leftShiftClick = true;
		}
		else
		{
			_leftClick = true;
		}
	}

	public void normalMouse1Clicked(boolean leftShiftDown)
	{
		_rightClick = true;
	}

	public void handleKeyEsc()
	{
		switch (_uiState)
		{
		case START:
			// Not implemented yet.
			throw Assert.unreachable();
		case INVENTORY:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			break;
		case PAUSE:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			_shouldResume = true;
			break;
		case PLAY:
			_uiState = _UiState.PAUSE;
			_openStationLocation = null;
			_captureState.shouldCaptureMouse(false);
			_shouldPause = true;
			break;
		case OPTIONS:
			_uiState = _UiState.PAUSE;
			break;
		}
		_continuousInInventory = null;
		_continuousInBlock = null;
	}

	public void handleHotbarIndex(int hotbarIndex)
	{
		switch (_uiState)
		{
		case START:
			// Not implemented yet.
			throw Assert.unreachable();
		case PAUSE:
		case OPTIONS:
			// Just ignore this.
			break;
		case INVENTORY:
		case PLAY:
			_client.changeHotbarIndex(hotbarIndex);
			break;
		}
	}

	public void handleKeyI()
	{
		switch (_uiState)
		{
		case START:
			// Not implemented yet.
			throw Assert.unreachable();
		case INVENTORY:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			break;
		case PAUSE:
		case OPTIONS:
			// Just ignore this.
			break;
		case PLAY:
			_uiState = _UiState.INVENTORY;
			_openStationLocation = null;
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_viewingFuelInventory = false;
			_captureState.shouldCaptureMouse(false);
			break;
		}
		_continuousInInventory = null;
		_continuousInBlock = null;
	}

	public void handleKeyF()
	{
		_viewingFuelInventory = !_viewingFuelInventory;
		if (_viewingFuelInventory)
		{
			// Make sure that this actually has a fuel slot.
			if (null == _openStationLocation)
			{
				_viewingFuelInventory = false;
			}
			else
			{
				BlockProxy stationBlock = _blockLookup.apply(_openStationLocation);
				_viewingFuelInventory = (null != stationBlock.getFuel());
			}
		}
		_continuousInInventory = null;
		_continuousInBlock = null;
	}

	public void updateEyeBlock(AbsoluteLocation eyeBlockLocation)
	{
		_eyeBlockLocation = eyeBlockLocation;
	}

	public void otherPlayerJoined(int clientId, String name)
	{
		Object old = _otherPlayersById.put(clientId, name);
		Assert.assertTrue(null == old);
	}

	public void otherPlayerLeft(int clientId)
	{
		Object old = _otherPlayersById.remove(clientId);
		Assert.assertTrue(null != old);
	}

	public void shutdown()
	{
		_ui.shutdown();
	}


	private void _handleHoverOverEntityInventoryItem(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		if (_leftClick)
		{
			// Select this in the hotbar (this will clear if already set).
			_client.setSelectedItemKeyOrClear(entityInventoryKey);
		}
		else if (_rightClick)
		{
			_client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _viewingFuelInventory);
		}
		else if (_leftShiftClick)
		{
			_client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, _viewingFuelInventory);
		}
	}

	private void _pullFromBlockToEntityInventory(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		// Note that we ignore the result since this will be reflected in the UI, if valid.
		if (_rightClick)
		{
			_client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _viewingFuelInventory);
		}
		else if (_leftShiftClick)
		{
			_client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, _viewingFuelInventory);
		}
	}

	private boolean _didOpenStationInventory(AbsoluteLocation blockLocation)
	{
		// See if there is an inventory we can open at the given block location.
		// NOTE:  We don't use this mechanism to talk about air blocks (or other empty blocks with ad-hoc inventories), only actual blocks.
		BlockProxy proxy = _blockLookup.apply(blockLocation);
		boolean didOpen = false;
		Block block = proxy.getBlock();
		if (_env.stations.getNormalInventorySize(block) > 0)
		{
			// We are at least some kind of station with an inventory.
			_uiState = _UiState.INVENTORY;
			_openStationLocation = blockLocation;
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_viewingFuelInventory = false;
			_captureState.shouldCaptureMouse(false);
			didOpen = true;
		}
		return didOpen;
	}

	private boolean _canAct(AbsoluteLocation selectedBlock)
	{
		// We apply our delay here.
		boolean canAct;
		long currentMillis = System.currentTimeMillis();
		if (null == selectedBlock)
		{
			// This is placing a block or interacting with a block/entity so we always apply the delay.
			if (currentMillis > (_lastActionMillis + MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS))
			{
				_lastActionMillis = currentMillis;
				canAct = true;
			}
			else
			{
				canAct = false;
			}
		}
		else
		{
			if (selectedBlock.equals(_lastActionBlock))
			{
				// We can continue breaking the current block, no matter the time.
				_lastActionMillis = currentMillis;
				canAct = true;
			}
			else
			{
				// If this is something else, apply the delay.
				if (currentMillis > (_lastActionMillis + MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS))
				{
					_lastActionBlock = selectedBlock;
					_lastActionMillis = currentMillis;
					canAct = true;
				}
				else
				{
					canAct = false;
				}
			}
		}
		return canAct;
	}

	/**
	 * Called when we perform some action which isn't directly breaking a block so that they next frame doesn't capture
	 * the event and accidentally break/place a block.
	 */
	private void _updateLastActionMillis()
	{
		_lastActionBlock = null;
		_lastActionMillis = System.currentTimeMillis();
	}

	private IAction _drawInventoryStateWindows()
	{
		// We are in inventory mode but we will need to handle station/floor cases differently.
		Inventory relevantInventory = null;
		Inventory inventoryToCraftFrom = null;
		List<Craft> validCrafts = null;
		CraftOperation currentOperation = null;
		String stationName = "Floor";
		ItemTuple<Void> fuelSlot = null;
		boolean isAutomaticCrafting = false;
		if (null != _openStationLocation)
		{
			// We are in station mode so check this block's inventory and crafting (potentially clearing it if it is no longer a station).
			BlockProxy stationBlock = _blockLookup.apply(_openStationLocation);
			Block stationType = stationBlock.getBlock();
			
			if (_env.stations.getNormalInventorySize(stationType) > 0)
			{
				Inventory stationInventory = stationBlock.getInventory();
				inventoryToCraftFrom = stationInventory;
				// If we are viewing the fuel inventory, we want to use that, instead.
				FuelState fuel = stationBlock.getFuel();
				if (null != fuel)
				{
					if (_viewingFuelInventory)
					{
						stationInventory = fuel.fuelInventory();
					}
					Item currentFuel = fuel.currentFuel();
					if (null != currentFuel)
					{
						long totalFuel = _env.fuel.millisOfFuel(currentFuel);
						long remainingFuel = fuel.millisFuelled();
						float fuelRemaining = (float)remainingFuel / (float) totalFuel;
						fuelSlot = new ItemTuple<>(currentFuel, 0, fuelRemaining, null);
					}
				}
				else
				{
					// This is invalid so just clear it.
					_viewingFuelInventory = false;
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
				if (_viewingFuelInventory)
				{
					stationName += " Fuel";
				}
			}
			else
			{
				// This is no longer a station.
				_openStationLocation = null;
				_continuousInInventory = null;
				_continuousInBlock = null;
			}
		}
		
		Inventory entityInventory = _thisEntityInventoryBinding.get();
		if (null == _openStationLocation)
		{
			// We are just looking at the floor at our feet.
			Entity thisEntity = _entityBinding.get();
			AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(thisEntity, _playerVolume);
			BlockProxy thisBlock = _blockLookup.apply(feetBlock);
			Inventory floorInventory = thisBlock.getInventory();
			
			relevantInventory = floorInventory;
			inventoryToCraftFrom = entityInventory;
			// We are just looking at the entity inventory so find the built-in crafting recipes.
			validCrafts = _env.crafting.craftsForClassifications(Set.of(CraftAspect.BUILT_IN));
			// We will convert these into CraftOperation instances so we can splice in the current craft.
			currentOperation = thisEntity.localCraftOperation();
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
		_isManualCraftingStation = canBeManuallySelected;
		
		// Now, do the actual drawing.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter();
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
		}
		
		IAction action = null;
		// We will show the crafting panel as long as there are any valid crafts.
		if (!convertedCrafts.isEmpty())
		{
			IAction hover = _craftingWindow.doRender(_cursor);
			if (null != hover)
			{
				action = hover;
			}
		}
		IAction hover = _thisEntityInventoryWindow.doRender(_cursor);
		if (null != hover)
		{
			action = hover;
		}
		hover = _bottomInventoryWindow.doRender(_cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// We are in windowed mode so also draw the armour slots.
		hover = _armourWindow.doRender(_cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action)
		{
			action.renderHover(_cursor);
		}
		
		// Return any action so that the caller can run the action now that rendering is finished.
		return action;
	}

	private IAction _drawPauseStateWindows()
	{
		_drawCommonPauseBackground();
		
		// Draw the menu title and other UI.
		String menuTitle = _isRunningOnServer ? "Connected to server" : "Paused";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.5f, menuTitle);
		IAction action = null;
		action = _renderViewChainAction(_returnToGameButton, new Rect(0.0f, 0.2f, 0.0f, 0.3f), action);
		action = _renderViewChainAction(_optionsButton, new Rect(0.0f, 0.0f, 0.0f, 0.1f), action);
		action = _renderViewChainAction(_quitButton, new Rect(0.0f, -0.3f, 0.0f, -0.2f), action);
		
		return action;
	}

	private IAction _drawPlayStateWindows()
	{
		// In this case, just draw the common UI elements.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter();
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
		}
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _selectionWindow.doRender(_cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		return null;
	}

	private IAction _drawOptionsStateWindows()
	{
		_drawCommonPauseBackground();
		
		// Draw the menu title and other UI.
		String menuTitle = "Game Options";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.5f, menuTitle);
		IAction action = null;
		action = _renderViewChainAction(_fullScreenButton, new Rect(0.0f, 0.2f, 0.0f, 0.3f), action);
		action = _renderViewChainAction(_viewDistanceControl, new Rect(-0.4f, 0.0f, 0.4f, 0.1f), action);
		action = _renderViewChainAction(_returnToPauseButton, new Rect(0.0f, -0.3f, 0.0f, -0.2f), action);
		
		return action;
	}

	private void _drawCommonPauseBackground()
	{
		// Draw whatever is common to states where we draw interactive buttons on top.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter();
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
		}
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _selectionWindow.doRender(_cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		// Draw the overlay to dim the window.
		_ui.drawWholeTextureRect(_ui.pixelDarkGreyAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
	}

	private void _handleEyeFilter()
	{
		// If our eye is under a liquid, draw the liquid over the screen (we do this here since it is part of the orthographic plane and not logically part of the scene).
		if (null != _eyeBlockLocation)
		{
			BlockProxy eyeProxy = _blockLookup.apply(_eyeBlockLocation);
			if (null != eyeProxy)
			{
				Block blockType = eyeProxy.getBlock();
				if (_waterBlockTypes.contains(blockType))
				{
					_ui.drawWholeTextureRect(_ui.pixelBlueAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
				}
				else if (_lavaBlockTypes.contains(blockType))
				{
					_ui.drawWholeTextureRect(_ui.pixelOrangeLava, -1.0f, -1.0f, 1.0f, 1.0f);
				}
			}
		}
	}

	private IAction _renderViewChainAction(IView view, Rect location, IAction existingAction)
	{
		IAction tempAction = view.render(location, _cursor);
		return (null != tempAction)
				? tempAction
				: existingAction
		;
	}

	private Binding<String> _inlineBinding(String text)
	{
		Binding<String> binding = new Binding<>();
		binding.set(text);
		return binding;
	}


	/**
	 *  Represents the high-level state of the UI.  This will likely be split out into a class to specifically manage UI
	 *  state, later one.
	 */
	private static enum _UiState
	{
		/**
		 * This is the mode where the game starts.
		 * Currently, this is just a placeholder in the state machine but will be used for other UI later.
		 */
		START,
		/**
		 * The mode where play is normal.  Cursor is captured and there is no open window.
		 */
		PLAY,
		/**
		 * The mode where play is effectively "paused".  The cursor is released and buttons to change game setup will be
		 * presented.
		 * Note that we call the state "paused" even though servers are never "paused" (the title will reflect this
		 * difference).
		 */
		PAUSE,
		/**
		 * The UI state under the PAUSE screen where we enter an options menu to view/change settings.
		 */
		OPTIONS,
		/**
		 * The mode where player control is largely disabled and the interface is mostly about clicking on buttons, etc.
		 */
		INVENTORY,
	}

	public static interface IInputStateChanger
	{
		public void shouldCaptureMouse(boolean setCapture);
		public void trySetPaused(boolean isPaused);
	}
}
