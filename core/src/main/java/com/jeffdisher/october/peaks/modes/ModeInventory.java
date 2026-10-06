package com.jeffdisher.october.peaks.modes;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.ClientWrapper;
import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.persistence.MutableControls;
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
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * The mode where player control is largely disabled and the interface is mostly about clicking on buttons, etc.
 */
public class ModeInventory implements IGameMode
{
	public static final Rect WINDOW_TOP_RIGHT = new Rect(0.05f, 0.05f, ViewArmour.ARMOUR_SLOT_RIGHT_EDGE - ViewArmour.ARMOUR_SLOT_SCALE - ViewArmour.ARMOUR_SLOT_SPACING, 0.95f);
	public static final Rect WINDOW_TOP_LEFT = new Rect(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final Rect WINDOW_BOTTOM = new Rect(-0.95f, -0.80f, 0.95f, -0.05f);

	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final InputCapture _inputCapture;

	private final Binding<Entity> _entityBinding;
	private final Binding<Inventory> _thisEntityInventoryBinding;
	private final Binding<Inventory> _bottomWindowInventoryBinding;
	private final Binding<String> _bottomWindowTitleBinding;
	private final Binding<ViewFuelSlot.FuelTuple> _bottomWindowFuelBinding;
	private final Binding<String> _craftingPanelTitleBinding;
	private final Binding<List<CraftDescription>> _craftingPanelBinding;
	private final Window _thisEntityInventoryWindow;
	private final Window _bottomInventoryWindow;
	private final Window _craftingWindow;
	private final Window _armourWindow;

	public GameSession currentGameSession;
	public AbsoluteLocation openStationLocation;
	private boolean _viewingFuelInventory;
	private Craft _continuousInInventory;
	private Craft _continuousInBlock;
	private boolean _isManualCraftingStation;

	public ModeInventory(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, Binding<Entity> entityBinding
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		
		_entityBinding = entityBinding;
		_bottomWindowInventoryBinding = new Binding<>(null);
		_bottomWindowTitleBinding = new Binding<>(null);
		_bottomWindowFuelBinding = new Binding<>(null);
		_craftingPanelTitleBinding = new Binding<>(null);
		_craftingPanelBinding = new Binding<>(null);
		
		IntConsumer mouseOverTopRightKeyConsumer = (int key) -> {
			_handleHoverOverEntityInventoryItem(_modeContainer.inventory.openStationLocation, key);
		};
		IntConsumer mouseOverBottomKeyConsumer = (int key) -> {
			AbsoluteLocation relevantBlock = _modeContainer.inventory.openStationLocation;
			_pullFromBlockToEntityInventory(relevantBlock, key);
		};
		Consumer<CraftDescription> craftHoverOverConsumer = (CraftDescription desc) -> {
			if (_isManualCraftingStation && (_inputCapture.mouseClicked0))
			{
				Craft craft = desc.craft();
				if (null != _modeContainer.inventory.openStationLocation)
				{
					_continuousInBlock = _inputCapture.leftShiftHeld ? craft : null;
					_modeContainer.inventory.currentGameSession.client.beginCraftInBlock(_modeContainer.inventory.openStationLocation, craft);
				}
				else
				{
					_continuousInInventory = _inputCapture.leftShiftHeld ? craft : null;
					_modeContainer.inventory.currentGameSession.client.beginCraftInInventory(craft);
				}
				_inputCapture.didAccountForTimeInFrame = true;
			}
		};
		
		BooleanSupplier isLeftClick = () -> inputCapture.mouseClicked0;
		_thisEntityInventoryBinding = new SubBinding<>(entityBinding, (Entity entity) -> MiscPeaksHelpers.getInventory(entity));
		Binding<String> inventoryTitleBinding = new Binding<>("Inventory");
		ViewEntityInventory thisEntityInventoryView = new ViewEntityInventory(ui, inventoryTitleBinding, _thisEntityInventoryBinding, null, mouseOverTopRightKeyConsumer, isLeftClick);
		_thisEntityInventoryWindow = new Window(WINDOW_TOP_RIGHT, thisEntityInventoryView);
		ViewFuelSlot fuelProgress = new ViewFuelSlot(_ui, _bottomWindowFuelBinding);
		ViewEntityInventory bottomInventoryView = new ViewEntityInventory(_ui, _bottomWindowTitleBinding, _bottomWindowInventoryBinding, fuelProgress, mouseOverBottomKeyConsumer, isLeftClick);
		_bottomInventoryWindow = new Window(WINDOW_BOTTOM, bottomInventoryView);
		ViewCraftingPanel craftingPanelView = new ViewCraftingPanel(_ui, _craftingPanelTitleBinding, _craftingPanelBinding, craftHoverOverConsumer, isLeftClick);
		_craftingWindow = new Window(WINDOW_TOP_LEFT, craftingPanelView);
		Consumer<BodyPart> eventHoverArmourBodyPart = (BodyPart hoverPart) -> {
			Assert.assertTrue(this == _modeContainer.currentMode);
			if (inputCapture.mouseClicked0)
			{
				// Note that we ignore the result since this will be reflected in the UI, if valid.
				GameSession currentGameSession = this.currentGameSession;
				currentGameSession.client.swapArmour(hoverPart);
			}
		};
		Binding<NonStackableItem[]> armourBinding = new SubBinding<>(entityBinding, (Entity entity) -> entity.armourSlots());
		_armourWindow = new Window(ViewArmour.LOCATION, new ViewArmour(_ui, armourBinding, eventHoverArmourBodyPart));
	}

	public ModeInventory becomeActive(GameSession currentGameSession, AbsoluteLocation openStationLocation)
	{
		this.currentGameSession = currentGameSession;
		this.openStationLocation = openStationLocation;
		_viewingFuelInventory = false;
		_continuousInInventory = null;
		_continuousInBlock = null;
		_isManualCraftingStation = false;
		
		// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
		_inputCapture.captureState.shouldCaptureMouse(false);
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.currentGameSession = null;
		this.openStationLocation = null;
		_viewingFuelInventory = false;
		_continuousInInventory = null;
		_continuousInBlock = null;
		_isManualCraftingStation = false;
	}

	@Override
	public void handleEscape()
	{
		_modeContainer.setActive(_modeContainer.play.becomeActive(this.currentGameSession));
	}

	@Override
	public IAction drawRelevantWindows()
	{
		this.currentGameSession.scene.renderCommon();
		this.currentGameSession.eyeEffect.drawEyeEffect();
		
		// We are in inventory mode but we will need to handle station/floor cases differently.
		Environment env = Environment.getShared();
		Inventory relevantInventory = null;
		Inventory inventoryToCraftFrom = null;
		List<Craft> validCrafts = null;
		CraftOperation currentOperation = null;
		String stationName = "Floor";
		ViewFuelSlot.FuelTuple fuelSlot = null;
		boolean isAutomaticCrafting = false;
		if (null != this.openStationLocation)
		{
			// We are in station mode so check this block's inventory and crafting (potentially clearing it if it is no longer a station).
			BlockProxy stationBlock = this.currentGameSession.blockLookup.readBlock(this.openStationLocation);
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
						fuelSlot = new ViewFuelSlot.FuelTuple(currentFuel, fuelRemaining);
					}
				}
				else
				{
					// This is invalid so just clear it.
					_viewingFuelInventory = false;
				}
				
				// Find the crafts for this station type.
				Set<String> classifications = env.stations.getCraftingClasses(stationType);
				
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
				this.openStationLocation = null;
				_continuousInInventory = null;
				_continuousInBlock = null;
			}
		}
		
		Inventory entityInventory = _thisEntityInventoryBinding.get();
		if (null == this.openStationLocation)
		{
			// We are just looking at the floor at our feet.
			Entity thisEntity = _entityBinding.get();
			
			inventoryToCraftFrom = entityInventory;
			// We are just looking at the entity inventory so find the built-in crafting recipes.
			validCrafts = env.crafting.craftsForClassifications(Set.of(CraftAspect.BUILT_IN));
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
		_isManualCraftingStation = canBeManuallySelected;
		
		// Now, do the actual drawing.
		_ui.enterUiRenderMode();
		
		// This is a window mode so draw the usual.
		_modeContainer.play.drawPassiveOverlayWindows(this.currentGameSession);
		IAction action1 = _armourWindow.doRender(_inputCapture.glCursorLocation);
		
		// We will show the crafting panel as long as there are any valid crafts.
		if (!convertedCrafts.isEmpty())
		{
			IAction hover = _craftingWindow.doRender(_inputCapture.glCursorLocation);
			if (null != hover)
			{
				action1 = hover;
			}
		}
		IAction hover = _thisEntityInventoryWindow.doRender(_inputCapture.glCursorLocation);
		if (null != hover)
		{
			action1 = hover;
		}
		hover = _bottomInventoryWindow.doRender(_inputCapture.glCursorLocation);
		if (null != hover)
		{
			action1 = hover;
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action1)
		{
			action1.renderHover(_inputCapture.glCursorLocation);
		}
		return action1;
	}

	@Override
	public void handleUserEvents()
	{
		if (-1 != _inputCapture.lastPressedNumber)
		{
			int hotbarIndex = _inputCapture.lastPressedNumber - 1;
			this.currentGameSession.client.changeHotbarIndex(hotbarIndex);
			_inputCapture.lastPressedNumber = -1;
		}
		if (_inputCapture.controlReleased[MutableControls.Control.TOGGLE_INVENTORY.ordinal()])
		{
			_modeContainer.setActive(_modeContainer.play.becomeActive(this.currentGameSession));
			_inputCapture.controlReleased[MutableControls.Control.TOGGLE_INVENTORY.ordinal()] = false;
		}
		if (_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FUEL.ordinal()])
		{
			_viewingFuelInventory = !_viewingFuelInventory;
			if (_viewingFuelInventory)
			{
				// Make sure that this actually has a fuel slot.
				if (null == this.openStationLocation)
				{
					_viewingFuelInventory = false;
				}
				else
				{
					BlockProxy stationBlock = this.currentGameSession.blockLookup.readBlock(this.openStationLocation);
					_viewingFuelInventory = (null != stationBlock.getFuel());
				}
			}
			_continuousInInventory = null;
			_continuousInBlock = null;
			_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FUEL.ordinal()] = false;
		}
		
		// This is similar to PLAY but only passive events are relevant here since any active events come from actions in the UI.
		_passTimeWhileRunning(this.currentGameSession);
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
			// Check to see if our continuous crafting operations are still valid.
			if (null != _continuousInInventory)
			{
				boolean isValid = currentGameSession.client.isCraftInInventoryValid(_continuousInInventory);
				if (!isValid)
				{
					// We can't continue this so drop it.
					_continuousInInventory = null;
				}
			}
			if (null != _continuousInBlock)
			{
				boolean isValid = currentGameSession.client.isCraftInBlockValid(this.openStationLocation, _continuousInBlock);
				if (!isValid)
				{
					// We can't continue this so drop it.
					_continuousInBlock = null;
				}
			}
			Craft rescheduleInInventory = _continuousInInventory;
			AbsoluteLocation openStationLocation = this.openStationLocation;
			Craft rescheduleInBlock = _continuousInBlock;
			currentGameSession.client.passTimeWhileRunning(rescheduleInInventory, openStationLocation, rescheduleInBlock);
			_inputCapture.didAccountForTimeInFrame = true;
		}
	}

	private void _handleHoverOverEntityInventoryItem(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		// This is the helper called when looking at the player's own inventory.
		if (_inputCapture.mouseClicked0 && !_inputCapture.leftShiftHeld)
		{
			// Select this in the hotbar (this will clear if already set).
			this.currentGameSession.client.setSelectedItemKeyOrClear(entityInventoryKey);
		}
		else if ((null != targetBlock) && _inputCapture.mouseClicked1)
		{
			this.currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _viewingFuelInventory);
		}
		else if ((null != targetBlock) && (_inputCapture.mouseClicked0 && _inputCapture.leftShiftHeld))
		{
			this.currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, _viewingFuelInventory);
		}
		else if (_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()])
		{
			// If we are holding ctrl, drop the entire stack.
			this.currentGameSession.client.dropItemSlot(entityInventoryKey, _inputCapture.leftCtrlHeld);
			_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()] = false;
		}
	}

	private void _pullFromBlockToEntityInventory(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		// Note that we ignore the result since this will be reflected in the UI, if valid.
		if (_inputCapture.mouseClicked1)
		{
			this.currentGameSession.client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _viewingFuelInventory);
		}
		else if (_inputCapture.leftShiftHeld && _inputCapture.mouseClicked0)
		{
			this.currentGameSession.client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, _viewingFuelInventory);
		}
	}
}
