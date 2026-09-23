package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Craft;


/**
 * The mode where player control is largely disabled and the interface is mostly about clicking on buttons, etc.
 */
public class ModeInventory implements IGameMode
{
	public GameSession currentGameSession;
	public AbsoluteLocation openStationLocation;
	public boolean viewingFuelInventory;
	public Craft continuousInInventory;
	public Craft continuousInBlock;
	public boolean isManualCraftingStation;

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
}
