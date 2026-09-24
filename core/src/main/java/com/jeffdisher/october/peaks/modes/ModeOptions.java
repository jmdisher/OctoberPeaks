package com.jeffdisher.october.peaks.modes;

import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ViewControlPlusMinus;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextField;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The UI state under the PAUSE screen where we enter an options menu to view/change settings.
 * If there is a _currentGameSession, it will be shown in the background.
 */
public class ModeOptions implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final Consumer<GameSession> _commonPauseRender;
	private final FixedWindow _optionsStateWindow;

	public GameSession currentGameSession;

	public ModeOptions(ModeContainer modeContainer
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
		
		_optionsStateWindow = _buildOptionsStateWindow(_ui, _uiData);
	}

	public ModeOptions becomeActive(GameSession currentGameSession)
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
		if (null != this.currentGameSession)
		{
			_commonPauseRender.accept(this.currentGameSession);
		}
		
		return _optionsStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildOptionsStateWindow(GlUi ui, UiData uiData)
	{
		ViewTextButton<Boolean> fullScreenButton = _buildToggleFullScreenButton(ui, uiData);
		ViewControlPlusMinus<Integer> viewDistanceControl = _buildViewDistanceSlider(ui, uiData);
		ViewControlPlusMinus<Float> brightnessControl = _buildBrightnessSlider(ui, uiData);
		ViewTextField<String> clientNameTextField = _buildClientNameTextField(ui, uiData);
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Game Options")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Toggle Display")), new Rect(-0.6f, 0.5f, -0.2f, 0.6f))
			.add(fullScreenButton, new Rect(-0.2f, 0.5f, 0.6f, 0.6f))
			
			.add(new ViewTextLabel(ui, new Binding<>("View Distance")), new Rect(-0.6f, 0.4f, -0.2f, 0.5f))
			.add(viewDistanceControl, new Rect(-0.2f, 0.4f, 0.6f, 0.5f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Scene Brightness")), new Rect(-0.6f, 0.3f, -0.2f, 0.4f))
			.add(brightnessControl, new Rect(-0.2f, 0.3f, 0.6f, 0.4f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Multiplayer Name")), new Rect(-0.6f, 0.2f, -0.2f, 0.3f))
			.add(clientNameTextField, new Rect(-0.2f, 0.2f, 0.6f, 0.3f))
			
			.add(backButton, new Rect(-0.3f, -0.1f, 0.3f, 0.0f))
			.finish()
		;
	}

	private ViewTextButton<Boolean> _buildToggleFullScreenButton(GlUi ui, UiData uiData)
	{
		return new ViewTextButton<>(ui, uiData.mutablePreferences.isFullScreen
			, (Boolean isFullScreen) -> isFullScreen ? "Change to Windowed" : "Change to Full Screen"
			, (ViewTextButton<Boolean> button, Boolean isFullScreen) -> {
				_action_clickFullScreenToggle(isFullScreen);
			}
		);
	}

	private ViewControlPlusMinus<Integer> _buildViewDistanceSlider(GlUi ui, UiData uiData)
	{
		return new ViewControlPlusMinus<>(ui, uiData.mutablePreferences.preferredViewDistance
			, (Integer distance) -> distance + " cuboids"
			, (boolean plus) -> {
				_action_clickViewDistanceSlider(plus);
			}
		);
	}

	private ViewControlPlusMinus<Float> _buildBrightnessSlider(GlUi ui, UiData uiData)
	{
		return new ViewControlPlusMinus<>(ui, uiData.mutablePreferences.screenBrightness
			, (Float brightness) -> String.format("%.1fx", brightness)
			, (boolean plus) -> {
				_action_clickBrightnessSlider(plus);
			}
		);
	}

	private ViewTextField<String> _buildClientNameTextField(GlUi ui, UiData uiData)
	{
		return new ViewTextField<>(ui, uiData.mutablePreferences.clientName
			, (String value) -> (uiData.typingCapture == uiData.mutablePreferences.clientName) ? (value + "_") : value
			, () -> (uiData.typingCapture == uiData.mutablePreferences.clientName) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				_action_clickClientNameTextField();
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

	private void _action_clickFullScreenToggle(boolean isFullScreen)
	{
		if (_mouseState.leftClick)
		{
			// We will toggle the full screen and update the binding data.
			boolean newFullScreen = !isFullScreen;
			if (newFullScreen)
			{
				// We will just use the full screen of the current display mode.
				Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
			}
			else
			{
				// For now, we will always use this same default window size.
				Gdx.graphics.setWindowedMode(1280, 960);
			}
			_uiData.mutablePreferences.isFullScreen.set(newFullScreen);
		}
	}

	private void _action_clickViewDistanceSlider(boolean shouldIncrease)
	{
		if (_mouseState.leftClick)
		{
			// TODO:  When we persist preferences, put this there whether or not in game.
			if (null != this.currentGameSession)
			{
				// We try changing this in the client and it will return the updated value.
				int oldDistance = _uiData.mutablePreferences.preferredViewDistance.get();
				int newDistance = oldDistance +
					(shouldIncrease ? 1 : -1)
				;
				int finalValue = this.currentGameSession.client.trySetViewDistance(newDistance);
				if (finalValue != oldDistance)
				{
					// If this change did anything, update the UI and save changes.
					_uiData.mutablePreferences.preferredViewDistance.set(finalValue);
					_uiData.mutablePreferences.saveToDisk();
				}
			}
		}
	}

	private void _action_clickBrightnessSlider(boolean shouldIncrease)
	{
		if (_mouseState.leftClick)
		{
			// We just want to increment this by 0.1 increments between 1.0 and 2.0.
			int current = (int)(10.0f * _uiData.mutablePreferences.screenBrightness.get());
			int next;
			if (shouldIncrease)
			{
				next = Math.min(20, current + 1);
			}
			else
			{
				next = Math.max(10, current - 1);
			}
			float updated = ((float) next) / 10.0f;
			_uiData.mutablePreferences.screenBrightness.set(updated);
		}
	}

	private void _action_clickClientNameTextField()
	{
		if (_mouseState.leftClick)
		{
			// We want to enable text capture for this binding.
			_uiData.typingCapture = _uiData.mutablePreferences.clientName;
		}
	}

	private void _action_clickBackButton()
	{
		if (_mouseState.leftClick)
		{
			// Write-back preferences.
			_uiData.mutablePreferences.saveToDisk();
			// Options depends on whether is a game playing.
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
