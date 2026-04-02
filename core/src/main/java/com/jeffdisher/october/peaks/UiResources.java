package com.jeffdisher.october.peaks;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.persistence.MutableServerList;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.PaginatedListView;
import com.jeffdisher.october.peaks.ui.ServerRecordTransformer;
import com.jeffdisher.october.peaks.ui.StatelessHBox;
import com.jeffdisher.october.peaks.ui.StatelessMultiLineButton;
import com.jeffdisher.october.peaks.ui.StatelessViewRadioButton;
import com.jeffdisher.october.peaks.ui.StatelessViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewControlPlusMinus;
import com.jeffdisher.october.peaks.ui.ViewKeyControlSelector;
import com.jeffdisher.october.peaks.ui.ViewOfStateless;
import com.jeffdisher.october.peaks.ui.ViewRadioButton;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextField;
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

	public static ViewTextButton<String> buildSinglePlayerButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Single Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickSinglePlayerButton();
			}
		);
	}

	public static ViewTextButton<String> buildMultiPlayerButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Multi-Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickMultiPlayerButton();
			}
		);
	}

	public static ViewTextButton<String> buildQuitButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Quit")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickQuitButton();
			}
		);
	}

	public static PaginatedListView<String> buildSinglePlayerWorldListView(GlUi ui
		, UiStateManager actions
		, Binding<List<String>> worldListBinding
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
			, worldListBinding
			, shouldChangePage
			, new StatelessHBox<>(enterWorldButton, SINGLE_PLAYER_WORLD_ROW_WIDTH - SINGLE_PLAYER_WORLD_ROW_HEIGHT, deleteWorldButton)
			, SINGLE_PLAYER_WORLD_ROW_HEIGHT
		);
	}

	public static ViewTextButton<String> buildBackButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Back")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickBackButton();
			}
		);
	}

	public static ViewTextButton<String> buildCreateNewWorldButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Create New World")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickCreateSingleWorldButton();
			}
		);
	}

	public static ViewTextButton<String> buildConfirmDeleteButton(GlUi ui, UiStateManager actions, Binding<String> selectedWorldNameForDelete, String worldDirectoryPrefix)
	{
		return new ViewTextButton<>(ui, selectedWorldNameForDelete
			, (String text) -> "Confirm delete world \"" + text.substring(worldDirectoryPrefix.length()) + "\" (cannot be undone)"
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickConfirmDeleteButton();
			}
		);
	}

	public static ViewRadioButton<WorldConfig.WorldGeneratorName> buildWorldGeneratorRadio(GlUi ui, UiStateManager actions, Binding<WorldConfig.WorldGeneratorName> worldGeneratorNameBinding)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (WorldConfig.WorldGeneratorName type) -> type.name()
				, (WorldConfig.WorldGeneratorName selected) -> {
					actions.action_clickWorldGeneratorRadioButton(selected);
				}
				, WorldConfig.WorldGeneratorName.class
			)
			, worldGeneratorNameBinding
		);
	}

	public static ViewRadioButton<WorldConfig.DefaultPlayerMode> buildDefaultModeRadio(GlUi ui, UiStateManager actions, Binding<WorldConfig.DefaultPlayerMode> defaultPlayerModeBinding)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (WorldConfig.DefaultPlayerMode type) -> type.name()
				, (WorldConfig.DefaultPlayerMode selected) -> {
					actions.action_clickPlayerModeRadioButton(selected);
				}
				, WorldConfig.DefaultPlayerMode.class
			)
			, defaultPlayerModeBinding
		);
	}

	public static ViewRadioButton<Difficulty> buildDifficultyRadio(GlUi ui, UiStateManager actions, Binding<Difficulty> difficultyBinding)
	{
		return new ViewRadioButton<>(new StatelessViewRadioButton<>(ui
				, (Difficulty type) -> type.name()
				, (Difficulty selected) -> {
					actions.action_clickDifficultyRadioButton(selected);
				}
				, Difficulty.class
			)
			, difficultyBinding
		);
	}

	public static ViewTextField<String> buildSeedTextField(GlUi ui, UiStateManager actions, Binding<String> newSeedBinding, BooleanSupplier isSelected)
	{
		return new ViewTextField<>(ui
			, newSeedBinding
			, (String text) -> text
			, () -> isSelected.getAsBoolean() ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickSeedTextField();
			}
		);
	}

	public static ViewTextButton<String> buildCreateNewButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Create New")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickConfirmCreateSingleWorldButton();
			}
		);
	}

	public static ViewTextField<String> buildNewWorldNameTextField(GlUi ui, UiStateManager actions, Binding<String> newWorldNameBinding, BooleanSupplier isSelected)
	{
		return new ViewTextField<>(ui, newWorldNameBinding
			, (String value) -> isSelected.getAsBoolean() ? (value + "_") : value
			, () -> isSelected.getAsBoolean() ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickNewWorldNameTextField();
			}
		);
	}

	public static PaginatedListView<MutableServerList.ServerRecord> buildServerListView(GlUi ui
		, UiStateManager actions
		, MutableServerList serverList
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
			, serverList.servers
			, shouldChangePage
			, new StatelessHBox<>(connectToServerLine, MULTI_PLAYER_SERVER_ROW_WIDTH - MULTI_PLAYER_SERVER_ROW_HEIGHT, deleteServerButton)
			, MULTI_PLAYER_SERVER_ROW_HEIGHT
		);
	}

	public static ViewTextButton<String> buildAddNewServerButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Add New Server")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickAddNewServerButton();
			}
		);
	}

	public static ViewOfStateless<MutableServerList.ServerRecord> buildServerTestingView(GlUi ui, UiStateManager actions, Binding<MutableServerList.ServerRecord> currentlyTestingServerBinding)
	{
		StatelessMultiLineButton<MutableServerList.ServerRecord> renderOnlyServerLine = new StatelessMultiLineButton<>(ui
			, new ServerRecordTransformer(ui)
			, null
		);
		return new ViewOfStateless<>(renderOnlyServerLine, currentlyTestingServerBinding);
	}

	public static ViewTextField<String> buildNewServerAddressTextField(GlUi ui, UiStateManager actions, Binding<String> newServerAddressBinding, BooleanSupplier isSelected)
	{
		return new ViewTextField<>(ui, newServerAddressBinding
			, (String value) -> isSelected.getAsBoolean() ? (value + "_") : value
			, () -> isSelected.getAsBoolean() ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickServerAddressTextField();
			}
		);
	}

	public static ViewTextButton<String> buildTestConnectionButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Test Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickTestServerButton();
			}
		);
	}

	public static ViewTextButton<String> buildSaveConnectionButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Save Tested Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickSaveServerButton();
			}
		);
	}

	public static ViewTextButton<String> buildCancelConnectionButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Cancel Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickCancelConnectButton();
			}
		);
	}

	public static ViewTextButton<String> buildExitGameButton(GlUi ui, UiStateManager actions, Binding<String> exitButtonBinding)
	{
		return new ViewTextButton<>(ui, exitButtonBinding
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickExitGameButton();
			}
		);
	}

	public static ViewTextButton<String> buildGameOptionsButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Game Options")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickOptionsButton();
			}
		);
	}

	public static ViewTextButton<String> buildKeyBindingsButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Key Bindings")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickKeyBindingsButton();
			}
		);
	}

	public static ViewTextButton<String> buildReturnToGameButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Return to Game")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				actions.action_clickReturnToGameButton();
			}
		);
	}

	public static ViewTextButton<Boolean> buildToggleFullScreenButton(GlUi ui, UiStateManager actions, Binding<Boolean> fullScreenBinding)
	{
		return new ViewTextButton<>(ui, fullScreenBinding
			, (Boolean isFullScreen) -> isFullScreen ? "Change to Windowed" : "Change to Full Screen"
			, (ViewTextButton<Boolean> button, Boolean isFullScreen) -> {
				actions.action_clickFullScreenToggle(isFullScreen);
			}
		);
	}

	public static ViewControlPlusMinus<Integer> buildViewDistanceSlider(GlUi ui, UiStateManager actions, Binding<Integer> viewDistanceBinding)
	{
		return new ViewControlPlusMinus<>(ui, viewDistanceBinding
			, (Integer distance) -> distance + " cuboids"
			, (boolean plus) -> {
				actions.action_clickViewDistanceSlider(plus);
			}
		);
	}

	public static ViewControlPlusMinus<Float> buildBrightnessSlider(GlUi ui, UiStateManager actions, Binding<Float> brightnessBinding)
	{
		return new ViewControlPlusMinus<>(ui, brightnessBinding
			, (Float brightness) -> String.format("%.1fx", brightness)
			, (boolean plus) -> {
				actions.action_clickBrightnessSlider(plus);
			}
		);
	}

	public static ViewTextField<String> buildClientNameTextField(GlUi ui, UiStateManager actions, Binding<String> clientNameBinding, BooleanSupplier isSelected)
	{
		return new ViewTextField<>(ui, clientNameBinding
			, (String value) -> isSelected.getAsBoolean() ? (value + "_") : value
			, () -> isSelected.getAsBoolean() ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				actions.action_clickClientNameTextField();
			}
		);
	}

	public static ViewKeyControlSelector buildKeyControlSelector(GlUi ui
		, UiStateManager actions
		, Binding<MutableControls.Control> currentlyChangingControl
		, MutableControls mutableControls
	)
	{
		return new ViewKeyControlSelector(ui, mutableControls
			, currentlyChangingControl
			, (MutableControls.Control selectedControl) -> {
				actions.action_clickKeyBindingSelector(selectedControl);
			}
		);
	}

	public static ViewTextButton<String> buildCopyToCliboardButton(GlUi ui, UiStateManager actions)
	{
		return new ViewTextButton<>(ui, new Binding<>("Copy to Clipboard")
			, (String value) -> value
			, (ViewTextButton<String> button, String ignored) -> {
				actions.action_clickCopyToClipboardButton();
			}
		);
	}
}
