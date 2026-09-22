package com.jeffdisher.october.peaks.modes;


/**
 * The game starts here when invoked without any arguments.  It presents a starting menu to create/join games.
 */
public class ModeStart implements IGameMode
{
	public ModeStart becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}
}
