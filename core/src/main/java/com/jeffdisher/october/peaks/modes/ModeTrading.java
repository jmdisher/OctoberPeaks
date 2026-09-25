package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.ui.Binding;


/**
 * Similar to inventory state, but for when we are viewing the trading UI for a villager.  The current villager
 * ID is stored in _currentTradingPartnerIdBinding (stored by ID, not instance, since they might move while open).
 */
public class ModeTrading implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final Binding<Integer> _currentTradingPartnerIdBinding;
	private final IMouseCapture _mouseCapture;

	public GameSession currentGameSession;

	public ModeTrading(ModeContainer modeContainer
		, Binding<Integer> currentTradingPartnerIdBinding
		, IMouseCapture mouseCapture
	)
	{
		_modeContainer = modeContainer;
		_currentTradingPartnerIdBinding = currentTradingPartnerIdBinding;
		_mouseCapture = mouseCapture;
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
		_mouseCapture.enableMouseCapture();
	}


	public static interface IMouseCapture
	{
		void enableMouseCapture();
	}
}
