package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ViewTextField;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The mode where the game is running but the UI is in a mode where it accepts generic typing into a box to send to the
 * server (or run as command).
 * Sending the message or hitting escape will switch back to normal play mode (only sending clears the message buffer).
 */
public class ModeChat implements IGameMode
{
	public static final int MAX_MESSAGE_LENGTH = 64;

	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final InputCapture _inputCapture;
	private final Binding<String> _messageBinding;
	private final FixedWindow _chatStateWindow;

	public GameSession currentGameSession;

	public ModeChat(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		
		_messageBinding = new Binding<>("");
		_chatStateWindow = _buildChatWindow(_ui);
	}

	public ModeChat becomeActive(GameSession currentGameSession)
	{
		this.currentGameSession = currentGameSession;
		_inputCapture.captureState.shouldCaptureMouse(false);
		_inputCapture.textCapture = _messageBinding;
		_inputCapture.textLengthLimit = MAX_MESSAGE_LENGTH;
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
		this.currentGameSession.scene.renderCommon();
		this.currentGameSession.eyeEffect.drawEyeEffect();
		
		// Draw whatever is common to states where we draw interactive buttons on top.
		_ui.enterUiRenderMode();
		
		_modeContainer.play.drawPassiveOverlayWindows(this.currentGameSession);
		
		return _chatStateWindow.render(_inputCapture.glCursorLocation);
	}

	@Override
	public void handleUserEvents()
	{
		// Capture the game session in case we switch mode.
		GameSession gameSession = this.currentGameSession;
		
		// The only special thing we hook here is the enter to send the message.
		if (_inputCapture.didReleaseEnter)
		{
			String message = _messageBinding.get();
			if (message.length() > 0)
			{
				// TODO:  Detect if this is a command we should run locally, instead.
				gameSession.client.sendChatMessage(message);
				_messageBinding.set("");
			}
			_returnToGame();
		}
		
		// While in the chat screen, the game is still active (whether local or server).
		_modeContainer.play.commonIdleWhileRunning(gameSession);
	}


	private FixedWindow _buildChatWindow(GlUi ui)
	{
		ViewTextField<String> messageTextField = _buildMessageTextField(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Chat")), new Rect(-1.0f, -0.95f, -0.9f, -0.85f))
			.add(messageTextField, new Rect(-0.85f, -0.95f, 0.95f, -0.85f))
			.finish()
		;
	}

	private ViewTextField<String> _buildMessageTextField(GlUi ui)
	{
		return new ViewTextField<>(ui
			, _messageBinding
			, (String text) -> text
			, () -> ui.pixelGreen
			, () -> {}
		);
	}

	private void _returnToGame()
	{
		this.currentGameSession.client.resumeGame();
		_modeContainer.setActive(_modeContainer.play.becomeActive(this.currentGameSession));
	}
}
