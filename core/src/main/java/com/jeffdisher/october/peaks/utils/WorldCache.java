package com.jeffdisher.october.peaks.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.Pair;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.types.TickProcessingContext;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.CommonBlockFetcher;


/**
 * This is just a container of data the client knows (the projected local projection) for convenient look-up by various
 * parts of the UI.
 */
public class WorldCache
{
	public final EntityType playerType;
	public final TickProcessingContext.IBlockFetcher blockLookup;

	private Entity _thisEntity;

	private final Map<Integer, PartialEntity> _otherEntities;

	private final Map<Integer, PartialPassive> _passiveItems;
	private final Map<Integer, PartialPassive> _itemSlotPassives;
	private final Map<Integer, PartialPassive> _fallingBlockPassives;
	private final Map<Integer, PartialPassive> _arrowPassives;

	private final Map<CuboidAddress, _CuboidData> _cuboids;

	public WorldCache(EntityType playerType)
	{
		this.playerType = playerType;
		
		_otherEntities = new HashMap<>();
		_passiveItems = new HashMap<>();
		_itemSlotPassives = new HashMap<>();
		_fallingBlockPassives = new HashMap<>();
		_arrowPassives = new HashMap<>();
		_cuboids = new HashMap<>();
		
		this.blockLookup = new CommonBlockFetcher(new _DataFetcher());
	}

	public IReadOnlyCuboidData getCuboid(CuboidAddress cuboidAddress)
	{
		_CuboidData data = _cuboids.get(cuboidAddress);
		return (null != data)
			? data.cuboid
			: null
		;
	}

	public Entity getThisEntity()
	{
		return _thisEntity;
	}

	public Collection<PartialPassive> getItemSlotPassives()
	{
		return Collections.unmodifiableCollection(_itemSlotPassives.values());
	}

	public Collection<PartialPassive> getFallingBlockPassives()
	{
		return Collections.unmodifiableCollection(_fallingBlockPassives.values());
	}

	public Collection<PartialPassive> getArrowPassives()
	{
		return Collections.unmodifiableCollection(_arrowPassives.values());
	}

	public Collection<PartialEntity> getOtherEntities()
	{
		return Collections.unmodifiableCollection(_otherEntities.values());
	}

	public EntityLocation getCentreOfPlayerOrCreature(int id)
	{
		// This interface seems a little specialized to AnimationManager but doesn't mean returning the various pieces of information in their own calls.
		EntityLocation centre;
		if (_thisEntity.id() == id)
		{
			centre = SpatialHelpers.getCentreOfRegion(_thisEntity.location(), this.playerType.volume());
		}
		else
		{
			PartialEntity partial = _otherEntities.get(id);
			centre = SpatialHelpers.getCentreOfRegion(partial.location(), partial.type().volume());
		}
		return centre;
	}

	public EntityType getCreatureOrEntityType(int id)
	{
		EntityType type;
		if (_thisEntity.id() == id)
		{
			type = this.playerType;
		}
		else
		{
			PartialEntity partial = _otherEntities.get(id);
			type = partial.type();
		}
		return type;
	}

	public PartialEntity getCreatureOrEntityPartial(int id)
	{
		// Note that this can also return a partial for the local entity.
		PartialEntity partial;
		if (_thisEntity.id() == id)
		{
			partial = PartialEntity.fromEntity(_thisEntity);
		}
		else
		{
			partial = _otherEntities.get(id);
		}
		return partial;
	}

	public PartialPassive getItemSlotPassive(int id)
	{
		return _itemSlotPassives.get(id);
	}

	// ----- Methods to update the cache state -----
	public void addCuboid(IReadOnlyCuboidData cuboid)
	{
		Object old = _cuboids.put(cuboid.getCuboidAddress(), new _CuboidData(cuboid));
		Assert.assertTrue(null == old);
	}

	public void updateCuboid(IReadOnlyCuboidData cuboid)
	{
		Object old = _cuboids.put(cuboid.getCuboidAddress(), new _CuboidData(cuboid));
		Assert.assertTrue(null != old);
	}

	public void removeCuboid(CuboidAddress address)
	{
		Object old = _cuboids.remove(address);
		Assert.assertTrue(null != old);
	}

	public void setThisEntity(Entity entity)
	{
		_thisEntity = entity;
	}

	public void addOtherEntity(PartialEntity partial)
	{
		Object old = _otherEntities.put(partial.id(), partial);
		Assert.assertTrue(null == old);
	}

	public void updateOtherEntity(PartialEntity partial)
	{
		Object old = _otherEntities.put(partial.id(), partial);
		Assert.assertTrue(null != old);
	}

	public void removeOtherEntity(int entityId)
	{
		Object old = _otherEntities.remove(entityId);
		Assert.assertTrue(null != old);
	}

	public void addPassive(PartialPassive passive)
	{
		int id = passive.id();
		PassiveType type = passive.type();
		_passiveItems.put(id, passive);
		
		Object old;
		if (PassiveType.ITEM_SLOT == type)
		{
			old = _itemSlotPassives.put(id, passive);
		}
		else if (PassiveType.PROJECTILE_ARROW == type)
		{
			old = _arrowPassives.put(id, passive);
		}
		else if (PassiveType.FALLING_BLOCK == type)
		{
			old = _fallingBlockPassives.put(id, passive);
		}
		else
		{
			// This is an unknown type we need to support.
			throw Assert.unreachable();
		}
		Assert.assertTrue(null == old);
	}

	public void updatePassive(PartialPassive passive)
	{
		int id = passive.id();
		PassiveType type = passive.type();
		_passiveItems.put(id, passive);
		
		Object old;
		if (PassiveType.ITEM_SLOT == type)
		{
			old = _itemSlotPassives.put(id, passive);
		}
		else if (PassiveType.PROJECTILE_ARROW == type)
		{
			old = _arrowPassives.put(id, passive);
		}
		else if (PassiveType.FALLING_BLOCK == type)
		{
			old = _fallingBlockPassives.put(id, passive);
		}
		else
		{
			// This is an unknown type we need to support.
			throw Assert.unreachable();
		}
		Assert.assertTrue(null != old);
	}

	public void removePassiveEntity(int entityId)
	{
		PartialPassive removed = _passiveItems.remove(entityId);
		PassiveType type = removed.type();
		
		Object old;
		if (PassiveType.ITEM_SLOT == type)
		{
			old = _itemSlotPassives.remove(entityId);
		}
		else if (PassiveType.PROJECTILE_ARROW == type)
		{
			old = _arrowPassives.remove(entityId);
		}
		else if (PassiveType.FALLING_BLOCK == type)
		{
			old = _fallingBlockPassives.remove(entityId);
		}
		else
		{
			// This is an unknown type we need to support.
			throw Assert.unreachable();
		}
		Assert.assertTrue(null != old);
	}


	private static class _CuboidData
	{
		public final IReadOnlyCuboidData cuboid;
		public Map<BlockAddress, BlockProxy> cache;
		public _CuboidData(IReadOnlyCuboidData cuboid)
		{
			this.cuboid = cuboid;
		}
	}

	private class _DataFetcher implements Function<CuboidAddress, Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>>>
	{
		@Override
		public Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>> apply(CuboidAddress cuboidAddress)
		{
			Pair<IReadOnlyCuboidData, Map<BlockAddress, BlockProxy>> pair = null;
			_CuboidData tuple = _cuboids.get(cuboidAddress);
			
			if (null != tuple)
			{
				Map<BlockAddress, BlockProxy> cache = tuple.cache;
				if (null == cache)
				{
					cache = new HashMap<>();
					tuple.cache = cache;
				}
				pair = new Pair<>(tuple.cuboid, cache);
			}
			return pair;
		}
	}
}
