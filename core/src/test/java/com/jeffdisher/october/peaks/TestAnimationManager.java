package com.jeffdisher.october.peaks;

import java.util.Collection;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;


public class TestAnimationManager
{
	private static Environment ENV;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basicTweening() throws Throwable
	{
		WorldCache worldCache = new WorldCache(ENV.creatures.PLAYER);
		long currentMillis = 1000L;
		AnimationManager animationManager = new AnimationManager(ENV, null, worldCache, currentMillis);
		
		int passiveId = 3;
		worldCache.addPassive(new PartialPassive(passiveId
			, PassiveType.ITEM_SLOT
			, new EntityLocation(10.0f, 10.0f, 10.0f)
			, new EntityLocation(0.0f, 0.0f, -1.0f)
			, null
		));
		
		animationManager.setEndOfTickTime(currentMillis);
		Collection<PartialPassive> unmoving = animationManager.getTweenedItemSlotPassives(currentMillis);
		Assert.assertEquals(1, unmoving.size());
		PartialPassive passive = unmoving.iterator().next();
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 10.0f), passive.location());
		
		currentMillis += 100L;
		Collection<PartialPassive> moving = animationManager.getTweenedItemSlotPassives(currentMillis);
		Assert.assertEquals(1, moving.size());
		passive = moving.iterator().next();
		Assert.assertEquals(new EntityLocation(10.0f, 10.0f, 9.90f), passive.location());
	}

	@Test
	public void animationFrame() throws Throwable
	{
		WorldCache worldCache = new WorldCache(ENV.creatures.PLAYER);
		long currentMillis = 1000L;
		AnimationManager animationManager = new AnimationManager(ENV, null, worldCache, currentMillis);
		
		PartialEntity entity = new PartialEntity(1
			, ENV.creatures.PLAYER
			, new EntityLocation(1.0f, 2.0f, 3.0f)
			, (byte)0
			, (byte)0
			, (byte)100
			, null
		);
		worldCache.addOtherEntity(entity);
		
		animationManager.setEndOfTickTime(currentMillis);
		currentMillis += 17L;
		animationManager.startNewFrame(currentMillis);
		
		byte frame = animationManager.getWalkingAnimationFrame(entity);
		Assert.assertEquals(1, frame);
	}
}
