package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


public class OctoberPeaks extends ApplicationAdapter
{
	private final String _clientName;
	private final InetSocketAddress _serverSocketAddress;
	private final SelectionManager _selectionManager;

	private Environment _environment;
	private GL20 _gl;
	private SceneRenderer _scene;
	private WindowManager _windowManager;
	private MovementControl _movement;
	private ClientWrapper _client;
	private InputManager _input;

	public OctoberPeaks(Options options)
	{
		_clientName = options.clientName();
		_serverSocketAddress = options.serverAddress();
		_selectionManager = new SelectionManager();
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
			_scene = new SceneRenderer(_gl);
		}
		catch (IOException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		_windowManager = new WindowManager(_gl);
		_movement = new MovementControl();
		_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
		
		_client = new ClientWrapper(_environment
				, new ClientWrapper.IUpdateConsumer() {
					@Override
					public void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
					{
						_scene.setCuboid(cuboid);
						_selectionManager.setCuboid(cuboid);
					}
					@Override
					public void updateExisting(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
					{
						_scene.setCuboid(cuboid);
						_selectionManager.setCuboid(cuboid);
					}
					@Override
					public void unload(CuboidAddress address)
					{
						_scene.removeCuboid(address);
						_selectionManager.removeCuboid(address);
					}
					@Override
					public void thisEntityUpdated(Entity authoritativeEntity, Entity projectedEntity)
					{
						EntityVolume volume = EntityConstants.getVolume(EntityType.PLAYER);
						_movement.setEye(Vector.fromEntity(projectedEntity.location(), volume.width() / 2.0f, volume.height()));
						Vector eye = _movement.computeEye();
						Vector target = _movement.computeTarget();
						_scene.updatePosition(eye, target);
						_selectionManager.updatePosition(eye, target);
						_windowManager.setThisEntity(projectedEntity);
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
		_client.finishStartup();
		_input = new InputManager(_movement, _client);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	@Override
	public void render()
	{
		// Handle any event processing.
		SelectionManager.SelectionTuple selection = _selectionManager.findSelection();
		PartialEntity entity = (null != selection) ? selection.entity() : null;
		AbsoluteLocation stopBlock = (null != selection) ? selection.stopBlock() : null;
		AbsoluteLocation preStopBlock = (null != selection) ? selection.preStopBlock() : null;

		boolean shouldUpdatePosition = _input.shouldUpdateSceneRunningEvents(entity, stopBlock, preStopBlock);
		if (shouldUpdatePosition)
		{
			Vector eye = _movement.computeEye();
			Vector target = _movement.computeTarget();
			_scene.updatePosition(eye, target);
			_selectionManager.updatePosition(eye, target);
		}
		
		_gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		_gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		_gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		_scene.render(entity, stopBlock);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Now, draw the common overlay windows (they are drawn in all cases, no matter the UI state).
		_windowManager.drawCommonOverlays();
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
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
