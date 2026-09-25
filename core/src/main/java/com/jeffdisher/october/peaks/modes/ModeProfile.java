package com.jeffdisher.october.peaks.modes;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.peaks.profiling.ProfilingSession;


/**
 * This state is similar to PLAY, in that it draws the game to the screen, but it is different in that it can
 * accept no UI interaction and beyond using Esc to quit the program.
 * Only possible to reach this state from LIST_FOR_PROFILE state.
 */
public class ModeProfile implements IGameMode
{
	public ProfilingSession profilingSession;

	public ModeProfile becomeActive(ProfilingSession profilingSession)
	{
		this.profilingSession = profilingSession;
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.profilingSession = null;
	}

	@Override
	public void handleEscape()
	{
		// We just want to exit, in this case.
		System.out.println("Ending Profile Run");
		this.profilingSession.shutdown();
		Gdx.app.exit();
	}
}
