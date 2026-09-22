package com.jeffdisher.october.peaks.modes;


/**
 * The state where present an option to add a new one server to our list.
 */
public class ModeNewMultiPlayer implements IGameMode
{
	public ModeNewMultiPlayer becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}
}
