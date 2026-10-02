package com.jeffdisher.october.peaks.modes;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.SubBinding;
import com.jeffdisher.october.peaks.ui.ViewArmour;
import com.jeffdisher.october.peaks.ui.ViewEntityInventory;
import com.jeffdisher.october.peaks.ui.ViewTradeOffers;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Similar to inventory state, but for when we are viewing the trading UI for a villager.  The current villager
 * ID is stored in _currentTradingPartnerIdBinding (stored by ID, not instance, since they might move while open).
 */
public class ModeTrading implements IGameMode
{
	public static final Rect WINDOW_LEFT = new Rect(-0.95f, -0.80f, -0.05f, 0.95f);
	public static final Rect WINDOW_TOP_RIGHT = new Rect(0.05f, 0.05f, ViewArmour.ARMOUR_SLOT_RIGHT_EDGE - ViewArmour.ARMOUR_SLOT_SCALE - ViewArmour.ARMOUR_SLOT_SPACING, 0.95f);

	private final ModeContainer _modeContainer;
	private final InputCapture _inputCapture;

	private final Binding<Integer> _currentTradingPartnerIdBinding;
	private final Binding<Inventory> _thisEntityInventoryBinding;
	public final Window thisEntityInventoryWindow;
	public final Window leftTradingWindow;

	public GameSession currentGameSession;

	public ModeTrading(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, Binding<Integer> currentTradingPartnerIdBinding
		, Binding<Entity> entityBinding
		, IntConsumer mouseOverTopRightKeyConsumer
	)
	{
		_modeContainer = modeContainer;
		_inputCapture = inputCapture;
		_currentTradingPartnerIdBinding = currentTradingPartnerIdBinding;
		
		BooleanSupplier isLeftClick = () -> inputCapture.leftClick;
		_thisEntityInventoryBinding = new SubBinding<>(entityBinding, (Entity entity) -> MiscPeaksHelpers.getInventory(entity));
		Binding<String> inventoryTitleBinding = new Binding<>("Inventory");
		ViewEntityInventory thisEntityInventoryView = new ViewEntityInventory(ui, inventoryTitleBinding, _thisEntityInventoryBinding, null, mouseOverTopRightKeyConsumer, isLeftClick);
		this.thisEntityInventoryWindow = new Window(WINDOW_TOP_RIGHT, thisEntityInventoryView);
		Consumer<Item> tradeButtonConsumer = (Item tradeItem) -> {
			Assert.assertTrue(_modeContainer.trading == _modeContainer.currentMode);
			if (inputCapture.leftClick)
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
		ViewTradeOffers bottomTradingView = new ViewTradeOffers(ui
			, _currentTradingPartnerIdBinding
			, (int villagerId) -> {
				Assert.assertTrue(_modeContainer.trading == _modeContainer.currentMode);
				PartialEntity partial = _modeContainer.trading.currentGameSession.getEntityForId(villagerId);
				return MinimalEntity.fromPartialEntity(partial);
			}, tradeButtonConsumer);
		this.leftTradingWindow = new Window(WINDOW_LEFT, bottomTradingView);
	}

	public ModeTrading becomeActive(GameSession currentGameSession)
	{
		this.currentGameSession = currentGameSession;
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.currentGameSession = null;
	}

	@Override
	public void handleEscape()
	{
		// Whenever we exit trading mode, we always go back into play mode.
		_currentTradingPartnerIdBinding.set(0);
		_modeContainer.setActive(_modeContainer.play.becomeActive(this.currentGameSession));
		_inputCapture.captureState.shouldCaptureMouse(true);
	}
}
