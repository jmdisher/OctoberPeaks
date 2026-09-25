package com.jeffdisher.october.peaks.modes;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.peaks.LocalStorageManager;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ViewStaticImage;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The game starts here when invoked without any arguments.  It presents a starting menu to create/join games.
 */
public class ModeStart implements IGameMode
{
	public static final String OCTOBER_PEAKS_PROFILE = "OCTOBER_PEAKS_PROFILE";

	private final ModeContainer _modeContainer;
	private final LocalStorageManager _localStorageManager;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final FixedWindow _startWindow;

	public ModeStart(ModeContainer modeContainer
		, LocalStorageManager localStorageManager
		, GlUi ui
		, MouseState mouseState
		, UiData uiData
	)
	{
		_modeContainer = modeContainer;
		_localStorageManager = localStorageManager;
		_ui = ui;
		_mouseState = mouseState;
		_uiData = uiData;
		
		_startWindow = _buildStartWindow(_ui, uiData);
	}

	public ModeStart becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}

	@Override
	public void handleEscape()
	{
	}

	public IAction drawRelevantWindows()
	{
		_ui.enterUiRenderMode();
		
		return _startWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildStartWindow(GlUi ui, UiData uiData)
	{
		ViewTextButton<String> singlePlayerButton = _buildSinglePlayerButton(ui);
		ViewTextButton<String> multiPlayerButton = _buildMultiPlayerButton(ui);
		ViewTextButton<String> optionsButton = _buildGameOptionsButton(ui);
		ViewTextButton<String> keyBindingsButton = _buildKeyBindingsButton(ui);
		ViewTextButton<String> profileRunsButton;
		if (null != System.getenv(OCTOBER_PEAKS_PROFILE))
		{
			profileRunsButton = new ViewTextButton<>(ui, new Binding<>("Profile Runs")
				, (String text) -> text
				, (ViewTextButton<String> button, String text) -> {
					_action_clickProfileRunsButton();
				}
			);
		}
		else
		{
			profileRunsButton = null;
		}
		ViewTextButton<String> quitButton = _buildQuitButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("October Peaks")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(new ViewStaticImage(ui, ui.logoTexture), new Rect(-0.2f, 0.3f, 0.2f, 0.7f))
			.add(singlePlayerButton, new Rect(-0.4f, 0.1f, 0.4f, 0.2f))
			.add(multiPlayerButton, new Rect(-0.4f, 0.0f, 0.4f, 0.1f))
			.add(optionsButton, new Rect(-0.4f, -0.1f, 0.4f, 0.0f))
			.add(keyBindingsButton, new Rect(-0.4f, -0.2f, 0.4f, -0.1f))
			.add(profileRunsButton, new Rect(-0.4f, -0.3f, 0.4f, -0.2f))
			.add(quitButton, new Rect(-0.2f, -0.7f, 0.2f, -0.6f))
			.finish()
		;
	}

	private ViewTextButton<String> _buildSinglePlayerButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Single Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickSinglePlayerButton();
			}
		);
	}

	private ViewTextButton<String> _buildMultiPlayerButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Multi-Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickMultiPlayerButton();
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

	private ViewTextButton<String> _buildQuitButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Quit")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickQuitButton();
			}
		);
	}

	private void _action_clickSinglePlayerButton()
	{
		if (_mouseState.leftClick)
		{
			// Enter the single-player list.
			_modeContainer.setActive(_modeContainer.listSinglePlayer.becomeActive());
			
			// Update the world name list since we are entering that state.
			_localStorageManager.rebuildSinglePlayerListBinding();
		}
	}

	private void _action_clickMultiPlayerButton()
	{
		if (_mouseState.leftClick)
		{
			// Enter the single-player list.
			_modeContainer.setActive(_modeContainer.listMultiPlayer.becomeActive());
			
			// Request that this list be validated.
			_uiData.serverList.pollServers();
		}
	}

	private void _action_clickOptionsButton()
	{
		if (_mouseState.leftClick)
		{
			_modeContainer.setActive(_modeContainer.options.becomeActive(null));
		}
	}

	private void _action_clickKeyBindingsButton()
	{
		if (_mouseState.leftClick)
		{
			_modeContainer.setActive(_modeContainer.keyBindings.becomeActive(null));
			_uiData.currentlyChangingControl.set(null);
		}
	}

	private void _action_clickQuitButton()
	{
		if (_mouseState.leftClick)
		{
			// From here, we quit directly, as this is top-level.
			Gdx.app.exit();
		}
	}

	private void _action_clickProfileRunsButton()
	{
		if (_mouseState.leftClick)
		{
			// This just changes state.
			_modeContainer.setActive(_modeContainer.listForProfile.becomeActive());
		}
	}
}
