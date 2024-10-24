package com.jeffdisher.october.peaks;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.peaks.WindowManager.ItemRequirement;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
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
	private final MovementControl _movement;
	private final ClientWrapper _client;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;

	private Entity _thisEntity;
	private boolean _rotationDidUpdate;
	private boolean _didAccountForTimeInFrame;
	private boolean _mouseHeld0;
	private boolean _mouseHeld1;
	private boolean _mouseClicked1;
	private float _normalGlX;
	private float _normalGlY;

	// Data specifically related to high-level UI state (will likely be pulled out, later).
	private _UiState _uiState;

	public UiStateManager(MovementControl movement, ClientWrapper client, Function<AbsoluteLocation, BlockProxy> blockLookup)
	{
		_movement = movement;
		_client = client;
		_blockLookup = blockLookup;
		
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
			
			// Get the inventory for this entity and the floor.
			List<_InventoryEntry> entityInventory = _inventoryToList(_thisEntity.inventory());
			BlockProxy thisBlock = _blockLookup.apply(GeometryHelpers.getCentreAtFeet(_thisEntity));
			List<_InventoryEntry> floorInventory = _inventoryToList(thisBlock.getInventory());
			
			// We are just looking at the entity inventory so find the built-in crafting recipes.
			List<Craft> builtInCrafts = env.crafting.craftsForClassifications(Set.of(CraftAspect.BUILT_IN));
			// We will convert these into CraftOperation instances so we can splice in the current craft.
			CraftOperation currentOperation = _thisEntity.localCraftOperation();
			Craft currentCraft = (null != currentOperation) ? currentOperation.selectedCraft() : null;
			List<WindowManager.CraftDescription> convertedCrafts = builtInCrafts.stream()
					.map((Craft craft) -> {
						long progressMillis = 0L;
						if (craft == currentCraft)
						{
							progressMillis = currentOperation.completedMillis();
						}
						float progress = (float)progressMillis / (float)craft.millisPerCraft;
						ItemRequirement[] requirements = Arrays.stream(craft.input)
								.map((Items input) -> {
									Item type = input.type();
									int available = _thisEntity.inventory().getCount(type);
									return new ItemRequirement(type, input.count(), available);
								})
								.toArray((int size) -> new ItemRequirement[size])
						;
						// Note that we are assuming that there is only one output type.
						return new WindowManager.CraftDescription(craft.name
								, new Items(craft.output[0], craft.output.length)
								, requirements
								, progress
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
			
			WindowManager.WindowData<WindowManager.CraftDescription> topLeft = new WindowManager.WindowData<>("Crafting"
					, 0
					, 0
					, 0
					, null
					, convertedCrafts
					, windowManager.renderCraftOperation
					, windowManager.hoverCraftOperation
					, null
			);
			WindowManager.WindowData<_InventoryEntry> topRight = new WindowManager.WindowData<>("Inventory"
					, 4
					, 10
					, 0
					, (int page) -> System.out.println("PAGE: " + page)
					, entityInventory
					, renderer
					, hover
					, (_InventoryEntry elt) -> System.out.println("KEY: " + elt.key)
			);
			WindowManager.WindowData<_InventoryEntry> bottom = new WindowManager.WindowData<>("Floor"
					, 0
					, 20
					, 0
					, (int page) -> {}
					, floorInventory
					, renderer
					, hover
					, (_InventoryEntry elt) -> {}
			);
			
			windowManager.drawActiveWindows(null, null, topLeft, topRight, bottom, _normalGlX, _normalGlY);
		}
		else
		{
			// In this case, just draw the common UI elements.
			windowManager.drawActiveWindows(selectedBlock, selectedEntity, null, null, null, _normalGlX, _normalGlY);
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
		// See if the click refers to anything selected.
		if (_mouseHeld0)
		{
			if (null != stopBlock)
			{
				_client.hitBlock(stopBlock);
			}
		}
		else if (_mouseHeld1)
		{
			if (null != preStopBlock)
			{
				_client.runRightClickAction(stopBlock, preStopBlock, _mouseClicked1);
			}
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
		_mouseClicked1 = false;
	}

	public void setThisEntity(Entity projectedEntity)
	{
		_thisEntity = projectedEntity;
	}

	public void capturedMouseMoved(int deltaX, int deltaY)
	{
		_movement.rotate(deltaX, deltaY);
		_rotationDidUpdate = true;
	}

	public void captureMouse0Down(boolean justClicked)
	{
		_mouseHeld0 = true;
	}

	public void captureMouse1Down(boolean justClicked)
	{
		_mouseHeld1 = true;
		_mouseClicked1 = justClicked;
	}

	public void moveForward()
	{
		EntityChangeMove.Direction direction = _movement.walk(true);
		_client.stepHorizontal(direction);
		_didAccountForTimeInFrame = true;
	}

	public void moveBackward()
	{
		EntityChangeMove.Direction direction = _movement.walk(false);
		_client.stepHorizontal(direction);
		_didAccountForTimeInFrame = true;
	}

	public void strafeRight()
	{
		EntityChangeMove.Direction direction = _movement.strafeRight(true);
		_client.stepHorizontal(direction);
		_didAccountForTimeInFrame = true;
	}

	public void strafeLeft()
	{
		EntityChangeMove.Direction direction = _movement.strafeRight(false);
		_client.stepHorizontal(direction);
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

	public void normalMouse0Clicked()
	{
		// TODO:  Implement (inventory/crafting mode).
	}

	public void normalMouse1Clicked()
	{
		// TODO:  Implement (inventory/crafting mode).
	}

	public void handleKeyEsc(IInputStateChanger captureState)
	{
		switch (_uiState)
		{
		case INVENTORY:
			_uiState = _UiState.PLAY;
			captureState.shouldCaptureMouse(true);
			break;
		case MENU:
			_uiState = _UiState.PLAY;
			captureState.shouldCaptureMouse(true);
			break;
		case PLAY:
			_uiState = _UiState.MENU;
			captureState.shouldCaptureMouse(false);
			break;
		}
	}

	public void handleKeyI(IInputStateChanger captureState)
	{
		switch (_uiState)
		{
		case INVENTORY:
			_uiState = _UiState.PLAY;
			captureState.shouldCaptureMouse(true);
			break;
		case MENU:
			// Just ignore this.
			break;
		case PLAY:
			_uiState = _UiState.INVENTORY;
			captureState.shouldCaptureMouse(false);
			break;
		}
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
