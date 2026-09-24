package com.jeffdisher.october.peaks.modes;

import java.net.InetSocketAddress;
import java.util.function.BooleanSupplier;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.persistence.MutableServerList;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.PaginatedListView;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ServerRecordTransformer;
import com.jeffdisher.october.peaks.ui.StatelessHBox;
import com.jeffdisher.october.peaks.ui.StatelessMultiLineButton;
import com.jeffdisher.october.peaks.ui.StatelessViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The state where we show a list of known multi-player servers.
 */
public class ModeListMultiPlayer implements IGameMode
{
	public static final float MULTI_PLAYER_SERVER_ROW_WIDTH = 0.8f;
	public static final float MULTI_PLAYER_SERVER_ROW_HEIGHT = 0.2f;

	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final ISessionStarter _sessionStarter;
	private final FixedWindow _listMultiPlayerStateWindow;

	public ModeListMultiPlayer(ModeContainer modeContainer
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
		_listMultiPlayerStateWindow = _buildListMultiPlayerStateWindow(_ui, _uiData, isLeftClick);
	}

	public ModeListMultiPlayer becomeActive()
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
		
		return _listMultiPlayerStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildListMultiPlayerStateWindow(GlUi ui
		, UiData uiData
		, BooleanSupplier shouldChangePage
	)
	{
		float halfListWidth = MULTI_PLAYER_SERVER_ROW_WIDTH / 2.0f;
		PaginatedListView<MutableServerList.ServerRecord> serverListView = _buildServerListView(ui, uiData, shouldChangePage);
		ViewTextButton<String> enterAddNewServerButton = _buildAddNewServerButton(ui);
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Multi-Player servers")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(serverListView, new Rect(-halfListWidth, -0.6f, halfListWidth, 0.6f))
			.add(enterAddNewServerButton, new Rect(-0.2f, -0.7f, 0.2f, -0.6f))
			.add(backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f))
			.finish()
		;
	}

	private PaginatedListView<MutableServerList.ServerRecord> _buildServerListView(GlUi ui
		, UiData uiData
		, BooleanSupplier shouldChangePage
	)
	{
		StatelessMultiLineButton<MutableServerList.ServerRecord> connectToServerLine = new StatelessMultiLineButton<>(ui
			, new ServerRecordTransformer(ui)
			, (MutableServerList.ServerRecord server) -> {
				_action_clickJoinMultiWorldButton(server);
			}
		);
		StatelessViewTextButton<MutableServerList.ServerRecord> deleteServerButton = new StatelessViewTextButton<>(ui
			, (MutableServerList.ServerRecord ignored) -> "X"
			, (MutableServerList.ServerRecord server) -> {
				_action_clickDeleteMultiWorldButton(server);
			}
		);
		return new PaginatedListView<>(ui
			, uiData.serverList.servers
			, shouldChangePage
			, new StatelessHBox<>(connectToServerLine, MULTI_PLAYER_SERVER_ROW_WIDTH - MULTI_PLAYER_SERVER_ROW_HEIGHT, deleteServerButton)
			, MULTI_PLAYER_SERVER_ROW_HEIGHT
		);
	}

	private ViewTextButton<String> _buildAddNewServerButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Add New Server")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickAddNewServerButton();
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

	private void _action_clickJoinMultiWorldButton(MutableServerList.ServerRecord server)
	{
		if (_mouseState.leftClick)
		{
			// Note that "_connectToServer" will try to connect to server and change state, but only if successful.
			String clientName = _uiData.mutablePreferences.clientName.get();
			int startingViewDistance = _uiData.mutablePreferences.preferredViewDistance.get();
			GameSession session = _sessionStarter.connectToServer(clientName, startingViewDistance, server.address);
			if (null != session)
			{
				// Note that the connection can fail, inline, so we only change state if we get a non-null session.
				_modeContainer.setActive(_modeContainer.connecting.becomeActive(session));
			}
		}
	}

	private void _action_clickDeleteMultiWorldButton(MutableServerList.ServerRecord server)
	{
		if (_mouseState.leftClick)
		{
			_uiData.serverList.removeServerFromList(server);
		}
	}

	private void _action_clickAddNewServerButton()
	{
		if (_mouseState.leftClick)
		{
			// Enter the single-player creation window.
			_modeContainer.setActive(_modeContainer.newMultiPlayer.becomeActive());
			
			// Select the default text field.
			_uiData.typingCapture = _uiData.newServerAddressBinding;
			
			// Clear any stale state from last time.
			_uiData.currentlyTestingServerBinding.set(null);
		}
	}

	private void _action_clickBackButton()
	{
		if (_mouseState.leftClick)
		{
			// We just want to go back.
			_modeContainer.setActive(_modeContainer.start.becomeActive());
		}
	}


	public static interface ISessionStarter
	{
		GameSession connectToServer(String clientName, int startingViewDistance, InetSocketAddress serverAddress);
	}
}
