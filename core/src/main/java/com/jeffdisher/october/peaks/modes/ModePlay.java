package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;


/**
 * The mode where play is normal.  Cursor is captured and there is no open window.
 */
public class ModePlay implements IGameMode
{
	public GameSession currentGameSession;

	public ModePlay becomeActive(GameSession currentGameSession)
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
