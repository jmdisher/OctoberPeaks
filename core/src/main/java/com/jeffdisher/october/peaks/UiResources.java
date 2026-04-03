package com.jeffdisher.october.peaks;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.persistence.MutableServerList;
import com.jeffdisher.october.peaks.profiling.ProfilingModes;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.PaginatedListView;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ServerRecordTransformer;
import com.jeffdisher.october.peaks.ui.StatelessHBox;
import com.jeffdisher.october.peaks.ui.StatelessMultiLineButton;
import com.jeffdisher.october.peaks.ui.StatelessViewRadioButton;
import com.jeffdisher.october.peaks.ui.StatelessViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewControlPlusMinus;
import com.jeffdisher.october.peaks.ui.ViewGenericLabel;
import com.jeffdisher.october.peaks.ui.ViewKeyControlSelector;
import com.jeffdisher.october.peaks.ui.ViewOfStateless;
import com.jeffdisher.october.peaks.ui.ViewRadioButton;
import com.jeffdisher.october.peaks.ui.ViewStaticImage;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextField;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.WorldConfig;


/**
 * This class contains the hard-coded UI resources used in UiStateManager.  In the future, this kind of data could be
 * pulled into resource files with some kind of loader, but that isn't required and should probably be made more
 * flexible, first.
 */
public class UiResources
{
	public static final float SINGLE_PLAYER_WORLD_ROW_WIDTH = 0.8f;
	public static final float SINGLE_PLAYER_WORLD_ROW_HEIGHT = 0.1f;
	public static final float MULTI_PLAYER_SERVER_ROW_WIDTH = 0.8f;
	public static final float MULTI_PLAYER_SERVER_ROW_HEIGHT = 0.2f;
	public static final String OCTOBER_PEAKS_PROFILE = "OCTOBER_PEAKS_PROFILE";

	public static FixedWindow buildStartWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		ViewTextButton<String> singlePlayerButton = UiResources._buildSinglePlayerButton(ui, actions);
		ViewTextButton<String> multiPlayerButton = UiResources._buildMultiPlayerButton(ui, actions);
		ViewTextButton<String> optionsButton = UiResources._buildGameOptionsButton(ui, actions);
		ViewTextButton<String> keyBindingsButton = UiResources._buildKeyBindingsButton(ui, actions);
		ViewTextButton<String> profileRunsButton;
		if (null != System.getenv(OCTOBER_PEAKS_PROFILE))
		{
			profileRunsButton = new ViewTextButton<>(ui, new Binding<>("Profile Runs")
				, (String text) -> text
				, (ViewTextButton<String> button, String text) -> {
					actions.action_clickProfileRunsButton();
				}
			);
		}
		else
		{
			profileRunsButton = null;
		}
		ViewTextButton<String> quitButton = UiResources._buildQuitButton(ui, actions);
		
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

	public static FixedWindow buildListSinglePlayerStateWindow(GlUi ui
		, UiStateManager actions
		, UiData uiData
		, BooleanSupplier shouldChangePage
		, String worldDirectoryPrefix
	)
	{
		float halfListWidth = UiResources.SINGLE_PLAYER_WORLD_ROW_WIDTH / 2.0f;
		PaginatedListView<String> worldListView = UiResources._buildSinglePlayerWorldListView(ui, actions, uiData, shouldChangePage, worldDirectoryPrefix);
		ViewTextButton<String> enterCreateSingleState = UiResources._buildCreateNewWorldButton(ui, actions);
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Single Player Worlds")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(worldListView, new Rect(-halfListWidth, -0.6f, halfListWidth, 0.6f))
			.add(enterCreateSingleState, new Rect(-0.2f, -0.7f, 0.2f, -0.6f))
			.add(backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f))
			.finish()
		;
	}

	public static FixedWindow buildConfirmDeleteSinglePlayerStateWindow(GlUi ui
		, UiStateManager actions
		, UiData uiData
		, String worldDirectoryPrefix
	)
	{
		ViewTextButton<String> confirmDeleteButton = UiResources._buildConfirmDeleteButton(ui, actions, uiData, worldDirectoryPrefix);
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Confirm Delete")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(confirmDeleteButton, new Rect(-0.6f, -0.1f, 0.6f, 0.0f))
			.add(backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f))
			.finish()
		;
	}

	public static FixedWindow buildNewSinglePlayerStateWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		float margin = 0.6f;
		float divider = -0.2f;
		ViewRadioButton<WorldConfig.WorldGeneratorName> newWorldGeneratorNameButton = UiResources._buildWorldGeneratorRadio(ui, actions, uiData);
		ViewRadioButton<WorldConfig.DefaultPlayerMode> newDefaultPlayerModeButton = UiResources._buildDefaultModeRadio(ui, actions, uiData);
		ViewRadioButton<Difficulty> newDifficultyButton = UiResources._buildDifficultyRadio(ui, actions, uiData);
		ViewTextField<String> newWorldSeedTextField = UiResources._buildSeedTextField(ui, actions, uiData);
		ViewTextField<String> newWorldNameTextField = UiResources._buildNewWorldNameTextField(ui, actions, uiData);
		ViewTextButton<String> createWorldButton = UiResources._buildCreateNewButton(ui, actions);
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
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

	public static FixedWindow buildListMultiPlayerStateWindow(GlUi ui
		, UiStateManager actions
		, UiData uiData
		, BooleanSupplier shouldChangePage
	)
	{
		float halfListWidth = MULTI_PLAYER_SERVER_ROW_WIDTH / 2.0f;
		PaginatedListView<MutableServerList.ServerRecord> serverListView = UiResources._buildServerListView(ui, actions, uiData, shouldChangePage);
		ViewTextButton<String> enterAddNewServerButton = UiResources._buildAddNewServerButton(ui, actions);
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Multi-Player servers")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(serverListView, new Rect(-halfListWidth, -0.6f, halfListWidth, 0.6f))
			.add(enterAddNewServerButton, new Rect(-0.2f, -0.7f, 0.2f, -0.6f))
			.add(backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f))
			.finish()
		;
	}

	public static FixedWindow buildNewMultiPlayerStateWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		float margin = 0.6f;
		float divider = -0.2f;
		ViewOfStateless<MutableServerList.ServerRecord> currentlyTestingServerView = UiResources._buildServerTestingView(ui, actions, uiData);
		ViewTextField<String> newServerAddressTextField = UiResources._buildNewServerAddressTextField(ui, actions, uiData);
		ViewTextButton<String> testServerButton = UiResources._buildTestConnectionButton(ui, actions);
		ViewTextButton<String> saveServerButton = UiResources._buildSaveConnectionButton(ui, actions);
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("New Server Connection")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(currentlyTestingServerView, new Rect(-margin, 0.4f, margin, 0.6f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Server IP:port")), new Rect(-margin, 0.2f, divider, 0.3f))
			.add(newServerAddressTextField, new Rect(divider, 0.2f, margin, 0.3f))
			
			.add(testServerButton, new Rect(-margin, 0.0f, 0.0f, 0.1f))
			.add(saveServerButton, new Rect(0.0f, 0.0f, margin, 0.1f))
			
			.add(backButton, new Rect(-0.3f, -0.2f, 0.3f, -0.1f))
			.finish()
		;
	}

	public static FixedWindow buildPauseStateWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		Function<Boolean, String> valueTransformer = (Boolean isRunningOnServer) -> isRunningOnServer ? "Connected to server" : "Paused";
		ViewTextButton<String> returnToGameButton = UiResources._buildReturnToGameButton(ui, actions);
		ViewTextButton<String> optionsButton = UiResources._buildGameOptionsButton(ui, actions);
		ViewTextButton<String> keyBindingsButton = UiResources._buildKeyBindingsButton(ui, actions);
		ViewTextButton<Boolean> exitButton = UiResources._buildExitGameButton(ui, actions, uiData);
		
		return new FixedWindow.Builder()
			.add(new ViewGenericLabel<>(ui, uiData.isRunningOnServerBinding, valueTransformer), new Rect(-0.5f, 0.4f, 0.5f, 0.5f))
			.add(returnToGameButton, new Rect(-0.3f, 0.2f, 0.3f, 0.3f))
			.add(optionsButton, new Rect(-0.3f, 0.0f, 0.3f, 0.1f))
			.add(keyBindingsButton, new Rect(-0.3f, -0.2f, 0.3f, -0.1f))
			.add(exitButton, new Rect(-0.2f, -0.5f, 0.2f, -0.4f))
			.finish()
		;
	}

	public static FixedWindow buildErrorStateWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		ViewTextButton<String> copyToClipboardButton = UiResources._buildCopyToCliboardButton(ui, actions);
		ViewTextButton<String> quitButton = UiResources._buildQuitButton(ui, actions);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Fatal Error")), new Rect(-0.5f, 0.6f, 0.5f, 0.7f))
			.add(copyToClipboardButton, new Rect(-0.6f, -0.8f, -0.1f, -0.7f))
			.add(quitButton, new Rect(0.1f, -0.8f, 0.6f, -0.7f))
			.finish()
		;
	}

	public static FixedWindow buildOptionsStateWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		ViewTextButton<Boolean> fullScreenButton = UiResources._buildToggleFullScreenButton(ui, actions, uiData);
		ViewControlPlusMinus<Integer> viewDistanceControl = UiResources._buildViewDistanceSlider(ui, actions, uiData);
		ViewControlPlusMinus<Float> brightnessControl = UiResources._buildBrightnessSlider(ui, actions, uiData);
		ViewTextField<String> clientNameTextField = UiResources._buildClientNameTextField(ui, actions, uiData);
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
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

	public static FixedWindow buildKeyBindingsStateWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		ViewKeyControlSelector keyBindingSelectorControl = UiResources._buildKeyControlSelector(ui, actions, uiData);
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Key Bindings")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(keyBindingSelectorControl, new Rect(-0.4f, -0.9f, 0.4f, 0.6f))
			.add(backButton, new Rect(-0.2f, -0.8f, 0.2f, -0.7f))
			.finish()
		;
	}

	public static FixedWindow buildConnectingStateWindow(GlUi ui, UiStateManager actions, UiData uiData)
	{
		ViewTextButton<String> cancelConnectButton = UiResources._buildCancelConnectionButton(ui, actions);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Connecting...")), new Rect(-0.5f, 0.2f, 0.5f, 0.3f))
			.add(cancelConnectButton, new Rect(-0.3f, -0.4f, 0.3f, -0.3f))
			.finish()
		;
	}

	public static FixedWindow buildListProfileRunsStateWindow(GlUi ui, UiStateManager actions, UiData uiData, ProfilingModes[] profilingModes)
	{
		ViewTextButton<String> backButton = UiResources._buildBackButton(ui, actions);
		
		FixedWindow.Builder builder = new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("Profile Runs")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
		;
		float topY = 0.6f;
		for (ProfilingModes profilingMode : profilingModes)
		{
			ViewTextButton<ProfilingModes> profileButton = new ViewTextButton<>(ui, new Binding<>(profilingMode)
				, (ProfilingModes mode) -> mode.name
				, (ViewTextButton<ProfilingModes> button, ProfilingModes mode) -> {
					actions.action_clickProfileRunButton(mode);
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


	private static ViewTextButton<String> _buildSinglePlayerButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Single Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickSinglePlayerButton();
			}
		);
	}

	private static ViewTextButton<String> _buildMultiPlayerButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Multi-Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickMultiPlayerButton();
			}
		);
	}

	private static ViewTextButton<String> _buildQuitButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Quit")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickQuitButton();
			}
		);
	}

	private static PaginatedListView<String> _buildSinglePlayerWorldListView(GlUi ui
		, UiStateManager actions
		, UiData uiData
		, BooleanSupplier shouldChangePage
		, String worldDirectoryPrefix
	)
	{
		StatelessViewTextButton<String> enterWorldButton = new StatelessViewTextButton<>(ui
			, (String text) -> text.substring(worldDirectoryPrefix.length())
			, (String directoryName) -> {
				actions.action_clickEnterSingleWorldButton(directoryName);
			}
		);
		StatelessViewTextButton<String> deleteWorldButton = new StatelessViewTextButton<>(ui
			, (String text) -> "X"
			, (String directoryName) -> {
				actions.action_clickDeleteSingleWorldButton(directoryName);
			}
		);
		return new PaginatedListView<>(ui
			, uiData.worldListBinding
			, shouldChangePage
			, new StatelessHBox<>(enterWorldButton, SINGLE_PLAYER_WORLD_ROW_WIDTH - SINGLE_PLAYER_WORLD_ROW_HEIGHT, deleteWorldButton)
			, SINGLE_PLAYER_WORLD_ROW_HEIGHT
		);
	}

	private static ViewTextButton<String> _buildBackButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Back")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickBackButton();
			}
		);
	}

	private static ViewTextButton<String> _buildCreateNewWorldButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Create New World")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickCreateSingleWorldButton();
			}
		);
	}

	private static ViewTextButton<String> _buildConfirmDeleteButton(GlUi ui, UiStateManager actions, UiData uiData, String worldDirectoryPrefix)
	{
		return new ViewTextButton<>(ui, uiData.selectedWorldNameForDelete
			, (String text) -> "Confirm delete world \"" + text.substring(worldDirectoryPrefix.length()) + "\" (cannot be undone)"
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickConfirmDeleteButton();
			}
		);
	}

	private static ViewRadioButton<WorldConfig.WorldGeneratorName> _buildWorldGeneratorRadio(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (WorldConfig.WorldGeneratorName type) -> type.name()
				, (WorldConfig.WorldGeneratorName selected) -> {
					actions.action_clickWorldGeneratorRadioButton(selected);
				}
				, WorldConfig.WorldGeneratorName.class
			)
			, uiData.worldGeneratorNameBinding
		);
	}

	private static ViewRadioButton<WorldConfig.DefaultPlayerMode> _buildDefaultModeRadio(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (WorldConfig.DefaultPlayerMode type) -> type.name()
				, (WorldConfig.DefaultPlayerMode selected) -> {
					actions.action_clickPlayerModeRadioButton(selected);
				}
				, WorldConfig.DefaultPlayerMode.class
			)
			, uiData.defaultPlayerModeBinding
		);
	}

	private static ViewRadioButton<Difficulty> _buildDifficultyRadio(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (Difficulty type) -> type.name()
				, (Difficulty selected) -> {
					actions.action_clickDifficultyRadioButton(selected);
				}
				, Difficulty.class
			)
			, uiData.difficultyBinding
		);
	}

	private static ViewTextField<String> _buildSeedTextField(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewTextField<>(ui
			, uiData.newSeedBinding
			, (String text) -> text
			, () -> (uiData.typingCapture == uiData.newSeedBinding) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickSeedTextField();
			}
		);
	}

	private static ViewTextButton<String> _buildCreateNewButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Create New")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickConfirmCreateSingleWorldButton();
			}
		);
	}

	private static ViewTextField<String> _buildNewWorldNameTextField(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewTextField<>(ui
			, uiData.newWorldNameBinding
			, (String value) -> (uiData.typingCapture == uiData.newWorldNameBinding) ? (value + "_") : value
			, () -> (uiData.typingCapture == uiData.newWorldNameBinding) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickNewWorldNameTextField();
			}
		);
	}

	private static PaginatedListView<MutableServerList.ServerRecord> _buildServerListView(GlUi ui
		, UiStateManager actions
		, UiData uiData
		, BooleanSupplier shouldChangePage
	)
	{
		StatelessMultiLineButton<MutableServerList.ServerRecord> connectToServerLine = new StatelessMultiLineButton<>(ui
			, new ServerRecordTransformer(ui)
			, (MutableServerList.ServerRecord server) -> {
				actions.action_clickJoinMultiWorldButton(server);
			}
		);
		StatelessViewTextButton<MutableServerList.ServerRecord> deleteServerButton = new StatelessViewTextButton<>(ui
			, (MutableServerList.ServerRecord ignored) -> "X"
			, (MutableServerList.ServerRecord server) -> {
				actions.action_clickDeleteMultiWorldButton(server);
			}
		);
		return new PaginatedListView<>(ui
			, uiData.serverList.servers
			, shouldChangePage
			, new StatelessHBox<>(connectToServerLine, MULTI_PLAYER_SERVER_ROW_WIDTH - MULTI_PLAYER_SERVER_ROW_HEIGHT, deleteServerButton)
			, MULTI_PLAYER_SERVER_ROW_HEIGHT
		);
	}

	private static ViewTextButton<String> _buildAddNewServerButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Add New Server")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickAddNewServerButton();
			}
		);
	}

	private static ViewOfStateless<MutableServerList.ServerRecord> _buildServerTestingView(GlUi ui, UiStateManager actions, UiData uiData)
	{
		StatelessMultiLineButton<MutableServerList.ServerRecord> renderOnlyServerLine = new StatelessMultiLineButton<>(ui
			, new ServerRecordTransformer(ui)
			, null
		);
		return new ViewOfStateless<>(renderOnlyServerLine, uiData.currentlyTestingServerBinding);
	}

	private static ViewTextField<String> _buildNewServerAddressTextField(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewTextField<>(ui, uiData.newServerAddressBinding
			, (String value) -> (uiData.typingCapture == uiData.newServerAddressBinding) ? (value + "_") : value
			, () -> (uiData.typingCapture == uiData.newServerAddressBinding) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickServerAddressTextField();
			}
		);
	}

	private static ViewTextButton<String> _buildTestConnectionButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Test Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickTestServerButton();
			}
		);
	}

	private static ViewTextButton<String> _buildSaveConnectionButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Save Tested Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickSaveServerButton();
			}
		);
	}

	private static ViewTextButton<String> _buildCancelConnectionButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Cancel Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickCancelConnectButton();
			}
		);
	}

	private static ViewTextButton<Boolean> _buildExitGameButton(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewTextButton<>(ui, uiData.isRunningOnServerBinding
			, (Boolean isOnServer) -> isOnServer ? "Disconnect" : "Exit"
			, (ViewTextButton<Boolean> button, Boolean isOnServer) -> {
				actions.action_clickExitGameButton();
			}
		);
	}

	private static ViewTextButton<String> _buildGameOptionsButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Game Options")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickOptionsButton();
			}
		);
	}

	private static ViewTextButton<String> _buildKeyBindingsButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Key Bindings")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickKeyBindingsButton();
			}
		);
	}

	private static ViewTextButton<String> _buildReturnToGameButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Return to Game")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickReturnToGameButton();
			}
		);
	}

	private static ViewTextButton<Boolean> _buildToggleFullScreenButton(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewTextButton<>(ui, uiData.mutablePreferences.isFullScreen
			, (Boolean isFullScreen) -> isFullScreen ? "Change to Windowed" : "Change to Full Screen"
			, (ViewTextButton<Boolean> button, Boolean isFullScreen) -> {
				actions.action_clickFullScreenToggle(isFullScreen);
			}
		);
	}

	private static ViewControlPlusMinus<Integer> _buildViewDistanceSlider(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewControlPlusMinus<>(ui, uiData.mutablePreferences.preferredViewDistance
			, (Integer distance) -> distance + " cuboids"
			, (boolean plus) -> {
				actions.action_clickViewDistanceSlider(plus);
			}
		);
	}

	private static ViewControlPlusMinus<Float> _buildBrightnessSlider(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewControlPlusMinus<>(ui, uiData.mutablePreferences.screenBrightness
			, (Float brightness) -> String.format("%.1fx", brightness)
			, (boolean plus) -> {
				actions.action_clickBrightnessSlider(plus);
			}
		);
	}

	private static ViewTextField<String> _buildClientNameTextField(GlUi ui, UiStateManager actions, UiData uiData)
	{
		return new ViewTextField<>(ui, uiData.mutablePreferences.clientName
			, (String value) -> (uiData.typingCapture == uiData.mutablePreferences.clientName) ? (value + "_") : value
			, () -> (uiData.typingCapture == uiData.mutablePreferences.clientName) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickClientNameTextField();
			}
		);
	}

	private static ViewKeyControlSelector _buildKeyControlSelector(GlUi ui
		, UiStateManager actions
		, UiData uiData
	)
	{
		return new ViewKeyControlSelector(ui, uiData.mutableControls
			, uiData.currentlyChangingControl
			, (MutableControls.Control selectedControl) -> {
				actions.action_clickKeyBindingSelector(selectedControl);
			}
		);
	}

	private static ViewTextButton<String> _buildCopyToCliboardButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Copy to Clipboard")
			, (String value) -> value
			, (ViewTextButton<String> button, String ignored) -> {
				actions.action_clickCopyToClipboardButton();
			}
		);
	}
}
