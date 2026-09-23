package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;


/**
 * The mode where we are waiting for the connection to complete.  This will naturally change into play on
 * success.
 */
public class ModeConnecting implements IGameMode
{
	public GameSession pendingGameSession;

	public ModeConnecting becomeActive(GameSession pendingGameSession)
	{
		this.pendingGameSession = pendingGameSession;
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.pendingGameSession = null;
	}
}
