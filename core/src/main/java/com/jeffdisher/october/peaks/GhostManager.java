package com.jeffdisher.october.peaks;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.utils.Assert;


/**
 * Ghosts are creatures/entities/passives which have disappeared but we still need to animate their departure.
 * In the case of creatures/entities, this is for the cases where they died and we want to animate them fading.
 * In the case of passives, it is when an item slot is picked up and we want to animate it moving to the entity which
 * picked them up instead of just disappearing.
 */
public class GhostManager
{
	public static final long DEATH_DELAY_MILLIS = 2000L;
	public static final long PICK_UP_DELAY_MILLIS = 100L;

	private final WorldCache _worldCache;
	private final List<_Ghost<PartialEntity>> _ghostEntities;
	private final List<_Ghost<PartialPassive>> _ghostPassives;

	public GhostManager(WorldCache worldCache)
	{
		_worldCache = worldCache;
		_ghostEntities = new ArrayList<>();
		_ghostPassives = new ArrayList<>();
	}

	public void entityWasKilled(long currentTimeMillis, int id)
	{
		PartialEntity entity = _worldCache.getCreatureOrEntityPartial(id);
		
		// Note that we expect to see the entity who was killed (will be filtered out before we get here if not).
		Assert.assertTrue(null != entity);
		
		EntityLocation location = entity.location();
		_Ghost<PartialEntity> ghost = new _Ghost<>(entity
			, currentTimeMillis + DEATH_DELAY_MILLIS
			, new EntityLocation(location.x(), location.y(), location.z() - 1.0f)
		);
		_ghostEntities.add(ghost);
	}

	public void passiveWasPickedUp(long currentTimeMillis, int passiveId, int pickingUpEntityId)
	{
		PartialPassive passive = _worldCache.getItemSlotPassive(passiveId);
		PartialEntity entity = _worldCache.getCreatureOrEntityPartial(pickingUpEntityId);
		
		// It is possible that the entity is just out of range but we should always see the passive.
		Assert.assertTrue(null != passive);
		if (null != entity)
		{
			_Ghost<PartialPassive> ghost = new _Ghost<>(passive
				, currentTimeMillis + PICK_UP_DELAY_MILLIS
				, SpatialHelpers.getCentreOfRegion(entity.location(), entity.type().volume())
			);
			_ghostPassives.add(ghost);
		}
	}

	public List<PartialEntity> pruneAndSnapshotEntities(long currentTimeMillis)
	{
		// Prune the list - we know that it is sorted since everything in it has the same TTL.
		while (!_ghostEntities.isEmpty() && (_ghostEntities.get(0).endOfLifeMillis <= currentTimeMillis))
		{
			_ghostEntities.remove(0);
		}
		
		// Now, map the remainder into a list we can return.
		return _ghostEntities.stream()
			.map((_Ghost<PartialEntity> ghost) -> {
				PartialEntity corpse = ghost.corpse;
				
				long millisRemaining = ghost.endOfLifeMillis - currentTimeMillis;
				EntityLocation location = _getTweenLocation(corpse.location(), ghost.endLocation, millisRemaining, DEATH_DELAY_MILLIS);
				
				return new PartialEntity(corpse.id()
					, corpse.type()
					, location
					, corpse.yaw()
					, corpse.pitch()
					, corpse.health()
					, corpse.extendedData()
				);
			})
			.toList()
		;
	}

	public List<PartialPassive> pruneAndSnapshotItemSlotPassives(long currentTimeMillis)
	{
		// Prune the list - we know that it is sorted since everything in it has the same TTL.
		while (!_ghostPassives.isEmpty() && (_ghostPassives.get(0).endOfLifeMillis <= currentTimeMillis))
		{
			_ghostPassives.remove(0);
		}
		
		// Now, map the remainder into a list we can return.
		return _ghostPassives.stream()
			.map((_Ghost<PartialPassive> ghost) -> {
				PartialPassive corpse = ghost.corpse;
				
				long millisRemaining = ghost.endOfLifeMillis - currentTimeMillis;
				EntityLocation location = _getTweenLocation(corpse.location(), ghost.endLocation, millisRemaining, PICK_UP_DELAY_MILLIS);
				
				return new PartialPassive(corpse.id()
					, corpse.type()
					, location
					, corpse.velocity()
					, corpse.extendedData()
				);
			})
			.toList()
		;
	}


	private static EntityLocation _getTweenLocation(EntityLocation start
		, EntityLocation end
		, long millisRemaining
		, long millisTotal
	)
	{
		long millisPassed = millisTotal - millisRemaining;
		float fractionCompleted = (float)millisPassed / (float)millisTotal;
		
		float deltaX = end.x() - start.x();
		float deltaY = end.y() - start.y();
		float deltaZ = end.z() - start.z();
		return new EntityLocation(start.x() + (fractionCompleted * deltaX)
			, start.y() + (fractionCompleted * deltaY)
			, start.z() + (fractionCompleted * deltaZ)
		);
	}


	private static record _Ghost<T>(T corpse
		, long endOfLifeMillis
		, EntityLocation endLocation
	) {}
}
