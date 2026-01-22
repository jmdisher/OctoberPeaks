package com.jeffdisher.october.peaks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.CraftingBlockSupport;
import com.jeffdisher.october.peaks.scene.ParticleEngine;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialPassive;


/**
 * The high-level interface and internal tracking used to direct the ParticleEngine to generate more particles in
 * response to events and other things happening in the world.
 */
public class AnimationManager
{
	public static final long DAMAGE_DURATION_MILLIS = 1000L;

	private final Environment _environment;
	private final ParticleEngine _particleEngine;
	private final WorldCache _worldCache;
	private final Map<Integer, Long> _entityDamageMillis;
	private long _lastTickTimeEndMillis;

	public AnimationManager(Environment environment, ParticleEngine particleEngine, WorldCache worldCache)
	{
		_environment = environment;
		_particleEngine = particleEngine;
		_worldCache = worldCache;
		_entityDamageMillis = new HashMap<>();
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
}
