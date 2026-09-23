package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;


/**
 * Similar to inventory state, but for when we are viewing the trading UI for a villager.  The current villager
 * ID is stored in _currentTradingPartnerIdBinding (stored by ID, not instance, since they might move while open).
 */
public class ModeTrading implements IGameMode
{
	public GameSession currentGameSession;

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
}
