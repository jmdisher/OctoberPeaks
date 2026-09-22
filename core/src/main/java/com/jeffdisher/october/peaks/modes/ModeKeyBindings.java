package com.jeffdisher.october.peaks.modes;


/**
 * The UI state under the PAUSE screen where the user can change key bindings.
 * If there is a _currentGameSession, it will be shown in the background.
 */
public class ModeKeyBindings implements IGameMode
{
	public ModeKeyBindings becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}
}
