package com.jeffdisher.october.peaks.modes;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.UiIdioms;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * A state used to display a fatal error message before exiting.
 */
public class ModeError implements IGameMode
{
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final FixedWindow _errorStateWindow;

	public String[] errorPayload;

	public ModeError(ModeContainer modeContainer
		, GlUi ui
		, MouseState mouseState
		, UiData uiData
	)
	{
		_ui = ui;
		_mouseState = mouseState;
		_uiData = uiData;
		
		_errorStateWindow = _buildErrorStateWindow(_ui, _uiData);
	}

	public ModeError becomeActive(String[] errorPayload)
	{
		this.errorPayload = errorPayload;
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		this.errorPayload = null;
	}

	@Override
	public void handleEscape()
	{
		// There is no transition from this state.
	}

	public IAction drawRelevantWindows()
	{
		// We will treat dumping the payload as a special case and just write it to the screen instead of making a binding to stitch it into the rest of the error window.
		float topY = 0.6f;
		for (String elt : this.errorPayload)
		{
			float bottomY = topY - UiIdioms.GENERAL_TEXT_HEIGHT;
			UiIdioms.drawTextLeft(_ui, new Rect(-0.8f, bottomY, 0.8f, topY), elt);
			topY = bottomY;
			if (topY < -0.6f)
			{
				break;
			}
		}
		
		// Now, just draw the rest of the fixed window to get the buttons we want.
		return _errorStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildErrorStateWindow(GlUi ui, UiData uiData)
	{
		ViewTextButton<String> copyToClipboardButton = _buildCopyToCliboardButton(ui);
		ViewTextButton<String> quitButton = _buildQuitButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Fatal Error")), new Rect(-0.5f, 0.6f, 0.5f, 0.7f))
			.add(copyToClipboardButton, new Rect(-0.6f, -0.8f, -0.1f, -0.7f))
			.add(quitButton, new Rect(0.1f, -0.8f, 0.6f, -0.7f))
			.finish()
		;
	}

	private ViewTextButton<String> _buildCopyToCliboardButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Copy to Clipboard")
			, (String value) -> value
			, (ViewTextButton<String> button, String ignored) -> {
				_action_clickCopyToClipboardButton();
			}
		);
	}

	private ViewTextButton<String> _buildQuitButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Quit")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickQuitButton();
			}
		);
	}

	private void _action_clickCopyToClipboardButton()
	{
		if (_mouseState.leftClick)
		{
			// Just copy the payload to the clipboard.
			StringBuilder builder = new StringBuilder();
			for (String elt : this.errorPayload)
			{
				builder.append(elt);
				builder.append('\n');
			}
			Gdx.app.getClipboard().setContents(builder.toString());
		}
	}

	private void _action_clickQuitButton()
	{
		if (_mouseState.leftClick)
		{
			// From here, we quit directly, as this is top-level.
			// (in the error state, we won't wait for the app to quit).
			System.exit(1);
		}
	}
}
