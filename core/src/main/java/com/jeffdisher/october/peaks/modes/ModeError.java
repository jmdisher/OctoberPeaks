package com.jeffdisher.october.peaks.modes;


/**
 * A state used to display a fatal error message before exiting.
 */
public class ModeError implements IGameMode
{
	public String[] errorPayload;

	public ModeError becomeActive(String[] errorPayload)
	{
		this.errorPayload = errorPayload;
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.errorPayload = null;
	}
}
