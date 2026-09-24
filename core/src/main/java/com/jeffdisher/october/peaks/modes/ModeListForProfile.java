package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.profiling.ProfilingModes;
import com.jeffdisher.october.peaks.profiling.ProfilingSession;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * A special state which is like the other LIST_* modes but the listed options are just the hard-coded profile
 * options for use with a local profile during performance investigations.
 * NOTE:  This state is only reachable if the "OCTOBER_PEAKS_PROFILE" environment variable is set.
 */
public class ModeListForProfile implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final ISessionStarter _sessionStarter;
	private final FixedWindow _listProfileRunsStateWindow;

	public ModeListForProfile(ModeContainer modeContainer
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
		
		_listProfileRunsStateWindow = _buildListProfileRunsStateWindow(_ui, _uiData, ProfilingModes.ALL_MODES);
	}

	public ModeListForProfile becomeActive()
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
		
		return _listProfileRunsStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildListProfileRunsStateWindow(GlUi ui, UiData uiData, ProfilingModes[] profilingModes)
	{
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		FixedWindow.Builder builder = new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Profile Runs")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
		;
		float topY = 0.6f;
		for (ProfilingModes profilingMode : profilingModes)
		{
			ViewTextButton<ProfilingModes> profileButton = new ViewTextButton<>(ui, new Binding<>(profilingMode)
				, (ProfilingModes mode) -> mode.name
				, (ViewTextButton<ProfilingModes> button, ProfilingModes mode) -> {
					_action_clickProfileRunButton(mode);
				}
			);
			builder.add(profileButton, new Rect(-0.4f, topY - 0.1f, 0.4f, topY));
			topY -= 0.1f;
		}
		return builder
			.add(backButton, new Rect(-0.2f, -0.7f, 0.2f, -0.6f))
			.finish()
		;
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

	private void _action_clickProfileRunButton(ProfilingModes mode)
	{
		if (_mouseState.leftClick)
		{
			// This just changes state.
			ProfilingSession session = _sessionStarter.startSession(mode);
			_modeContainer.setActive(_modeContainer.profile.becomeActive(session));
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
		ProfilingSession startSession(ProfilingModes mode);
	}
}
