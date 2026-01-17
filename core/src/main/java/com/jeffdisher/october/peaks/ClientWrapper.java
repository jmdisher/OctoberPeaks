package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.client.MovementAccumulator;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.logic.PropagationHelpers;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.persistence.ResourceLoader;
import com.jeffdisher.october.process.ClientProcess;
import com.jeffdisher.october.process.ServerProcess;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.server.ServerRunner;
import com.jeffdisher.october.server.TickRunner;
import com.jeffdisher.october.subactions.EntityChangeAttackEntity;
import com.jeffdisher.october.subactions.EntityChangeChangeHotbarSlot;
import com.jeffdisher.october.subactions.EntityChangeCraft;
import com.jeffdisher.october.subactions.EntityChangeCraftInBlock;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.subactions.EntityChangeIncrementalBlockRepair;
import com.jeffdisher.october.subactions.EntityChangeJump;
import com.jeffdisher.october.subactions.EntityChangePlaceMultiBlock;
import com.jeffdisher.october.subactions.EntityChangeSetBlockLogicState;
import com.jeffdisher.october.subactions.EntityChangeSetDayAndSpawn;
import com.jeffdisher.october.subactions.EntityChangeSwapArmour;
import com.jeffdisher.october.subactions.EntityChangeSwim;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnBlock;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnEntity;
import com.jeffdisher.october.subactions.EntityChangeUseSelectedItemOnSelf;
import com.jeffdisher.october.subactions.EntitySubActionChargeWeapon;
import com.jeffdisher.october.subactions.EntitySubActionDropItemsAsPassive;
import com.jeffdisher.october.subactions.EntitySubActionLadderAscend;
import com.jeffdisher.october.subactions.EntitySubActionLadderDescend;
import com.jeffdisher.october.subactions.EntitySubActionPickUpPassive;
import com.jeffdisher.october.subactions.EntitySubActionReleaseWeapon;
import com.jeffdisher.october.subactions.EntitySubActionRequestSwapSpecialSlot;
import com.jeffdisher.october.subactions.EntitySubActionTravelViaBlock;
import com.jeffdisher.october.subactions.MutationEntityPushItems;
import com.jeffdisher.october.subactions.MutationEntityRequestItemPickUp;
import com.jeffdisher.october.subactions.MutationEntitySelectItem;
import com.jeffdisher.october.subactions.MutationPlaceSelectedBlock;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CraftOperation;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.EventRecord;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.FuelState;
import com.jeffdisher.october.types.IEntitySubAction;
import com.jeffdisher.october.types.IMutablePlayerEntity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.MutableInventory;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.worldgen.IWorldGenerator;
import com.jeffdisher.october.worldgen.WorldGenHelpers;


public class ClientWrapper
{
	/**
	 * Technically, we can pick up a passive on every tick but we use this cooldown so that we don't aggressively check
	 * distances.
	 */
	public static final long PICK_UP_COOLDOWN_MILLIS = 200L;
	/**
	 * We want to delay pick-up after dropping so we don't immediately pick the item up, again.
	 */
	public static final long PICK_UP_DELAY_AFTER_DROP_MILLIS = 5_000L;
	/**
	 * In order to avoid cases like placing blocks too quickly or aggressively breaking a block behind a target, we will
	 * delay an action on a new block by this many milliseconds.
	 * This will also be applied to things like right-click actions on entities/blocks.
	 */
	public static final long MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS = 200L;

	private final Environment _environment;
	private final IUpdateConsumer _updateConsumer;
	// NOTE:  This is updated here since it is closest point to the underlying library, in terms of updates.
	private final WorldCache _worldCache;
	private final WorldConfig _config;
	private final ResourceLoader _loader;
	private final MonitoringAgent _monitoringAgent;
	private final ServerProcess _server;
	private final ClientProcess _client;
	private final ConsoleRunner _console;

	// We need to track if the monitoring agent was used to pause processing (in single-player) so we can resume it before shutdown.
	private boolean _isAgentPaused;

	// Local state information to avoid redundant events, etc.
	private boolean _didJump;
	private long _lastSpecialActionMillis;
	private long _nextPickUpAttemptMillis;
	private AbsoluteLocation _lastBlockTarget;
	private long _lastBlockActionMillis;
	private int _currentViewDistance;

	public ClientWrapper(Environment environment
			, IUpdateConsumer updateConsumer
			, WorldCache worldCache
			, String clientName
			, int startingViewDistance
			, InetSocketAddress serverAddress
			, File localWorldDirectory
			, WorldConfig.WorldGeneratorName worldGeneratorName
			, WorldConfig.DefaultPlayerMode defaultPlayerMode
			, Difficulty difficulty
			, Integer basicWorldGeneratorSeed
	) throws ConnectException
	{
		_environment = environment;
		_updateConsumer = updateConsumer;
		_worldCache = worldCache;
		
		try
		{
			// If we weren't given a server address, start the internal server.
			if (null == serverAddress)
			{
				System.out.println("Starting local server for single-player...");
				if (!localWorldDirectory.isDirectory())
				{
					Assert.assertTrue(localWorldDirectory.mkdirs());
				}
				
				// Since we use this same routine for both new and existing local games, add the options before we load
				// from disk, since the disk copy will override our options, if it already exists.
				_config = new WorldConfig();
				if (null != worldGeneratorName)
				{
					_config.worldGeneratorName = worldGeneratorName;
				}
				if (null != defaultPlayerMode)
				{
					_config.defaultPlayerMode = defaultPlayerMode;
				}
				if (null != difficulty)
				{
					_config.difficulty = difficulty;
				}
				if (null != basicWorldGeneratorSeed)
				{
					_config.basicSeed = basicWorldGeneratorSeed.intValue();
				}
				boolean didLoadConfig = ResourceLoader.populateWorldConfig(localWorldDirectory, _config);
				IWorldGenerator worldGen = WorldGenHelpers.createConfiguredWorldGenerator(environment, _config);
				if (!didLoadConfig)
				{
					// There is no config so ask the world-gen for the default spawn.
					EntityLocation spawnLocation = worldGen.getDefaultSpawnLocation();
					_config.worldSpawn = spawnLocation.getBlockLocation();
				}
				_loader = new ResourceLoader(localWorldDirectory
						, worldGen
						, _config
				);
				_monitoringAgent = new MonitoringAgent();
				_server = new ServerProcess(0
						, ServerRunner.DEFAULT_MILLIS_PER_TICK
						, _loader
						, () -> System.currentTimeMillis()
						, _monitoringAgent
						, _config
				);
				int ephemeralPort = _server.getPort();
				_client = new ClientProcess(new _ClientListener(), InetAddress.getLoopbackAddress(), ephemeralPort, clientName, startingViewDistance);
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
				_client = new ClientProcess(new _ClientListener(), serverAddress.getAddress(), serverAddress.getPort(), clientName, startingViewDistance);
				_console = null;
			}
		}
		catch (ConnectException e)
		{
			// This one we just want to throw back.
			throw e;
		}
		catch (IOException | TabListReader.TabListException e)
		{
			// TODO:  Handle this network start-up failure or make sure it can't happen.
			throw Assert.unexpected(e);
		}
		
		_currentViewDistance = MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE;
	}

	/**
	 * Blocks until the user's entity is loaded.
	 * NOTE:  This blocks the calling thread so should only be used in the "No UI" mode.
	 */
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
		catch (ClientProcess.DisconnectException e)
		{
			// TODO:  Implement this correctly once we no longer call "System.exit(0)" on disconnect, below.
			throw Assert.unreachable();
		}
	}

	/**
	 * Polls to see if we are connected.
	 * 
	 * @return True if we are connected, false if we are still connecting.
	 */
	public boolean isConnectionReady()
	{
		// We just poll if the client has received the local entity.
		_client.advanceTime(System.currentTimeMillis());
		return (null != _worldCache.getThisEntity());
	}

	public void passTimeWhilePaused()
	{
		Assert.assertTrue(_isAgentPaused);
		
		long currentTimeMillis = System.currentTimeMillis();
		_client.advanceTime(currentTimeMillis);
	}

	public void passTimeWhileRunning(Craft rescheduleInInventory, AbsoluteLocation openStationLocation, Craft rescheduleInBlock)
	{
		Assert.assertTrue(!_isAgentPaused);
		
		IEntitySubAction<IMutablePlayerEntity> subAction = null;
		
		// We want to check the if there are any active crafting operations in the entity/block and if we should be auto-rescheduling any.
		Entity thisEntity = _worldCache.getThisEntity();
		CraftOperation ongoing = thisEntity.ephemeralShared().localCraftOperation();
		long currentTimeMillis = System.currentTimeMillis();
		if (null != ongoing)
		{
			subAction = new EntityChangeCraft(null);
		}
		else if (null != rescheduleInInventory)
		{
			subAction = new EntityChangeCraft(rescheduleInInventory);
		}
		else if (null != openStationLocation)
		{
			IReadOnlyCuboidData cuboid = _worldCache.getCuboid(openStationLocation.getCuboidAddress());
			// We already have this open in another view.
			Assert.assertTrue(null != cuboid);
			CraftOperation blockOperation = cuboid.getDataSpecial(AspectRegistry.CRAFTING, openStationLocation.getBlockAddress());
			if (null != blockOperation)
			{
				subAction = new EntityChangeCraftInBlock(openStationLocation, blockOperation.selectedCraft());
			}
			else if (null != rescheduleInBlock)
			{
				subAction = new EntityChangeCraftInBlock(openStationLocation, rescheduleInBlock);
			}
		}
		else
		{
			// If we are standing in a portal, see if we are ready to pass through it.
			if ((_lastSpecialActionMillis + EntitySubActionTravelViaBlock.TRAVEL_COOLDOWN_MILLIS) < currentTimeMillis)
			{
				AbsoluteLocation surfaceLocation = EntitySubActionTravelViaBlock.getValidPortalSurface(_environment, _worldCache.blockLookup, thisEntity.location(), _worldCache.playerType.volume());
				if (null != surfaceLocation)
				{
					subAction = new EntitySubActionTravelViaBlock(surfaceLocation);
				}
			}
		}
		
		// If we aren't taking any other action, see if it is time for us to try to pick something up and if there is anything nearby.
		if (null == subAction)
		{
			subAction = _tryPassivePickup(currentTimeMillis);
		}
		if (null != subAction)
		{
			_client.sendAction(subAction, currentTimeMillis);
		}
		// Now, just allow time to pass while standing.
		_client.doNothing(currentTimeMillis);
	}

	public void setOrientation(float yawRadians, float pitchRadians)
	{
		byte yaw = OrientationHelpers.yawFromRadians(yawRadians);
		byte pitch = OrientationHelpers.yawFromRadians(pitchRadians);
		Assert.assertTrue(!_isAgentPaused);
		_client.setOrientation(yaw, pitch);
	}

	public void accelerateHorizontal(MovementAccumulator.Relative relativeDirection, boolean runningSpeed)
	{
		long currentTimeMillis = System.currentTimeMillis();
		Assert.assertTrue(!_isAgentPaused);
		
		// Enqueue a passive action, if that makes sense.
		IEntitySubAction<IMutablePlayerEntity> subAction = _tryPassivePickup(currentTimeMillis);
		if (null != subAction)
		{
			_client.sendAction(subAction, currentTimeMillis);
		}
		_client.walk(relativeDirection, runningSpeed, currentTimeMillis);
	}

	public void sneak(MovementAccumulator.Relative relativeDirection)
	{
		long currentTimeMillis = System.currentTimeMillis();
		Assert.assertTrue(!_isAgentPaused);
		
		// Enqueue a passive action, if that makes sense.
		IEntitySubAction<IMutablePlayerEntity> subAction = _tryPassivePickup(currentTimeMillis);
		if (null != subAction)
		{
			_client.sendAction(subAction, currentTimeMillis);
		}
		_client.sneak(relativeDirection, currentTimeMillis);
	}

	public boolean ascendOrJumpOrSwim()
	{
		// See if we can jump or swim.
		boolean didMove = false;
		Assert.assertTrue(!_isAgentPaused);
		// Filter for redundant events.
		if (!_didJump)
		{
			Entity thisEntity = _worldCache.getThisEntity();
			Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = _worldCache.blockLookup;
			EntityLocation location = thisEntity.location();
			EntityLocation vector = thisEntity.velocity();
			EntityVolume playerVolume = _worldCache.playerType.volume();
			
			IEntitySubAction<IMutablePlayerEntity> subAction = null;
			if (EntitySubActionLadderAscend.canAscend(previousBlockLookUp, location, playerVolume))
			{
				subAction = new EntitySubActionLadderAscend<>();
			}
			else if (EntityChangeJump.canJump(previousBlockLookUp
				, location
				, playerVolume
				, vector
			))
			{
				subAction = new EntityChangeJump<>();
			}
			else if (EntityChangeSwim.canSwim(previousBlockLookUp
					, location
					, vector
			))
			{
				subAction = new EntityChangeSwim<>();
			}
			
			// Run this, if we found something.
			if (null != subAction)
			{
				long currentTimeMillis = System.currentTimeMillis();
				_client.sendAction(subAction, currentTimeMillis);
				didMove = true;
				_didJump = true;
			}
		}
		return didMove;
	}

	public boolean tryDescend()
	{
		// Try to descend, if we are on a ladder.
		Assert.assertTrue(!_isAgentPaused);
		
		long currentTimeMillis = System.currentTimeMillis();
		Function<AbsoluteLocation, BlockProxy> previousBlockLookUp = _worldCache.blockLookup;
		EntityLocation location = _worldCache.getThisEntity().location();
		
		boolean didMove = false;
		if (EntitySubActionLadderDescend.canDescend(previousBlockLookUp, location, _worldCache.playerType.volume()))
		{
			EntitySubActionLadderDescend<IMutablePlayerEntity> subAction = new EntitySubActionLadderDescend<>();
			_client.sendAction(subAction, currentTimeMillis);
			didMove = true;
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
		boolean didHit = false;
		Assert.assertTrue(!_isAgentPaused);
		
		// We need to make sure that we can interact with this block.
		long currentTimeMillis = System.currentTimeMillis();
		BlockProxy proxy = _readyProxyContinuous(blockLocation, currentTimeMillis);
		if (null != proxy)
		{
			if (!_environment.blocks.canBeReplaced(proxy.getBlock()))
			{
				// This block is not the kind which can be replaced, meaning it can potentially be broken.
				EntityChangeIncrementalBlockBreak change = new EntityChangeIncrementalBlockBreak(blockLocation);
				_client.sendAction(change, currentTimeMillis);
				_resetBlockTarget(blockLocation, currentTimeMillis);
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
		Entity thisEntity = _worldCache.getThisEntity();
		int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
		
		Block solidBlockType = _getBlockType(solidBlock);
		Block emptyBlockType = _getBlockType(emptyBlock);
		long currentTimeMillis = System.currentTimeMillis();
		
		// First, see if the target block has a general logic state we can change.
		IEntitySubAction<IMutablePlayerEntity> change;
		if ((null == solidBlockType) || (null == emptyBlockType))
		{
			// The target isn't loaded.
			change = null;
		}
		else if (EntityChangeSetBlockLogicState.canChangeBlockLogicState(solidBlockType) && _readyToInteractOneOff(solidBlock, currentTimeMillis))
		{
			byte flags = _worldCache.getCuboid(solidBlock.getCuboidAddress()).getData7(AspectRegistry.FLAGS, solidBlock.getBlockAddress());
			boolean existingState = EntityChangeSetBlockLogicState.getCurrentBlockLogicState(solidBlockType, flags);
			change = new EntityChangeSetBlockLogicState(solidBlock, !existingState);
			_resetBlockTarget(solidBlock, currentTimeMillis);
		}
		else if ((_environment.items.getItemById("op.bed") == solidBlockType.item()) && _readyToInteractOneOff(solidBlock, currentTimeMillis))
		{
			// This is a bed so we need to take a special action to set spawn and reset the day.
			change = new EntityChangeSetDayAndSpawn(solidBlock);
			_resetBlockTarget(solidBlock, currentTimeMillis);
		}
		else if (_environment.specialSlot.hasSpecialSlot(solidBlockType) && _readyToInteractOneOff(solidBlock, currentTimeMillis))
		{
			// This change will typically succeed even if nothing changes.
			// For now, we will always send all.
			boolean sendAll = true;
			change = new EntitySubActionRequestSwapSpecialSlot(solidBlock, sendAll);
			_resetBlockTarget(solidBlock, currentTimeMillis);
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
			if (EntityChangeUseSelectedItemOnBlock.canUseOnBlock(selectedType, solidBlockType) && _readyToInteractOneOff(solidBlock, currentTimeMillis))
			{
				change = new EntityChangeUseSelectedItemOnBlock(solidBlock);
				_resetBlockTarget(solidBlock, currentTimeMillis);
			}
			// See if we can use it on the emppty block
			else if (EntityChangeUseSelectedItemOnBlock.canUseOnBlock(selectedType, emptyBlockType) && _readyToInteractOneOff(emptyBlock, currentTimeMillis))
			{
				change = new EntityChangeUseSelectedItemOnBlock(emptyBlock);
				_resetBlockTarget(emptyBlock, currentTimeMillis);
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
		
		Assert.assertTrue(!_isAgentPaused);
		if (null != change)
		{
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
		Entity thisEntity = _worldCache.getThisEntity();
		int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
		
		IEntitySubAction<IMutablePlayerEntity> change;
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
		
		Assert.assertTrue(!_isAgentPaused);
		if (null != change)
		{
			long currentTimeMillis = System.currentTimeMillis();
			_client.sendAction(change, currentTimeMillis);
			_resetBlockTarget(null, currentTimeMillis);
		}
		return (null != change);
	}

	/**
	 * Should handle only the cases which matter if a right-click is held down, whether or not anything else is in range
	 * or if this was just a momentary click (for example, charging a weapon).
	 * 
	 * @return True if some action was taken.
	 */
	public boolean holdRightClickOnSelf()
	{
		Entity thisEntity = _worldCache.getThisEntity();
		int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
		
		IEntitySubAction<IMutablePlayerEntity> change;
		if (Entity.NO_SELECTION != selectedKey)
		{
			// Check to see if this is a weapon we can charge.
			Inventory inventory = _getEntityInventory();
			Item type = inventory.getSlotForKey(selectedKey).getType();
			int millisToCharge = _environment.tools.getChargeMillis(type);
			if (millisToCharge > 0)
			{
				// We can charge this.
				change = new EntitySubActionChargeWeapon();
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
		
		Assert.assertTrue(!_isAgentPaused);
		if (null != change)
		{
			long currentTimeMillis = System.currentTimeMillis();
			_client.sendAction(change, currentTimeMillis);
			_resetBlockTarget(null, currentTimeMillis);
		}
		return (null != change);
	}

	/**
	 * Used for "weapon release" support and is only called if holdRightClickOnSelf() recently returned true.
	 * Note that this usage is currently very specialized and may be changed in the future.
	 * 
	 * @return True if the release weapon sub-action was sent, false if it isn't ready yet.
	 */
	public boolean releasedRightClickOnSelf()
	{
		Assert.assertTrue(!_isAgentPaused);
		IEntitySubAction<IMutablePlayerEntity> change = new EntitySubActionReleaseWeapon();
		long currentTimeMillis = System.currentTimeMillis();
		boolean didApply = _client.sendAction(change, currentTimeMillis);
		_resetBlockTarget(null, currentTimeMillis);
		return didApply;
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
		
		// See if this is loaded and we are at the right time to perform one-off block interactions.
		long currentTimeMillis = System.currentTimeMillis();
		boolean isReady = _readyToInteractOneOff(emptyBlock, currentTimeMillis);
		
		// We need to check our selected item and see what "action" is associated with it.
		Entity thisEntity = _worldCache.getThisEntity();
		int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
		
		Assert.assertTrue(!_isAgentPaused);
		boolean didAttemptPlace = false;
		if (isReady && (Entity.NO_SELECTION != selectedKey))
		{
			// Check this type to see if it is a block and, if so, if it is a multi-block.
			Item type;
			if (thisEntity.isCreativeMode())
			{
				CreativeInventory inv = new CreativeInventory();
				Items stack = inv.getStackForKey(selectedKey);
				NonStackableItem nonStack = inv.getNonStackableForKey(selectedKey);
				type = (null != stack) ? stack.type() : nonStack.type();
			}
			else
			{
				Items stack = thisEntity.inventory().getStackForKey(selectedKey);
				NonStackableItem nonStack = thisEntity.inventory().getNonStackableForKey(selectedKey);
				type = (null != stack) ? stack.type() : nonStack.type();
			}
			Block block = _environment.blocks.getAsPlaceableBlock(type);
			if (null != block)
			{
				IEntitySubAction<IMutablePlayerEntity> change;
				if (_environment.blocks.isMultiBlock(block))
				{
					// We will place the multi-block in the same orientation as this user.
					byte yaw = thisEntity.yaw();
					FacingDirection direction = OrientationHelpers.getYawDirection(yaw);
					change = new EntityChangePlaceMultiBlock(emptyBlock, direction);
				}
				else
				{
					change = new MutationPlaceSelectedBlock(emptyBlock, solidBlock);
				}
				_client.sendAction(change, currentTimeMillis);
				_resetBlockTarget(emptyBlock, currentTimeMillis);
				didAttemptPlace = true;
			}
		}
		return didAttemptPlace;
	}

	public boolean runRepairBlock(AbsoluteLocation blockLocation)
	{
		// The only check we perform is to see if this block is damaged.
		long currentTimeMillis = System.currentTimeMillis();
		BlockProxy proxy = _readyProxyContinuous(blockLocation, currentTimeMillis);
		boolean didAttemptRepair = false;
		if (null != proxy)
		{
			int damage = proxy.getDamage();
			if (damage > 0)
			{
				EntityChangeIncrementalBlockRepair change = new EntityChangeIncrementalBlockRepair(blockLocation);
				_client.sendAction(change, currentTimeMillis);
				_resetBlockTarget(blockLocation, currentTimeMillis);
				didAttemptRepair = true;
			}
		}
		return didAttemptRepair;
	}

	public void applyToEntity(PartialEntity selectedEntity)
	{
		Assert.assertTrue(!_isAgentPaused);
		// We need to check our selected item and see if there is some interaction it has with this entity.
		Entity thisEntity = _worldCache.getThisEntity();
		int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
		if (Entity.NO_SELECTION != selectedKey)
		{
			Inventory inventory = _getEntityInventory();
			Items stack = inventory.getStackForKey(selectedKey);
			NonStackableItem nonStack = inventory.getNonStackableForKey(selectedKey);
			Item selectedType = (null != stack) ? stack.type() : nonStack.type();
			
			long gameTimeMillis = _client.serverState.millisPerTick * _client.serverState.latestTickNumber;
			if (EntityChangeUseSelectedItemOnEntity.canUseOnEntity(selectedType, selectedEntity, gameTimeMillis))
			{
				EntityChangeUseSelectedItemOnEntity change = new EntityChangeUseSelectedItemOnEntity(selectedEntity.id());
				long currentTimeMillis = System.currentTimeMillis();
				_client.sendAction(change, currentTimeMillis);
				_resetBlockTarget(null, currentTimeMillis);
			}
		}
	}

	public void changeHotbarIndex(int index)
	{
		Assert.assertTrue(!_isAgentPaused);
		EntityChangeChangeHotbarSlot change = new EntityChangeChangeHotbarSlot(index);
		long currentTimeMillis = System.currentTimeMillis();
		_client.sendAction(change, currentTimeMillis);
	}

	public void setSelectedItemKeyOrClear(int itemInventoryKey)
	{
		Entity thisEntity = _worldCache.getThisEntity();
		int current = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
		int keyToSelect = (current == itemInventoryKey)
				? 0
				: itemInventoryKey
		;
		
		Assert.assertTrue(!_isAgentPaused);
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
		BlockProxy proxy = new BlockProxy(location.getBlockAddress(), _worldCache.getCuboid(location.getCuboidAddress()));
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
		
		Assert.assertTrue(!_isAgentPaused);
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
		BlockProxy proxy = new BlockProxy(location.getBlockAddress(), _worldCache.getCuboid(location.getCuboidAddress()));
		Inventory entityInventory = _getEntityInventory();
		Items stack = entityInventory.getStackForKey(entityInventoryKey);
		NonStackableItem nonStack = entityInventory.getNonStackableForKey(entityInventoryKey);
		
		Assert.assertTrue(!_isAgentPaused);
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
		Assert.assertTrue(!_isAgentPaused);
		long currentTimeMillis = System.currentTimeMillis();
		EntityChangeCraft change = new EntityChangeCraft(craft);
		_client.sendAction(change, currentTimeMillis);
	}

	public void beginCraftInBlock(AbsoluteLocation block, Craft craft)
	{
		Assert.assertTrue(!_isAgentPaused);
		long currentTimeMillis = System.currentTimeMillis();
		EntityChangeCraftInBlock change = new EntityChangeCraftInBlock(block, craft);
		_client.sendAction(change, currentTimeMillis);
	}

	public void swapArmour(BodyPart part)
	{
		Assert.assertTrue(!_isAgentPaused);
		// In order to avoid gratuitous validation duplication, we will submit this mutation if it seems possible and rely on its own validation.
		Entity thisEntity = _worldCache.getThisEntity();
		int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
		EntityChangeSwapArmour swap = new EntityChangeSwapArmour(part, selectedKey);
		long currentTimeMillis = System.currentTimeMillis();
		_client.sendAction(swap, currentTimeMillis);
	}

	public void hitEntity(PartialEntity selectedEntity)
	{
		Assert.assertTrue(!_isAgentPaused);
		EntityChangeAttackEntity attack = new EntityChangeAttackEntity(selectedEntity.id());
		long currentTimeMillis = System.currentTimeMillis();
		_client.sendAction(attack, currentTimeMillis);
		_resetBlockTarget(null, currentTimeMillis);
	}

	public void dropItemSlot(int localInventoryId, boolean dropAll)
	{
		Assert.assertTrue(!_isAgentPaused);
		EntitySubActionDropItemsAsPassive drop = new EntitySubActionDropItemsAsPassive(localInventoryId, dropAll);
		long currentTimeMillis = System.currentTimeMillis();
		boolean didDrop = _client.sendAction(drop, currentTimeMillis);
		if (didDrop)
		{
			// We want to delay pick-up.
			_nextPickUpAttemptMillis = currentTimeMillis + PICK_UP_DELAY_AFTER_DROP_MILLIS;
		}
	}

	public boolean pauseGame()
	{
		if (null != _monitoringAgent)
		{
			_monitoringAgent.getCommandSink().pauseTickProcessing();
			_isAgentPaused = true;
		}
		return _isAgentPaused;
	}

	public void resumeGame()
	{
		if (null != _monitoringAgent)
		{
			_monitoringAgent.getCommandSink().resumeTickProcessing();
			_isAgentPaused = false;
		}
	}

	public int trySetViewDistance(int updated)
	{
		// We will only change the view distance if it is valid and something we could change to.
		if ((updated >= 0) && _client.updateOptions(updated))
		{
			_currentViewDistance = updated;
		}
		return _currentViewDistance;
	}

	public void disconnect()
	{
		// The server needs to be running in order for this shutdown to work.
		if (_isAgentPaused)
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
		_worldCache.setThisEntity(thisEntity);
		_didJump = false;
		
		// We typically only see the ephemeral locally so check if it is here to update our last action time.
		if ((null != thisEntity.ephemeralLocal()) && (thisEntity.ephemeralLocal().lastSpecialActionMillis() > 0L))
		{
			_lastSpecialActionMillis = thisEntity.ephemeralLocal().lastSpecialActionMillis();
		}
	}

	private Inventory _getEntityInventory()
	{
		Entity thisEntity = _worldCache.getThisEntity();
		Inventory inventory = thisEntity.isCreativeMode()
			? CreativeInventory.fakeInventory()
			: thisEntity.inventory()
		;
		return inventory;
	}

	private Block _getBlockType(AbsoluteLocation block)
	{
		// This can be null when the action is taken due to loading issues, respawn, etc.
		BlockProxy proxy = _worldCache.blockLookup.apply(block);
		Block blockType = (null != proxy)
			? proxy.getBlock()
			: null
		;
		return blockType;
	}

	private IEntitySubAction<IMutablePlayerEntity> _tryPassivePickup(long currentTimeMillis)
	{
		IEntitySubAction<IMutablePlayerEntity> subAction = null;
		if (currentTimeMillis >= _nextPickUpAttemptMillis)
		{
			// See if there is something to pick up.
			int passive = _getClosestPassiveItems();
			if (passive > 0)
			{
				subAction = new EntitySubActionPickUpPassive(passive);
			}
			// Reset our next attempt time.
			_nextPickUpAttemptMillis = currentTimeMillis + PICK_UP_COOLDOWN_MILLIS;
		}
		return subAction;
	}

	private int _getClosestPassiveItems()
	{
		int id = 0;
		float closest = Float.MAX_VALUE;
		// All the passives we store are the ItemSlot type.
		EntityVolume volume = PassiveType.ITEM_SLOT.volume();
		Entity thisEntity = _worldCache.getThisEntity();
		// TODO:  We need to organize this data better since we shouldn't always search all of them.
		for (PartialPassive passive : _worldCache.getItemSlotPassives())
		{
			float distance = SpatialHelpers.distanceFromPlayerEyeToVolume(thisEntity.location(), _environment.creatures.PLAYER, passive.location(), volume);
			if ((distance <= EntitySubActionPickUpPassive.PICKUP_DISTANCE) && (distance < closest))
			{
				id = passive.id();
				closest = distance;
			}
		}
		return id;
	}

	private BlockProxy _readyProxyContinuous(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		// Continuous interaction is possible if the block location is unchanged or the time has passed.
		// We can interact with a block if there has been a long enough delay since other actions _or_ if the block is the same one we previously interacted with.
		boolean timeReady = _readyToInteractOneOff(blockLocation, currentTimeMillis);
		boolean isReady = timeReady
			|| ((null != blockLocation) && blockLocation.equals(_lastBlockTarget))
		;
		BlockProxy proxy = null;
		if (isReady)
		{
			IReadOnlyCuboidData cuboid = _worldCache.getCuboid(blockLocation.getCuboidAddress());
			// This can be null when the action is taken due to loading issues, respawn, etc.
			if (null != cuboid)
			{
				proxy = new BlockProxy(blockLocation.getBlockAddress(), cuboid);
			}
		}
		return proxy;
	}

	private boolean _readyToInteractOneOff(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		// One-off interacts are only possible if the time has passed.
		boolean timeReady = (_lastBlockActionMillis + MILLIS_DELAY_BETWEEN_BLOCK_ACTIONS) <= currentTimeMillis;
		return timeReady;
	}

	private void _resetBlockTarget(AbsoluteLocation blockLocation, long currentTimeMillis)
	{
		// Set this as our target.
		_lastBlockTarget = blockLocation;
		_lastBlockActionMillis = currentTimeMillis;
	}


	// Note that these calls all arrive on the main thread (they are dispatched when our main thread calls into ClientProcess).
	private class _ClientListener implements ClientProcess.IListener
	{
		private int _assignedLocalEntityId;
		private int _ticksPerDay;
		private int _dayStartTick;
		@Override
		public void connectionClosed()
		{
			// We don't expect this disconnect callback in a single-player run since we explicitly disconnect the client before stopping the server.
			Assert.assertTrue(null == _server);
			
			_updateConsumer.didDisconnect();
		}
		@Override
		public void connectionEstablished(int assignedEntityId, int currentViewDistance)
		{
			_assignedLocalEntityId = assignedEntityId;
			_updateConsumer.didConnect(currentViewDistance);
		}
		@Override
		public void cuboidDidChange(IReadOnlyCuboidData cuboid
				, ColumnHeightMap heightMap
				, Set<BlockAddress> changedBlocks
				, Set<Aspect<?, ?>> changedAspects
		)
		{
			_worldCache.updateCuboid(cuboid);
			_updateConsumer.updateExisting(cuboid, heightMap, changedBlocks, changedAspects);
		}
		@Override
		public void cuboidDidLoad(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			_worldCache.addCuboid(cuboid);
			_updateConsumer.loadNew(cuboid, heightMap);
		}
		@Override
		public void cuboidDidUnload(CuboidAddress address)
		{
			_worldCache.removeCuboid(address);
			_updateConsumer.unload(address);
		}
		@Override
		public void thisEntityDidLoad(Entity authoritativeEntity)
		{
			Assert.assertTrue(_assignedLocalEntityId == authoritativeEntity.id());
			
			// To start, we will use the authoritative data as the projection.
			_setEntity(authoritativeEntity);
			_updateConsumer.thisEntityUpdated(authoritativeEntity);
		}
		@Override
		public void thisEntityDidChange(Entity projectedEntity)
		{
			Assert.assertTrue(_assignedLocalEntityId == projectedEntity.id());
			
			// Locally, we just use the projection.
			_setEntity(projectedEntity);
			_updateConsumer.thisEntityUpdated(projectedEntity);
		}
		@Override
		public void otherEntityDidChange(PartialEntity entity)
		{
			Assert.assertTrue(_assignedLocalEntityId != entity.id());
			
			_worldCache.updateOtherEntity(entity);
		}
		@Override
		public void otherEntityDidLoad(PartialEntity entity)
		{
			Assert.assertTrue(_assignedLocalEntityId != entity.id());
			
			_worldCache.addOtherEntity(entity);
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			_worldCache.removeOtherEntity(id);
			_updateConsumer.otherEntityDidUnload(id);
		}
		@Override
		public void passiveEntityDidLoad(PartialPassive entity)
		{
			_worldCache.addPassive(entity);
		}
		@Override
		public void passiveEntityDidChange(PartialPassive entity)
		{
			_worldCache.updatePassive(entity);
		}
		@Override
		public void passiveEntityDidUnload(int id)
		{
			_worldCache.removePassiveEntity(id);
		}
		@Override
		public void tickDidComplete(long tickNumber)
		{
			float multiplier = PropagationHelpers.skyLightMultiplier(tickNumber, _ticksPerDay, _dayStartTick);
			long step = (tickNumber + _dayStartTick) % _ticksPerDay;
			float dayProgression = (float)step / (float)_ticksPerDay;
			_updateConsumer.tickDidComplete(tickNumber, multiplier, dayProgression);
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
			case CRAFT_IN_INVENTORY_COMPLETE:
				// Either of these entity references are the same, for this event.
				_updateConsumer.craftInInventoryComplete(event.entitySource());
				break;
			case CRAFT_IN_BLOCK_COMPLETE:
				_updateConsumer.craftInBlockComplete(event.location());
				break;
			case ENCHANT_COMPLETE:
				_updateConsumer.enchantComplete(event.location());
				break;
			case LIQUID_PLACED:
			case LIQUID_REMOVED:
			case ENTITY_ATE_FOOD:
			case ENTITY_PICKED_UP_PASSIVE:
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
		void didConnect(int currentViewDistance);
		void didDisconnect();
		
		void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap);
		void updateExisting(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks, Set<Aspect<?, ?>> changedAspects);
		void unload(CuboidAddress address);
		void blockPlaced(AbsoluteLocation location);
		void blockBroken(AbsoluteLocation location);
		
		void thisEntityUpdated(Entity projectedEntity);
		void thisEntityHurt();
		
		void otherClientJoined(int clientId, String name);
		void otherClientLeft(int clientId);
		
		void otherEntityDidUnload(int id);
		void otherEntityHurt(int id, AbsoluteLocation location);
		void otherEntityKilled(int id, AbsoluteLocation location);
		
		void enchantComplete(AbsoluteLocation location);
		void craftInBlockComplete(AbsoluteLocation location);
		void craftInInventoryComplete(int entityId);
		
		void tickDidComplete(long gameTick, float skyLightMultiplier, float dayProgression);
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
