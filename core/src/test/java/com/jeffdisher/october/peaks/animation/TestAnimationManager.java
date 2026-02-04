package com.jeffdisher.october.peaks.animation;

import java.util.Collection;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
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
		worldCache.setThisEntity(MutableEntity.createForTest(1).freeze());
		long currentMillis = 1000L;
		AnimationManager animationManager = new AnimationManager(ENV, null, worldCache, currentMillis);
		
		int otherId = -1;
		PartialEntity originalEntity = new PartialEntity(otherId
			, ENV.creatures.PLAYER
			, new EntityLocation(1.0f, 2.0f, 3.0f)
			, (byte)0
			, (byte)0
			, (byte)100
			, null
		);
		worldCache.addOtherEntity(originalEntity);
		
		PartialEntity movedEntity = new PartialEntity(originalEntity.id()
			, originalEntity.type()
			, new EntityLocation(2.0f, 2.0f, 3.0f)
			, (byte)0
			, (byte)0
			, originalEntity.health()
			, originalEntity.extendedData()
		);
		animationManager.otherEntityWillUpdate(movedEntity);
		worldCache.updateOtherEntity(movedEntity);
		animationManager.setEndOfTickTime(currentMillis);
		
		// We expect to see the frames go from 0 to 32, back to 0, then to -32, then to 0 (but we stop one tick before the third 0).
		int[] counts = new int[65];
		for (int i = 0; i < 256; ++i)
		{
			byte frame = animationManager.getWalkingAnimationFrame(movedEntity);
			counts[frame + 32] += 1;
			currentMillis += 5L;
			animationManager.startNewFrame(currentMillis);
		}
		
		Assert.assertEquals(2, counts[0]);
		for (int i = 0; i < 63; ++i)
		{
			Assert.assertEquals(4, counts[i + 1]);
		}
		Assert.assertEquals(2, counts[64]);
	}
}
