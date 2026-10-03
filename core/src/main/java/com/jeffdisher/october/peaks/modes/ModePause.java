package com.jeffdisher.october.peaks.modes;

import java.util.function.Function;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
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
	private final InputCapture _inputCapture;
	private final UiData _uiData;
	private final FixedWindow _pauseStateWindow;

	public GameSession currentGameSession;

	public ModePause(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, UiData uiData
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		_uiData = uiData;
		
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

	@Override
	public void handleEscape()
	{
		_returnToGame();
	}

	@Override
	public IAction drawRelevantWindows()
	{
		_modeContainer.pause.drawCommonPauseBackground(this.currentGameSession);
		
		return _pauseStateWindow.render(_inputCapture.glCursorLocation);
	}

	public void drawCommonPauseBackground(GameSession currentGameSession)
	{
		if (null != currentGameSession)
		{
			currentGameSession.scene.renderCommon();
			currentGameSession.eyeEffect.drawEyeEffect();
		}
		
		// Draw whatever is common to states where we draw interactive buttons on top.
		_ui.enterUiRenderMode();
		
		_modeContainer.play.drawPassiveOverlayWindows(currentGameSession);
		
		// Draw the overlay to dim the window.
		_ui.drawWholeTextureRect(_ui.pixelDarkGreyAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
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
		if (_inputCapture.mouseReleased0)
		{
			_returnToGame();
		}
	}

	private void _action_clickOptionsButton()
	{
		if (_inputCapture.mouseReleased0)
		{
			_modeContainer.setActive(_modeContainer.options.becomeActive(this.currentGameSession));
		}
	}

	private void _action_clickKeyBindingsButton()
	{
		if (_inputCapture.mouseReleased0)
		{
			_modeContainer.setActive(_modeContainer.keyBindings.becomeActive(this.currentGameSession));
			_uiData.currentlyChangingControl.set(null);
		}
	}

	private void _action_clickExitGameButton()
	{
		if (_inputCapture.mouseReleased0)
		{
			this.currentGameSession.shutdown();
			_modeContainer.setActive(_modeContainer.start.becomeActive());
		}
	}

	private void _returnToGame()
	{
		this.currentGameSession.client.resumeGame();
		_modeContainer.setActive(_modeContainer.play.becomeActive(this.currentGameSession));
		_inputCapture.captureState.shouldCaptureMouse(true);
	}
}
