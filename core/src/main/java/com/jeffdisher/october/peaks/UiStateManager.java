package com.jeffdisher.october.peaks;

import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.CraftAspect;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.client.MovementAccumulator;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.peaks.persistence.MutableControls;
import com.jeffdisher.october.peaks.persistence.MutablePreferences;
import com.jeffdisher.october.peaks.persistence.MutableServerList;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.ComplexItemView;
import com.jeffdisher.october.peaks.ui.CraftDescription;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.IView;
import com.jeffdisher.october.peaks.ui.ItemTuple;
import com.jeffdisher.october.peaks.ui.PaginatedListView;
import com.jeffdisher.october.peaks.ui.Point;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ServerRecordTransformer;
import com.jeffdisher.october.peaks.ui.StatelessHBox;
import com.jeffdisher.october.peaks.ui.StatelessMultiLineButton;
import com.jeffdisher.october.peaks.ui.StatelessViewRadioButton;
import com.jeffdisher.october.peaks.ui.StatelessViewTextButton;
import com.jeffdisher.october.peaks.ui.SubBinding;
import com.jeffdisher.october.peaks.ui.UiIdioms;
import com.jeffdisher.october.peaks.ui.ViewArmour;
import com.jeffdisher.october.peaks.ui.ViewControlPlusMinus;
import com.jeffdisher.october.peaks.ui.ViewCraftingPanel;
import com.jeffdisher.october.peaks.ui.ViewEntityInventory;
import com.jeffdisher.october.peaks.ui.ViewHotbar;
import com.jeffdisher.october.peaks.ui.ViewKeyControlSelector;
import com.jeffdisher.october.peaks.ui.ViewMetaData;
import com.jeffdisher.october.peaks.ui.ViewOfStateless;
import com.jeffdisher.october.peaks.ui.ViewRadioButton;
import com.jeffdisher.october.peaks.ui.ViewSelection;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextField;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.utils.GeometryHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Handles the current high-level state of the UI based on events from the InputManager.
 */
public class UiStateManager implements GameSession.ICallouts
{
	/**
	 * In order to avoid cases like placing blocks too quickly or aggressively breaking a block behind a target, we will
	 * delay an action on a new block by this many milliseconds.
	 * This will also be applied to things like right-click actions on entities/blocks.
	 */
	public static final long MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS = 200L;
	public static final float RETICLE_SIZE = 0.05f;
	public static final Rect WINDOW_TOP_LEFT = new Rect(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final Rect WINDOW_TOP_RIGHT = new Rect(0.05f, 0.05f, ViewArmour.ARMOUR_SLOT_RIGHT_EDGE - ViewArmour.ARMOUR_SLOT_SCALE - ViewArmour.ARMOUR_SLOT_SPACING, 0.95f);
	public static final Rect WINDOW_BOTTOM = new Rect(-0.95f, -0.80f, 0.95f, -0.05f);
	public static final int MAX_WORLD_NAME = 16;
	public static final String WORLD_DIRECTORY_PREFIX = "world_";
	public static final float SINGLE_PLAYER_WORLD_ROW_WIDTH = 0.8f;
	public static final float SINGLE_PLAYER_WORLD_ROW_HEIGHT = 0.1f;
	public static final float MULTI_PLAYER_SERVER_ROW_WIDTH = 0.8f;
	public static final float MULTI_PLAYER_SERVER_ROW_HEIGHT = 0.2f;

	private final Environment _env;
	private final GlUi _ui;
	private final EntityVolume _playerVolume;
	private final MutableControls _mutableControls;
	private final MutablePreferences _mutablePreferences;
	private final MutableServerList _serverList;
	private final ICallouts _captureState;
	private final Map<Integer, String> _otherPlayersById;

	private boolean _rotationDidUpdate;
	private boolean _didAccountForTimeInFrame;
	private boolean _didWalkInFrame;
	private boolean _mouseHeld0;
	private boolean _mouseHeld1;
	private boolean _mouseClicked0;
	private boolean _mouseClicked1;
	private Point _cursor;

	// Variables related to the window overlay mode.
	private boolean _leftClick;
	private boolean _leftShiftClick;
	private boolean _rightClick;
	private Binding<String> _typingCapture;

	// Data specifically related to high-level UI state.
	private _UiState _uiState;
	private boolean _shouldQuitDirectly;
	private boolean _isRunningOnServer;
	private AbsoluteLocation _openStationLocation;
	private boolean _viewingFuelInventory;
	private Craft _continuousInInventory;
	private Craft _continuousInBlock;
	private boolean _isManualCraftingStation;

	// Tracking related to delayed actions when switching targets.
	private AbsoluteLocation _lastActionBlock;
	private long _lastActionMillis;

	// Tracking related to orientation change updates.
	private boolean _orientationNeedsFlush;
	private float _yawRadians;
	private float _pitchRadians;

	// UI for the start state when first launched with no args.
	private final ViewTextButton<String> _singlePlayerButton;
	private final ViewTextButton<String> _multiPlayerButton;
	private final ViewTextButton<String> _quitButton;

	// UI for the single-player list.
	private final Binding<List<String>> _worldListBinding;
	private final PaginatedListView<String> _worldListView;
	private final Binding<String> _newWorldNameBinding;
	private final ViewTextButton<String> _enterCreateSingleState;
	private final ViewTextButton<String> _backButton;

	// UI for delete confirmation.
	private final ViewTextButton<String> _confirmDeleteButton;

	// Ui for new single-player.
	private final ViewTextField<String> _newWorldNameTextField;
	private final ViewRadioButton<WorldConfig.WorldGeneratorName> _newWorldGeneratorNameButton;
	private final ViewRadioButton<WorldConfig.DefaultPlayerMode> _newDefaultPlayerModeButton;
	private final ViewRadioButton<Difficulty> _newDifficultyButton;
	private final ViewTextField<String> _newWorldSeedTextField;
	private final ViewTextButton<String> _createWorldButton;

	// UI for the multi-player list.
	private final PaginatedListView<MutableServerList.ServerRecord> _serverListView;
	private final ViewTextButton<String> _enterAddNewServerButton;

	// UI for new multi-player.
	private final Binding<String> _newServerAddressBinding;
	private final Binding<MutableServerList.ServerRecord> _currentlyTestingServerBinding;
	private final ViewOfStateless<MutableServerList.ServerRecord> _currentlyTestingServerView;
	private final ViewTextField<String> _newServerAddressTextField;
	private final ViewTextButton<String> _testServerButton;
	private final ViewTextButton<String> _saveServerButton;

	// Bindings defined/owned here and referenced by various UI components.
	private final Binding<WorldSelection> _selectionBinding;
	private final Binding<Entity> _entityBinding;
	private final Binding<Inventory> _thisEntityInventoryBinding;
	private final Binding<Inventory> _bottomWindowInventoryBinding;
	private final Binding<String> _bottomWindowTitleBinding;
	private final Binding<ItemTuple<Void>> _bottomWindowFuelBinding;
	private final Binding<String> _craftingPanelTitleBinding;
	private final Binding<List<CraftDescription>> _craftingPanelBinding;
	
	// Views for rendering parts of the UI in specific modes.
	private final Window<Inventory> _thisEntityInventoryWindow;
	private final Window<Inventory> _bottomInventoryWindow;
	private final Window<List<ItemTuple<CraftDescription>>> _craftingWindow;
	private final Window<Entity> _metaDataWindow;
	private final Window<Entity> _hotbarWindow;
	private final Window<NonStackableItem[]> _armourWindow;
	private final Window<WorldSelection> _selectionWindow;

	// UI for rendering the controls in the pause state.
	private final Binding<String> _exitButtonBinding;
	private final ViewTextButton<String> _exitButton;
	private final ViewTextButton<String> _optionsButton;
	private final ViewTextButton<String> _keyBindingsButton;
	private final ViewTextButton<String> _returnToGameButton;

	// UI for rendering the options state.
	private final ViewTextButton<Boolean> _fullScreenButton;
	private final ViewControlPlusMinus<Integer> _viewDistanceControl;
	private final ViewControlPlusMinus<Float> _brightnessControl;
	private final ViewTextField<String> _clientNameTextField;

	// UI and state related to key bindings prefs.
	private final ViewKeyControlSelector _keyBindingSelectorControl;
	private MutableControls.Control _currentlyChangingControl;
	// We also use _returnToPauseButton here.

	// Data related to the liquid overlay.
	private final Set<Block> _waterBlockTypes;
	private final Set<Block> _lavaBlockTypes;
	private AbsoluteLocation _eyeBlockLocation;

	// The current game session (can be null if not in the right state).
	private GameSession _currentGameSession;

	public UiStateManager(Environment environment
			, GL20 gl
			, File localStorageDirectory
			, LoadedResources resources
			, MutableControls mutableControls
			, ICallouts captureState
	)
	{
		_env = environment;
		_ui = new GlUi(gl, resources);
		_playerVolume = environment.creatures.PLAYER.volume();
		_mutableControls = mutableControls;
		_mutablePreferences = new MutablePreferences(localStorageDirectory);
		_serverList = new MutableServerList(localStorageDirectory);
		_captureState = captureState;
		_otherPlayersById = new HashMap<>();
		
		// The UI state is fairly high-level, deciding what is on screen and how we handle inputs.
		_uiState = _UiState.START;
		_exitButtonBinding = new Binding<>(null);
		_worldListBinding = new Binding<>(null);
		
		_singlePlayerButton = new ViewTextButton<>(_ui, new Binding<>("Single Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// Enter the single-player list.
					Assert.assertTrue(_UiState.START == _uiState);
					_uiState = _UiState.LIST_SINGLE_PLAYER;
					
					// Update the world name list since we are entering that state.
					_rebuildSinglePlayerListBinding(_worldListBinding, localStorageDirectory);
				}
		});
		_multiPlayerButton = new ViewTextButton<>(_ui, new Binding<>("Multi-Player")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// Enter the single-player list.
					Assert.assertTrue(_UiState.START == _uiState);
					_uiState = _UiState.LIST_MULTI_PLAYER;
					
					// Request that this list be validated.
					_serverList.pollServers();
				}
		});
		_quitButton = new ViewTextButton<>(_ui, new Binding<>("Quit")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// From here, we quit directly, as this is top-level.
					Gdx.app.exit();
				}
		});
		
		// Single-player UI.
		StatelessViewTextButton<String> enterWorldButton = new StatelessViewTextButton<>(_ui
			, (String text) -> text.substring(WORLD_DIRECTORY_PREFIX.length())
			, (String directoryName) -> {
				if (_leftClick)
				{
					// We just pass nulls for our new game options.
					_enterSingleWorld(gl, localStorageDirectory, resources, directoryName, null, null, null, 0);
				}
			}
		);
		Binding<String> selectedWorldNameForDelete = new Binding<>(null);
		StatelessViewTextButton<String> deleteWorldButton = new StatelessViewTextButton<>(_ui
			, (String text) -> "X"
			, (String directoryName) -> {
				if (_leftClick)
				{
					// We want to enter the confirmation state.
					Assert.assertTrue(_UiState.LIST_SINGLE_PLAYER == _uiState);
					_uiState = _UiState.CONFIRM_DELETE_SINGLE_PLAYER;
					
					// We also need to put this chosen directory in the binding.
					selectedWorldNameForDelete.set(directoryName);
				}
			}
		);
		_worldListView = new PaginatedListView<>(_ui
			, _worldListBinding
			, () -> _leftClick
			, new StatelessHBox<>(enterWorldButton, SINGLE_PLAYER_WORLD_ROW_WIDTH - SINGLE_PLAYER_WORLD_ROW_HEIGHT, deleteWorldButton)
			, SINGLE_PLAYER_WORLD_ROW_HEIGHT
		);
		_backButton = new ViewTextButton<>(_ui, new Binding<>("Back")
				, (String text) -> text
				, (ViewTextButton<String> button, String text) -> {
					if (_leftClick)
					{
						// This is the same as hitting escape.
						_doBackStateTransition();
					}
			}
		);
		_newWorldNameBinding = new Binding<>("");
		_enterCreateSingleState = new ViewTextButton<>(_ui, new Binding<>("Create New World")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// Enter the single-player creation window.
					Assert.assertTrue(_UiState.LIST_SINGLE_PLAYER == _uiState);
					_uiState = _UiState.NEW_SINGLE_PLAYER;
					
					// Select the default text field.
					_typingCapture = _newWorldNameBinding;
				}
			}
		);
		
		// World delete confirmation UI.
		_confirmDeleteButton = new ViewTextButton<>(_ui, selectedWorldNameForDelete
				, (String text) -> "Confirm delete world \"" + text.substring(WORLD_DIRECTORY_PREFIX.length()) + "\" (cannot be undone)"
				, (ViewTextButton<String> button, String text) -> {
					if (_leftClick)
					{
						// Verify state transition.
						Assert.assertTrue(_UiState.CONFIRM_DELETE_SINGLE_PLAYER == _uiState);
						_uiState = _UiState.LIST_SINGLE_PLAYER;
						
						// Delete the directory, then return to the listing.
						File localWorldDirectory = new File(localStorageDirectory, selectedWorldNameForDelete.get());
						System.out.println("Deleting local world: " + localWorldDirectory);
						_deleteWorldRecursively(localWorldDirectory);
						
						selectedWorldNameForDelete.set(null);
						
						// We also need to rebuild the list.
						_rebuildSinglePlayerListBinding(_worldListBinding, localStorageDirectory);
					}
			}
		);
		
		// New single player UI.
		Binding<WorldConfig.WorldGeneratorName> worldGeneratorNameBinding = new Binding<>(WorldConfig.WorldGeneratorName.BASIC);
		_newWorldGeneratorNameButton = new ViewRadioButton<>(new StatelessViewRadioButton<>(_ui
				, (WorldConfig.WorldGeneratorName type) -> type.name()
				, (WorldConfig.WorldGeneratorName selected) -> {
					if (_leftClick)
					{
						worldGeneratorNameBinding.set(selected);
					}
				}
				, WorldConfig.WorldGeneratorName.class
			)
			, worldGeneratorNameBinding
		);
		Binding<WorldConfig.DefaultPlayerMode> defaultPlayerModeBinding = new Binding<>(WorldConfig.DefaultPlayerMode.SURVIVAL);
		_newDefaultPlayerModeButton = new ViewRadioButton<>(new StatelessViewRadioButton<>(_ui
				, (WorldConfig.DefaultPlayerMode type) -> type.name()
				, (WorldConfig.DefaultPlayerMode selected) -> {
					if (_leftClick)
					{
						defaultPlayerModeBinding.set(selected);
					}
				}
				, WorldConfig.DefaultPlayerMode.class
			)
			, defaultPlayerModeBinding
		);
		Binding<Difficulty> difficultyBinding = new Binding<>(Difficulty.HOSTILE);
		_newDifficultyButton = new ViewRadioButton<>(new StatelessViewRadioButton<>(_ui
				, (Difficulty type) -> type.name()
				, (Difficulty selected) -> {
					if (_leftClick)
					{
						difficultyBinding.set(selected);
					}
				}
				, Difficulty.class
			)
			, difficultyBinding
		);
		Binding<String> newSeedBinding = new Binding<>("");
		_newWorldSeedTextField = new ViewTextField<>(_ui
				, newSeedBinding
				, (String text) -> text
				, () -> (_typingCapture == newSeedBinding) ? _ui.pixelGreen : _ui.pixelLightGrey
				, () -> {
					// We want to enable text capture for this binding.
					if (_leftClick)
					{
						_typingCapture = newSeedBinding;
					}
				}
		);
		_createWorldButton = new ViewTextButton<>(_ui, new Binding<>("Create New")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// We want to start a single-player game.
					Assert.assertTrue(_UiState.NEW_SINGLE_PLAYER == _uiState);
					
					// Make sure that the name is non-empty and not already used.
					String worldName = _newWorldNameBinding.get();
					String directoryName = "world_" + worldName;
					boolean alreadyExists = _worldListBinding.get().contains(directoryName);
					if (!alreadyExists && (worldName.length() > 0))
					{
						WorldConfig.WorldGeneratorName worldGeneratorName = worldGeneratorNameBinding.get();
						WorldConfig.DefaultPlayerMode defaultPlayerMode = defaultPlayerModeBinding.get();
						Difficulty difficulty = difficultyBinding.get();
						// The seed is a little tricky: if empty, use the default, if a number, use the number, if text, use the hash.
						Integer basicWorldGeneratorSeed = null;
						String rawSeed = newSeedBinding.get();
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
						_enterSingleWorld(gl
							, localStorageDirectory
							, resources
							, directoryName
							, worldGeneratorName
							, defaultPlayerMode
							, difficulty
							, basicWorldGeneratorSeed
						);
						_newWorldNameBinding.set("");
						_typingCapture = null;
					}
				}
			}
		);
		_newWorldNameTextField = new ViewTextField<>(_ui, _newWorldNameBinding
			, (String value) -> (_typingCapture == _newWorldNameBinding) ? (value + "_") : value
			, () -> (_typingCapture == _newWorldNameBinding) ? _ui.pixelGreen : _ui.pixelLightGrey
			, () -> {
				if (_leftClick)
				{
					// We want to enable text capture for this binding.
					_typingCapture = _newWorldNameBinding;
				}
			}
		);
		
		// Server list UI.
		StatelessMultiLineButton<MutableServerList.ServerRecord> connectToServerLine = new StatelessMultiLineButton<>(_ui
			, new ServerRecordTransformer(_ui)
			, (MutableServerList.ServerRecord server) -> {
				if (_leftClick)
				{
					try
					{
						String clientName = _mutablePreferences.clientName.get();
						_connectToServer(gl, localStorageDirectory, resources, clientName, server.address);
					}
					catch (ConnectException e)
					{
						// TODO:  Display this somewhere.
						e.printStackTrace();
					}
				}
			}
		);
		StatelessViewTextButton<MutableServerList.ServerRecord> deleteServerButton = new StatelessViewTextButton<>(_ui
			, (MutableServerList.ServerRecord ignored) -> "X"
			, (MutableServerList.ServerRecord record) -> {
				if (_leftClick)
				{
					_serverList.removeServerFromList(record);
				}
			}
		);
		_serverListView = new PaginatedListView<>(_ui
			, _serverList.servers
			, () -> _leftClick
			, new StatelessHBox<>(connectToServerLine, MULTI_PLAYER_SERVER_ROW_WIDTH - MULTI_PLAYER_SERVER_ROW_HEIGHT, deleteServerButton)
			, MULTI_PLAYER_SERVER_ROW_HEIGHT
		);
		_currentlyTestingServerBinding = new Binding<>(null);
		_newServerAddressBinding = new Binding<>("");
		_enterAddNewServerButton = new ViewTextButton<>(_ui, new Binding<>("Add New Server")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// Enter the single-player creation window.
					Assert.assertTrue(_UiState.LIST_MULTI_PLAYER == _uiState);
					_uiState = _UiState.NEW_MULTI_PLAYER;
					
					// Select the default text field.
					_typingCapture = _newServerAddressBinding;
					
					// Clear any stale state from last time.
					_currentlyTestingServerBinding.set(null);
				}
			}
		);
		
		// New server UI.
		StatelessMultiLineButton<MutableServerList.ServerRecord> renderOnlyServerLine = new StatelessMultiLineButton<>(_ui
				, new ServerRecordTransformer(_ui)
				, null
			);
		_currentlyTestingServerView = new ViewOfStateless<>(renderOnlyServerLine, _currentlyTestingServerBinding);
		_newServerAddressTextField = new ViewTextField<>(_ui, _newServerAddressBinding
			, (String value) -> (_typingCapture == _newServerAddressBinding) ? (value + "_") : value
			, () -> (_typingCapture == _newServerAddressBinding) ? _ui.pixelGreen : _ui.pixelLightGrey
			, () -> {
				if (_leftClick)
				{
					// We want to enable text capture for this binding.
					_typingCapture = _newServerAddressBinding;
				}
			}
		);
		_testServerButton = new ViewTextButton<>(_ui, new Binding<>("Test Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// We want to do the test for version, etc, and add this to our list on success.
					Assert.assertTrue(_UiState.NEW_MULTI_PLAYER == _uiState);
					
					// We will need to parse this address from the binding.
					String rawAddress = _newServerAddressBinding.get();
					int colonIndex = rawAddress.indexOf(":");
					if (-1 != colonIndex)
					{
						String ipHostName = rawAddress.substring(0, colonIndex);
						int port = Integer.parseInt(rawAddress.substring(colonIndex + 1));
						InetSocketAddress address = new InetSocketAddress(ipHostName, port);
						
						// Create the socket and start the background test, storing the new token in the binding.
						MutableServerList.ServerRecord record = _serverList.beginSpecialPollRequest(address);
						_currentlyTestingServerBinding.set(record);
						_newServerAddressBinding.set("");
						_typingCapture = null;
					}
				}
			}
		);
		_saveServerButton = new ViewTextButton<>(_ui, new Binding<>("Save Tested Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					Assert.assertTrue(_UiState.NEW_MULTI_PLAYER == _uiState);
					
					// If there is a binding, and it is good, add it to the server list and back out of this.
					MutableServerList.ServerRecord record = _currentlyTestingServerBinding.get();
					if ((null != record) && record.isGood)
					{
						_serverList.addServerToList(record);
						_currentlyTestingServerBinding.set(null);
						
						// We can escape this state.
						_doBackStateTransition();
					}
				}
			}
		);
		
		// Define all of our bindings.
		_selectionBinding = new Binding<>(null);
		_entityBinding = new Binding<>(null);
		_thisEntityInventoryBinding = new SubBinding<>(_entityBinding, (Entity entity) -> {
			Inventory inventory = entity.isCreativeMode()
					? CreativeInventory.fakeInventory()
					: entity.inventory()
			;
			return inventory;
		});
		Binding<NonStackableItem[]> armourBinding = new SubBinding<>(_entityBinding, (Entity entity) -> entity.armourSlots());
		_bottomWindowInventoryBinding = new Binding<>(null);
		_bottomWindowTitleBinding = new Binding<>(null);
		_bottomWindowFuelBinding = new Binding<>(null);
		_craftingPanelTitleBinding = new Binding<>(null);
		_craftingPanelBinding = new Binding<>(null);
		
		// Create our views.
		IntConsumer mouseOverTopRightKeyConsumer = (int key) -> {
			AbsoluteLocation relevantBlock;
			if (null != _openStationLocation)
			{
				relevantBlock = _openStationLocation;
			}
			else
			{
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_entityBinding.get(), _playerVolume);
				relevantBlock = feetBlock;
			}
			_handleHoverOverEntityInventoryItem(relevantBlock, key);
		};
		IntConsumer mouseOverBottomKeyConsumer = (int key) -> {
			AbsoluteLocation relevantBlock;
			if (null != _openStationLocation)
			{
				relevantBlock = _openStationLocation;
			}
			else
			{
				AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(_entityBinding.get(), _playerVolume);
				relevantBlock = feetBlock;
			}
			_pullFromBlockToEntityInventory(relevantBlock, key);
		};
		Consumer<CraftDescription> craftHoverOverConsumer = (CraftDescription desc) -> {
			if (_isManualCraftingStation && (_leftClick || _leftShiftClick))
			{
				Craft craft = desc.craft();
				if (null != _openStationLocation)
				{
					_continuousInBlock = _leftShiftClick ? craft : null;
					_currentGameSession.client.beginCraftInBlock(_openStationLocation, craft);
				}
				else
				{
					_continuousInInventory = _leftShiftClick ? craft : null;
					_currentGameSession.client.beginCraftInInventory(craft);
				}
				_didAccountForTimeInFrame = true;
			}
		};
		
		BooleanSupplier commonPageChangeCheck = () -> {
			return _leftClick;
		};
		
		Binding<String> inventoryTitleBinding = new Binding<>("Inventory");
		ViewEntityInventory thisEntityInventoryView = new ViewEntityInventory(_ui, inventoryTitleBinding, _thisEntityInventoryBinding, null, mouseOverTopRightKeyConsumer, commonPageChangeCheck);
		ComplexItemView.IBindOptions<Void> fuelViewOptions = new ComplexItemView.IBindOptions<Void>()
		{
			@Override
			public int getOutlineTexture(ItemTuple<Void> context)
			{
				// We always just show the same background for fuel.
				return _ui.pixelLightGrey;
			}
			@Override
			public void hoverRender(Point cursor, ItemTuple<Void> context)
			{
				// No fuel slot hover.
			}
			@Override
			public void hoverAction(ItemTuple<Void> context)
			{
				// There is no fuel slot action.
			}
		};
		_thisEntityInventoryWindow = new Window<>(WINDOW_TOP_RIGHT, thisEntityInventoryView);
		ComplexItemView<Void> fuelProgress = new ComplexItemView<>(_ui, _bottomWindowFuelBinding, fuelViewOptions);
		ViewEntityInventory bottomInventoryView = new ViewEntityInventory(_ui, _bottomWindowTitleBinding, _bottomWindowInventoryBinding, fuelProgress, mouseOverBottomKeyConsumer, commonPageChangeCheck);
		_bottomInventoryWindow = new Window<>(WINDOW_BOTTOM, bottomInventoryView);
		ViewCraftingPanel craftingPanelView = new ViewCraftingPanel(_ui, _craftingPanelTitleBinding, _craftingPanelBinding, craftHoverOverConsumer, commonPageChangeCheck);
		_craftingWindow = new Window<>(WINDOW_TOP_LEFT, craftingPanelView);
		_metaDataWindow = new Window<>(ViewMetaData.LOCATION, new ViewMetaData(_ui, _entityBinding));
		_hotbarWindow = new Window<>(ViewHotbar.LOCATION, new ViewHotbar(_ui, _entityBinding));
		Consumer<BodyPart> eventHoverArmourBodyPart = (BodyPart hoverPart) -> {
			if (_leftClick)
			{
				// Note that we ignore the result since this will be reflected in the UI, if valid.
				_currentGameSession.client.swapArmour(hoverPart);
			}
		};
		_armourWindow = new Window<>(ViewArmour.LOCATION, new ViewArmour(_ui, armourBinding, eventHoverArmourBodyPart));
		// The ViewSelection should only use block lookup during play state so the _currentGameSession should never be null.
		Function<AbsoluteLocation, BlockProxy> blockLookup = (AbsoluteLocation location) -> _currentGameSession.blockLookup.apply(location);
		_selectionWindow = new Window<>(ViewSelection.LOCATION, new ViewSelection(_ui, _env, _selectionBinding, blockLookup, _otherPlayersById));
		
		// Pause state controls.
		_exitButton = new ViewTextButton<>(_ui, _exitButtonBinding
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					// The exit button should default to quitting if we started up directly into play state.
					if (_shouldQuitDirectly)
					{
						Gdx.app.exit();
					}
					else
					{
						_currentGameSession.shutdown();
						_currentGameSession = null;
						_uiState = _UiState.START;
					}
				}
		});
		_optionsButton = new ViewTextButton<>(_ui, new Binding<>("Game Options")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					_uiState = _UiState.OPTIONS;
				}
		});
		_keyBindingsButton = new ViewTextButton<>(_ui, new Binding<>("Key Bindings")
				, (String text) -> text
				, (ViewTextButton<String> button, String text) -> {
					if (_leftClick)
					{
						_uiState = _UiState.KEY_BINDINGS;
						_currentlyChangingControl = null;
					}
			});
		_returnToGameButton = new ViewTextButton<>(_ui, new Binding<>("Return to Game")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				if (_leftClick)
				{
					_uiState = _UiState.PLAY;
					_captureState.shouldCaptureMouse(true);
					_currentGameSession.client.resumeGame();
				}
		});
		
		// Options state controls.
		_fullScreenButton = new ViewTextButton<>(_ui, _mutablePreferences.isFullScreen
			, (Boolean isFullScreen) -> isFullScreen ? "Change to Windowed" : "Change to Full Screen"
			, (ViewTextButton<Boolean> button, Boolean isFullScreen) -> {
				if (_leftClick)
				{
					// We will toggle the full screen and update the binding data.
					boolean newFullScreen = !isFullScreen;
					if (newFullScreen)
					{
						// We will just use the full screen of the current display mode.
						Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
					}
					else
					{
						// For now, we will always use this same default window size.
						Gdx.graphics.setWindowedMode(1280, 960);
					}
					_mutablePreferences.isFullScreen.set(newFullScreen);
				}
		});
		_viewDistanceControl = new ViewControlPlusMinus<>(_ui, _mutablePreferences.preferredViewDistance
			, (Integer distance) -> distance + " cuboids"
			, (boolean plus) -> {
				if (_leftClick)
				{
					// TODO:  When we persist preferences, put this there whether or not in game.
					if (null != _currentGameSession)
					{
						// We try changing this in the client and it will return the updated value.
						int newDistance = _mutablePreferences.preferredViewDistance.get() +
								(plus ? 1 : -1)
						;
						int finalValue = _currentGameSession.client.trySetViewDistance(newDistance);
						_mutablePreferences.preferredViewDistance.set(finalValue);
					}
				}
		});
		_brightnessControl = new ViewControlPlusMinus<>(_ui, _mutablePreferences.screenBrightness
				, (Float brightness) -> String.format("%.1fx", brightness)
				, (boolean plus) -> {
					if (_leftClick)
					{
						// We just want to increment this by 0.1 increments between 1.0 and 2.0.
						int current = (int)(10.0f * _mutablePreferences.screenBrightness.get());
						int next;
						if (plus)
						{
							next = Math.min(20, current + 1);
						}
						else
						{
							next = Math.max(10, current - 1);
						}
						float updated = ((float) next) / 10.0f;
						_mutablePreferences.screenBrightness.set(updated);
					}
			});
		_clientNameTextField = new ViewTextField<>(_ui, _mutablePreferences.clientName
			, (String value) -> (_typingCapture == _mutablePreferences.clientName) ? (value + "_") : value
			, () -> (_typingCapture == _mutablePreferences.clientName) ? _ui.pixelGreen : _ui.pixelLightGrey
			, () -> {
				if (_leftClick)
				{
					// We want to enable text capture for this binding.
					_typingCapture = _mutablePreferences.clientName;
				}
			}
		);
		
		// Key-binding prefs.
		_keyBindingSelectorControl = new ViewKeyControlSelector(_ui, _mutableControls
			, (MutableControls.Control selectedControl) -> {
				if (_leftClick)
				{
					_currentlyChangingControl = selectedControl;
				}
			}
		);
		
		// Look up the liquid overlay types.
		_waterBlockTypes = Set.of(_env.blocks.fromItem(_env.items.getItemById("op.water_source"))
				, _env.blocks.fromItem(_env.items.getItemById("op.water_strong"))
				, _env.blocks.fromItem(_env.items.getItemById("op.water_weak"))
		);
		_lavaBlockTypes = Set.of(_env.blocks.fromItem(_env.items.getItemById("op.lava_source"))
				, _env.blocks.fromItem(_env.items.getItemById("op.lava_strong"))
				, _env.blocks.fromItem(_env.items.getItemById("op.lava_weak"))
		);
	}

	@Override
	public void thisEntityUpdated(Entity projectedEntity)
	{
		_entityBinding.set(projectedEntity);
		
		// Make sure that we close the inventory if it is now too far away (can happen if falling or respawning).
		if (null != _openStationLocation)
		{
			float distance = SpatialHelpers.distanceFromPlayerEyeToBlockSurface(projectedEntity.location(), _env.creatures.PLAYER, _openStationLocation);
			boolean isLocationClose = (distance <= MiscConstants.REACH_BLOCK);
			if (!isLocationClose)
			{
				_openStationLocation = null;
			}
		}
	}

	@Override
	public void otherClientJoined(int clientId, String name)
	{
		Object old = _otherPlayersById.put(clientId, name);
		Assert.assertTrue(null == old);
	}

	@Override
	public void otherClientLeft(int clientId)
	{
		Object old = _otherPlayersById.remove(clientId);
		Assert.assertTrue(null != old);
	}

	public void startPlay(GameSession currentGameSession, boolean onServer)
	{
		Assert.assertTrue(_UiState.START == _uiState);
		_uiState = _UiState.PLAY;
		_currentGameSession = currentGameSession;
		_isRunningOnServer = onServer;
		// We will quit directly from the entry-point, given that there is no return-to state.
		_exitButtonBinding.set(_isRunningOnServer ? "Disconnect" : "Quit");
		_shouldQuitDirectly = true;
	}

	public void capturedMouseMoved(int deltaX, int deltaY)
	{
		if (null != _currentGameSession)
		{
			if ((0 != deltaX) || (0 != deltaY))
			{
				_yawRadians = _currentGameSession.movement.rotateYaw(deltaX);
				_pitchRadians = _currentGameSession.movement.rotatePitch(deltaY);
				_orientationNeedsFlush = true;
			}
			_rotationDidUpdate = true;
		}
	}

	public void captureMouse0Down(boolean justClicked)
	{
		_mouseHeld0 = true;
		_mouseClicked0 = justClicked;
	}

	public void captureMouse1Down(boolean justClicked, boolean leftShiftHeld)
	{
		_mouseHeld1 = true;
		// We use the shift to allow us to set the "held" without "clicked".
		// In the future, this will likely be expanded but it isn't obvious where the interpretation of this key should
		// go (InputManager, where it can associated with key settings, or here where it is associated with the UI state).
		if (!leftShiftHeld)
		{
			_mouseClicked1 = justClicked;
		}
	}

	public void moveForward(WalkType walk)
	{
		MovementAccumulator.Relative relative = MovementAccumulator.Relative.FORWARD;
		_commonWalk(relative, walk);
	}

	public void moveBackward(WalkType walk)
	{
		MovementAccumulator.Relative relative = MovementAccumulator.Relative.BACKWARD;
		_commonWalk(relative, walk);
	}

	public void strafeRight(WalkType walk)
	{
		MovementAccumulator.Relative relative = MovementAccumulator.Relative.RIGHT;
		_commonWalk(relative, walk);
	}

	public void strafeLeft(WalkType walk)
	{
		MovementAccumulator.Relative relative = MovementAccumulator.Relative.LEFT;
		_commonWalk(relative, walk);
	}

	public void jumpOrSwim()
	{
		_currentGameSession.client.jumpOrSwim();
	}

	public void normalMouseMoved(Point cursor)
	{
		_cursor = cursor;
	}

	public void normalMouse0Clicked(boolean leftShiftDown)
	{
		if (leftShiftDown)
		{
			_leftShiftClick = true;
		}
		else
		{
			_leftClick = true;
		}
	}

	public void normalMouse1Clicked(boolean leftShiftDown)
	{
		_rightClick = true;
	}

	public void handleKeyEsc()
	{
		_doBackStateTransition();
		_continuousInInventory = null;
		_continuousInBlock = null;
	}

	public void handleHotbarIndex(int hotbarIndex)
	{
		// We will accept the hotbar index for any active game session.
		if (null != _currentGameSession)
		{
			_currentGameSession.client.changeHotbarIndex(hotbarIndex);
		}
	}

	public void handleKeyI()
	{
		// This only matters if we are playing or in the inventory screen.
		if (_UiState.INVENTORY == _uiState)
		{
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
		}
		else if (_UiState.PLAY == _uiState)
		{
			_uiState = _UiState.INVENTORY;
			_openStationLocation = null;
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_viewingFuelInventory = false;
			_captureState.shouldCaptureMouse(false);
		}
		_continuousInInventory = null;
		_continuousInBlock = null;
	}

	public void handleKeyF()
	{
		_viewingFuelInventory = !_viewingFuelInventory;
		if (_viewingFuelInventory)
		{
			// Make sure that this actually has a fuel slot.
			if (null == _openStationLocation)
			{
				_viewingFuelInventory = false;
			}
			else
			{
				BlockProxy stationBlock = _currentGameSession.blockLookup.apply(_openStationLocation);
				_viewingFuelInventory = (null != stationBlock.getFuel());
			}
		}
		_continuousInInventory = null;
		_continuousInBlock = null;
	}

	public void keyCodeUp(int lastKeyUp)
	{
		if ((_UiState.KEY_BINDINGS == _uiState) && (null != _currentlyChangingControl))
		{
			_mutableControls.setKeyForControl(_currentlyChangingControl, lastKeyUp);
			_currentlyChangingControl = null;
		}
	}

	/**
	 * Called after clearing the framebuffer in order to render the frame with whatever is required for in the current
	 * UI state.
	 * Internally, this is also an opportunity for the state manager to act on, flush, or reset any input events it has
	 * received since the last frame.
	 */
	public void renderFrame()
	{
		// Find the selection, if the mode supports this.
		WorldSelection selection = null;
		PartialEntity entity = null;
		AbsoluteLocation stopBlock = null;
		Block stopBlockType = null;
		AbsoluteLocation preStopBlock = null;
		if (_UiState.PLAY == _uiState)
		{
			// See if the perspective changed.
			if (_rotationDidUpdate)
			{
				_rotationDidUpdate = false;
				Vector eye = _currentGameSession.movement.computeEye();
				Vector target = _currentGameSession.movement.computeTarget();
				Vector upVector = _currentGameSession.movement.computeUpVector();
				_currentGameSession.selectionManager.updatePosition(eye, target);
				_currentGameSession.scene.updatePosition(eye, target, upVector);
				_eyeBlockLocation = GeometryHelpers.locationFromVector(eye);
			}
			
			// Capture whatever is selected.
			selection = _currentGameSession.selectionManager.findSelection();
			if (null != selection)
			{
				entity = selection.entity();
				stopBlock = selection.stopBlock();
				BlockProxy proxy = (null != stopBlock)
						? _currentGameSession.blockLookup.apply(stopBlock)
						: null
				;
				if (null != proxy)
				{
					stopBlockType = proxy.getBlock();
				}
				preStopBlock = selection.preStopBlock();
			}
		}
		_selectionBinding.set(selection);
		
		if (null != _currentGameSession)
		{
			// Draw the main scene first (since we only draw the other data on top of this).
			_currentGameSession.scene.render(entity, stopBlock, stopBlockType);
			
			// Draw any eye effect overlay.
			_currentGameSession.eyeEffect.drawEyeEffect();
		}
		
		// Draw the relevant windows on top of this scene (passing in any information describing the UI state).
		_drawRelevantWindows();
		
		if (_UiState.PLAY == _uiState)
		{
			// Finalize the event processing with this selection and accounting for inter-frame time.
			// Note that this must be last since we deliver some events while drawing windows, etc, when we discover click locations, etc.
			_finalizeFrameEvents(entity, stopBlock, preStopBlock);
		}
		if (null != _currentGameSession)
		{
			// Complete any of the idle operations and account for time passing.
			_idleInActiveFrame();
		}
		_clearEvents();
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
	}

	public void handleScreenResize(int width, int height)
	{
		if (null != _currentGameSession)
		{
			_currentGameSession.scene.rebuildProjection(width, height);
		}
	}

	public void keyTyped(char typedCharacter)
	{
		// If we have a binding capturing keys, make sure that this is one of our whitelist character types and then append it.
		if (null != _typingCapture)
		{
			String string = _typingCapture.get();
			int nameLength = string.length();
			if (('\b' == typedCharacter) && (nameLength > 0))
			{
				// Backspace is a special case.
				_typingCapture.set(string.substring(0, string.length() - 1));
			}
			else if (nameLength < MAX_WORLD_NAME)
			{
				int type = Character.getType(typedCharacter);
				switch (type)
				{
				case Character.LOWERCASE_LETTER:
				case Character.UPPERCASE_LETTER:
				case Character.DECIMAL_DIGIT_NUMBER:
					_typingCapture.set(string + typedCharacter);
					break;
					default:
						// Special-case whitelist.
						switch (typedCharacter)
						{
						case '.':
						case ':':
						case '-':
						case '_':
						case ' ':
							_typingCapture.set(string + typedCharacter);
							break;
						default:
							// Ignored.
						}
				}
			}
		}
	}

	public void shutdown()
	{
		if (null != _currentGameSession)
		{
			_currentGameSession.shutdown();
		}
		_serverList.shutdown();
	}


	private void _handleHoverOverEntityInventoryItem(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		if (_leftClick)
		{
			// Select this in the hotbar (this will clear if already set).
			_currentGameSession.client.setSelectedItemKeyOrClear(entityInventoryKey);
		}
		else if (_rightClick)
		{
			_currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _viewingFuelInventory);
		}
		else if (_leftShiftClick)
		{
			_currentGameSession.client.pushItemsToBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, _viewingFuelInventory);
		}
	}

	private void _pullFromBlockToEntityInventory(AbsoluteLocation targetBlock, int entityInventoryKey)
	{
		// Note that we ignore the result since this will be reflected in the UI, if valid.
		if (_rightClick)
		{
			_currentGameSession.client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ONE, _viewingFuelInventory);
		}
		else if (_leftShiftClick)
		{
			_currentGameSession.client.pullItemsFromBlockInventory(targetBlock, entityInventoryKey, ClientWrapper.TransferQuantity.ALL, _viewingFuelInventory);
		}
	}

	private boolean _didOpenStationInventory(AbsoluteLocation blockLocation)
	{
		// See if there is an inventory we can open at the given block location.
		// NOTE:  We don't use this mechanism to talk about air blocks (or other empty blocks with ad-hoc inventories), only actual blocks.
		BlockProxy proxy = _currentGameSession.blockLookup.apply(blockLocation);
		boolean didOpen = false;
		Block block = proxy.getBlock();
		if (_env.stations.getNormalInventorySize(block) > 0)
		{
			// We are at least some kind of station with an inventory.
			_uiState = _UiState.INVENTORY;
			_openStationLocation = blockLocation;
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_viewingFuelInventory = false;
			_captureState.shouldCaptureMouse(false);
			didOpen = true;
		}
		return didOpen;
	}

	private boolean _canAct(AbsoluteLocation selectedBlock)
	{
		// We apply our delay here.
		boolean canAct;
		long currentMillis = System.currentTimeMillis();
		if (null == selectedBlock)
		{
			// This is placing a block or interacting with a block/entity so we always apply the delay.
			if (currentMillis > (_lastActionMillis + MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS))
			{
				_lastActionMillis = currentMillis;
				canAct = true;
			}
			else
			{
				canAct = false;
			}
		}
		else
		{
			if (selectedBlock.equals(_lastActionBlock))
			{
				// We can continue breaking the current block, no matter the time.
				_lastActionMillis = currentMillis;
				canAct = true;
			}
			else
			{
				// If this is something else, apply the delay.
				if (currentMillis > (_lastActionMillis + MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS))
				{
					_lastActionBlock = selectedBlock;
					_lastActionMillis = currentMillis;
					canAct = true;
				}
				else
				{
					canAct = false;
				}
			}
		}
		return canAct;
	}

	/**
	 * Called when we perform some action which isn't directly breaking a block so that they next frame doesn't capture
	 * the event and accidentally break/place a block.
	 */
	private void _updateLastActionMillis()
	{
		_lastActionBlock = null;
		_lastActionMillis = System.currentTimeMillis();
	}

	private IAction _drawStartStateWindows()
	{
		// Draw whatever is common to states where we draw interactive buttons on top.
		_ui.enterUiRenderMode();
		
		String menuTitle = "October Peaks";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.5f, menuTitle);
		IAction action = null;
		action = _renderViewChainAction(_singlePlayerButton, new Rect(-0.3f, 0.2f, 0.3f, 0.3f), action);
		action = _renderViewChainAction(_multiPlayerButton, new Rect(-0.3f, 0.1f, 0.3f, 0.2f), action);
		action = _renderViewChainAction(_optionsButton, new Rect(-0.3f, 0.0f, 0.3f, 0.1f), action);
		action = _renderViewChainAction(_keyBindingsButton, new Rect(-0.3f, -0.1f, 0.3f, 0.0f), action);
		action = _renderViewChainAction(_quitButton, new Rect(-0.2f, -0.6f, 0.2f, -0.5f), action);
		
		return action;
	}

	private IAction _drawListSinglePlayerStateWindows()
	{
		_ui.enterUiRenderMode();
		
		String menuTitle = "Single Player Worlds";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.8f, menuTitle);
		IAction action = null;
		float halfListWidth = SINGLE_PLAYER_WORLD_ROW_WIDTH / 2.0f;
		action = _renderViewChainAction(_worldListView, new Rect(-halfListWidth, -0.6f, halfListWidth, 0.6f), action);
		action = _renderViewChainAction(_enterCreateSingleState, new Rect(-0.2f, -0.7f, 0.2f, -0.6f), action);
		action = _renderViewChainAction(_backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f), action);
		
		return action;
	}

	private IAction _drawConfirmDeleteSinglePlayerStateWindows()
	{
		_ui.enterUiRenderMode();
		
		String menuTitle = "Confirm Delete?";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.8f, menuTitle);
		IAction action = null;
		action = _renderViewChainAction(_confirmDeleteButton, new Rect(-0.6f, -0.1f, 0.6f, 0.0f), action);
		action = _renderViewChainAction(_backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f), action);
		
		return action;
	}

	private IAction _drawNewSinglePlayerStateWindows()
	{
		_ui.enterUiRenderMode();
		
		String menuTitle = "Create Single Player World";
		float margin = 0.6f;
		float divider = -0.2f;
		float nextTop = 0.8f;
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, nextTop, menuTitle);
		IAction action = null;
		nextTop -= 0.2f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("World Generator")), new Rect(-margin, nextTop - 0.1f, divider, nextTop), action);
		action = _renderViewChainAction(_newWorldGeneratorNameButton, new Rect(divider, nextTop - 0.1f, margin, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("Player Mode")), new Rect(-margin, nextTop - 0.1f, divider, nextTop), action);
		action = _renderViewChainAction(_newDefaultPlayerModeButton, new Rect(divider, nextTop - 0.1f, margin, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("Difficulty")), new Rect(-margin, nextTop - 0.1f, divider, nextTop), action);
		action = _renderViewChainAction(_newDifficultyButton, new Rect(divider, nextTop - 0.1f, margin, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("Seed Override")), new Rect(-margin, nextTop - 0.1f, divider, nextTop), action);
		action = _renderViewChainAction(_newWorldSeedTextField, new Rect(divider, nextTop - 0.1f, margin, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("World Name")), new Rect(-margin, nextTop - 0.1f, divider, nextTop), action);
		action = _renderViewChainAction(_newWorldNameTextField, new Rect(divider, nextTop - 0.1f, margin, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(_createWorldButton, new Rect(-0.4f, nextTop - 0.1f, 0.4f, nextTop), action);
		nextTop -= 0.2f;
		action = _renderViewChainAction(_backButton, new Rect(-0.3f, nextTop - 0.1f, 0.3f, nextTop), action);
		
		return action;
	}

	private IAction _drawListMultiPlayerStateWindows()
	{
		_ui.enterUiRenderMode();
		
		String menuTitle = "Multi-Player servers";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.8f, menuTitle);
		IAction action = null;
		float halfListWidth = MULTI_PLAYER_SERVER_ROW_WIDTH / 2.0f;
		action = _renderViewChainAction(_serverListView, new Rect(-halfListWidth, -0.6f, halfListWidth, 0.6f), action);
		action = _renderViewChainAction(_enterAddNewServerButton, new Rect(-0.2f, -0.7f, 0.2f, -0.6f), action);
		action = _renderViewChainAction(_backButton, new Rect(-0.2f, -0.9f, 0.2f, -0.8f), action);
		
		return action;
	}

	private IAction _drawNewMultiPlayerStateWindows()
	{
		_ui.enterUiRenderMode();
		
		String menuTitle = "New Server Connection";
		float margin = 0.6f;
		float divider = -0.2f;
		float nextTop = 0.8f;
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, nextTop, menuTitle);
		IAction action = null;
		nextTop -= 0.2f;
		action = _renderViewChainAction(_currentlyTestingServerView, new Rect(-margin, nextTop - 0.2f, margin, nextTop), action);
		nextTop -= 0.3f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("Server IP:port")), new Rect(-margin, nextTop - 0.1f, divider, nextTop), action);
		action = _renderViewChainAction(_newServerAddressTextField, new Rect(divider, nextTop - 0.1f, margin, nextTop), action);
		nextTop -= 0.2f;
		action = _renderViewChainAction(_testServerButton, new Rect(-margin, nextTop - 0.1f, 0.0f, nextTop), action);
		action = _renderViewChainAction(_saveServerButton, new Rect(0.0f, nextTop - 0.1f, margin, nextTop), action);
		nextTop -= 0.2f;
		action = _renderViewChainAction(_backButton, new Rect(-0.3f, nextTop - 0.1f, 0.3f, nextTop), action);
		
		return action;
	}

	private IAction _drawInventoryStateWindows()
	{
		// We are in inventory mode but we will need to handle station/floor cases differently.
		Inventory relevantInventory = null;
		Inventory inventoryToCraftFrom = null;
		List<Craft> validCrafts = null;
		CraftOperation currentOperation = null;
		String stationName = "Floor";
		ItemTuple<Void> fuelSlot = null;
		boolean isAutomaticCrafting = false;
		if (null != _openStationLocation)
		{
			// We are in station mode so check this block's inventory and crafting (potentially clearing it if it is no longer a station).
			BlockProxy stationBlock = _currentGameSession.blockLookup.apply(_openStationLocation);
			Block stationType = stationBlock.getBlock();
			
			if (_env.stations.getNormalInventorySize(stationType) > 0)
			{
				Inventory stationInventory = stationBlock.getInventory();
				inventoryToCraftFrom = stationInventory;
				// If we are viewing the fuel inventory, we want to use that, instead.
				FuelState fuel = stationBlock.getFuel();
				if (null != fuel)
				{
					if (_viewingFuelInventory)
					{
						stationInventory = fuel.fuelInventory();
					}
					Item currentFuel = fuel.currentFuel();
					if (null != currentFuel)
					{
						long totalFuel = _env.fuel.millisOfFuel(currentFuel);
						long remainingFuel = fuel.millisFuelled();
						float fuelRemaining = (float)remainingFuel / (float) totalFuel;
						fuelSlot = new ItemTuple<>(currentFuel, 0, fuelRemaining, null);
					}
				}
				else
				{
					// This is invalid so just clear it.
					_viewingFuelInventory = false;
				}
				
				// Find the crafts for this station type.
				Set<String> classifications = _env.stations.getCraftingClasses(stationType);
				
				relevantInventory = stationInventory;
				validCrafts = _env.crafting.craftsForClassifications(classifications);
				// We will convert these into CraftOperation instances so we can splice in the current craft.
				currentOperation = stationBlock.getCrafting();
				if (0 == _env.stations.getManualMultiplier(stationType))
				{
					isAutomaticCrafting = true;
				}
				stationName = stationType.item().name();
				if (_viewingFuelInventory)
				{
					stationName += " Fuel";
				}
			}
			else
			{
				// This is no longer a station.
				_openStationLocation = null;
				_continuousInInventory = null;
				_continuousInBlock = null;
			}
		}
		
		Inventory entityInventory = _thisEntityInventoryBinding.get();
		if (null == _openStationLocation)
		{
			// We are just looking at the floor at our feet.
			Entity thisEntity = _entityBinding.get();
			AbsoluteLocation feetBlock = GeometryHelpers.getCentreAtFeet(thisEntity, _playerVolume);
			BlockProxy thisBlock = _currentGameSession.blockLookup.apply(feetBlock);
			Inventory floorInventory = thisBlock.getInventory();
			
			relevantInventory = floorInventory;
			inventoryToCraftFrom = entityInventory;
			// We are just looking at the entity inventory so find the built-in crafting recipes.
			validCrafts = _env.crafting.craftsForClassifications(Set.of(CraftAspect.BUILT_IN));
			// We will convert these into CraftOperation instances so we can splice in the current craft.
			currentOperation = thisEntity.localCraftOperation();
		}
		
		Inventory finalInventoryToCraftFrom = inventoryToCraftFrom;
		final CraftOperation finalCraftOperation = currentOperation;
		Craft currentCraft = (null != currentOperation) ? currentOperation.selectedCraft() : null;
		boolean canBeManuallySelected = !isAutomaticCrafting;
		List<CraftDescription> convertedCrafts = validCrafts.stream()
				.map((Craft craft) -> {
					long progressMillis = 0L;
					if (craft == currentCraft)
					{
						progressMillis = finalCraftOperation.completedMillis();
					}
					float progress = (float)progressMillis / (float)craft.millisPerCraft;
					CraftDescription.ItemRequirement[] requirements = Arrays.stream(craft.input)
							.map((Items input) -> {
								Item type = input.type();
								int available = finalInventoryToCraftFrom.getCount(type);
								return new CraftDescription.ItemRequirement(type, input.count(), available);
							})
							.toArray((int size) -> new CraftDescription.ItemRequirement[size])
					;
					// Note that we are assuming that there is only one output type.
					return new CraftDescription(craft
							, new Items(craft.output[0], craft.output.length)
							, requirements
							, progress
							, canBeManuallySelected
					);
				})
				.toList()
		;
		
		String craftingType = isAutomaticCrafting
				? "Automatic Crafting"
				: "Manual Crafting"
		;
		
		// We need to update our bindings BEFORE rendering anything.
		_bottomWindowInventoryBinding.set(relevantInventory);
		_bottomWindowTitleBinding.set(stationName);
		_bottomWindowFuelBinding.set(fuelSlot);
		_craftingPanelTitleBinding.set(craftingType);
		_craftingPanelBinding.set(convertedCrafts);
		_isManualCraftingStation = canBeManuallySelected;
		
		// Now, do the actual drawing.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter();
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
		}
		
		IAction action = null;
		// We will show the crafting panel as long as there are any valid crafts.
		if (!convertedCrafts.isEmpty())
		{
			IAction hover = _craftingWindow.doRender(_cursor);
			if (null != hover)
			{
				action = hover;
			}
		}
		IAction hover = _thisEntityInventoryWindow.doRender(_cursor);
		if (null != hover)
		{
			action = hover;
		}
		hover = _bottomInventoryWindow.doRender(_cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// We are in windowed mode so also draw the armour slots.
		hover = _armourWindow.doRender(_cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action)
		{
			action.renderHover(_cursor);
		}
		
		// Return any action so that the caller can run the action now that rendering is finished.
		return action;
	}

	private IAction _drawPauseStateWindows()
	{
		_drawCommonPauseBackground();
		
		// Draw the menu title and other UI.
		String menuTitle = _isRunningOnServer ? "Connected to server" : "Paused";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.5f, menuTitle);
		IAction action = null;
		action = _renderViewChainAction(_returnToGameButton, new Rect(-0.3f, 0.2f, 0.3f, 0.3f), action);
		action = _renderViewChainAction(_optionsButton, new Rect(-0.3f, 0.0f, 0.3f, 0.1f), action);
		action = _renderViewChainAction(_keyBindingsButton, new Rect(-0.3f, -0.2f, 0.3f, -0.1f), action);
		action = _renderViewChainAction(_exitButton, new Rect(-0.2f, -0.5f, 0.2f, -0.4f), action);
		
		return action;
	}

	private IAction _drawPlayStateWindows()
	{
		// In this case, just draw the common UI elements.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter();
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
		}
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _selectionWindow.doRender(_cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		return null;
	}

	private IAction _drawOptionsStateWindows()
	{
		if (null != _currentGameSession)
		{
			_drawCommonPauseBackground();
		}
		
		// Draw the menu title and other UI.
		String menuTitle = "Game Options";
		float nextTop = 0.8f;
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, nextTop, menuTitle);
		IAction action = null;
		nextTop -= 0.2f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("Toggle Display")), new Rect(-0.6f, nextTop - 0.1f, -0.2f, nextTop), action);
		action = _renderViewChainAction(_fullScreenButton, new Rect(-0.2f, nextTop - 0.1f, 0.6f, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("View Distance")), new Rect(-0.6f, nextTop - 0.1f, -0.2f, nextTop), action);
		action = _renderViewChainAction(_viewDistanceControl, new Rect(-0.2f, nextTop - 0.1f, 0.6f, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("Scene Brightness")), new Rect(-0.6f, nextTop - 0.1f, -0.2f, nextTop), action);
		action = _renderViewChainAction(_brightnessControl, new Rect(-0.2f, nextTop - 0.1f, 0.6f, nextTop), action);
		nextTop -= 0.1f;
		action = _renderViewChainAction(new ViewTextLabel(_ui, new Binding<>("Multiplayer Name")), new Rect(-0.6f, nextTop - 0.1f, -0.2f, nextTop), action);
		action = _renderViewChainAction(_clientNameTextField, new Rect(-0.2f, nextTop - 0.1f, 0.6f, nextTop), action);
		nextTop -= 0.2f;
		action = _renderViewChainAction(_backButton, new Rect(-0.3f, nextTop - 0.1f, 0.3f, nextTop), action);
		
		return action;
	}

	private IAction _drawKeyBindingStateWindows()
	{
		if (null != _currentGameSession)
		{
			_drawCommonPauseBackground();
		}
		
		// Draw the menu title and other UI.
		String menuTitle = "Key Bindings";
		UiIdioms.drawRawTextCentredAtTop(_ui, 0.0f, 0.8f, menuTitle);
		IAction action = null;
		action = _renderViewChainAction(_keyBindingSelectorControl, new Rect(-0.4f, -0.9f, 0.4f, 0.6f), action);
		action = _renderViewChainAction(_backButton, new Rect(-0.2f, -0.8f, 0.2f, -0.7f), action);
		
		return action;
	}

	private void _drawCommonPauseBackground()
	{
		// Draw whatever is common to states where we draw interactive buttons on top.
		_ui.enterUiRenderMode();
		
		_handleEyeFilter();
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(_cursor);
			Assert.assertTrue(null == noAction);
		}
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _selectionWindow.doRender(_cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		// Draw the overlay to dim the window.
		_ui.drawWholeTextureRect(_ui.pixelDarkGreyAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
	}

	private void _handleEyeFilter()
	{
		// If our eye is under a liquid, draw the liquid over the screen (we do this here since it is part of the orthographic plane and not logically part of the scene).
		if (null != _eyeBlockLocation)
		{
			BlockProxy eyeProxy = _currentGameSession.blockLookup.apply(_eyeBlockLocation);
			if (null != eyeProxy)
			{
				Block blockType = eyeProxy.getBlock();
				if (_waterBlockTypes.contains(blockType))
				{
					_ui.drawWholeTextureRect(_ui.pixelBlueAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
				}
				else if (_lavaBlockTypes.contains(blockType))
				{
					_ui.drawWholeTextureRect(_ui.pixelOrangeLava, -1.0f, -1.0f, 1.0f, 1.0f);
				}
			}
		}
	}

	private IAction _renderViewChainAction(IView view, Rect location, IAction existingAction)
	{
		IAction tempAction = view.render(location, _cursor);
		return (null != tempAction)
				? tempAction
				: existingAction
		;
	}

	private void _drawRelevantWindows()
	{
		// Perform state-specific drawing.
		IAction action;
		switch (_uiState)
		{
		case START:
			action = _drawStartStateWindows();
			break;
		case LIST_SINGLE_PLAYER:
			action = _drawListSinglePlayerStateWindows();
			break;
		case CONFIRM_DELETE_SINGLE_PLAYER:
			action = _drawConfirmDeleteSinglePlayerStateWindows();
			break;
		case NEW_SINGLE_PLAYER:
			action = _drawNewSinglePlayerStateWindows();
			break;
		case LIST_MULTI_PLAYER:
			action = _drawListMultiPlayerStateWindows();
			break;
		case NEW_MULTI_PLAYER:
			action = _drawNewMultiPlayerStateWindows();
			break;
		case OPTIONS:
			action = _drawOptionsStateWindows();
			break;
		case KEY_BINDINGS:
			action = _drawKeyBindingStateWindows();
			break;
		case PLAY:
			action = _drawPlayStateWindows();
			break;
		case INVENTORY:
			action = _drawInventoryStateWindows();
			break;
		case PAUSE:
			action = _drawPauseStateWindows();
			break;
		default:
			throw Assert.unreachable();
		}
		
		// Run any actions based on clicking on the UI.
		if (null != action)
		{
			action.takeAction();
		}
	}

	private void _finalizeFrameEvents(PartialEntity entity, AbsoluteLocation stopBlock, AbsoluteLocation preStopBlock)
	{
		// See if we need to update our orientation.
		if (_orientationNeedsFlush)
		{
			_currentGameSession.client.setOrientation(_yawRadians, _pitchRadians);
			_orientationNeedsFlush = false;
		}
		
		// See if the click refers to anything selected.
		if (_mouseHeld0)
		{
			if (null != stopBlock)
			{
				if (_canAct(stopBlock))
				{
					_currentGameSession.client.hitBlock(stopBlock);
				}
			}
			else if (null != entity)
			{
				if (_mouseClicked0)
				{
					_currentGameSession.client.hitEntity(entity);
					_updateLastActionMillis();
				}
			}
		}
		else if (_mouseHeld1)
		{
			boolean didAct = false;
			if (null != stopBlock)
			{
				// First, see if we need to change the UI state if this is a station we just clicked on.
				if (_mouseClicked1)
				{
					didAct = _didOpenStationInventory(stopBlock);
				}
			}
			else if (null != entity)
			{
				if (_mouseClicked1)
				{
					// Try to apply the selected item to the entity (we consider this an action even if it did nothing).
					_currentGameSession.client.applyToEntity(entity);
					_updateLastActionMillis();
					didAct = true;
				}
			}
			
			// If we still didn't do anything, try clicks on the block or self.
			if (!didAct && _mouseClicked1 && (null != stopBlock) && (null != preStopBlock))
			{
				didAct = _currentGameSession.client.runRightClickOnBlock(stopBlock, preStopBlock);
				if (didAct)
				{
					_updateLastActionMillis();
				}
			}
			if (!didAct && _mouseClicked1)
			{
				didAct = _currentGameSession.client.runRightClickOnSelf();
				if (didAct)
				{
					_updateLastActionMillis();
				}
			}
			if (!didAct && (null != stopBlock) && (null != preStopBlock))
			{
				if (_canAct(stopBlock))
				{
					// In this case, we either want to place a block or repair a block.
					didAct = _currentGameSession.client.runPlaceBlock(stopBlock, preStopBlock);
					if (!didAct)
					{
						didAct = _currentGameSession.client.runRepairBlock(stopBlock);
					}
				}
				else
				{
					didAct = false;
				}
			}
		}
		
		ViscosityReader reader = new ViscosityReader(_env, _currentGameSession.blockLookup);
		if (_didWalkInFrame && SpatialHelpers.isStandingOnGround(reader, _entityBinding.get().location(), _playerVolume))
		{
			_currentGameSession.audioManager.setWalking();
		}
		else
		{
			_currentGameSession.audioManager.setStanding();
		}
	}

	/**
	 * Continues any active operations and completes accounting for time in a frame where the game is active and not
	 * paused.
	 */
	private void _idleInActiveFrame()
	{
		// If we took no action, just tell the client to pass time.
		if (!_didAccountForTimeInFrame)
		{
			_currentGameSession.client.doNothing(_continuousInInventory, _openStationLocation, _continuousInBlock);
		}
		
		_didAccountForTimeInFrame = false;
		_didWalkInFrame = false;
	}

	private void _clearEvents()
	{
		_mouseHeld0 = false;
		_mouseHeld1 = false;
		_mouseClicked0 = false;
		_mouseClicked1 = false;
		
		_leftClick = false;
		_leftShiftClick = false;
		_rightClick = false;
	}

	private void _doBackStateTransition()
	{
		switch (_uiState)
		{
		case START:
			// Key events are ignored in start state.
			break;
		case LIST_SINGLE_PLAYER:
			// We just want to go back.
			_uiState = _UiState.START;
			break;
		case CONFIRM_DELETE_SINGLE_PLAYER:
			// Go back to the list.
			_uiState = _UiState.LIST_SINGLE_PLAYER;
			break;
		case NEW_SINGLE_PLAYER:
			// Go back to the list.
			_uiState = _UiState.LIST_SINGLE_PLAYER;
			break;
		case LIST_MULTI_PLAYER:
			// We just want to go back.
			_uiState = _UiState.START;
			break;
		case NEW_MULTI_PLAYER:
			// Go back to the list.
			_uiState = _UiState.LIST_MULTI_PLAYER;
			break;
		case INVENTORY:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			break;
		case PAUSE:
			_uiState = _UiState.PLAY;
			_captureState.shouldCaptureMouse(true);
			_currentGameSession.client.resumeGame();
			break;
		case PLAY:
			_uiState = _UiState.PAUSE;
			_openStationLocation = null;
			_captureState.shouldCaptureMouse(false);
			_currentGameSession.client.pauseGame();
			break;
		case OPTIONS:
			// Write-back preferences.
			_mutablePreferences.saveToDisk();
			// Options depends on whether is a game playing.
			if (null != _currentGameSession)
			{
				_uiState = _UiState.PAUSE;
			}
			else
			{
				_uiState = _UiState.START;
			}
			break;
		case KEY_BINDINGS:
			if (null != _currentlyChangingControl)
			{
				_currentlyChangingControl = null;
			}
			else
			{
				// Key bindings depends on whether is a game playing.
				if (null != _currentGameSession)
				{
					_uiState = _UiState.PAUSE;
				}
				else
				{
					_uiState = _UiState.START;
				}
			}
			break;
		}
		// Any meaning of "back" should stop text input.
		_typingCapture = null;
	}

	private void _enterSingleWorld(GL20 gl, File localStorageDirectory
		, LoadedResources resources
		, String directoryName
		, WorldConfig.WorldGeneratorName worldGeneratorName
		, WorldConfig.DefaultPlayerMode defaultPlayerMode
		, Difficulty difficulty
		, Integer basicWorldGeneratorSeed
	)
	{
		_uiState = _UiState.PLAY;
		_captureState.shouldCaptureMouse(true);
		File localWorldDirectory = new File(localStorageDirectory, directoryName);
		try
		{
			_currentGameSession = new GameSession(_env
				, gl
				, _mutablePreferences.screenBrightness
				, resources
				, "Local"
				, null
				, localWorldDirectory
				, worldGeneratorName
				, defaultPlayerMode
				, difficulty
				, basicWorldGeneratorSeed
				, this
			);
		}
		catch (ConnectException e)
		{
			// There are no connections in this case.
			throw Assert.unexpected(e);
		}
		// TODO:  Use an intermediate state for this delay.
		_currentGameSession.finishStartup();
		_isRunningOnServer = false;
		// We can exit from here since we have a return-to state.
		_exitButtonBinding.set(_isRunningOnServer ? "Disconnect" : "Exit");
	}

	private void _connectToServer(GL20 gl, File localStorageDirectory, LoadedResources resources, String clientName, InetSocketAddress serverAddress) throws ConnectException
	{
		_currentGameSession = new GameSession(_env, gl, _mutablePreferences.screenBrightness, resources, clientName, serverAddress, null, null, null, null, null, this);
		// TODO:  Use an intermediate state for this delay.
		_currentGameSession.finishStartup();
		_isRunningOnServer = true;
		// We can exit from here since we have a return-to state.
		_exitButtonBinding.set(_isRunningOnServer ? "Disconnect" : "Exit");
		
		// We will only change state if nothing went wrong.
		_uiState = _UiState.PLAY;
		_captureState.shouldCaptureMouse(true);
	}

	private static void _rebuildSinglePlayerListBinding(Binding<List<String>> worldListBinding, File localStorageDirectory)
	{
		List<String> worldNames = List.of(localStorageDirectory.list((File dir, String name) -> name.startsWith(WORLD_DIRECTORY_PREFIX)));
		worldListBinding.set(worldNames);
	}

	private static void _deleteWorldRecursively(File directory)
	{
		// We should only see directories this way (unless someone was messing with our on-disk data).
		Assert.assertTrue(directory.isDirectory());
		
		// Walk all the files, recursively deleting directories.
		for (File sub : directory.listFiles())
		{
			if (sub.isDirectory())
			{
				_deleteWorldRecursively(sub);
			}
			else
			{
				sub.delete();
			}
		}
		directory.delete();
	}

	private void _commonWalk(MovementAccumulator.Relative relative, WalkType walk)
	{
		if (WalkType.SNEAK == walk)
		{
			_currentGameSession.client.sneak(relative);
		}
		else
		{
			boolean runningSpeed = (WalkType.RUN == walk);
			_currentGameSession.client.accelerateHorizontal(relative, runningSpeed);
		}
		_didAccountForTimeInFrame = true;
		_didWalkInFrame = true;
	}


	/**
	 *  Represents the high-level state of the UI.  This will likely be split out into a class to specifically manage UI
	 *  state, later one.
	 */
	private static enum _UiState
	{
		/**
		 * The game starts here when invoked without any arguments.  It presents a starting menu to create/join games.
		 */
		START,
		/**
		 * The state where we show a list of existing single-player worlds and present an option to create a new one.
		 */
		LIST_SINGLE_PLAYER,
		/**
		 * The state where we just allow confirmation to delete a single-player world.
		 */
		CONFIRM_DELETE_SINGLE_PLAYER,
		/**
		 * The state where we present an option to create a new one.
		 */
		NEW_SINGLE_PLAYER,
		/**
		 * The state where we show a list of known multi-player servers.
		 */
		LIST_MULTI_PLAYER,
		/**
		 * The state where present an option to add a new one server to our list.
		 */
		NEW_MULTI_PLAYER,
		/**
		 * The UI state under the PAUSE screen where we enter an options menu to view/change settings.
		 * If there is a _currentGameSession, it will be shown in the background.
		 */
		OPTIONS,
		/**
		 * The UI state under the PAUSE screen where the user can change key bindings.
		 * If there is a _currentGameSession, it will be shown in the background.
		 */
		KEY_BINDINGS,
		
		// These modes are specific to something involving a running game .
		/**
		 * The mode where play is normal.  Cursor is captured and there is no open window.
		 */
		PLAY,
		/**
		 * The mode where player control is largely disabled and the interface is mostly about clicking on buttons, etc.
		 */
		INVENTORY,
		/**
		 * The mode where play is effectively "paused".  The cursor is released and buttons to change game setup will be
		 * presented.
		 * Note that we call the state "paused" even though servers are never "paused" (the title will reflect this
		 * difference).
		 */
		PAUSE,
	}


	/**
	 * Methods passed in from a higher-level component to control other aspects of the native window manager environment
	 * required by the internal logic.
	 */
	public static interface ICallouts
	{
		public void shouldCaptureMouse(boolean setCapture);
	}

	public static enum WalkType
	{
		WALK,
		RUN,
		SNEAK,
	}
}
