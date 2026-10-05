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

	public final Binding<Entity> _entityBinding;
	public final Binding<Inventory> thisEntityInventoryBinding;
	public final Binding<Inventory> bottomWindowInventoryBinding;
	public final Binding<String> bottomWindowTitleBinding;
	public final Binding<ViewFuelSlot.FuelTuple> bottomWindowFuelBinding;
	public final Binding<String> craftingPanelTitleBinding;
	public final Binding<List<CraftDescription>> craftingPanelBinding;
	public final Window thisEntityInventoryWindow;
	public final Window bottomInventoryWindow;
	public final Window craftingWindow;
	public final Window armourWindow;

	public GameSession currentGameSession;
	public AbsoluteLocation openStationLocation;
	public boolean viewingFuelInventory;
	public Craft continuousInInventory;
	public Craft continuousInBlock;
	public boolean isManualCraftingStation;

	public ModeInventory(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, Binding<Entity> entityBinding
		, IntConsumer mouseOverTopRightKeyConsumer
		, IntConsumer mouseOverBottomKeyConsumer
		, Consumer<CraftDescription> craftHoverOverConsumer
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		
		_entityBinding = entityBinding;
		this.bottomWindowInventoryBinding = new Binding<>(null);
		this.bottomWindowTitleBinding = new Binding<>(null);
		this.bottomWindowFuelBinding = new Binding<>(null);
		this.craftingPanelTitleBinding = new Binding<>(null);
		this.craftingPanelBinding = new Binding<>(null);
		
		BooleanSupplier isLeftClick = () -> inputCapture.mouseClicked0;
		this.thisEntityInventoryBinding = new SubBinding<>(entityBinding, (Entity entity) -> MiscPeaksHelpers.getInventory(entity));
		Binding<String> inventoryTitleBinding = new Binding<>("Inventory");
		ViewEntityInventory thisEntityInventoryView = new ViewEntityInventory(ui, inventoryTitleBinding, this.thisEntityInventoryBinding, null, mouseOverTopRightKeyConsumer, isLeftClick);
		this.thisEntityInventoryWindow = new Window(WINDOW_TOP_RIGHT, thisEntityInventoryView);
		ViewFuelSlot fuelProgress = new ViewFuelSlot(_ui, this.bottomWindowFuelBinding);
		ViewEntityInventory bottomInventoryView = new ViewEntityInventory(_ui, this.bottomWindowTitleBinding, this.bottomWindowInventoryBinding, fuelProgress, mouseOverBottomKeyConsumer, isLeftClick);
		this.bottomInventoryWindow = new Window(WINDOW_BOTTOM, bottomInventoryView);
		ViewCraftingPanel craftingPanelView = new ViewCraftingPanel(_ui, this.craftingPanelTitleBinding, this.craftingPanelBinding, craftHoverOverConsumer, isLeftClick);
		this.craftingWindow = new Window(WINDOW_TOP_LEFT, craftingPanelView);
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
		this.armourWindow = new Window(ViewArmour.LOCATION, new ViewArmour(_ui, armourBinding, eventHoverArmourBodyPart));
	}

	public ModeInventory becomeActive(GameSession currentGameSession, AbsoluteLocation openStationLocation)
	{
		this.currentGameSession = currentGameSession;
		this.openStationLocation = openStationLocation;
		this.viewingFuelInventory = false;
		this.continuousInInventory = null;
		this.continuousInBlock = null;
		this.isManualCraftingStation = false;
		
		// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
		_inputCapture.captureState.shouldCaptureMouse(false);
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.currentGameSession = null;
		this.openStationLocation = null;
		this.viewingFuelInventory = false;
		this.continuousInInventory = null;
		this.continuousInBlock = null;
		this.isManualCraftingStation = false;
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
					if (this.viewingFuelInventory)
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
					this.viewingFuelInventory = false;
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
				if (this.viewingFuelInventory)
				{
					stationName += " Fuel";
				}
			}
			else
			{
				// This is no longer a station.
				this.openStationLocation = null;
				this.continuousInInventory = null;
				this.continuousInBlock = null;
			}
		}
		
		Inventory entityInventory = this.thisEntityInventoryBinding.get();
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
		this.bottomWindowInventoryBinding.set(relevantInventory);
		this.bottomWindowTitleBinding.set(stationName);
		this.bottomWindowFuelBinding.set(fuelSlot);
		this.craftingPanelTitleBinding.set(craftingType);
		this.craftingPanelBinding.set(convertedCrafts);
		this.isManualCraftingStation = canBeManuallySelected;
		
		// Now, do the actual drawing.
		_ui.enterUiRenderMode();
		
		// This is a window mode so draw the usual.
		_modeContainer.play.drawPassiveOverlayWindows(this.currentGameSession);
		IAction action1 = this.armourWindow.doRender(_inputCapture.glCursorLocation);
		
		// We will show the crafting panel as long as there are any valid crafts.
		if (!convertedCrafts.isEmpty())
		{
			IAction hover = this.craftingWindow.doRender(_inputCapture.glCursorLocation);
			if (null != hover)
			{
				action1 = hover;
			}
		}
		IAction hover = this.thisEntityInventoryWindow.doRender(_inputCapture.glCursorLocation);
		if (null != hover)
		{
			action1 = hover;
		}
		hover = this.bottomInventoryWindow.doRender(_inputCapture.glCursorLocation);
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
			this.viewingFuelInventory = !this.viewingFuelInventory;
			if (this.viewingFuelInventory)
			{
				// Make sure that this actually has a fuel slot.
				if (null == this.openStationLocation)
				{
					this.viewingFuelInventory = false;
				}
				else
				{
					BlockProxy stationBlock = this.currentGameSession.blockLookup.readBlock(this.openStationLocation);
					this.viewingFuelInventory = (null != stationBlock.getFuel());
				}
			}
			this.continuousInInventory = null;
			this.continuousInBlock = null;
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
			if (null != this.continuousInInventory)
			{
				boolean isValid = currentGameSession.client.isCraftInInventoryValid(this.continuousInInventory);
				if (!isValid)
				{
					// We can't continue this so drop it.
					this.continuousInInventory = null;
				}
			}
			if (null != this.continuousInBlock)
			{
				boolean isValid = currentGameSession.client.isCraftInBlockValid(this.openStationLocation, this.continuousInBlock);
				if (!isValid)
				{
					// We can't continue this so drop it.
					this.continuousInBlock = null;
				}
			}
			Craft rescheduleInInventory = this.continuousInInventory;
			AbsoluteLocation openStationLocation = this.openStationLocation;
			Craft rescheduleInBlock = this.continuousInBlock;
			currentGameSession.client.passTimeWhileRunning(rescheduleInInventory, openStationLocation, rescheduleInBlock);
			_inputCapture.didAccountForTimeInFrame = true;
		}
	}
}
