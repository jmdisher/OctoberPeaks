package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.StatelessViewRadioButton;
import com.jeffdisher.october.peaks.ui.ViewRadioButton;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextField;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.WorldConfig;


/**
 * The state where we present an option to create a new one.
 */
public class ModeNewSinglePlayer implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final UiData _uiData;
	private final ISessionStarter _sessionStarter;
	private final FixedWindow _newSinglePlayerStateWindow;

	public ModeNewSinglePlayer(ModeContainer modeContainer
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
		
		_newSinglePlayerStateWindow = _buildNewSinglePlayerStateWindow(_ui, _uiData);
	}

	public ModeNewSinglePlayer becomeActive()
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
		
		return _newSinglePlayerStateWindow.render(_mouseState.cursor);
	}


	private FixedWindow _buildNewSinglePlayerStateWindow(GlUi ui, UiData uiData)
	{
		float margin = 0.6f;
		float divider = -0.2f;
		ViewRadioButton<WorldConfig.WorldGeneratorName> newWorldGeneratorNameButton = _buildWorldGeneratorRadio(ui, uiData);
		ViewRadioButton<WorldConfig.DefaultPlayerMode> newDefaultPlayerModeButton = _buildDefaultModeRadio(ui, uiData);
		ViewRadioButton<Difficulty> newDifficultyButton = _buildDifficultyRadio(ui, uiData);
		ViewTextField<String> newWorldSeedTextField = _buildSeedTextField(ui, uiData);
		ViewTextField<String> newWorldNameTextField = _buildNewWorldNameTextField(ui, uiData);
		ViewTextButton<String> createWorldButton = _buildCreateNewButton(ui);
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Create Single Player World")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			
			.add(new ViewTextLabel(ui, new Binding<>("World Generator")), new Rect(-margin, 0.5f, divider, 0.6f))
			.add(newWorldGeneratorNameButton, new Rect(divider, 0.5f, margin, 0.6f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Player Mode")), new Rect(-margin, 0.4f, divider, 0.5f))
			.add(newDefaultPlayerModeButton, new Rect(divider, 0.4f, margin, 0.5f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Difficulty")), new Rect(-margin, 0.3f, divider, 0.4f))
			.add(newDifficultyButton, new Rect(divider, 0.3f, margin, 0.4f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Seed Override")), new Rect(-margin, 0.2f, divider, 0.3f))
			.add(newWorldSeedTextField, new Rect(divider, 0.2f, margin, 0.3f))
			
			.add(new ViewTextLabel(ui, new Binding<>("World Name")), new Rect(-margin, 0.1f, divider, 0.2f))
			.add(newWorldNameTextField, new Rect(divider, 0.1f, margin, 0.2f))
			
			.add(createWorldButton, new Rect(-0.4f, 0.0f, 0.4f, 0.1f))
			.add(backButton, new Rect(-0.3f, -0.2f, 0.3f, -0.1f))
			.finish()
		;
	}

	private ViewRadioButton<WorldConfig.DefaultPlayerMode> _buildDefaultModeRadio(GlUi ui, UiData uiData)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (WorldConfig.DefaultPlayerMode type) -> type.name()
				, (WorldConfig.DefaultPlayerMode selected) -> {
					_action_clickPlayerModeRadioButton(selected);
				}
				, WorldConfig.DefaultPlayerMode.class
			)
			, uiData.defaultPlayerModeBinding
		);
	}

	private ViewRadioButton<WorldConfig.WorldGeneratorName> _buildWorldGeneratorRadio(GlUi ui, UiData uiData)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (WorldConfig.WorldGeneratorName type) -> type.name()
				, (WorldConfig.WorldGeneratorName selected) -> {
					_action_clickWorldGeneratorRadioButton(selected);
				}
				, WorldConfig.WorldGeneratorName.class
			)
			, uiData.worldGeneratorNameBinding
		);
	}

	private ViewRadioButton<Difficulty> _buildDifficultyRadio(GlUi ui, UiData uiData)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (Difficulty type) -> type.name()
				, (Difficulty selected) -> {
					_action_clickDifficultyRadioButton(selected);
				}
				, Difficulty.class
			)
			, uiData.difficultyBinding
		);
	}

	private ViewTextField<String> _buildSeedTextField(GlUi ui, UiData uiData)
	{
		return new ViewTextField<>(ui
			, uiData.newSeedBinding
			, (String text) -> text
			, () -> (uiData.typingCapture == uiData.newSeedBinding) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				_action_clickSeedTextField();
			}
		);
	}

	private ViewTextField<String> _buildNewWorldNameTextField(GlUi ui, UiData uiData)
	{
		return new ViewTextField<>(ui
			, uiData.newWorldNameBinding
			, (String value) -> (uiData.typingCapture == uiData.newWorldNameBinding) ? (value + "_") : value
			, () -> (uiData.typingCapture == uiData.newWorldNameBinding) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				_action_clickNewWorldNameTextField();
			}
		);
	}

	private ViewTextButton<String> _buildCreateNewButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Create New")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickConfirmCreateSingleWorldButton();
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

	private void _action_clickPlayerModeRadioButton(WorldConfig.DefaultPlayerMode selected)
	{
		if (_mouseState.leftClick)
		{
			_uiData.defaultPlayerModeBinding.set(selected);
		}
	}

	private void _action_clickWorldGeneratorRadioButton(WorldConfig.WorldGeneratorName selected)
	{
		if (_mouseState.leftClick)
		{
			_uiData.worldGeneratorNameBinding.set(selected);
		}
	}

	private void _action_clickDifficultyRadioButton(Difficulty selected)
	{
		if (_mouseState.leftClick)
		{
			_uiData.difficultyBinding.set(selected);
		}
	}

	private void _action_clickSeedTextField()
	{
		// We want to enable text capture for this binding.
		if (_mouseState.leftClick)
		{
			_uiData.typingCapture = _uiData.newSeedBinding;
		}
	}

	private void _action_clickNewWorldNameTextField()
	{
		// We want to enable text capture for this binding.
		if (_mouseState.leftClick)
		{
			_uiData.typingCapture = _uiData.newWorldNameBinding;
		}
	}

	private void _action_clickConfirmCreateSingleWorldButton()
	{
		if (_mouseState.leftClick)
		{
			// Make sure that the name is non-empty and not already used.
			String worldName = _uiData.newWorldNameBinding.get();
			String directoryName = "world_" + worldName;
			boolean alreadyExists = _uiData.worldListBinding.get().contains(directoryName);
			if (!alreadyExists && (worldName.length() > 0))
			{
				// This appears to be ok so reset the name.
				_uiData.newWorldNameBinding.set("");
				_uiData.typingCapture = null;
				
				WorldConfig.WorldGeneratorName worldGeneratorName = _uiData.worldGeneratorNameBinding.get();
				WorldConfig.DefaultPlayerMode defaultPlayerMode = _uiData.defaultPlayerModeBinding.get();
				Difficulty difficulty = _uiData.difficultyBinding.get();
				// The seed is a little tricky: if empty, use the default, if a number, use the number, if text, use the hash.
				Integer basicWorldGeneratorSeed = null;
				String rawSeed = _uiData.newSeedBinding.get();
				if (!rawSeed.isEmpty())
				{
					try
					{
						basicWorldGeneratorSeed = Integer.parseInt(rawSeed);
					}
					catch (NumberFormatException e)
					{
						basicWorldGeneratorSeed = rawSeed.hashCode();
					}
				}
				// Create the session and enter the connecting state.
				GameSession session = _sessionStarter.createNewWorld(directoryName
					, worldGeneratorName
					, defaultPlayerMode
					, difficulty
					, basicWorldGeneratorSeed
				);
				_modeContainer.setActive(_modeContainer.connecting.becomeActive(session));
			}
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
		// Go back to the list.
		_modeContainer.setActive(_modeContainer.listSinglePlayer.becomeActive());
	}


	public static interface ISessionStarter
	{
		GameSession createNewWorld(String directoryName
			, WorldConfig.WorldGeneratorName worldGeneratorName
			, WorldConfig.DefaultPlayerMode defaultPlayerMode
			, Difficulty difficulty
			, Integer basicWorldGeneratorSeed
		);
	}
}
