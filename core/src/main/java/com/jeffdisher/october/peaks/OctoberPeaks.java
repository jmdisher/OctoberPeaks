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
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;


public class OctoberPeaks extends ApplicationAdapter
{
	private final String _clientName;
	private final InetSocketAddress _serverSocketAddress;

	private Environment _environment;
	private GL20 _gl;
	private SceneRenderer _scene;
	private MovementControl _movement;
	private ClientWrapper _client;
	private InputManager _input;

	public OctoberPeaks(Options options)
	{
		_clientName = options.clientName();
		_serverSocketAddress = options.serverAddress();
	}

	@Override
	public void create()
	{
		_environment = Environment.createSharedInstance();
		_gl = Gdx.graphics.getGL20();
		
		// Set common GL functionality for the view.
		_gl.glEnable(GL20.GL_BLEND);
		_gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glEnable(GL20.GL_CULL_FACE);
		
		try
		{
			_scene = new SceneRenderer(_gl);
		}
		catch (IOException e)
		{
			throw new AssertionError("Startup scene", e);
		}
		_movement = new MovementControl();
		_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
		
		_client = new ClientWrapper(_environment
				, new ClientWrapper.IUpdateConsumer() {
					@Override
					public void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
					{
						_scene.setCuboid(cuboid);
					}
					@Override
					public void updateExisting(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
					{
						_scene.setCuboid(cuboid);
					}
					@Override
					public void unload(CuboidAddress address)
					{
						_scene.removeCuboid(address);
					}
					@Override
					public void thisEntityUpdated(Entity authoritativeEntity, Entity projectedEntity)
					{
						EntityVolume volume = EntityConstants.getVolume(EntityType.PLAYER);
						_movement.setEye(Vector.fromEntity(projectedEntity.location(), volume.width() / 2.0f, volume.height()));
						_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
					}
				}
				, _clientName
				, _serverSocketAddress
		);
		_client.finishStartup();
		_input = new InputManager(_movement, _client);
	}

	@Override
	public void render()
	{
		// Handle any event processing.
		boolean shouldUpdatePosition = _input.shouldUpdateSceneRunningEvents();
		if (shouldUpdatePosition)
		{
			_scene.updatePosition(_movement.computeEye(), _movement.computeTarget());
		}
		
		_gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		_gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		_gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		
		_scene.render();
		
		// Handle any interactions with the client.
		_client.doNothing();
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
