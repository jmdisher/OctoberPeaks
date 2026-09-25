package com.jeffdisher.october.peaks.modes;

import java.util.function.Consumer;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.MouseState;
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
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final Consumer<GameSession> _commonPauseRender;
	private final FixedWindow _keyBindingsStateWindow;

	public GameSession currentGameSession;

	public ModeKeyBindings(ModeContainer modeContainer
		, GlUi ui
		, MouseState mouseState
		, UiData uiData
		, Consumer<GameSession> commonPauseRender
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_mouseState = mouseState;
		_uiData = uiData;
		_commonPauseRender = commonPauseRender;
		
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

	public IAction drawRelevantWindows()
	{
		if (null != this.currentGameSession)
		{
			_commonPauseRender.accept(this.currentGameSession);
		}
		
		return _keyBindingsStateWindow.render(_mouseState.cursor);
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
		if (_mouseState.leftClick)
		{
			_uiData.currentlyChangingControl.set(selectedControl);
		}
	}

	private void _action_clickBackButton()
	{
		if (_mouseState.leftClick)
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
