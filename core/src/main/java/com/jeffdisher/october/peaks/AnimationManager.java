package com.jeffdisher.october.peaks;

import com.jeffdisher.october.peaks.scene.ParticleEngine;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;


/**
 * The high-level interface and internal tracking used to direct the ParticleEngine to generate more particles in
 * response to events and other things happening in the world.
 */
public class AnimationManager
{
	private final ParticleEngine _particleEngine;
	private final WorldCache _worldCache;

	public AnimationManager(ParticleEngine particleEngine, WorldCache worldCache)
	{
		_particleEngine = particleEngine;
		_worldCache = worldCache;
	}

	public void craftInInventoryComplete(int entityId)
	{
		EntityLocation centre = _worldCache.getCentreOfPlayerOrCreature(entityId);
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
