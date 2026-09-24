package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.LocalStorageManager;
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
 * The state where we just allow confirmation to delete a single-player world.
 */
public class ModeConfirmDeleteSinglePlayer implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final LocalStorageManager _localStorageManager;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final FixedWindow _confirmDeleteSinglePlayerStateWindow;

	public ModeConfirmDeleteSinglePlayer(ModeContainer modeContainer
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
		
		_confirmDeleteSinglePlayerStateWindow = _buildConfirmDeleteSinglePlayerStateWindow(_ui
			, _uiData
			, LocalStorageManager.WORLD_DIRECTORY_PREFIX
		);
	}

	public ModeConfirmDeleteSinglePlayer becomeActive()
	{
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}

	public IAction drawRelevantWindows()
	{
		_ui.enterUiRenderMode();
		
		return _confirmDeleteSinglePlayerStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildConfirmDeleteSinglePlayerStateWindow(GlUi ui
		, UiData uiData
		, String worldDirectoryPrefix
	)
	{
		ViewTextButton<String> confirmDeleteButton = _buildConfirmDeleteButton(ui, uiData, worldDirectoryPrefix);
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Confirm Delete")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(confirmDeleteButton, new Rect(-0.6f, -0.1f, 0.6f, 0.0f))
			.add(backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f))
			.finish()
		;
	}

	private ViewTextButton<String> _buildConfirmDeleteButton(GlUi ui, UiData uiData, String worldDirectoryPrefix)
	{
		return new ViewTextButton<>(ui, uiData.selectedWorldNameForDelete
			, (String text) -> "Confirm delete world \"" + text.substring(worldDirectoryPrefix.length()) + "\" (cannot be undone)"
			, (ViewTextButton<String> button, String text) -> {
				_action_clickConfirmDeleteButton();
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

	private void _action_clickConfirmDeleteButton()
	{
		if (_mouseState.leftClick)
		{
			// Verify state transition.
			_modeContainer.setActive(_modeContainer.listSinglePlayer.becomeActive());
			
			// Delete the directory, then return to the listing.
			_localStorageManager.deleteWorldAndUpdateList(_uiData.selectedWorldNameForDelete.get());
			
			_uiData.selectedWorldNameForDelete.set(null);
		}
	}

	private void _action_clickBackButton()
	{
		if (_mouseState.leftClick)
		{
			// Go back to the list.
			_modeContainer.setActive(_modeContainer.listSinglePlayer.becomeActive());
		}
	}
}
