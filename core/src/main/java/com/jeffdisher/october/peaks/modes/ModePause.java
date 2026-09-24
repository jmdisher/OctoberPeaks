package com.jeffdisher.october.peaks.modes;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ViewGenericLabel;
import com.jeffdisher.october.peaks.ui.ViewTextButton;


/**
 * The mode where play is effectively "paused".  The cursor is released and buttons to change game setup will be
 * presented.
 * Note that we call the state "paused" even though servers are never "paused" (the title will reflect this
 * difference).
 */
public class ModePause implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final Consumer<GameSession> _commonPauseRender;
	private final IMouseCapture _mouseCapture;
	private final FixedWindow _pauseStateWindow;

	public GameSession currentGameSession;

	public ModePause(ModeContainer modeContainer
		, GlUi ui
		, MouseState mouseState
		, UiData uiData
		, Consumer<GameSession> commonPauseRender
		, IMouseCapture mouseCapture
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_mouseState = mouseState;
		_uiData = uiData;
		_commonPauseRender = commonPauseRender;
		_mouseCapture = mouseCapture;
		
		_pauseStateWindow = _buildPauseStateWindow(_ui, _uiData);
	}

	public ModePause becomeActive(GameSession currentGameSession)
	{
		this.currentGameSession = currentGameSession;
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.currentGameSession = null;
	}

	public IAction drawRelevantWindows()
	{
		_commonPauseRender.accept(this.currentGameSession);
		
		return _pauseStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildPauseStateWindow(GlUi ui, UiData uiData)
	{
		Function<Boolean, String> valueTransformer = (Boolean isRunningOnServer) -> isRunningOnServer ? "Connected to server" : "Paused";
		ViewTextButton<String> returnToGameButton = _buildReturnToGameButton(ui);
		ViewTextButton<String> optionsButton = _buildGameOptionsButton(ui);
		ViewTextButton<String> keyBindingsButton = _buildKeyBindingsButton(ui);
		ViewTextButton<Boolean> exitButton = _buildExitGameButton(ui, uiData);
		
		return new FixedWindow.Builder()
			.add(new ViewGenericLabel<>(ui, uiData.isRunningOnServerBinding, valueTransformer), new Rect(-0.5f, 0.4f, 0.5f, 0.5f))
			.add(returnToGameButton, new Rect(-0.3f, 0.2f, 0.3f, 0.3f))
			.add(optionsButton, new Rect(-0.3f, 0.0f, 0.3f, 0.1f))
			.add(keyBindingsButton, new Rect(-0.3f, -0.2f, 0.3f, -0.1f))
			.add(exitButton, new Rect(-0.2f, -0.5f, 0.2f, -0.4f))
			.finish()
		;
	}

	private ViewTextButton<String> _buildReturnToGameButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Return to Game")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickReturnToGameButton();
			}
		);
	}

	private ViewTextButton<String> _buildGameOptionsButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Game Options")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickOptionsButton();
			}
		);
	}

	private ViewTextButton<String> _buildKeyBindingsButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Key Bindings")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickKeyBindingsButton();
			}
		);
	}

	private ViewTextButton<Boolean> _buildExitGameButton(GlUi ui, UiData uiData)
	{
		return new ViewTextButton<>(ui, uiData.isRunningOnServerBinding
			, (Boolean isOnServer) -> isOnServer ? "Disconnect" : "Exit"
			, (ViewTextButton<Boolean> button, Boolean isOnServer) -> {
				_action_clickExitGameButton();
			}
		);
	}

	private void _action_clickReturnToGameButton()
	{
		if (_mouseState.leftClick)
		{
			this.currentGameSession.client.resumeGame();
			_modeContainer.setActive(_modeContainer.play.becomeActive(this.currentGameSession));
			_mouseCapture.enableMouseCapture();
		}
	}

	private void _action_clickOptionsButton()
	{
		if (_mouseState.leftClick)
		{
			_modeContainer.setActive(_modeContainer.options.becomeActive(this.currentGameSession));
		}
	}

	private void _action_clickKeyBindingsButton()
	{
		if (_mouseState.leftClick)
		{
			_modeContainer.setActive(_modeContainer.keyBindings.becomeActive(this.currentGameSession));
			_uiData.currentlyChangingControl.set(null);
		}
	}

	private void _action_clickExitGameButton()
	{
		if (_mouseState.leftClick)
		{
			this.currentGameSession.shutdown();
			_modeContainer.setActive(_modeContainer.start.becomeActive());
		}
	}


	public static interface IMouseCapture
	{
		void enableMouseCapture();
	}
}
