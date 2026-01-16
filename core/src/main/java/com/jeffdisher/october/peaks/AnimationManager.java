package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.peaks.scene.ParticleEngine;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.utils.Assert;


/**
 * The high-level interface and internal tracking used to direct the ParticleEngine to generate more particles in
 * response to events and other things happening in the world.
 */
public class AnimationManager
{
	private final ParticleEngine _particleEngine;
	private final Map<Integer, EntityLocation> _entityAndCreatureLocations;

	public AnimationManager(ParticleEngine particleEngine)
	{
		_particleEngine = particleEngine;
		_entityAndCreatureLocations = new HashMap<>();
	}

	public void setEntityOrCreatureLocation(int id, EntityType type, EntityLocation location)
	{
		EntityLocation centre = SpatialHelpers.getCentreOfRegion(location, type.volume());
		// We use the same helper for new and updated so we ignore the return value.
		_entityAndCreatureLocations.put(id, centre);
	}

	public void removeEntityOrCreature(int id)
	{
		Object old = _entityAndCreatureLocations.remove(id);
		Assert.assertTrue(null != old);
	}

	public void craftInInventoryComplete(int entityId)
	{
		EntityLocation centre = _entityAndCreatureLocations.get(entityId);
		_flatBurst(centre, false);
	}

	public void craftInBlockComplete(AbsoluteLocation location, boolean isFurnace)
	{
		EntityLocation centre = _getTopCentreOfBlock(location);
		_flatBurst(centre, isFurnace);
	}

	public void enchantComplete(AbsoluteLocation location)
	{
		EntityLocation centre = _getTopCentreOfBlock(location);
		_upwardBurst(centre);
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
}
