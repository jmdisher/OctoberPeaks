package com.jeffdisher.october.peaks.modes;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.CraftDescription;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.SubBinding;
import com.jeffdisher.october.peaks.ui.ViewArmour;
import com.jeffdisher.october.peaks.ui.ViewCraftingPanel;
import com.jeffdisher.october.peaks.ui.ViewEntityInventory;
import com.jeffdisher.october.peaks.ui.ViewFuelSlot;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
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
		
		this.bottomWindowInventoryBinding = new Binding<>(null);
		this.bottomWindowTitleBinding = new Binding<>(null);
		this.bottomWindowFuelBinding = new Binding<>(null);
		this.craftingPanelTitleBinding = new Binding<>(null);
		this.craftingPanelBinding = new Binding<>(null);
		
		BooleanSupplier isLeftClick = () -> inputCapture.mouseReleased0;
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
			Assert.assertTrue(_modeContainer.inventory == _modeContainer.currentMode);
			if (inputCapture.mouseReleased0)
			{
				// Note that we ignore the result since this will be reflected in the UI, if valid.
				GameSession currentGameSession = _modeContainer.inventory.currentGameSession;
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
		_inputCapture.captureState.shouldCaptureMouse(true);
	}
}
