package com.jeffdisher.october.peaks.modes;

import java.util.function.BooleanSupplier;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.LocalStorageManager;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.PaginatedListView;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.StatelessHBox;
import com.jeffdisher.october.peaks.ui.StatelessViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The state where we show a list of existing single-player worlds and present an option to create a new one.
 */
public class ModeListSinglePlayer implements IGameMode
{
	public static final float SINGLE_PLAYER_WORLD_ROW_WIDTH = 0.8f;
	public static final float SINGLE_PLAYER_WORLD_ROW_HEIGHT = 0.1f;

	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final ISessionStarter _sessionStarter;
	private final FixedWindow _listSinglePlayerStateWindow;

	public ModeListSinglePlayer(ModeContainer modeContainer
		, GlUi ui
		, MouseState mouseState
		, UiData uiData
		, ISessionStarter sessionStarter
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_mouseState = mouseState;
		_uiData = uiData;
		_sessionStarter = sessionStarter;
		
		BooleanSupplier isLeftClick = () -> _mouseState.leftClick;
		_listSinglePlayerStateWindow = _buildListSinglePlayerStateWindow(_ui
			, uiData
			, isLeftClick
			, LocalStorageManager.WORLD_DIRECTORY_PREFIX
		);
	}

	public ModeListSinglePlayer becomeActive()
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
		_goBack();
	}

	public IAction drawRelevantWindows()
	{
		_ui.enterUiRenderMode();
		
		return _listSinglePlayerStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildListSinglePlayerStateWindow(GlUi ui
		, UiData uiData
		, BooleanSupplier shouldChangePage
		, String worldDirectoryPrefix
	)
	{
		float halfListWidth = SINGLE_PLAYER_WORLD_ROW_WIDTH / 2.0f;
		PaginatedListView<String> worldListView = _buildSinglePlayerWorldListView(ui, uiData, shouldChangePage, worldDirectoryPrefix);
		ViewTextButton<String> enterCreateSingleState = _buildCreateNewWorldButton(ui);
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Single Player Worlds")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(worldListView, new Rect(-halfListWidth, -0.6f, halfListWidth, 0.6f))
			.add(enterCreateSingleState, new Rect(-0.2f, -0.7f, 0.2f, -0.6f))
			.add(backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f))
			.finish()
		;
	}

	private PaginatedListView<String> _buildSinglePlayerWorldListView(GlUi ui
		, UiData uiData
		, BooleanSupplier shouldChangePage
		, String worldDirectoryPrefix
	)
	{
		StatelessViewTextButton<String> enterWorldButton = new StatelessViewTextButton<>(ui
			, (String text) -> text.substring(worldDirectoryPrefix.length())
			, (String directoryName) -> {
				_action_clickEnterSingleWorldButton(directoryName);
			}
		);
		StatelessViewTextButton<String> deleteWorldButton = new StatelessViewTextButton<>(ui
			, (String text) -> "X"
			, (String directoryName) -> {
				_action_clickDeleteSingleWorldButton(directoryName);
			}
		);
		return new PaginatedListView<>(ui
			, uiData.worldListBinding
			, shouldChangePage
			, new StatelessHBox<>(enterWorldButton, SINGLE_PLAYER_WORLD_ROW_WIDTH - SINGLE_PLAYER_WORLD_ROW_HEIGHT, deleteWorldButton)
			, SINGLE_PLAYER_WORLD_ROW_HEIGHT
		);
	}

	private ViewTextButton<String> _buildCreateNewWorldButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Create New World")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickCreateSingleWorldButton();
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

	private void _action_clickEnterSingleWorldButton(String directoryName)
	{
		if (_mouseState.leftClick)
		{
			// Load the session and enter the connecting state.
			GameSession session = _sessionStarter.loadExistingWorld(directoryName);
			_modeContainer.setActive(_modeContainer.connecting.becomeActive(session));
		}
	}

	private void _action_clickDeleteSingleWorldButton(String directoryName)
	{
		if (_mouseState.leftClick)
		{
			// We want to enter the confirmation state.
			_modeContainer.setActive(_modeContainer.confirmDeleteSinglePlayer);
			
			// We also need to put this chosen directory in the binding.
			_uiData.selectedWorldNameForDelete.set(directoryName);
		}
	}

	private void _action_clickCreateSingleWorldButton()
	{
		if (_mouseState.leftClick)
		{
			// Enter the single-player creation window.
			_modeContainer.setActive(_modeContainer.newSinglePlayer.becomeActive());
			
			// Select the default text field.
			_uiData.typingCapture = _uiData.newWorldNameBinding;
		}
	}

	private void _action_clickBackButton()
	{
		if (_mouseState.leftClick)
		{
			_goBack();
		}
	}

	private void _goBack()
	{
		// We just want to go back to start.
		_modeContainer.setActive(_modeContainer.start.becomeActive());
	}


	public static interface ISessionStarter
	{
		GameSession loadExistingWorld(String directoryName);
	}
}
