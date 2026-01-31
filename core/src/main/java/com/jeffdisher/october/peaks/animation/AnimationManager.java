package com.jeffdisher.october.peaks.animation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CraftingBlockSupport;
import com.jeffdisher.october.logic.SparseByteCube;
import com.jeffdisher.october.peaks.scene.BlockRenderer;
import com.jeffdisher.october.peaks.scene.FireFaceBuilder;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


/**
 * The high-level interface and internal tracking used to direct the ParticleEngine to generate more particles in
 * response to events and other things happening in the world.
 */
public class AnimationManager
{
	public static final long DAMAGE_DURATION_MILLIS = 1000L;
	public static final long FIRE_PARTICLE_PERIOD_MILLIS = 100L;

	private final Environment _environment;
	private final ParticleEngine _particleEngine;
	private final WorldCache _worldCache;
	private final Map<Integer, Long> _entityDamageMillis;
	private long _lastTickTimeEndMillis;

	private byte _frameAnimationStep;
	private long _lastAnimationUpdateMillis;
	private long _lastFireParticleMillis;
	private Map<Integer, Byte> _activeEntityAnimationOffset;
	private Map<Integer, Byte> _buildingEntityAnimationOffset;

	public AnimationManager(Environment environment, ParticleEngine particleEngine, WorldCache worldCache, long currentTimeMillis)
	{
		_environment = environment;
		_particleEngine = particleEngine;
		_worldCache = worldCache;
		_entityDamageMillis = new HashMap<>();
		
		_frameAnimationStep = 0;
		_lastAnimationUpdateMillis = currentTimeMillis;
		_lastFireParticleMillis = currentTimeMillis;
		_activeEntityAnimationOffset = new HashMap<>();
		_buildingEntityAnimationOffset = new HashMap<>();
	}

	public void craftingBlockChanged(IReadOnlyCuboidData cuboid, Set<BlockAddress> changedBlocks)
	{
		// We will just generate particles for this, but first check to see what type it is.
		AbsoluteLocation base = cuboid.getCuboidAddress().getBase();
		for (BlockAddress b : changedBlocks)
		{
			// This may be null if it ended.
			if (null != cuboid.getDataSpecial(AspectRegistry.CRAFTING, b))
			{
				short blockNumber = cuboid.getData15(AspectRegistry.BLOCK, b);
				Block craftingBlock = _environment.blocks.fromItem(_environment.items.ITEMS_BY_TYPE[blockNumber]);
				boolean isFurnace = _environment.stations.getCraftingClasses(craftingBlock).contains(CraftingBlockSupport.FUELLED_CLASSIFICATION);
				EntityLocation centre = _getTopCentreOfBlock(base.relativeForBlock(b));
				_flatBurst(centre, isFurnace);
			}
		}
	}

	public void enchantingBlockChanged(IReadOnlyCuboidData cuboid, Set<BlockAddress> changedBlocks)
	{
		// We will just generate particles for this.
		AbsoluteLocation base = cuboid.getCuboidAddress().getBase();
		for (BlockAddress b : changedBlocks)
		{
			// This may be null if it ended.
			if (null != cuboid.getDataSpecial(AspectRegistry.ENCHANTING, b))
			{
				EntityLocation centre = _getTopCentreOfBlock(base.relativeForBlock(b));
				_upwardBurst(centre);
			}
		}
	}

	public void craftInInventoryComplete(int entityId)
	{
		EntityLocation centre = _worldCache.getCentreOfPlayerOrCreature(entityId);
		_flatBurst(centre, false);
	}

	public void craftInBlockComplete(AbsoluteLocation location)
	{
		Block craftingBlock = _worldCache.blockLookup.apply(location).getBlock();
		boolean isFurnace = _environment.stations.getCraftingClasses(craftingBlock).contains(CraftingBlockSupport.FUELLED_CLASSIFICATION);
		EntityLocation centre = _getTopCentreOfBlock(location);
		_flatBurst(centre, isFurnace);
	}

	public void enchantComplete(AbsoluteLocation location)
	{
		EntityLocation centre = _getTopCentreOfBlock(location);
		_upwardBurst(centre);
	}

	public void removeEntity(int id)
	{
		_entityDamageMillis.remove(id);
	}

	public void entityHurt(int id)
	{
		long endOfEffectMillis = System.currentTimeMillis() + DAMAGE_DURATION_MILLIS;
		_entityDamageMillis.put(id, endOfEffectMillis);
	}

	public float getDamageFreshnessFraction(long currentTimeMillis, int id)
	{
		float fraction;
		if (_entityDamageMillis.containsKey(id))
		{
			// We want to set the damage.
			long effectEndMillis = _entityDamageMillis.get(id);
			if (effectEndMillis > currentTimeMillis)
			{
				long millisLeft = effectEndMillis - currentTimeMillis;
				fraction = (float)millisLeft / (float)DAMAGE_DURATION_MILLIS;
			}
			else
			{
				fraction = 0.0f;
				_entityDamageMillis.remove(id);
			}
		}
		else
		{
			fraction = 0.0f;
		}
		return fraction;
	}

	public void setEndOfTickTime(long currentTimeMillis)
	{
		_lastTickTimeEndMillis = currentTimeMillis;
		
		// The end of tick also means we can stop building the new entity animation map.
		_activeEntityAnimationOffset = _buildingEntityAnimationOffset;
		_buildingEntityAnimationOffset = new HashMap<>();
	}

	public Collection<PartialPassive> getTweenedItemSlotPassives(long currentTimeMillis)
	{
		float secondsAdvanced = _getSecondsSinceTick(currentTimeMillis);
		return _worldCache.getItemSlotPassives().stream()
			.map((PartialPassive logical) -> {
				return _getTweenedInstance(logical, secondsAdvanced);
			})
			.toList()
		;
	}

	public Collection<PartialPassive> getTweenedFallingBlockPassives(long currentTimeMillis)
	{
		float secondsAdvanced = _getSecondsSinceTick(currentTimeMillis);
		return _worldCache.getFallingBlockPassives().stream()
			.map((PartialPassive logical) -> {
				return _getTweenedInstance(logical, secondsAdvanced);
			})
			.toList()
		;
	}

	public Collection<PartialPassive> getTweenedArrowPassives(long currentTimeMillis)
	{
		float secondsAdvanced = _getSecondsSinceTick(currentTimeMillis);
		return _worldCache.getArrowPassives().stream()
			.map((PartialPassive logical) -> {
				return _getTweenedInstance(logical, secondsAdvanced);
			})
			.toList()
		;
	}

	/**
	 * Updates any internal per-frame animation advance timers.
	 * 
	 * @param currentTimeMillis The current time when the frame started.
	 */
	public void startNewFrame(long currentTimeMillis)
	{
		// Make sure that it is time to update the frame animation.
		long millisSinceLast = (currentTimeMillis - _lastAnimationUpdateMillis);
		if (millisSinceLast > 10L)
		{
			_frameAnimationStep += 1;
			_lastAnimationUpdateMillis = currentTimeMillis;
		}
	}

	/**
	 * Looks up the walking animation frame for the given entity.  Note that it is acceptable and expected to ask this
	 * about entities it may not be tracking or which may have already died and it will return (byte)0 in those cases.
	 * Animation walking frames start at 0, then climb to 66, then fall to -64, then climb back to 0 and repeat.
	 * 
	 * @param entity The entity to look up.
	 * @return The animation frame (in the range [-64..64]) or 0, if not known.
	 */
	public byte getWalkingAnimationFrame(PartialEntity entity)
	{
		byte animationFrame;
		Byte offset = _activeEntityAnimationOffset.get(entity.id());
		if (null != offset)
		{
			animationFrame = (byte) (Math.abs((byte)(offset.byteValue() + _frameAnimationStep + 64)) - 64);
		}
		else
		{
			// "0" means "not animated".
			animationFrame = 0;
		}
		return animationFrame;
	}

	/**
	 * Called when processing updates from the end of a game tick to notify the receiver that the given entity is about
	 * to be updated in the rest of the system.  This allows it a chance to load the previous version from the
	 * WorldCache, if it is required, in order to check what changed.
	 * 
	 * @param entity The new entity instance which is about to overwrite an old instance of the same ID, in WorldCache.
	 */
	public void otherEntityWillUpdate(PartialEntity entity)
	{
		// We use this call to infer if an entity is animating.
		int id = entity.id();
		PartialEntity old = _worldCache.getCreatureOrEntityPartial(id);
		
		// This can only be called for something which already exists.
		Assert.assertTrue(null != old);
		
		EntityLocation oldLocation = old.location();
		EntityLocation newLocation = entity.location();
		
		// Ignore z movement but animate for horizontal movement.
		boolean didMove = (oldLocation.x() != newLocation.x()) || (oldLocation.y() != newLocation.y());
		if (didMove)
		{
			// If this isn't already present, add it.
			if (_activeEntityAnimationOffset.containsKey(id))
			{
				// We want to carry this forward.
				_buildingEntityAnimationOffset.put(id, _activeEntityAnimationOffset.get(id));
			}
			else
			{
				// We want to initialize this such that it will return 0 from getWalkingAnimationFrame on the first call.
				byte frameStart = (byte)(-_frameAnimationStep);
				_buildingEntityAnimationOffset.put(id, frameStart);
			}
		}
		else
		{
			// If this was in the active map, it will be dropped when the building map becomes active.
		}
	}

	public void handleFireAnimation(BlockRenderer blockRenderer, long currentTimeMillis)
	{
		Map<CuboidAddress, SparseByteCube> fireFaces = blockRenderer.renderFireBlocksAndReturnValidFaces();
		
		if ((currentTimeMillis - _lastFireParticleMillis) >= FIRE_PARTICLE_PERIOD_MILLIS)
		{
			for (Map.Entry<CuboidAddress, SparseByteCube> elt : fireFaces.entrySet())
			{
				CuboidAddress address = elt.getKey();
				SparseByteCube cube = elt.getValue();
				
				// TODO:  We need to update the particles periodically, but this will allow us to see the visible change, for now.
				AbsoluteLocation base = address.getBase();
				SparseByteCube.Walker walker = (int x, int y, int z, byte value) -> {
					float fullX = (float)(base.x() + x);
					float fullY = (float)(base.y() + y);
					float fullZ = (float)(base.z() + z);
					float midX = fullX + 0.5f;
					float midY = fullY + 0.5f;
					_createFireParticle(midX, fullY + 1.0f, fullZ
						, 0.0f, 0.2f
						, value, FireFaceBuilder.FACE_NORTH, currentTimeMillis
					);
					_createFireParticle(midX, fullY, fullZ
						, 0.0f, -0.2f
						, value, FireFaceBuilder.FACE_SOUTH, currentTimeMillis
					);
					_createFireParticle(fullX + 1.0f, midY, fullZ
						, 0.2f, 0.0f
						, value, FireFaceBuilder.FACE_EAST, currentTimeMillis
					);
					_createFireParticle(fullX, midY, fullZ
						, -0.2f, 0.0f
						, value, FireFaceBuilder.FACE_WEST, currentTimeMillis
					);
					_createFireParticle(midX, midY, fullZ + 1.0f
						, 0.0f, 0.0f
						, value, FireFaceBuilder.FACE_UP, currentTimeMillis
					);
					_createFireParticle(midX, midY, fullZ - 0.5f
						, 0.0f, 0.0f
						, value, FireFaceBuilder.FACE_DOWN, currentTimeMillis
					);
				};
				cube.walkAllValues(walker, 0, 0, 0, Encoding.CUBOID_EDGE_SIZE);
			}
			_lastFireParticleMillis = currentTimeMillis;
		}
	}


	private EntityLocation _getTopCentreOfBlock(AbsoluteLocation location)
	{
		EntityLocation base = location.toEntityLocation();
		EntityLocation centre = new EntityLocation(base.x() + 0.5f, base.y() + 0.5f, base.z() + 1.0f);
		return centre;
	}

	private void _flatBurst(EntityLocation location, boolean useRed)
	{
		float r = 1.0f;
		float g = useRed ? 0.0f : 1.0f;
		float b = useRed ? 0.0f : 1.0f;
		long currentTimeMillis = System.currentTimeMillis();
		_particleEngine.addNewParticle(location, new EntityLocation(location.x() - 1.0f, location.y(), location.z()), r, g, b, currentTimeMillis);
		_particleEngine.addNewParticle(location, new EntityLocation(location.x() + 1.0f, location.y(), location.z()), r, g, b, currentTimeMillis);
		_particleEngine.addNewParticle(location, new EntityLocation(location.x(), location.y() - 1.0f, location.z()), r, g, b, currentTimeMillis);
		_particleEngine.addNewParticle(location, new EntityLocation(location.x(), location.y() + 1.0f, location.z()), r, g, b, currentTimeMillis);
	}

	private void _upwardBurst(EntityLocation location)
	{
		float r = 0.0f;
		float g = 0.0f;
		float b = 1.0f;
		long currentTimeMillis = System.currentTimeMillis();
		_particleEngine.addNewParticle(location, new EntityLocation(location.x() - 1.0f, location.y(), location.z() + 0.5f), r, g, b, currentTimeMillis);
		_particleEngine.addNewParticle(location, new EntityLocation(location.x() + 1.0f, location.y(), location.z() + 0.5f), r, g, b, currentTimeMillis);
		_particleEngine.addNewParticle(location, new EntityLocation(location.x(), location.y() - 1.0f, location.z() + 0.5f), r, g, b, currentTimeMillis);
		_particleEngine.addNewParticle(location, new EntityLocation(location.x(), location.y() + 1.0f, location.z() + 0.5f), r, g, b, currentTimeMillis);
		_particleEngine.addNewParticle(location, new EntityLocation(location.x(), location.y(), location.z() + 1.0f), r, g, b, currentTimeMillis);
	}

	private float _getSecondsSinceTick(long currentTimeMillis)
	{
		long delta = currentTimeMillis - _lastTickTimeEndMillis;
		float secondsAdvanced = (float)delta / 1000.0f;
		return secondsAdvanced;
	}

	private PartialPassive _getTweenedInstance(PartialPassive logical, float secondsAdvanced)
	{
		EntityLocation location = logical.location();
		EntityLocation velocity = logical.velocity();
		EntityLocation offset = new EntityLocation(secondsAdvanced * velocity.x()
			, secondsAdvanced * velocity.y()
			, secondsAdvanced * velocity.z()
		);
		EntityLocation updated = new EntityLocation(location.x() + offset.x()
			, location.y() + offset.y()
			, location.z() + offset.z()
		);
		return new PartialPassive(logical.id()
			, logical.type()
			, updated
			, velocity
			, logical.extendedData()
		);
	}

	private void _createFireParticle(float startX, float startY, float startZ, float endOffX, float endOffY, byte bits, byte bit, long currentTimeMillis)
	{
		if (bit == (bits & bit))
		{
			EntityLocation location = new EntityLocation(startX, startY, startZ);
			float endOffZ = 0.5f;
			_particleEngine.addNewParticle(location, new EntityLocation(startX + endOffX, startY + endOffY, startZ + endOffZ), 1.0f, 0.0f, 0.0f, currentTimeMillis);
		}
	}
}
