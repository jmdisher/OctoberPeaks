package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The mode where we are waiting for the connection to complete.  This will naturally change into play on
 * success.
 */
public class ModeConnecting implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final FixedWindow _connectingStateWindow;

	public GameSession pendingGameSession;

	public ModeConnecting(ModeContainer modeContainer
		, GlUi ui
		, MouseState mouseState
		, UiData uiData
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_mouseState = mouseState;
		_uiData = uiData;
		
		_connectingStateWindow = _buildConnectingStateWindow(_ui, _uiData);
	}

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

	@Override
	public void handleEscape()
	{
		_goBack();
	}

	public IAction drawRelevantWindows()
	{
		_ui.enterUiRenderMode();
		
		return _connectingStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildConnectingStateWindow(GlUi ui, UiData uiData)
	{
		ViewTextButton<String> cancelConnectButton = _buildCancelConnectionButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Connecting...")), new Rect(-0.5f, 0.2f, 0.5f, 0.3f))
			.add(cancelConnectButton, new Rect(-0.3f, -0.4f, 0.3f, -0.3f))
			.finish()
		;
	}

	private ViewTextButton<String> _buildCancelConnectionButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Cancel Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickCancelConnectButton();
			}
		);
	}

	private void _action_clickCancelConnectButton()
	{
		if (_mouseState.leftClick)
		{
			_goBack();
		}
	}

	private void _goBack()
	{
		// We need to cancel the disconnect and switch back to start.
		this.pendingGameSession.shutdown();
		this.pendingGameSession = null;
		_modeContainer.setActive(_modeContainer.start.becomeActive());
	}
}
