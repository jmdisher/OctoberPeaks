package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeMove;
import com.jeffdisher.october.mutations.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.mutations.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationPlaceSelectedBlock;
import com.jeffdisher.october.persistence.BasicWorldGenerator;
import com.jeffdisher.october.persistence.FlatWorldGenerator;
import com.jeffdisher.october.persistence.IWorldGenerator;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.server.TickRunner;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
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
	private Entity _thisEntity;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _cuboids;

	// Local state information to avoid redundant events, etc.
	private boolean _didJump;

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

	public void stepHorizontal(EntityChangeMove.Direction direction)
	{
		// Make sure that there is no in-progress action, first.
		long currentTimeMillis = System.currentTimeMillis();
		_client.moveHorizontalFully(direction, currentTimeMillis);
	}

	public boolean jumpOrSwim()
	{
		// See if we can jump or swim.
		boolean didMove = false;
		// Filter for redundant events.
		if (!_didJump)
		{
			Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = (AbsoluteLocation location) -> {
				IReadOnlyCuboidData cuboid = _cuboids.get(location.getCuboidAddress());
				return (null != cuboid)
						? new BlockProxy(location.getBlockAddress(), cuboid)
						: null
				;
			};
			EntityLocation location = _thisEntity.location();
			EntityLocation vector = _thisEntity.velocity();
			long currentTimeMillis = System.currentTimeMillis();
			
			if (EntityChangeJump.canJump(previousBlockLookUp
					, location
					, EntityConstants.getVolume(EntityType.PLAYER)
					, vector
			))
			{
				EntityChangeJump<IMutablePlayerEntity> jumpChange = new EntityChangeJump<>();
				_client.sendAction(jumpChange, currentTimeMillis);
				didMove = true;
				_didJump = true;
			}
			else if (EntityChangeSwim.canSwim(previousBlockLookUp
					, location
					, vector
			))
			{
				EntityChangeSwim<IMutablePlayerEntity> swimChange = new EntityChangeSwim<>();
				_client.sendAction(swimChange, currentTimeMillis);
				didMove = true;
				_didJump = true;
			}
		}
		return didMove;
	}

	/**
	 * Generates a hit event for the given location.  This may break the block or just damage it.
	 * 
	 * @param blockLocation The location of the block to hit.
	 * @return True if the hit event was sent or false if it wasn't valid.
	 */
	public boolean hitBlock(AbsoluteLocation blockLocation)
	{
		// Make sure that this is a block we can break.
		IReadOnlyCuboidData cuboid = _cuboids.get(blockLocation.getCuboidAddress());
		BlockProxy proxy = new BlockProxy(blockLocation.getBlockAddress(), cuboid);
		long currentTimeMillis = System.currentTimeMillis();
		boolean didHit = false;
		if (!_environment.blocks.canBeReplaced(proxy.getBlock()))
		{
			// This block is not the kind which can be replaced, meaning it can potentially be broken.
			_client.hitBlock(blockLocation, currentTimeMillis);
			didHit = true;
		}
		return didHit;
	}

	/**
	 * Running an action is a generic "right-click on block" situation, assuming it wasn't a block with an inventory.
	 * 
	 * @param solidBlock The solid block the user clicked.
	 * @param emptyBlock The block block before where the user clicked.
	 * @param isJustClicked True if the click just happened (false if it is held down).
	 */
	public void runRightClickAction(AbsoluteLocation solidBlock, AbsoluteLocation emptyBlock, boolean isJustClicked)
	{
		// We need to check our selected item and see what "action" is associated with it.
		int selectedKey = _thisEntity.hotbarItems()[_thisEntity.hotbarIndex()];
		Block solidBlockType = new BlockProxy(solidBlock.getBlockAddress(), _cuboids.get(solidBlock.getCuboidAddress())).getBlock();
		
		// First, see if the target block has a general logic state we can change.
		IMutationEntity<IMutablePlayerEntity> change;
		if (isJustClicked && EntityChangeSetBlockLogicState.canChangeBlockLogicState(solidBlockType))
		{
			boolean existingState = EntityChangeSetBlockLogicState.getCurrentBlockLogicState(solidBlockType);
			change = new EntityChangeSetBlockLogicState(solidBlock, !existingState);
		}
		else if (isJustClicked && _environment.items.getItemById("op.bed") == solidBlockType.item())
		{
			// This is a bed so we need to take a special action to set spawn and reset the day.
			change = new EntityChangeSetDayAndSpawn(solidBlock);
		}
		else if (Entity.NO_SELECTION != selectedKey)
		{
			// Check if a special use exists for this item and block or if we are just placing.
			change = null;
			if (isJustClicked)
			{
				// All special actions are only taken when we just clicked.
				Inventory inventory = _getEntityInventory();
				Items stack = inventory.getStackForKey(selectedKey);
				NonStackableItem nonStack = inventory.getNonStackableForKey(selectedKey);
				Item selectedType = (null != stack) ? stack.type() : nonStack.type();
				
				// First, can we use this on the block.
				if (EntityChangeUseSelectedItemOnBlock.canUseOnBlock(selectedType, solidBlockType))
				{
					change = new EntityChangeUseSelectedItemOnBlock(solidBlock);
				}
				// See if this block can just be activated, directly.
				else if (EntityChangeSetBlockLogicState.canChangeBlockLogicState(solidBlockType))
				{
					boolean existingState = EntityChangeSetBlockLogicState.getCurrentBlockLogicState(solidBlockType);
					change = new EntityChangeSetBlockLogicState(solidBlock, !existingState);
				}
				// Check to see if we can use it, directly.
				else if (EntityChangeUseSelectedItemOnSelf.canBeUsedOnSelf(selectedType))
				{
					change = new EntityChangeUseSelectedItemOnSelf();
				}
				else
				{
					// Fall-through to try placement.
					change = null;
				}
			}
			
			// We can place the block if we are right-clicking or holding.
			if (null == change)
			{
				// The mutation will check proximity and collision.
				change = new MutationPlaceSelectedBlock(emptyBlock, solidBlock);
			}
		}
		else
		{
			// Nothing to do.
			change = null;
		}
		
		if (null != change)
		{
			long currentTimeMillis = System.currentTimeMillis();
			_client.sendAction(change, currentTimeMillis);
		}
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


	private void _setEntity(Entity thisEntity)
	{
		_thisEntity = thisEntity;
		_didJump = false;
	}

	private Inventory _getEntityInventory()
	{
		Inventory inventory = _thisEntity.isCreativeMode()
				? CreativeInventory.fakeInventory()
				: _thisEntity.inventory()
		;
		return inventory;
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
			
			// To start, we will use the authoritative data as the projection.
			_setEntity(authoritativeEntity);
			_updateConsumer.thisEntityUpdated(authoritativeEntity, authoritativeEntity);
		}
		@Override
		public void thisEntityDidChange(Entity authoritativeEntity, Entity projectedEntity)
		{
			Assert.assertTrue(_assignedLocalEntityId == authoritativeEntity.id());
			
			// Locally, we just use the projection.
			_setEntity(projectedEntity);
			_updateConsumer.thisEntityUpdated(authoritativeEntity, projectedEntity);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			Assert.assertTrue(_assignedLocalEntityId != entity.id());
			
			_updateConsumer.otherEntityUpdated(entity);
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			Assert.assertTrue(_assignedLocalEntityId != entity.id());
			
			_updateConsumer.otherEntityUpdated(entity);
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			_updateConsumer.otherEntityDidUnload(id);
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
		
		void otherEntityUpdated(PartialEntity entity);
		void otherEntityDidUnload(int id);
	}
}
