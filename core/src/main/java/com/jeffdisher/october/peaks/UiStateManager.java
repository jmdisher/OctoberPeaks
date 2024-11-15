package com.jeffdisher.october.peaks;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
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

	private final MovementControl _movement;
	private final ClientWrapper _client;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final IInputStateChanger _captureState;

	private Entity _thisEntity;
	private boolean _rotationDidUpdate;
	private boolean _didAccountForTimeInFrame;
	private boolean _mouseHeld0;
	private boolean _mouseHeld1;
	private boolean _mouseClicked0;
	private boolean _mouseClicked1;
	private float _normalGlX;
	private float _normalGlY;

	// Variables related to the window overlay mode.
	private boolean _leftClick;
	private boolean _leftShiftClick;
	private boolean _rightClick;

	// Data specifically related to high-level UI state.
	private _UiState _uiState;
	private AbsoluteLocation _openStationLocation;
	private int _topLeftPage;
	private int _topRightPage;
	private int _bottomPage;
	private boolean _viewingFuelInventory;

	// Tracking related to delayed actions when switching targets.
	private AbsoluteLocation _lastActionBlock;
	private long _lastActionMillis;

	// Tracking related to orientation change updates.
	private boolean _orientationNeedsFlush;
	private float _yawRadians;
	private float _pitchRadians;

	public UiStateManager(MovementControl movement, ClientWrapper client, Function<AbsoluteLocation, BlockProxy> blockLookup, IInputStateChanger captureState)
	{
		_movement = movement;
		_client = client;
		_blockLookup = blockLookup;
		_captureState = captureState;
		
		// We start up in the play state.
		_uiState = _UiState.PLAY;
	}

	public boolean canSelectInScene()
	{
		// This just means whether or not we are in play mode.
		return _UiState.PLAY == _uiState;
	}

	public void drawRelevantWindows(WindowManager windowManager, AbsoluteLocation selectedBlock, PartialEntity selectedEntity)
	{
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
					_topLeftPage = 0;
					_bottomPage = 0;
				}
			}
			
			Inventory entityInventory = _getEntityInventory();
			if (null == _openStationLocation)
			{
				// We are just looking at the floor at our feet.
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_thisEntity);
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
			List<_InventoryEntry> entityInventoryList = _inventoryToList(entityInventory);
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
			WindowManager.HoverRenderer<_InventoryEntry> hover = (float glX, float glY, _InventoryEntry item) -> {
				Item type;
				if (null != item.stackable)
				{
					type = item.stackable.type();
				}
				else
				{
					type = item.nonStackable.type();
				}
				windowManager.hoverItem.drawHoverAtPoint(glX, glY, type);
			};
			
			// Determine if we can handle manual crafting selection callbacks.
			Consumer<WindowManager.CraftDescription> craftHoverOverItem = canBeManuallySelected
					? (WindowManager.CraftDescription elt) -> {
						if (_leftClick)
						{
							Craft craft = elt.craft();
							if (null != _openStationLocation)
							{
								_client.beginCraftInBlock(_openStationLocation, craft);
							}
							else
							{
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
			WindowManager.WindowData<_InventoryEntry> topRight = new WindowManager.WindowData<>("Inventory"
					, entityInventory.currentEncumbrance
					, entityInventory.maxEncumbrance
					, _topRightPage
					, (int page) -> {
						if (_leftClick)
						{
							_topRightPage = page;
						}
					}
					, entityInventoryList
					, renderer
					, hover
					, (_InventoryEntry elt) -> {
						_handleHoverOverEntityInventoryItem(finalRelevantBlock, elt.key);
					}
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
			Consumer<BodyPart> armourEvent = (BodyPart part) -> {
				if (_leftClick)
				{
					// Note that we ignore the result since this will be reflected in the UI, if valid.
					_client.swapArmour(part);
				}
			};
			
			// Determine if we even want the crafting window since it doesn't apply to all stations.
			WindowManager.WindowData<WindowManager.CraftDescription> applicableCrafting = convertedCrafts.isEmpty()
					? null
					: topLeft
			;
			windowManager.drawActiveWindows(null, null, applicableCrafting, topRight, bottom, _thisEntity.armourSlots(), armourEvent, _normalGlX, _normalGlY);
		}
		else
		{
			// In this case, just draw the common UI elements.
			windowManager.drawActiveWindows(selectedBlock, selectedEntity, null, null, null, null, null, _normalGlX, _normalGlY);
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
					// Try to apply the selected item to the inventory (we consider this an action even if it did nothing).
					_client.applyToEntity(entity);
					didAct = true;
				}
			}
			
			// If we still didn't do anything, try clicks on the block or self.
			if (!didAct && _mouseClicked1 && (null != stopBlock) && (null != preStopBlock))
			{
				didAct = _client.runRightClickOnBlock(stopBlock, preStopBlock);
			}
			if (!didAct && _mouseClicked1)
			{
				didAct = _client.runRightClickOnSelf();
			}
			if (!didAct && (null != stopBlock) && (null != preStopBlock))
			{
				if (_canAct(stopBlock))
				{
					didAct = _client.runPlaceBlock(stopBlock, preStopBlock);
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
			_client.beginCraftInBlock(_openStationLocation, null);
			// We don't account for time here since this usually doesn't do anything.
		}
		
		// If we took no action, just tell the client to pass time.
		if (!_didAccountForTimeInFrame)
		{
			_client.doNothing();
		}
		// And reset.
		_didAccountForTimeInFrame = false;
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

	public void captureMouse1Down(boolean justClicked)
	{
		_mouseHeld1 = true;
		_mouseClicked1 = justClicked;
	}

	public void moveForward()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.FORWARD);
		_didAccountForTimeInFrame = true;
	}

	public void moveBackward()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.BACKWARD);
		_didAccountForTimeInFrame = true;
	}

	public void strafeRight()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.RIGHT);
		_didAccountForTimeInFrame = true;
	}

	public void strafeLeft()
	{
		_client.accelerateHorizontal(EntityChangeAccelerate.Relative.LEFT);
		_didAccountForTimeInFrame = true;
	}

	public void jumpOrSwim()
	{
		_client.jumpOrSwim();
	}

	public void normalMouseMoved(float glX, float glY)
	{
		_normalGlX = glX;
		_normalGlY = glY;
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
			break;
		case PLAY:
			_uiState = _UiState.MENU;
			_captureState.shouldCaptureMouse(false);
			break;
		}
	}

	public void handleHotbarIndex(int hotbarIndex)
	{
		_client.changeHotbarIndex(hotbarIndex);
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
			_topRightPage = 0;
			_bottomPage = 0;
			_viewingFuelInventory = false;
			_captureState.shouldCaptureMouse(false);
			break;
		}
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
			_topRightPage = 0;
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

	private Inventory _getEntityInventory()
	{
		Inventory inventory = _thisEntity.isCreativeMode()
				? CreativeInventory.fakeInventory()
				: _thisEntity.inventory()
		;
		return inventory;
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
	}
}
