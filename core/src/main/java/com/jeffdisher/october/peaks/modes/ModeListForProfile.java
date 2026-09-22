package com.jeffdisher.october.peaks.modes;


/**
 * A special state which is like the other LIST_* modes but the listed options are just the hard-coded profile
 * options for use with a local profile during performance investigations.
 * NOTE:  This state is only reachable if the "OCTOBER_PEAKS_PROFILE" environment variable is set.
 */
public class ModeListForProfile implements IGameMode
{
	public ModeListForProfile becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}
}
