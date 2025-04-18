package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.peaks.scene.SceneRenderer;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.utils.GeometryHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


public class OctoberPeaks extends ApplicationAdapter
{
	private final Environment _environment;
	private final String _clientName;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _cuboids;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final InetSocketAddress _serverSocketAddress;

	private GL20 _gl;
	private TextureAtlas<ItemVariant> _itemAtlas;
	private SceneRenderer _scene;
	private EyeEffect _eyeEffect;
	private MovementControl _movement;
	private SelectionManager _selectionManager;
	private ClientWrapper _client;
	private AudioManager _audioManager;
	private InputManager _input;
	private UiStateManager _uiState;

	public OctoberPeaks(Options options)
	{
		_environment = Environment.createSharedInstance();
		_clientName = options.clientName();
		_cuboids = new HashMap<>();
		_blockLookup = (AbsoluteLocation location) -> {
			BlockProxy proxy = null;
			IReadOnlyCuboidData cuboid = _cuboids.get(location.getCuboidAddress());
			if (null != cuboid)
			{
				proxy = new BlockProxy(location.getBlockAddress(), cuboid);
			}
			return proxy;
		};
		_serverSocketAddress = options.serverAddress();
	}

	@Override
	public void create()
	{
		_gl = Gdx.graphics.getGL20();
		
		// Set common GL functionality for the view.
		_gl.glEnable(GL20.GL_BLEND);
		_gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		_gl.glEnable(GL20.GL_CULL_FACE);
		
		try
		{
			_itemAtlas = TextureHelpers.loadAtlasForItems(_gl
					, _environment.items.ITEMS_BY_TYPE
					, "missing_texture.png"
			);
			
			_scene = new SceneRenderer(_environment, _gl, _itemAtlas);
			_scene.rebuildProjection(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		}
		catch (IOException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		
		_eyeEffect = new EyeEffect(_gl);
		GlUi ui = new GlUi(_gl, _itemAtlas);
		_movement = new MovementControl();
		_scene.updatePosition(_movement.computeEye(), _movement.computeTarget(), _movement.computeUpVector());
		
		Map<Block, Prism> specialBlockBounds = _scene.getModelBoundingBoxes();
		_selectionManager = new SelectionManager(_environment, specialBlockBounds, _blockLookup);
		
		_client = new ClientWrapper(_environment
				, new ClientWrapper.IUpdateConsumer() {
					@Override
					public void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
					{
						_cuboids.put(cuboid.getCuboidAddress(), cuboid);
						_scene.setCuboid(cuboid, heightMap, null);
					}
					@Override
					public void updateExisting(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
					{
						_cuboids.put(cuboid.getCuboidAddress(), cuboid);
						_scene.setCuboid(cuboid, heightMap, changedBlocks);
					}
					@Override
					public void unload(CuboidAddress address)
					{
						IReadOnlyCuboidData removed = _cuboids.remove(address);
						Assert.assertTrue(null != removed);
						_scene.removeCuboid(address);
					}
					@Override
					public void thisEntityUpdated(Entity authoritativeEntity, Entity projectedEntity)
					{
						EntityLocation eyeLocation = SpatialHelpers.getEyeLocation(MutableEntity.existing(projectedEntity));
						_movement.setEye(Vector.fromEntityLocation(eyeLocation));
						Vector eye = _movement.computeEye();
						Vector target = _movement.computeTarget();
						Vector upVector = _movement.computeUpVector();
						_scene.updatePosition(eye, target, upVector);
						_selectionManager.updatePosition(eye, target);
						_uiState.setThisEntity(projectedEntity);
						_selectionManager.setThisEntity(projectedEntity);
						_eyeEffect.setThisEntity(projectedEntity);
						_audioManager.setThisEntity(authoritativeEntity, projectedEntity);
					}
					@Override
					public void thisEntityHurt()
					{
						_eyeEffect.thisEntityHurt();
						_audioManager.thisEntityHurt();
					}
					@Override
					public void otherClientJoined(int clientId, String name)
					{
						_uiState.otherPlayerJoined(clientId, name);
					}
					@Override
					public void otherClientLeft(int clientId)
					{
						_uiState.otherPlayerLeft(clientId);
					}
					@Override
					public void otherEntityUpdated(PartialEntity entity)
					{
						_scene.setEntity(entity);
						_selectionManager.setEntity(entity);
						_audioManager.setOtherEntity(entity);
					}
					@Override
					public void otherEntityDidUnload(int id)
					{
						_scene.removeEntity(id);
						_selectionManager.removeEntity(id);
						_audioManager.removeOtherEntity(id);
					}
					@Override
					public void otherEntityHurt(int id, AbsoluteLocation location)
					{
						_scene.entityHurt(id);
						_audioManager.otherEntityHurt(location, id);
					}
					@Override
					public void otherEntityKilled(int id, AbsoluteLocation location)
					{
						// NOTE:  ServerStateManager will always notify us of entity death, but only sends the location if we have seen them (to notify us that a player died, for example).
						if (null != location)
						{
							_audioManager.otherEntityKilled(location, id);
						}
					}
					@Override
					public void tickDidComplete(long gameTick, float skyLightMultiplier, float dayProgression)
					{
						_scene.setDayTime(dayProgression, skyLightMultiplier);
						_audioManager.tickCompleted();
					}
					@Override
					public void blockPlaced(AbsoluteLocation location)
					{
						_audioManager.blockPlaced(location);
					}
					@Override
					public void blockBroken(AbsoluteLocation location)
					{
						_audioManager.blockBroken(location);
					}
				}
				, _clientName
				, _serverSocketAddress
		);
		
		// Load the audio.
		_audioManager = AudioManager.load(_environment, Map.of(AudioManager.Cue.WALK, "walking.ogg"
				, AudioManager.Cue.TAKE_DAMAGE, "take_damage.ogg"
				, AudioManager.Cue.BREAK_BLOCK, "break_block.ogg"
				, AudioManager.Cue.PLACE_BLOCK, "place_block.ogg"
				, AudioManager.Cue.COW_IDLE, "cow_idle.ogg"
				, AudioManager.Cue.COW_DEATH, "cow_death.ogg"
				, AudioManager.Cue.COW_INJURY, "cow_injury.ogg"
				, AudioManager.Cue.ORC_IDLE, "orc_idle.ogg"
				, AudioManager.Cue.ORC_INJURY, "orc_injury.ogg"
				, AudioManager.Cue.ORC_DEATH, "orc_death.ogg"
		));
		
		// Create the input manager and connect the UI state manager to the relevant parts of the system.
		_input = new InputManager();
		_uiState = new UiStateManager(_environment, ui, _movement, _client, _audioManager, _blockLookup, new UiStateManager.IInputStateChanger() {
			@Override
			public void shouldCaptureMouse(boolean setCapture)
			{
				_input.enterCaptureState(setCapture);
			}
			@Override
			public void trySetPaused(boolean isPaused)
			{
				if (isPaused)
				{
					_client.pauseGame();
				}
				else
				{
					_client.resumeGame();
				}
			}
		});
		
		// Finish the rest of the startup now that the pieces are in place.
		_client.finishStartup();
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	@Override
	public void resize(int width, int height)
	{
		_scene.rebuildProjection(width, height);
	}

	@Override
	public void render()
	{
		// Flush any captured input events.
		_input.flushEventsToStateManager(_uiState);
		
		// Find the selection, if the mode supports this.
		WorldSelection selection = null;
		PartialEntity entity = null;
		AbsoluteLocation stopBlock = null;
		Block stopBlockType = null;
		AbsoluteLocation preStopBlock = null;
		if (_uiState.canSelectInScene())
		{
			// See if the perspective changed.
			if (_uiState.didViewPerspectiveChange())
			{
				Vector eye = _movement.computeEye();
				Vector target = _movement.computeTarget();
				Vector upVector = _movement.computeUpVector();
				_selectionManager.updatePosition(eye, target);
				_scene.updatePosition(eye, target, upVector);
				_uiState.updateEyeBlock(GeometryHelpers.locationFromVector(eye));
			}
			
			// Capture whatever is selected.
			selection = _selectionManager.findSelection();
			if (null != selection)
			{
				entity = selection.entity();
				stopBlock = selection.stopBlock();
				BlockProxy proxy = (null != stopBlock)
						? _blockLookup.apply(stopBlock)
						: null
				;
				if (null != proxy)
				{
					stopBlockType = proxy.getBlock();
				}
				preStopBlock = selection.preStopBlock();
			}
		}
		
		// Reset the screen so we can draw this frame.
		_gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		_gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		_gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Draw the main scene first (since we only draw the other data on top of this).
		_scene.render(entity, stopBlock, stopBlockType);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Draw any eye effect overlay.
		_eyeEffect.drawEyeEffect();
		
		// Draw the relevant windows on top of this scene (passing in any information describing the UI state).
		_uiState.drawRelevantWindows(selection);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Finalize the event processing with this selection and accounting for inter-frame time.
		// Note that this must be last since we deliver some events while drawing windows, etc, when we discover click locations, etc.
		_uiState.finalizeFrameEvents(entity, stopBlock, preStopBlock);
	}

	@Override
	public void dispose()
	{
		// Disconnect from the server.
		_client.disconnect();
		
		// Shut down the other components.
		_itemAtlas.shutdown(_gl);
		_scene.shutdown();
		_uiState.shutdown();
		
		// Tear-down the shared environment.
		Environment.clearSharedInstance();
	}
}
