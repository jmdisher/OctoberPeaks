package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;


/**
 * The UI state under the PAUSE screen where the user can change key bindings.
 * If there is a _currentGameSession, it will be shown in the background.
 */
public class ModeKeyBindings implements IGameMode
{
	public GameSession currentGameSession;

	public ModeKeyBindings becomeActive(GameSession currentGameSession)
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
