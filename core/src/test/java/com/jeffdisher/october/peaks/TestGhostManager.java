package com.jeffdisher.october.peaks;

import java.util.List;

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


public class TestGhostManager
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
	public void basicPassive() throws Throwable
	{
		WorldCache worldCache = new WorldCache(ENV.creatures.PLAYER);
		GhostManager ghostManager = new GhostManager(worldCache);
		int entityId = 1;
		int otherEntityId = 2;
		int passiveId = 3;
		
		worldCache.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		worldCache.addOtherEntity(new PartialEntity(otherEntityId
			, ENV.creatures.PLAYER
			, new EntityLocation(1.0f, 1.0f, 1.0f)
			, (byte)0
			, (byte)0
			, (byte)0
			, null
		));
		worldCache.addPassive(new PartialPassive(passiveId
			, PassiveType.ITEM_SLOT
			, new EntityLocation(0.0f, 0.0f, 0.0f)
			, null
			, null
		));
		
		long currentMillis = 1000L;
		ghostManager.passiveWasPickedUp(currentMillis, passiveId, otherEntityId);
		List<PartialPassive> ghosts = ghostManager.pruneAndSnapshotItemSlotPassives(currentMillis);
		Assert.assertEquals(1, ghosts.size());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), ghosts.get(0).location());
		ghosts = ghostManager.pruneAndSnapshotItemSlotPassives(currentMillis + GhostManager.PICK_UP_DELAY_MILLIS / 2L);
		Assert.assertEquals(1, ghosts.size());
		Assert.assertEquals(new EntityLocation(0.6f, 0.6f, 0.93f), ghosts.get(0).location());
		ghosts = ghostManager.pruneAndSnapshotItemSlotPassives(currentMillis + GhostManager.PICK_UP_DELAY_MILLIS);
		Assert.assertEquals(0, ghosts.size());
	}

	@Test
	public void basicCreatureDeath() throws Throwable
	{
		WorldCache worldCache = new WorldCache(ENV.creatures.PLAYER);
		GhostManager ghostManager = new GhostManager(worldCache);
		int entityId = 1;
		int otherEntityId = 2;
		
		worldCache.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		worldCache.addOtherEntity(new PartialEntity(otherEntityId
			, ENV.creatures.PLAYER
			, new EntityLocation(1.0f, 1.0f, 1.0f)
			, (byte)0
			, (byte)0
			, (byte)0
			, null
		));
		
		long currentMillis = 1000L;
		ghostManager.entityWasKilled(currentMillis, otherEntityId);
		List<PartialEntity> ghosts = ghostManager.pruneAndSnapshotEntities(currentMillis);
		Assert.assertEquals(1, ghosts.size());
		Assert.assertEquals(new EntityLocation(1.0f, 1.0f, 1.0f), ghosts.get(0).location());
		ghosts = ghostManager.pruneAndSnapshotEntities(currentMillis + GhostManager.DEATH_DELAY_MILLIS / 2L);
		Assert.assertEquals(1, ghosts.size());
		Assert.assertEquals(new EntityLocation(1.0f, 1.0f, 0.5f), ghosts.get(0).location());
		ghosts = ghostManager.pruneAndSnapshotEntities(currentMillis + GhostManager.DEATH_DELAY_MILLIS);
		Assert.assertEquals(0, ghosts.size());
	}

	@Test
	public void thisEntityDeath() throws Throwable
	{
		WorldCache worldCache = new WorldCache(ENV.creatures.PLAYER);
		GhostManager ghostManager = new GhostManager(worldCache);
		int entityId = 1;
		
		worldCache.setThisEntity(MutableEntity.createForTest(entityId).freeze());
		
		long currentMillis = 1000L;
		ghostManager.entityWasKilled(currentMillis, entityId);
		List<PartialEntity> ghosts = ghostManager.pruneAndSnapshotEntities(currentMillis);
		Assert.assertEquals(1, ghosts.size());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, 0.0f), ghosts.get(0).location());
		ghosts = ghostManager.pruneAndSnapshotEntities(currentMillis + GhostManager.DEATH_DELAY_MILLIS / 2L);
		Assert.assertEquals(1, ghosts.size());
		Assert.assertEquals(new EntityLocation(0.0f, 0.0f, -0.5f), ghosts.get(0).location());
		ghosts = ghostManager.pruneAndSnapshotEntities(currentMillis + GhostManager.DEATH_DELAY_MILLIS);
		Assert.assertEquals(0, ghosts.size());
	}
}
