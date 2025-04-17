package com.jeffdisher.october.peaks;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.IView;
import com.jeffdisher.october.peaks.ui.Point;
import com.jeffdisher.october.peaks.utils.GeometryHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;


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

	private final EntityVolume _playerVolume;
	private final MovementControl _movement;
	private final ClientWrapper _client;
	private final AudioManager _audioManager;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final IInputStateChanger _captureState;

	private Entity _thisEntity;
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
	private AbsoluteLocation _openStationLocation;
	private int _topLeftPage;
	private int _bottomPage;
	private boolean _viewingFuelInventory;
	private Craft _continuousInInventory;
	private Craft _continuousInBlock;

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
	private final Binding<Inventory> _thisEntityInventoryBinding;
	
	// Views for rendering parts of the UI in specific modes.
	private final IView<Inventory> _thisEntityInventoryView;

	public UiStateManager(Environment environment
			, MovementControl movement
			, ClientWrapper client
			, AudioManager audioManager
			, Function<AbsoluteLocation, BlockProxy> blockLookup
			, IInputStateChanger captureState
			, WindowManager windowManager
			, Binding<WorldSelection> selectionBinding
			, Binding<Inventory> thisEntityInventoryBinding
	)
	{
		_playerVolume = environment.creatures.PLAYER.volume();
		_movement = movement;
		_client = client;
		_audioManager = audioManager;
		_blockLookup = blockLookup;
		_captureState = captureState;
		
		// We start up in the play state.
		_uiState = _UiState.PLAY;
		
		// Create our views.
		IntConsumer mouseOverKeyConsumer = (int key) -> {
			AbsoluteLocation relevantBlock;
			if (null != _openStationLocation)
			{
				relevantBlock = _openStationLocation;
			}
			else
			{
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_thisEntity, _playerVolume);
				relevantBlock = feetBlock;
			}
			_handleHoverOverEntityInventoryItem(relevantBlock, key);
		};
		BooleanSupplier shouldChangePage = () -> {
			return _leftClick;
		};
		_selectionBinding = selectionBinding;
		_thisEntityInventoryBinding = thisEntityInventoryBinding;
		_thisEntityInventoryView = windowManager.buildTopRightView("Inventory", mouseOverKeyConsumer, shouldChangePage);
	}

	public boolean canSelectInScene()
	{
		// This just means whether or not we are in play mode.
		return _UiState.PLAY == _uiState;
	}

	public void drawRelevantWindows(WindowManager windowManager, WorldSelection selection)
	{
		// Update the selection binding for the UI.
		_selectionBinding.set(selection);
		
		IAction action = null;
		if (_UiState.INVENTORY == _uiState)
		{
			Environment env = Environment.getShared();
			
			// We are in inventory mode but we will need to handle station/floor cases differently.
			AbsoluteLocation relevantBlock = null;
			Inventory relevantInventory = null;
			Inventory inventoryToCraftFrom = null;
			List<Craft> validCrafts = null;
			CraftOperation currentOperation = null;
			String stationName = "Floor";
			WindowManager.FuelSlot fuelSlot = null;
			boolean isAutomaticCrafting = false;
			if (null != _openStationLocation)
			{
				// We are in station mode so check this block's inventory and crafting (potentially clearing it if it is no longer a station).
				BlockProxy stationBlock = _blockLookup.apply(_openStationLocation);
				Block stationType = stationBlock.getBlock();
				
				if (env.stations.getNormalInventorySize(stationType) > 0)
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
							long totalFuel = env.fuel.millisOfFuel(currentFuel);
							long remainingFuel = fuel.millisFuelled();
							float fuelRemaining = (float)remainingFuel / (float) totalFuel;
							fuelSlot = new WindowManager.FuelSlot(currentFuel, fuelRemaining);
						}
					}
					else
					{
						// This is invalid so just clear it.
						_viewingFuelInventory = false;
					}
					
					// Find the crafts for this station type.
					Set<String> classifications = env.stations.getCraftingClasses(stationType);
					
					relevantBlock = _openStationLocation;
					relevantInventory = stationInventory;
					validCrafts = env.crafting.craftsForClassifications(classifications);
					// We will convert these into CraftOperation instances so we can splice in the current craft.
					currentOperation = stationBlock.getCrafting();
					if (0 == env.stations.getManualMultiplier(stationType))
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
					_topLeftPage = 0;
					_bottomPage = 0;
				}
			}
			
			Inventory entityInventory = _thisEntityInventoryBinding.get();
			if (null == _openStationLocation)
			{
				// We are just looking at the floor at our feet.
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_thisEntity, _playerVolume);
				BlockProxy thisBlock = _blockLookup.apply(feetBlock);
				Inventory floorInventory = thisBlock.getInventory();
				
				relevantBlock = feetBlock;
				relevantInventory = floorInventory;
				inventoryToCraftFrom = entityInventory;
				// We are just looking at the entity inventory so find the built-in crafting recipes.
				validCrafts = env.crafting.craftsForClassifications(Set.of(CraftAspect.BUILT_IN));
				// We will convert these into CraftOperation instances so we can splice in the current craft.
				currentOperation = _thisEntity.localCraftOperation();
			}
			
			Inventory finalInventoryToCraftFrom = inventoryToCraftFrom;
			List<_InventoryEntry> relevantInventoryList = _inventoryToList(relevantInventory);
			final AbsoluteLocation finalRelevantBlock = relevantBlock;
			final CraftOperation finalCraftOperation = currentOperation;
			Craft currentCraft = (null != currentOperation) ? currentOperation.selectedCraft() : null;
			boolean canBeManuallySelected = !isAutomaticCrafting;
			List<WindowManager.CraftDescription> convertedCrafts = validCrafts.stream()
					.map((Craft craft) -> {
						long progressMillis = 0L;
						if (craft == currentCraft)
						{
							progressMillis = finalCraftOperation.completedMillis();
						}
						float progress = (float)progressMillis / (float)craft.millisPerCraft;
						WindowManager.ItemRequirement[] requirements = Arrays.stream(craft.input)
								.map((Items input) -> {
									Item type = input.type();
									int available = finalInventoryToCraftFrom.getCount(type);
									return new WindowManager.ItemRequirement(type, input.count(), available);
								})
								.toArray((int size) -> new WindowManager.ItemRequirement[size])
						;
						// Note that we are assuming that there is only one output type.
						return new WindowManager.CraftDescription(craft
								, new Items(craft.output[0], craft.output.length)
								, requirements
								, progress
								, canBeManuallySelected
						);
					})
					.toList()
			;
			
			WindowManager.ItemRenderer<_InventoryEntry> renderer = (float left, float bottom, float right, float top, _InventoryEntry item, boolean isMouseOver) -> {
				Items items = item.stackable;
				if (null != items)
				{
					windowManager.renderItemStack.drawItem(left, bottom, right, top, items, isMouseOver);
				}
				else
				{
					windowManager.renderNonStackable.drawItem(left, bottom, right, top, item.nonStackable, isMouseOver);
				}
			};
			WindowManager.HoverRenderer<_InventoryEntry> hover = (Point cursor, _InventoryEntry item) -> {
				Item type;
				if (null != item.stackable)
				{
					type = item.stackable.type();
				}
				else
				{
					type = item.nonStackable.type();
				}
				windowManager.hoverItem.drawHoverAtPoint(cursor, type);
			};
			
			// Determine if we can handle manual crafting selection callbacks.
			Consumer<WindowManager.CraftDescription> craftHoverOverItem = canBeManuallySelected
					? (WindowManager.CraftDescription elt) -> {
						if (_leftClick || _leftShiftClick)
						{
							Craft craft = elt.craft();
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
					}
					: (WindowManager.CraftDescription elt) -> {}
			;
			
			String craftingType = isAutomaticCrafting
					? "Automatic Crafting"
					: "Manual Crafting"
			;
			WindowManager.WindowData<WindowManager.CraftDescription> topLeft = new WindowManager.WindowData<>(craftingType
					, 0
					, 0
					, _topLeftPage
					, (int page) -> {
						if (_leftClick)
						{
							_topLeftPage = page;
						}
					}
					, convertedCrafts
					, windowManager.renderCraftOperation
					, windowManager.hoverCraftOperation
					, craftHoverOverItem
					, null
			);
			WindowManager.WindowData<_InventoryEntry> bottom = new WindowManager.WindowData<>(stationName
					, relevantInventory.currentEncumbrance
					, relevantInventory.maxEncumbrance
					, _bottomPage
					, (int page) -> {
						if (_leftClick)
						{
							_bottomPage = page;
						}
					}
					, relevantInventoryList
					, renderer
					, hover
					, (_InventoryEntry elt) -> {
						_pullFromBlockToEntityInventory(finalRelevantBlock, elt.key);
					}
					, fuelSlot
			);
			
			// Determine if we even want the crafting window since it doesn't apply to all stations.
			WindowManager.WindowData<WindowManager.CraftDescription> applicableCrafting = convertedCrafts.isEmpty()
					? null
					: topLeft
			;
			action = windowManager.drawActiveWindows(applicableCrafting, _thisEntityInventoryView, _thisEntityInventoryBinding, bottom, _thisEntity.armourSlots(), _cursor);
		}
		else
		{
			// In this case, just draw the common UI elements.
			action = windowManager.drawActiveWindows(null, null, null, null, null, _cursor);
		}
		
		// Run any actions based on clicking on the UI.
		if (null != action)
		{
			action.takeAction();
		}
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
		if (_didWalkInFrame && SpatialHelpers.isStandingOnGround(_blockLookup, _thisEntity.location(), _playerVolume))
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
		_thisEntity = projectedEntity;
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
		case INVENTORY:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			break;
		case MENU:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			_shouldResume = true;
			break;
		case PLAY:
			_uiState = _UiState.MENU;
			_openStationLocation = null;
			_captureState.shouldCaptureMouse(false);
			_shouldPause = true;
			break;
		}
		_continuousInInventory = null;
		_continuousInBlock = null;
	}

	public void handleHotbarIndex(int hotbarIndex)
	{
		switch (_uiState)
		{
		case MENU:
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
		case INVENTORY:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			break;
		case MENU:
			// Just ignore this.
			break;
		case PLAY:
			_uiState = _UiState.INVENTORY;
			_openStationLocation = null;
			_topLeftPage = 0;
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView?
			_bottomPage = 0;
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

	public void changeScreenMode(boolean fullScreen)
	{
		if (fullScreen)
		{
			// We will just use the full screen of the current display mode.
			Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
		}
		else
		{
			Gdx.graphics.setWindowedMode(1280, 960);
		}
	}

	public void handleScaleChange(int change)
	{
		// For now, we only want to change the scale in play mode.
		if (_UiState.PLAY == _uiState)
		{
			_client.tryChangeViewDistance(change);
		}
	}

	public void swapArmour(BodyPart hoverPart)
	{
		if (_leftClick)
		{
			// Note that we ignore the result since this will be reflected in the UI, if valid.
			_client.swapArmour(hoverPart);
		}
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
		if (Environment.getShared().stations.getNormalInventorySize(block) > 0)
		{
			// We are at least some kind of station with an inventory.
			_uiState = _UiState.INVENTORY;
			_openStationLocation = blockLocation;
			_topLeftPage = 0;
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView?
			_bottomPage = 0;
			_viewingFuelInventory = false;
			_captureState.shouldCaptureMouse(false);
			didOpen = true;
		}
		return didOpen;
	}

	private List<_InventoryEntry> _inventoryToList(Inventory inventory)
	{
		return inventory.sortedKeys().stream()
			.map((Integer key) -> {
				Items stack = inventory.getStackForKey(key);
				_InventoryEntry entry;
				if (null != stack)
				{
					entry = new _InventoryEntry(key, stack, null);
				}
				else
				{
					NonStackableItem nonStack = inventory.getNonStackableForKey(key);
					entry = new _InventoryEntry(key, null, nonStack);
				}
				return entry;
			})
			.toList()
		;
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


	/**
	 *  Represents the high-level state of the UI.  This will likely be split out into a class to specifically manage UI
	 *  state, later one.
	 */
	private static enum _UiState
	{
		/**
		 * The mode where play is normal.  Cursor is captured and there is no open window.
		 */
		PLAY,
		/**
		 * The mode where play is effectively "paused".  The cursor is released and buttons to change game setup will be
		 * presented.
		 * TODO:  Add a "pause" mode to the server, used here in single-player mode.
		 * TODO:  Determine the required buttons for control and add them.
		 */
		MENU,
		/**
		 * The mode where player control is largely disabled and the interface is mostly about clicking on buttons, etc.
		 */
		INVENTORY,
	}

	private static record _InventoryEntry(int key, Items stackable, NonStackableItem nonStackable) {}

	public static interface IInputStateChanger
	{
		public void shouldCaptureMouse(boolean setCapture);
		public void trySetPaused(boolean isPaused);
	}
}
