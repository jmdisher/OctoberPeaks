package com.jeffdisher.october.peaks.modes;


/**
 * The mode where play is normal.  Cursor is captured and there is no open window.
 */
public class ModePlay implements IGameMode
{
	public ModePlay becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}
}
