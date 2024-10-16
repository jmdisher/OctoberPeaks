package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.persistence.BasicWorldGenerator;
import com.jeffdisher.october.persistence.FlatWorldGenerator;
import com.jeffdisher.october.persistence.IWorldGenerator;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.server.TickRunner;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


public class ClientWrapper
{
	public static final int PORT = 5678;

	private final Environment _environment;
	private final IUpdateConsumer _updateConsumer;
	private final WorldConfig _config;
	private final ResourceLoader _loader;
	private final MonitoringAgent _monitoringAgent;
	private final ServerProcess _server;
	private final ClientProcess _client;

	// Data cached from the client listener.
	private final Map<CuboidAddress, IReadOnlyCuboidData> _cuboids;

	public ClientWrapper(Environment environment
			, IUpdateConsumer updateConsumer
			, String clientName
			, InetSocketAddress serverAddress
	)
	{
		_environment = environment;
		_updateConsumer = updateConsumer;
		
		try
		{
			// If we weren't given a server address, start the internal server.
			if (null == serverAddress)
			{
				System.out.println("Starting local server for single-player...");
				// We will just store the world in the current directory.
				File worldDirectory = new File("world");
				if (!worldDirectory.isDirectory())
				{
					Assert.assertTrue(worldDirectory.mkdirs());
				}
				
				// We will use the basic world generator, as that is our current standard generator.
				_config = new WorldConfig();
				boolean didLoadConfig = ResourceLoader.populateWorldConfig(worldDirectory, _config);
				IWorldGenerator worldGen;
				switch (_config.worldGeneratorName)
				{
				case BASIC:
					worldGen = new BasicWorldGenerator(_environment, _config.basicSeed);
					break;
				case FLAT:
					worldGen = new FlatWorldGenerator(true);
					break;
					default:
						throw Assert.unreachable();
				}
				if (!didLoadConfig)
				{
					// There is no config so ask the world-gen for the default spawn.
					EntityLocation spawnLocation = worldGen.getDefaultSpawnLocation();
					_config.worldSpawn = spawnLocation.getBlockLocation();
				}
				_loader = new ResourceLoader(worldDirectory
						, worldGen
						, _config.worldSpawn.toEntityLocation()
				);
				_monitoringAgent = new MonitoringAgent();
				_server = new ServerProcess(PORT
						, ServerRunner.DEFAULT_MILLIS_PER_TICK
						, _loader
						, () -> System.currentTimeMillis()
						, _monitoringAgent
						, _config
				);
				_client = new ClientProcess(new _ClientListener(), InetAddress.getLocalHost(), PORT, clientName);
			}
			else
			{
				System.out.println("Connecting to server: " + serverAddress);
				_loader = null;
				_config = null;
				_monitoringAgent = null;
				_server = null;
				_client = new ClientProcess(new _ClientListener(), serverAddress.getAddress(), serverAddress.getPort(), clientName);
			}
		}
		catch (IOException e)
		{
			// TODO:  Handle this network start-up failure or make sure it can't happen.
			throw Assert.unexpected(e);
		}
		
		_cuboids = new HashMap<>();
	}

	public void finishStartup()
	{
		// Wait for the initial entity data to appear.
		// We need to wait for a few ticks for everything to go through on the server and then be pushed through here.
		// TODO:  Better handle asynchronous start-up.
		try
		{
			long tick = _client.waitForLocalEntity(System.currentTimeMillis());
			_client.waitForTick(tick + 1, System.currentTimeMillis());
		}
		catch (InterruptedException e)
		{
			// We don't use interruption.
			throw Assert.unexpected(e);
		}
	}

	public void doNothing()
	{
		long currentTimeMillis = System.currentTimeMillis();
		_client.doNothing(currentTimeMillis);
	}

	public void disconnect()
	{
		_client.disconnect();
		if (null != _server)
		{
			_server.stop();
			// Look at how many ticks were run.
			TickRunner.Snapshot lastSnapshot = _monitoringAgent.getLastSnapshot();
			long ticksRun = (null != lastSnapshot)
					? lastSnapshot.tickNumber()
					: 0L
			;
			// Adjust the config's day start so that it will sync up with the time of day when ending.
			_config.dayStartTick = (int)PropagationHelpers.resumableStartTick(ticksRun, _config.ticksPerDay, _config.dayStartTick);
			// Write-back the world config.
			try
			{
				_loader.storeWorldConfig(_config);
			}
			catch (IOException e)
			{
				// This shouldn't happen since we already loaded it at the beginning so this would be a serious, and odd, problem.
				throw Assert.unexpected(e);
			}
		}
	}


	private class _ClientListener implements ClientProcess.IListener
	{
		private int _assignedLocalEntityId;
		@Override
		public void connectionClosed()
		{
			// TODO:  Handle this more gracefully in the future (we have no "connection interface" so there is not much to do beyond exit, at the moment).
			System.out.println("Connection closed");
			if (null != _server)
			{
				_server.stop();
			}
			System.exit(0);
		}
		@Override
		public void connectionEstablished(int assignedLocalEntityId)
		{
			_assignedLocalEntityId = assignedLocalEntityId;
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
		{
			_cuboids.put(cuboid.getCuboidAddress(), cuboid);
			_updateConsumer.updateExisting(cuboid, heightMap, changedBlocks);
		}
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			_cuboids.put(cuboid.getCuboidAddress(), cuboid);
			_updateConsumer.loadNew(cuboid, heightMap);
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			_cuboids.remove(address);
			_updateConsumer.unload(address);
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			Assert.assertTrue(_assignedLocalEntityId == authoritativeEntity.id());
			
			_updateConsumer.thisEntityUpdated(authoritativeEntity, authoritativeEntity);
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			Assert.assertTrue(_assignedLocalEntityId == authoritativeEntity.id());
			
			_updateConsumer.thisEntityUpdated(authoritativeEntity, projectedEntity);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			Assert.assertTrue(_assignedLocalEntityId != entity.id());
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			Assert.assertTrue(_assignedLocalEntityId != entity.id());
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
		}
		@Override
		public void tickDidComplete(long tickNumber)
		{
		}
		@Override
		public void configUpdated(int ticksPerDay, int dayStartTick)
		{
		}
		@Override
		public void otherClientJoined(int clientId, String name)
		{
		}
		@Override
		public void otherClientLeft(int clientId)
		{
		}
		@Override
		public void receivedChatMessage(int senderId, String message)
		{
			System.out.println("* " + senderId + "> " + message);
		}
	}

	public static interface IUpdateConsumer
	{
		void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap);
		void updateExisting(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks);
		void unload(CuboidAddress address);
		void thisEntityUpdated(Entity authoritativeEntity, Entity projectedEntity);
	}
}
