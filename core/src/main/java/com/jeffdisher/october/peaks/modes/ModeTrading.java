package com.jeffdisher.october.peaks.modes;


/**
 * Similar to inventory state, but for when we are viewing the trading UI for a villager.  The current villager
 * ID is stored in _currentTradingPartnerIdBinding (stored by ID, not instance, since they might move while open).
 */
public class ModeTrading implements IGameMode
{
	public ModeTrading becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}
}
