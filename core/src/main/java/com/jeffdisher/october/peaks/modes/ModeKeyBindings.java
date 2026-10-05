package com.jeffdisher.october.peaks.modes;

import com.badlogic.gdx.Input.Keys;
import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ViewKeyControlSelector;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The UI state under the PAUSE screen where the user can change key bindings.
 * If there is a _currentGameSession, it will be shown in the background.
 */
public class ModeKeyBindings implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final InputCapture _inputCapture;
	private final UiData _uiData;
	private final FixedWindow _keyBindingsStateWindow;

	public GameSession currentGameSession;

	public ModeKeyBindings(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, UiData uiData
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		_uiData = uiData;
		
		_keyBindingsStateWindow = _buildKeyBindingsStateWindow(_ui, _uiData);
	}

	public ModeKeyBindings becomeActive(GameSession currentGameSession)
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
		_escapeOrBack();
	}

	@Override
	public IAction drawRelevantWindows()
	{
		if (null != this.currentGameSession)
		{
			_modeContainer.pause.drawCommonPauseBackground(this.currentGameSession);
		}
		
		return _keyBindingsStateWindow.render(_inputCapture.glCursorLocation);
	}

	@Override
	public void handleUserEvents()
	{
		if (Keys.UNKNOWN != _inputCapture.lastKeyCodeReleased)
		{
			if (null != _uiData.currentlyChangingControl.get())
			{
				boolean didSet = _uiData.mutableControls.setKeyForControl(_uiData.currentlyChangingControl.get(), _inputCapture.lastKeyCodeReleased);
				if (didSet)
				{
					_uiData.currentlyChangingControl.set(null);
				}
			}
			_inputCapture.lastKeyCodeReleased = Keys.UNKNOWN;
		}
		
		// We can be in this state while running or while at the main menu.
		if (null != this.currentGameSession)
		{
			// This mode is also accessible from the pause menu so check if we are on a server.
			if (this.currentGameSession.isOnServer)
			{
				_modeContainer.play.commonIdleWhileRunning(this.currentGameSession);
			}
			else
			{
				this.currentGameSession.client.passTimeWhilePaused();
			}
		}
	}


	private FixedWindow _buildKeyBindingsStateWindow(GlUi ui, UiData uiData)
	{
		ViewKeyControlSelector keyBindingSelectorControl = _buildKeyControlSelector(ui, uiData);
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Key Bindings")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(keyBindingSelectorControl, new Rect(-0.4f, -0.9f, 0.4f, 0.6f))
			.add(backButton, new Rect(-0.2f, -0.8f, 0.2f, -0.7f))
			.finish()
		;
	}

	private ViewKeyControlSelector _buildKeyControlSelector(GlUi ui
		, UiData uiData
	)
	{
		return new ViewKeyControlSelector(ui, uiData.mutableControls
			, uiData.currentlyChangingControl
			, (MutableControls.Control selectedControl) -> {
				_action_clickKeyBindingSelector(selectedControl);
			}
		);
	}

	private ViewTextButton<String> _buildBackButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Back")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickBackButton();
			}
		);
	}

	private void _action_clickKeyBindingSelector(MutableControls.Control selectedControl)
	{
		if (_inputCapture.mouseClicked0)
		{
			_uiData.currentlyChangingControl.set(selectedControl);
		}
	}

	private void _action_clickBackButton()
	{
		if (_inputCapture.mouseClicked0)
		{
			_escapeOrBack();
		}
	}

	private void _escapeOrBack()
	{
		if (null != _uiData.currentlyChangingControl.get())
		{
			_uiData.currentlyChangingControl.set(null);
		}
		else
		{
			// Key bindings depends on whether is a game playing.
			if (null != this.currentGameSession)
			{
				_modeContainer.setActive(_modeContainer.pause.becomeActive(this.currentGameSession));
			}
			else
			{
				_modeContainer.setActive(_modeContainer.start.becomeActive());
			}
		}
	}
}
