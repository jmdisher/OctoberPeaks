package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;


/**
 * The mode where play is effectively "paused".  The cursor is released and buttons to change game setup will be
 * presented.
 * Note that we call the state "paused" even though servers are never "paused" (the title will reflect this
 * difference).
 */
public class ModePause implements IGameMode
{
	public GameSession currentGameSession;

	public ModePause becomeActive(GameSession currentGameSession)
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
