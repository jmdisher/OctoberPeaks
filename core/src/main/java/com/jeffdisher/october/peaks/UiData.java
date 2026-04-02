package com.jeffdisher.october.peaks;

import java.io.File;
import java.util.List;

import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.persistence.MutablePreferences;
import com.jeffdisher.october.peaks.persistence.MutableServerList;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.WorldConfig;


/**
 * A container of the UI bindings manipulated by UiStateManager and read by the logic in UiStateManager and the UI
 * components created in UiResources.
 */
public class UiData
{
	public final MutableControls mutableControls;
	public final MutablePreferences mutablePreferences;
	public final MutableServerList serverList;

	public Binding<String> typingCapture;

	public final Binding<List<String>> worldListBinding;
	public final Binding<String> newWorldNameBinding;
	public final Binding<String> newServerAddressBinding;
	public final Binding<MutableServerList.ServerRecord> currentlyTestingServerBinding;
	public final Binding<MutableControls.Control> currentlyChangingControl;
	public final Binding<Boolean> isRunningOnServerBinding;
	public final Binding<String> selectedWorldNameForDelete;
	public final Binding<WorldConfig.WorldGeneratorName> worldGeneratorNameBinding;
	public final Binding<WorldConfig.DefaultPlayerMode> defaultPlayerModeBinding;
	public final Binding<Difficulty> difficultyBinding;
	public final Binding<String> newSeedBinding;

	public UiData(File localStorageDirectory
		, MutableControls mutableControls
		, MutablePreferences mutablePreferences
	)
	{
		this.mutableControls = mutableControls;
		this.mutablePreferences = mutablePreferences;
		this.serverList = new MutableServerList(localStorageDirectory);
		
		this.isRunningOnServerBinding = new Binding<>(false);
		this.worldListBinding = new Binding<>(null);
		this.newWorldNameBinding = new Binding<>("");
		this.selectedWorldNameForDelete = new Binding<>(null);
		this.worldGeneratorNameBinding = new Binding<>(WorldConfig.WorldGeneratorName.BASIC);
		this.defaultPlayerModeBinding = new Binding<>(WorldConfig.DefaultPlayerMode.SURVIVAL);
		this.difficultyBinding = new Binding<>(Difficulty.HOSTILE);
		this.newSeedBinding = new Binding<>("");
		this.currentlyTestingServerBinding = new Binding<>(null);
		this.newServerAddressBinding = new Binding<>("");
		this.currentlyChangingControl = new Binding<>(null);
	}
}
