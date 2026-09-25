package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;


/**
 * The mode where play is normal.  Cursor is captured and there is no open window.
 */
public class ModePlay implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final IMouseCapture _mouseCapture;

	public GameSession currentGameSession;

	public ModePlay(ModeContainer modeContainer
		, IMouseCapture mouseCapture
	)
	{
		_modeContainer = modeContainer;
		_mouseCapture = mouseCapture;
	}

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

	@Override
	public void handleEscape()
	{
		this.currentGameSession.client.pauseGame();
		_modeContainer.setActive(_modeContainer.pause.becomeActive(this.currentGameSession));
		_mouseCapture.disableMouseCapture();
	}


	public static interface IMouseCapture
	{
		void disableMouseCapture();
	}
}
