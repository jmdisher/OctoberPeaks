package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.mutations.EntityChangeAccelerate;
import com.jeffdisher.october.mutations.EntityChangeAttackEntity;
import com.jeffdisher.october.mutations.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.mutations.EntityChangeJump;
import com.jeffdisher.october.mutations.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.mutations.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.mutations.EntityChangeSetOrientation;
import com.jeffdisher.october.mutations.EntityChangeSwapArmour;
import com.jeffdisher.october.mutations.EntityChangeSwim;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnEntity;
import com.jeffdisher.october.mutations.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.mutations.IMutationEntity;
import com.jeffdisher.october.mutations.MutationEntityPushItems;
import com.jeffdisher.october.mutations.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.mutations.MutationEntitySelectItem;
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
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
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
	private final ConsoleRunner _console;
	private boolean _isPaused;

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
				_console = ConsoleRunner.runInBackground(System.in
						, System.out
						, _monitoringAgent
						, _config
				);
			}
			else
			{
				System.out.println("Connecting to server: " + serverAddress);
				_loader = null;
				_config = null;
				_monitoringAgent = null;
				_server = null;
				_client = new ClientProcess(new _ClientListener(), serverAddress.getAddress(), serverAddress.getPort(), clientName);
				_console = null;
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
		if (_isPaused)
		{
			_client.advanceTime(currentTimeMillis);
		}
		else
		{
			_client.doNothing(currentTimeMillis);
		}
	}

	public void setOrientation(float yawRadians, float pitchRadians)
	{
		byte yaw = OrientationHelpers.yawFromRadians(yawRadians);
		byte pitch = OrientationHelpers.yawFromRadians(pitchRadians);
		EntityChangeSetOrientation<IMutablePlayerEntity> set = new EntityChangeSetOrientation<>(yaw, pitch);
		long currentTimeMillis = System.currentTimeMillis();
		Assert.assertTrue(!_isPaused);
		_client.sendAction(set, currentTimeMillis);
	}

	public void accelerateHorizontal(EntityChangeAccelerate.Relative relativeDirection)
	{
		long currentTimeMillis = System.currentTimeMillis();
		Assert.assertTrue(!_isPaused);
		_client.accelerateHorizontally(relativeDirection, currentTimeMillis);
	}

	public boolean jumpOrSwim()
	{
		// See if we can jump or swim.
		boolean didMove = false;
		Assert.assertTrue(!_isPaused);
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
		boolean didHit = false;
		Assert.assertTrue(!_isPaused);
		// This can be null when the action is taken due to loading issues, respawn, etc.
		if (null != cuboid)
		{
			BlockProxy proxy = new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			long currentTimeMillis = System.currentTimeMillis();
			if (!_environment.blocks.canBeReplaced(proxy.getBlock()))
			{
				// This block is not the kind which can be replaced, meaning it can potentially be broken.
				_client.hitBlock(blockLocation, currentTimeMillis);
				didHit = true;
			}
		}
		return didHit;
	}

	/**
	 * Runs any right-click actions on the given block, returning true if it was able to take any action.  Note that
	 * this includes any right-click action related to the block, potentially using the currently-selected inventory
	 * item.
	 * Note that this is only used in the cases where there is a solid block selected and it is a single click, not just
	 * held down.
	 * 
	 * @param solidBlock The solid block the user clicked (cannot be null).
	 * @param emptyBlock The block block before where the user clicked (cannot be null).
	 * @return True if an action was taken, false if no action was available.
	 */
	public boolean runRightClickOnBlock(AbsoluteLocation solidBlock, AbsoluteLocation emptyBlock)
	{
		Assert.assertTrue(null != solidBlock);
		Assert.assertTrue(null != emptyBlock);
		
		// We need to check our selected item and see what "action" is associated with it.
		int selectedKey = _thisEntity.hotbarItems()[_thisEntity.hotbarIndex()];
		
		Block solidBlockType = _getBlockType(solidBlock);
		Block emptyBlockType = _getBlockType(emptyBlock);
		
		// First, see if the target block has a general logic state we can change.
		IMutationEntity<IMutablePlayerEntity> change;
		if ((null == solidBlockType) || (null == emptyBlockType))
		{
			// The target isn't loaded.
			change = null;
		}
		else if (EntityChangeSetBlockLogicState.canChangeBlockLogicState(solidBlockType))
		{
			boolean existingState = EntityChangeSetBlockLogicState.getCurrentBlockLogicState(solidBlockType);
			change = new EntityChangeSetBlockLogicState(solidBlock, !existingState);
		}
		else if (_environment.items.getItemById("op.bed") == solidBlockType.item())
		{
			// This is a bed so we need to take a special action to set spawn and reset the day.
			change = new EntityChangeSetDayAndSpawn(solidBlock);
		}
		else if (Entity.NO_SELECTION != selectedKey)
		{
			// We have something selected so see if it can be applied to this block (bucket, fertilizer, etc).
			// Check if a special use exists for this item and block or if we are just placing.
			Inventory inventory = _getEntityInventory();
			Items stack = inventory.getStackForKey(selectedKey);
			NonStackableItem nonStack = inventory.getNonStackableForKey(selectedKey);
			Item selectedType = (null != stack) ? stack.type() : nonStack.type();
			
			// First, can we use this on the block.
			if (EntityChangeUseSelectedItemOnBlock.canUseOnBlock(selectedType, solidBlockType))
			{
				change = new EntityChangeUseSelectedItemOnBlock(solidBlock);
			}
			// See if we can use it on the emppty block
			else if (EntityChangeUseSelectedItemOnBlock.canUseOnBlock(selectedType, emptyBlockType))
			{
				change = new EntityChangeUseSelectedItemOnBlock(emptyBlock);
			}
			else
			{
				// Fall-through to try placement.
				change = null;
			}
		}
		else
		{
			// Nothing to do.
			change = null;
		}
		
		Assert.assertTrue(!_isPaused);
		if (null != change)
		{
			long currentTimeMillis = System.currentTimeMillis();
			_client.sendAction(change, currentTimeMillis);
		}
		return (null != change);
	}

	/**
	 * Attempts to run an action on the current entity, using whatever is selected in the inventory, returning true if
	 * an action was run (false if not).
	 * 
	 * @return True if an action was taken.
	 */
	public boolean runRightClickOnSelf()
	{
		// We need to check our selected item and see what "action" is associated with it.
		int selectedKey = _thisEntity.hotbarItems()[_thisEntity.hotbarIndex()];
		
		IMutationEntity<IMutablePlayerEntity> change;
		if (Entity.NO_SELECTION != selectedKey)
		{
			// Check if a special use exists for this item and block or if we are just placing.
			Inventory inventory = _getEntityInventory();
			Items stack = inventory.getStackForKey(selectedKey);
			NonStackableItem nonStack = inventory.getNonStackableForKey(selectedKey);
			Item selectedType = (null != stack) ? stack.type() : nonStack.type();
			if (EntityChangeUseSelectedItemOnSelf.canBeUsedOnSelf(selectedType))
			{
				change = new EntityChangeUseSelectedItemOnSelf();
			}
			else
			{
				// No valid action.
				change = null;
			}
		}
		else
		{
			// Nothing to do.
			change = null;
		}
		
		Assert.assertTrue(!_isPaused);
		if (null != change)
		{
			long currentTimeMillis = System.currentTimeMillis();
			_client.sendAction(change, currentTimeMillis);
		}
		return (null != change);
	}

	/**
	 * Just tries placing the currently selected block, returning true successful.
	 * Note that this requires that there be a solid and empty block, but can be called if there is no selection.
	 * 
	 * @param solidBlock The solid block the user clicked (cannot be null).
	 * @param emptyBlock The block block before where the user clicked (cannot be null).
	 * @return True if a block was placed, false if not.
	 */
	public boolean runPlaceBlock(AbsoluteLocation solidBlock, AbsoluteLocation emptyBlock)
	{
		Assert.assertTrue(null != solidBlock);
		Assert.assertTrue(null != emptyBlock);
		
		// We need to check our selected item and see what "action" is associated with it.
		int selectedKey = _thisEntity.hotbarItems()[_thisEntity.hotbarIndex()];
		
		Assert.assertTrue(!_isPaused);
		boolean didAttemptPlace = false;
		if (Entity.NO_SELECTION != selectedKey)
		{
			long currentTimeMillis = System.currentTimeMillis();
			IMutationEntity<IMutablePlayerEntity> change = new MutationPlaceSelectedBlock(emptyBlock, solidBlock);
			_client.sendAction(change, currentTimeMillis);
			didAttemptPlace = true;
		}
		return didAttemptPlace;
	}

	public boolean runRepairBlock(AbsoluteLocation solidBlock)
	{
		// The only check we perform is to see if this block is damaged.
		IReadOnlyCuboidData cuboid = _cuboids.get(solidBlock.getCuboidAddress());
		boolean didAttemptRepair = false;
		if (null != cuboid)
		{
			short damage = cuboid.getData15(AspectRegistry.DAMAGE, solidBlock.getBlockAddress());
			if (damage > 0)
			{
				long currentTimeMillis = System.currentTimeMillis();
				_client.repairBlock(solidBlock, currentTimeMillis);
				didAttemptRepair = true;
			}
		}
		return didAttemptRepair;
	}

	public void applyToEntity(PartialEntity selectedEntity)
	{
		Assert.assertTrue(!_isPaused);
		// We need to check our selected item and see if there is some interaction it has with this entity.
		int selectedKey = _thisEntity.hotbarItems()[_thisEntity.hotbarIndex()];
		if (Entity.NO_SELECTION != selectedKey)
		{
			Inventory inventory = _getEntityInventory();
			Items stack = inventory.getStackForKey(selectedKey);
			NonStackableItem nonStack = inventory.getNonStackableForKey(selectedKey);
			Item selectedType = (null != stack) ? stack.type() : nonStack.type();
			
			if (EntityChangeUseSelectedItemOnEntity.canUseOnEntity(selectedType, selectedEntity.type()))
			{
				EntityChangeUseSelectedItemOnEntity change = new EntityChangeUseSelectedItemOnEntity(selectedEntity.id());
				long currentTimeMillis = System.currentTimeMillis();
				_client.sendAction(change, currentTimeMillis);
			}
		}
	}

	public void changeHotbarIndex(int index)
	{
		Assert.assertTrue(!_isPaused);
		EntityChangeChangeHotbarSlot change = new EntityChangeChangeHotbarSlot(index);
		long currentTimeMillis = System.currentTimeMillis();
		_client.sendAction(change, currentTimeMillis);
	}

	public void setSelectedItemKeyOrClear(int itemInventoryKey)
	{
		int current = _thisEntity.hotbarItems()[_thisEntity.hotbarIndex()];
		int keyToSelect = (current == itemInventoryKey)
				? 0
				: itemInventoryKey
		;
		
		Assert.assertTrue(!_isPaused);
		MutationEntitySelectItem select = new MutationEntitySelectItem(keyToSelect);
		long currentTimeMillis = System.currentTimeMillis();
		_client.sendAction(select, currentTimeMillis);
	}

	/**
	 * Submits a mutation to pull items from the inventory at location into the entity's inventory.
	 * Note that this WILL CHECK if the request is valid and safely fail, if not.  This avoids the caller needing to
	 * check there with a duplicated assertion here.
	 * 
	 * @param location The location of the block inventory.
	 * @param blockInventoryKey The inventory key of the item in that block.
	 * @param quantity How many to transfer.
	 * @param useFuel True if the fuel inventory should be used instead of the normal inventory.
	 * @return True if the mutation was submitted or false if this request was invalid.
	 */
	public boolean pullItemsFromBlockInventory(AbsoluteLocation location, int blockInventoryKey, TransferQuantity quantity, boolean useFuel)
	{
		BlockProxy proxy = new BlockProxy(location.getBlockAddress(), _cuboids.get(location.getCuboidAddress()));
		Inventory blockInventory;
		byte inventoryAspect;
		if (useFuel)
		{
			FuelState fuel = proxy.getFuel();
			blockInventory = (null != fuel)
					? fuel.fuelInventory()
					: null
			;
			inventoryAspect = Inventory.INVENTORY_ASPECT_FUEL;
		}
		else
		{
			blockInventory = proxy.getInventory();
			inventoryAspect = Inventory.INVENTORY_ASPECT_INVENTORY;
		}
		
		Assert.assertTrue(!_isPaused);
		boolean didSubmit = false;
		if (null != blockInventory)
		{
			Items stack = blockInventory.getStackForKey(blockInventoryKey);
			NonStackableItem nonStack = blockInventory.getNonStackableForKey(blockInventoryKey);
			if ((null != stack) != (null != nonStack))
			{
				Inventory entityInventory = _getEntityInventory();
				Item type = (null != stack) ? stack.type() : nonStack.type();
				int available = (null != stack) ? stack.count() : 1;
				int vacancy = new MutableInventory(entityInventory).maxVacancyForItem(type);
				int count = quantity.getCount(Math.min(available, vacancy));
				
				if (count > 0)
				{
					MutationEntityRequestItemPickUp request = new MutationEntityRequestItemPickUp(location, blockInventoryKey, count, inventoryAspect);
					long currentTimeMillis = System.currentTimeMillis();
					_client.sendAction(request, currentTimeMillis);
					didSubmit = true;
				}
			}
		}
		return didSubmit;
	}

	/**
	 * Submits a mutation to push items from the entity's inventory to the inventory at location.
	 * Note that this WILL CHECK if the request is valid and safely fail, if not.  This avoids the caller needing to
	 * check there with a duplicated assertion here.
	 * 
	 * @param location The location of the block inventory.
	 * @param entityInventoryKey The inventory key of the item in that the entity's inventory.
	 * @param quantity How many to transfer.
	 * @param useFuel True if the fuel inventory should be used instead of the normal inventory.
	 * @return True if the mutation was submitted or false if this request was invalid.
	 */
	public boolean pushItemsToBlockInventory(AbsoluteLocation location, int entityInventoryKey, TransferQuantity quantity, boolean useFuel)
	{
		BlockProxy proxy = new BlockProxy(location.getBlockAddress(), _cuboids.get(location.getCuboidAddress()));
		Inventory entityInventory = _getEntityInventory();
		Items stack = entityInventory.getStackForKey(entityInventoryKey);
		NonStackableItem nonStack = entityInventory.getNonStackableForKey(entityInventoryKey);
		
		Assert.assertTrue(!_isPaused);
		boolean didSubmit = false;
		if ((null != stack) != (null != nonStack))
		{
			Item type = (null != stack) ? stack.type() : nonStack.type();
			// Make sure that these can fit in the tile.
			Inventory targetInventory;
			byte inventoryAspect;
			if (useFuel)
			{
				// If we are pushing to the fuel slot, make sure that this is a valid type.
				if (_environment.fuel.millisOfFuel(type) > 0)
				{
					FuelState fuel = proxy.getFuel();
					targetInventory = fuel.fuelInventory();
				}
				else
				{
					targetInventory = null;
				}
				inventoryAspect = Inventory.INVENTORY_ASPECT_FUEL;
			}
			else
			{
				targetInventory = proxy.getInventory();
				inventoryAspect = Inventory.INVENTORY_ASPECT_INVENTORY;
			}
			
			if (null != targetInventory)
			{
				MutableInventory inv = new MutableInventory(targetInventory);
				int available = (null != stack) ? stack.count() : 1;
				int vacancy = inv.maxVacancyForItem(type);
				int count = quantity.getCount(Math.min(available, vacancy));
				if (count > 0)
				{
					MutationEntityPushItems push = new MutationEntityPushItems(location, entityInventoryKey, count, inventoryAspect);
					long currentTimeMillis = System.currentTimeMillis();
					_client.sendAction(push, currentTimeMillis);
					didSubmit = true;
				}
			}
		}
		return didSubmit;
	}

	public void beginCraftInInventory(Craft craft)
	{
		Assert.assertTrue(!_isPaused);
		long currentTimeMillis = System.currentTimeMillis();
		_client.craft(craft, currentTimeMillis);
	}

	public void beginCraftInBlock(AbsoluteLocation block, Craft craft)
	{
		Assert.assertTrue(!_isPaused);
		long currentTimeMillis = System.currentTimeMillis();
		_client.craftInBlock(block, craft, currentTimeMillis);
	}

	public void swapArmour(BodyPart part)
	{
		Assert.assertTrue(!_isPaused);
		// In order to avoid gratuitous validation duplication, we will submit this mutation if it seems possible and rely on its own validation.
		int selectedKey = _thisEntity.hotbarItems()[_thisEntity.hotbarIndex()];
		EntityChangeSwapArmour swap = new EntityChangeSwapArmour(part, selectedKey);
		long currentTimeMillis = System.currentTimeMillis();
		_client.sendAction(swap, currentTimeMillis);
	}

	public void hitEntity(PartialEntity selectedEntity)
	{
		Assert.assertTrue(!_isPaused);
		EntityChangeAttackEntity attack = new EntityChangeAttackEntity(selectedEntity.id());
		long currentTimeMillis = System.currentTimeMillis();
		_client.sendAction(attack, currentTimeMillis);
	}

	public boolean pauseGame()
	{
		if (null != _monitoringAgent)
		{
			_monitoringAgent.getCommandSink().pauseTickProcessing();
			_isPaused = true;
		}
		return _isPaused;
	}

	public void resumeGame()
	{
		if (null != _monitoringAgent)
		{
			_monitoringAgent.getCommandSink().resumeTickProcessing();
			_isPaused = false;
		}
	}

	public void disconnect()
	{
		// The server needs to be running in order for this shutdown to work.
		if (_isPaused)
		{
			_monitoringAgent.getCommandSink().resumeTickProcessing();
		}
		
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
			try
			{
				_console.stop();
			}
			catch (InterruptedException e)
			{
				// We are just shutting down so we don't expect anything here.
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

	private Block _getBlockType(AbsoluteLocation block)
	{
		CuboidAddress address = block.getCuboidAddress();
		IReadOnlyCuboidData cuboid = _cuboids.get(address);
		// This can be null when the action is taken due to loading issues, respawn, etc.
		Block blockType = (null != cuboid)
				? new BlockProxy(block.getBlockAddress(), _cuboids.get(address)).getBlock()
				: null
		;
		return blockType;
	}


	private class _ClientListener implements ClientProcess.IListener
	{
		private int _assignedLocalEntityId;
		private int _ticksPerDay;
		private int _dayStartTick;
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
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
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
			float multiplier = PropagationHelpers.skyLightMultiplier(tickNumber, _ticksPerDay,_dayStartTick);
			_updateConsumer.setSkyLightMultiplier(multiplier);
			_updateConsumer.tickDidComplete(tickNumber);
		}
		@Override
		public void handleEvent(EventRecord event)
		{
			// We will see if this kind of event needs special handling (this will evolve over time).
			switch (event.type())
			{
			case BLOCK_BROKEN:
				_updateConsumer.blockBroken(event.location());
				break;
			case BLOCK_PLACED:
				_updateConsumer.blockPlaced(event.location());
				break;
			case ENTITY_HURT:
				if (_assignedLocalEntityId == event.entityTarget())
				{
					_updateConsumer.thisEntityHurt();
				}
				else
				{
					_updateConsumer.otherEntityHurt(event.entityTarget(), event.location());
				}
				break;
			case ENTITY_KILLED:
				if (_assignedLocalEntityId != event.entityTarget())
				{
					_updateConsumer.otherEntityKilled(event.entityTarget(), event.location());
				}
				break;
			case LIQUID_PLACED:
			case LIQUID_REMOVED:
				// Ignore these.
				break;
			default:
				// Undefined.
				throw Assert.unreachable();
			}
		}
		@Override
		public void configUpdated(int ticksPerDay, int dayStartTick)
		{
			_ticksPerDay = ticksPerDay;
			_dayStartTick = dayStartTick;
		}
		@Override
		public void otherClientJoined(int clientId, String name)
		{
			_updateConsumer.otherClientJoined(clientId, name);
		}
		@Override
		public void otherClientLeft(int clientId)
		{
			_updateConsumer.otherClientLeft(clientId);
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
		void blockPlaced(AbsoluteLocation location);
		void blockBroken(AbsoluteLocation location);
		
		void thisEntityUpdated(Entity authoritativeEntity, Entity projectedEntity);
		void thisEntityHurt();
		
		void otherClientJoined(int clientId, String name);
		void otherClientLeft(int clientId);
		
		void otherEntityUpdated(PartialEntity entity);
		void otherEntityDidUnload(int id);
		void otherEntityHurt(int id, AbsoluteLocation location);
		void otherEntityKilled(int id, AbsoluteLocation location);
		
		void setSkyLightMultiplier(float skyLightMultiplier);
		void tickDidComplete(long gameTick);
	}

	/**
	 * Used when requesting a transfer of items between inventories so that the caller doesn't need to calculate itself
	 * only for the callee to do the same work to verify it.
	 */
	public static enum TransferQuantity
	{
		ONE,
		ALL,
		;
		
		public int getCount(int available)
		{
			int select;
			switch (this)
			{
			case ALL:
				select = available;
				break;
			case ONE:
				select = (available > 0) ? 1 : 0;
				break;
				default:
					throw Assert.unreachable();
			}
			return select;
		}
	}
}
