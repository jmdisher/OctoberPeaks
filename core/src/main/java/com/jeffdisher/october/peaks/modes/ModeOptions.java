package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;


/**
 * The UI state under the PAUSE screen where we enter an options menu to view/change settings.
 * If there is a _currentGameSession, it will be shown in the background.
 */
public class ModeOptions implements IGameMode
{
	public GameSession currentGameSession;

	public ModeOptions becomeActive(GameSession currentGameSession)
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
