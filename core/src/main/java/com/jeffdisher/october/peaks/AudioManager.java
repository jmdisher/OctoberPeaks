package com.jeffdisher.october.peaks;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Controls the audio cues in the client.
 * This is nearly identical to the same file in OctoberPlains, as the only meaningful difference between how the 2
 * projects use audio is with regard to direction.
 */
public class AudioManager
{
	public static final float AUDIO_RANGE_FLOAT = 10.0f;
	public static final float AUDIO_RANGE_SQUARED_FLOAT = AUDIO_RANGE_FLOAT * AUDIO_RANGE_FLOAT;
	public static final int AUDIO_RANGE_SQUARED = (int)AUDIO_RANGE_SQUARED_FLOAT;
	// We randomly make a noise every 1000 ticks - so every 10 seconds.
	public static int IDLE_SOUND_PER_TICK_DIVISOR = 1000;

	public static class Resources
	{
		private final Sound _walk;
		private final Sound _run;
		private final Sound _takeDamage;
		private final Sound _breakBlock;
		private final Sound _placeBlock;
		private final Sound _cowIdle;
		private final Sound _cowInjury;
		private final Sound _cowDeath;
		private final Sound _orcIdle;
		private final Sound _orcInjury;
		private final Sound _orcDeath;
		
		public Resources()
		{
			_walk = Gdx.audio.newSound(Gdx.files.internal("walking.ogg"));
			_run = Gdx.audio.newSound(Gdx.files.internal("running.ogg"));
			_takeDamage = Gdx.audio.newSound(Gdx.files.internal("take_damage.ogg"));
			_breakBlock = Gdx.audio.newSound(Gdx.files.internal("break_block.ogg"));
			_placeBlock = Gdx.audio.newSound(Gdx.files.internal("place_block.ogg"));
			_cowIdle = Gdx.audio.newSound(Gdx.files.internal("cow_idle.ogg"));
			_cowInjury = Gdx.audio.newSound(Gdx.files.internal("cow_injury.ogg"));
			_cowDeath = Gdx.audio.newSound(Gdx.files.internal("cow_death.ogg"));
			_orcIdle = Gdx.audio.newSound(Gdx.files.internal("orc_idle.ogg"));
			_orcInjury = Gdx.audio.newSound(Gdx.files.internal("orc_injury.ogg"));
			_orcDeath = Gdx.audio.newSound(Gdx.files.internal("orc_death.ogg"));
		}
		
		public void shutdown()
		{
			_walk.dispose();
			_run.dispose();
			_takeDamage.dispose();
			_breakBlock.dispose();
			_placeBlock.dispose();
			_cowIdle.dispose();
			_cowInjury.dispose();
			_cowDeath.dispose();
			_orcIdle.dispose();
			_orcInjury.dispose();
			_orcDeath.dispose();
		}
	}


	private final Random _random;
	private final EntityType _player;
	private final EntityType _cow;
	private final EntityType _orc;
	private final Resources _resources;
	private final long _walkingId;
	private final long _runningId;

	private final WorldCache _worldCache;
	private boolean _isWalking;
	private boolean _isRunning;

	public AudioManager(Environment environment
		, LoadedResources resources
		, WorldCache worldCache
	)
	{
		_random = new Random();
		_resources = resources.audioManager();
		_worldCache = worldCache;
		_player = environment.creatures.PLAYER;
		_cow = environment.creatures.getTypeById("op.cow");
		_orc = environment.creatures.getTypeById("op.orc");
		
		// We will start the walking sound as looping and pause it.
		_walkingId = _resources._walk.loop();
		_resources._walk.pause(_walkingId);
		_runningId = _resources._run.loop();
		_resources._run.pause(_runningId);
	}

	public void tickCompleted()
	{
		// See if there is a nearby entity which should make a noise.
		Entity thisEntity = _worldCache.getThisEntity();
		AbsoluteLocation entityLocation = thisEntity.location().getBlockLocation();
		for (PartialEntity other : _worldCache.getOtherEntities())
		{
			// We only care abut orcs and cows.
			Sound soundToPlay = null;
			EntityType type = other.type();
			if (_cow == type)
			{
				soundToPlay = _resources._cowIdle;
			}
			else if (_orc == type)
			{
				soundToPlay = _resources._orcIdle;
			}
			if (null != soundToPlay)
			{
				// Check if they are close enough to hear them.
				AbsoluteLocation otherLocation = other.location().getBlockLocation();
				int distanceSquared = _squaredDistance(entityLocation, otherLocation);
				if (distanceSquared <= AUDIO_RANGE_SQUARED)
				{
					// Generate a random number to see if this should make a sound.
					if (0 == _random.nextInt(IDLE_SOUND_PER_TICK_DIVISOR))
					{
						_playSound(soundToPlay, otherLocation);
					}
				}
			}
		}
	}

	public void setWalking()
	{
		if (!_isWalking)
		{
			if (_isRunning)
			{
				_resources._run.pause(_runningId);
				_isRunning = false;
			}
			_resources._walk.resume(_walkingId);
			_isWalking = true;
		}
	}

	public void setRunning()
	{
		if (!_isRunning)
		{
			if (_isWalking)
			{
				_resources._walk.pause(_walkingId);
				_isWalking = false;
			}
			_resources._run.resume(_runningId);
			_isRunning = true;
		}
	}

	public void setStanding()
	{
		if (_isWalking)
		{
			_resources._walk.pause(_walkingId);
			_isWalking = false;
		}
		if (_isRunning)
		{
			_resources._run.pause(_runningId);
			_isRunning = false;
		}
	}

	public void blockBroken(AbsoluteLocation location)
	{
		_playSoundIfInRange(location, _resources._breakBlock);
	}

	public void blockPlaced(AbsoluteLocation location)
	{
		_playSoundIfInRange(location, _resources._placeBlock);
	}

	public void thisEntityHurt()
	{
		float fullVolume = 1.0f;
		float defaultPitch = 1.0f;
		float centreBalance = 0.0f;
		_resources._takeDamage.play(fullVolume, defaultPitch, centreBalance);
	}

	public void otherEntityHurt(AbsoluteLocation location, int entityTargetId)
	{
		Sound soundToPlay = _selectSoundForEntity(entityTargetId, _resources._orcInjury, _resources._cowInjury, _resources._takeDamage);
		if (null != soundToPlay)
		{
			_playSoundIfInRange(location, soundToPlay);
		}
	}

	public void otherEntityKilled(AbsoluteLocation location, int entityTargetId)
	{
		Sound soundToPlay = _selectSoundForEntity(entityTargetId, _resources._orcDeath, _resources._cowDeath, _resources._takeDamage);
		if (null != soundToPlay)
		{
			_playSoundIfInRange(location, soundToPlay);
		}
	}

	public void shutdown()
	{
		_resources._walk.stop(_walkingId);
	}


	private int _squaredDistance(AbsoluteLocation entityLocation, AbsoluteLocation blockLocation)
	{
		int xDistance = entityLocation.x() - blockLocation.x();
		int yDistance = entityLocation.y() - blockLocation.y();
		int zDistance = entityLocation.z() - blockLocation.z();
		return (xDistance * xDistance)
				+ (yDistance * yDistance)
				+ (zDistance * zDistance)
		;
	}

	private void _playSound(Sound soundToPlay, AbsoluteLocation otherLocation)
	{
		Entity thisEntity = _worldCache.getThisEntity();
		EntityLocation entityLocation = thisEntity.location();
		float xDistance = (float)otherLocation.x() - entityLocation.x();
		float yDistance = (float)otherLocation.y() - entityLocation.y();
		float zDistance = (float)otherLocation.z() - entityLocation.z();
		float floatSquaredDistance = (xDistance * xDistance) + (yDistance * yDistance) + (zDistance * zDistance);
		float closeness = AUDIO_RANGE_SQUARED_FLOAT - floatSquaredDistance;
		float volume = closeness / AUDIO_RANGE_SQUARED_FLOAT;
		
		// To figure out the pan, we figure out which way we are currently facing:  yaw 0 is north and positive turns counter-clockwise.
		byte yaw = thisEntity.yaw();
		float yawRadians = OrientationHelpers.getYawRadians(yaw);
		float distance = (float)Math.sqrt(floatSquaredDistance);
		Assert.assertTrue(xDistance <= distance);
		
		// Now, compute the difference between these angles to find the effective pan.
		float pan;
		if (distance > 0.0f)
		{
			float soundRadians = (float)Math.acos(xDistance / distance);
			float radiansToSound = soundRadians - yawRadians;
			pan = (float) Math.cos(radiansToSound);
			Assert.assertTrue(pan >= -1.0f);
			Assert.assertTrue(pan <= 1.0f);
		}
		else
		{
			pan = 0.0f;
		}
		
		// The panning value for the sound is -1 left, 0 centre, 1 right.
		soundToPlay.play(volume, 1.0f, pan);
	}

	private Sound _selectSoundForEntity(int entityTargetId, Sound orc, Sound cow, Sound player)
	{
		Sound soundToPlay;
		EntityType type = _worldCache.getCreatureOrEntityType(entityTargetId);
		if (_cow == type)
		{
			soundToPlay = cow;
		}
		else if (_orc == type)
		{
			soundToPlay = orc;
		}
		else if (_player == type)
		{
			soundToPlay = player;
		}
		else
		{
			// For now, we will return null on an unknown type and they will have no sound.
			soundToPlay = null;
		}
		return soundToPlay;
	}

	private void _playSoundIfInRange(AbsoluteLocation location, Sound soundToPlay)
	{
		Entity thisEntity = _worldCache.getThisEntity();
		AbsoluteLocation entityLocation = thisEntity.location().getBlockLocation();
		int distanceSquared = _squaredDistance(entityLocation, location);
		if (distanceSquared <= AUDIO_RANGE_SQUARED)
		{
			_playSound(soundToPlay, location);
		}
	}
}
