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
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


public class OctoberPeaks extends ApplicationAdapter
{
	private final String _clientName;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _cuboids;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final InetSocketAddress _serverSocketAddress;
	private final SelectionManager _selectionManager;

	private Environment _environment;
	private GL20 _gl;
	private TextureAtlas _itemAtlas;
	private SceneRenderer _scene;
	private WindowManager _windowManager;
	private MovementControl _movement;
	private ClientWrapper _client;
	private UiStateManager _uiState;
	private InputManager _input;

	public OctoberPeaks(Options options)
	{
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
		_selectionManager = new SelectionManager(_blockLookup);
	}

	@Override
	public void create()
	{
		_environment = Environment.createSharedInstance();
		_gl = Gdx.graphics.getGL20();
		
		// Set common GL functionality for the view.
		_gl.glEnable(GL20.GL_BLEND);
		_gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		_gl.glEnable(GL20.GL_CULL_FACE);
		
		try
		{
			_itemAtlas = GraphicsHelpers.loadAtlasForItems(_gl
					, _environment.items.ITEMS_BY_TYPE
					, "missing_texture.png"
			);
			
			_scene = new SceneRenderer(_environment, _gl, _itemAtlas);
		}
		catch (IOException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		_windowManager = new WindowManager(_environment, _gl, _itemAtlas, _blockLookup);
		_movement = new MovementControl();
		_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
		
		_client = new ClientWrapper(_environment
				, new ClientWrapper.IUpdateConsumer() {
					@Override
					public void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
					{
						_cuboids.put(cuboid.getCuboidAddress(), cuboid);
						_scene.setCuboid(cuboid);
					}
					@Override
					public void updateExisting(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
					{
						_cuboids.put(cuboid.getCuboidAddress(), cuboid);
						_scene.setCuboid(cuboid);
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
						_scene.updatePosition(eye, target);
						_selectionManager.updatePosition(eye, target);
						_windowManager.setThisEntity(projectedEntity);
						_uiState.setThisEntity(projectedEntity);
					}
					@Override
					public void otherEntityUpdated(PartialEntity entity)
					{
						_scene.setEntity(entity);
						_selectionManager.setEntity(entity);
					}
					@Override
					public void otherEntityDidUnload(int id)
					{
						_scene.removeEntity(id);
						_selectionManager.removeEntity(id);
					}
				}
				, _clientName
				, _serverSocketAddress
		);
		_uiState = new UiStateManager(_movement, _client, _blockLookup, (boolean setCapture) -> _input.enterCaptureState(setCapture));
		_input = new InputManager();
		_client.finishStartup();
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	@Override
	public void render()
	{
		// Flush any captured input events.
		_input.flushEventsToStateManager(_uiState);
		
		// Find the selection, if the mode supports this.
		PartialEntity entity = null;
		AbsoluteLocation stopBlock = null;
		AbsoluteLocation preStopBlock = null;
		if (_uiState.canSelectInScene())
		{
			// See if the perspective changed.
			if (_uiState.didViewPerspectiveChange())
			{
				Vector eye = _movement.computeEye();
				Vector target = _movement.computeTarget();
				_selectionManager.updatePosition(eye, target);
				_scene.updatePosition(eye, target);
			}
			
			// Capture whatever is selected.
			SelectionManager.SelectionTuple selection = _selectionManager.findSelection();
			if (null != selection)
			{
				entity = selection.entity();
				stopBlock = selection.stopBlock();
				preStopBlock = selection.preStopBlock();
			}
		}
		
		// Reset the screen so we can draw this frame.
		_gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		_gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		_gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Draw the main scene first (since we only draw the other data on top of this).
		_scene.render(entity, stopBlock);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Draw the relevant windows on top of this scene (passing in any information describing the UI state).
		_uiState.drawRelevantWindows(_windowManager, stopBlock, entity);
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
		
		// Tear-down the shared environment.
		Environment.clearSharedInstance();
		_environment = null;
	}
}
