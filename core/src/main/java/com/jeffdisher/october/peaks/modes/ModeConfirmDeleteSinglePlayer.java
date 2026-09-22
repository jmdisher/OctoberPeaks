package com.jeffdisher.october.peaks.modes;


/**
 * The state where we just allow confirmation to delete a single-player world.
 */
public class ModeConfirmDeleteSinglePlayer implements IGameMode
{
	public ModeConfirmDeleteSinglePlayer becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}
}
