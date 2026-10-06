package com.jeffdisher.october.peaks.modes;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
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
	private final GlUi _ui;
	private final InputCapture _inputCapture;

	private final Binding<Integer> _currentTradingPartnerIdBinding;
	private final Window _thisEntityInventoryWindow;
	private final Window _leftTradingWindow;

	public GameSession currentGameSession;

	public ModeTrading(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, Binding<Entity> entityBinding
		, Binding<Integer> currentTradingPartnerIdBinding
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		_currentTradingPartnerIdBinding = currentTradingPartnerIdBinding;
		
		IntConsumer mouseOverTopRightKeyConsumer = (int key) -> {
			_handleHoverOverEntityInventoryItem(key);
		};
		
		BooleanSupplier isLeftClick = () -> inputCapture.mouseClicked0;
		Binding<Inventory> thisEntityInventoryBinding = new SubBinding<>(entityBinding, (Entity entity) -> MiscPeaksHelpers.getInventory(entity));
		Binding<String> inventoryTitleBinding = new Binding<>("Inventory");
		ViewEntityInventory thisEntityInventoryView = new ViewEntityInventory(ui, inventoryTitleBinding, thisEntityInventoryBinding, null, mouseOverTopRightKeyConsumer, isLeftClick);
		_thisEntityInventoryWindow = new Window(WINDOW_TOP_RIGHT, thisEntityInventoryView);
		Consumer<Item> tradeButtonConsumer = (Item tradeItem) -> {
			Assert.assertTrue(_modeContainer.trading == _modeContainer.currentMode);
			if (inputCapture.mouseClicked0)
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
		_leftTradingWindow = new Window(WINDOW_LEFT, bottomTradingView);
	}

	public ModeTrading becomeActive(GameSession currentGameSession)
	{
		_inputCapture.captureState.shouldCaptureMouse(false);
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
	}

	@Override
	public IAction drawRelevantWindows()
	{
		this.currentGameSession.scene.renderCommon();
		this.currentGameSession.eyeEffect.drawEyeEffect();
		
		_ui.enterUiRenderMode();
		
		// This is a window mode so draw the usual.
		_modeContainer.play.drawPassiveOverlayWindows(this.currentGameSession);
		IAction action = null;
		
		// The trading window is the interesting part of this view.
		IAction hover = _leftTradingWindow.doRender(_inputCapture.glCursorLocation);
		if (null != hover)
		{
			action = hover;
		}
		
		hover = _thisEntityInventoryWindow.doRender(_inputCapture.glCursorLocation);
		if (null != hover)
		{
			action = hover;
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action)
		{
			action.renderHover(_inputCapture.glCursorLocation);
		}
		
		// Return any action so that the caller can run the action now that rendering is finished.
		return action;
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
		
		// This is similar to PLAY but only passive events are relevant here since any active events come from actions in the UI.
		_modeContainer.play.commonIdleWhileRunning(this.currentGameSession);
	}


	private void _handleHoverOverEntityInventoryItem(int entityInventoryKey)
	{
		// This is the helper called when looking at the player's own inventory.
		if (_inputCapture.mouseClicked0 && !_inputCapture.leftShiftHeld)
		{
			// Select this in the hotbar (this will clear if already set).
			this.currentGameSession.client.setSelectedItemKeyOrClear(entityInventoryKey);
		}
		else if (_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()])
		{
			// If we are holding ctrl, drop the entire stack.
			this.currentGameSession.client.dropItemSlot(entityInventoryKey, _inputCapture.leftCtrlHeld);
			_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()] = false;
		}
	}
}
