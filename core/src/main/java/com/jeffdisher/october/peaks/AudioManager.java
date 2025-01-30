package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.OrientationHelpers;
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

	public static AudioManager load(Environment environment, Map<Cue, String> audioFileNames)
	{
		Sound walk = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.WALK)));
		Sound takeDamage = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.TAKE_DAMAGE)));
		Sound breakBlock = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.BREAK_BLOCK)));
		Sound placeBlock = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.PLACE_BLOCK)));
		Sound cowIdle = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.COW_IDLE)));
		Sound cowInjury = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.COW_INJURY)));
		Sound cowDeath = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.COW_DEATH)));
		Sound orcIdle = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.ORC_IDLE)));
		Sound orcInjury = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.ORC_INJURY)));
		Sound orcDeath = Gdx.audio.newSound(Gdx.files.internal(audioFileNames.get(Cue.ORC_DEATH)));
		return new AudioManager(environment
				, walk
				, takeDamage
				, breakBlock
				, placeBlock
				, cowIdle
				, cowInjury
				, cowDeath
				, orcIdle
				, orcInjury
				, orcDeath
		);
	}

	private final Random _random;
	private final EntityType _player;
	private final EntityType _cow;
	private final EntityType _orc;
	private final Sound _walk;
	private final Sound _takeDamage;
	private final Sound _breakBlock;
	private final Sound _placeBlock;
	private final Sound _cowIdle;
	private final Sound _cowInjury;
	private final Sound _cowDeath;
	private final Sound _orcIdle;
	private final Sound _orcInjury;
	private final Sound _orcDeath;
	private final long _walkingId;

	private Entity _projectedEntity;
	private final Map<Integer, PartialEntity> _otherEntities;
	private boolean _isWalking;

	private AudioManager(Environment environment
			, Sound walk
			, Sound takeDamage
			, Sound breakBlock
			, Sound placeBlock
			, Sound cowIdle
			, Sound cowInjury
			, Sound cowDeath
			, Sound orcIdle
			, Sound orcInjury
			, Sound orcDeath
	)
	{
		_random = new Random();
		_player = environment.creatures.PLAYER;
		_cow = environment.creatures.getTypeById("op.cow");
		_orc = environment.creatures.getTypeById("op.orc");
		_walk = walk;
		_takeDamage = takeDamage;
		_breakBlock = breakBlock;
		_placeBlock = placeBlock;
		_cowIdle = cowIdle;
		_cowInjury = cowInjury;
		_cowDeath = cowDeath;
		_orcIdle = orcIdle;
		_orcInjury = orcInjury;
		_orcDeath = orcDeath;
		
		_otherEntities = new HashMap<>();
		
		// We will start the walking sound as looping and pause it.
		_walkingId = _walk.loop();
		_walk.pause(_walkingId);
	}

	public void setThisEntity(Entity authoritativeEntity, Entity projectedEntity)
	{
		_projectedEntity = projectedEntity;
	}

	public void setOtherEntity(PartialEntity otherEntity)
	{
		// This is called whether the entity is new or updated so we can't check if it is already here.
		_otherEntities.put(otherEntity.id(), otherEntity);
	}

	public void removeOtherEntity(int entityId)
	{
		PartialEntity other  = _otherEntities.remove(entityId);
		Assert.assertTrue(null != other);
	}

	public void tickCompleted()
	{
		// See if there is a nearby entity which should make a noise.
		AbsoluteLocation entityLocation = _projectedEntity.location().getBlockLocation();
		for (PartialEntity other : _otherEntities.values())
		{
			// We only care abut orcs and cows.
			Sound soundToPlay = null;
			EntityType type = other.type();
			if (_cow == type)
			{
				soundToPlay = _cowIdle;
			}
			else if (_orc == type)
			{
				soundToPlay = _orcIdle;
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
			_walk.resume(_walkingId);
			_isWalking = true;
		}
	}

	public void setStanding()
	{
		if (_isWalking)
		{
			_walk.pause(_walkingId);
			_isWalking = false;
		}
	}

	public void blockBroken(AbsoluteLocation location)
	{
		_playSoundIfInRange(location, _breakBlock);
	}

	public void blockPlaced(AbsoluteLocation location)
	{
		_playSoundIfInRange(location, _placeBlock);
	}

	public void thisEntityHurt()
	{
		float fullVolume = 1.0f;
		float defaultPitch = 1.0f;
		float centreBalance = 0.0f;
		_takeDamage.play(fullVolume, defaultPitch, centreBalance);
	}

	public void otherEntityHurt(AbsoluteLocation location, int entityTargetId)
	{
		Sound soundToPlay = _selectSoundForEntity(entityTargetId, _orcInjury, _cowInjury, _takeDamage);
		_playSoundIfInRange(location, soundToPlay);
	}

	public void otherEntityKilled(AbsoluteLocation location, int entityTargetId)
	{
		// Entities don't have special death sounds so just play injury.
		Sound soundToPlay = _selectSoundForEntity(entityTargetId, _orcDeath, _cowDeath, _takeDamage);
		_playSoundIfInRange(location, soundToPlay);
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
		EntityLocation entityLocation = _projectedEntity.location();
		float xDistance = (float)otherLocation.x() - entityLocation.x();
		float yDistance = (float)otherLocation.y() - entityLocation.y();
		float zDistance = (float)otherLocation.z() - entityLocation.z();
		float floatSquaredDistance = (xDistance * xDistance) + (yDistance * yDistance) + (zDistance * zDistance);
		float closeness = AUDIO_RANGE_SQUARED_FLOAT - floatSquaredDistance;
		float volume = closeness / AUDIO_RANGE_SQUARED_FLOAT;
		
		// To figure out the pan, we figure out which way we are currently facing:  yaw 0 is north and positive turns counter-clockwise.
		byte yaw = _projectedEntity.yaw();
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
		EntityType type = _otherEntities.get(entityTargetId).type();
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
			// This would be an unkonwn type.
			throw Assert.unreachable();
		}
		return soundToPlay;
	}

	private void _playSoundIfInRange(AbsoluteLocation location, Sound soundToPlay)
	{
		AbsoluteLocation entityLocation = _projectedEntity.location().getBlockLocation();
		int distanceSquared = _squaredDistance(entityLocation, location);
		if (distanceSquared <= AUDIO_RANGE_SQUARED)
		{
			_playSound(soundToPlay, location);
		}
	}


	public static enum Cue
	{
		WALK,
		TAKE_DAMAGE,
		BREAK_BLOCK,
		PLACE_BLOCK,
		COW_IDLE,
		COW_INJURY,
		COW_DEATH,
		ORC_IDLE,
		ORC_INJURY,
		ORC_DEATH,
	};
}
