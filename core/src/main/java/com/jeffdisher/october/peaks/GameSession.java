package com.jeffdisher.october.peaks;

import java.io.File;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.peaks.scene.ParticleEngine;
import com.jeffdisher.october.peaks.scene.SceneRenderer;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Difficulty;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.MutableEntity;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.types.WorldConfig;


public class GameSession
{
	private final WorldCache _worldCache;
	private final ICallouts _callouts;
	public final SceneRenderer scene;
	public final EyeEffect eyeEffect;
	public final MovementControl movement;
	public final SelectionManager selectionManager;
	public final ClientWrapper client;
	public final AudioManager audioManager;
	public final AnimationManager animationManager;
	public final GhostManager ghostManager;
	public final Function<AbsoluteLocation, BlockProxy> blockLookup;

	public GameSession(Environment environment
			, GL20 gl
			, Binding<Float> screenBrightness
			, LoadedResources resources
			, String clientName
			, int startingViewDistance
			, InetSocketAddress serverSocketAddress
			, File localWorldDirectory
			, WorldConfig.WorldGeneratorName worldGeneratorName
			, WorldConfig.DefaultPlayerMode defaultPlayerMode
			, Difficulty difficulty
			, Integer basicWorldGeneratorSeed
			, ICallouts callouts
	) throws ConnectException
	{
		_worldCache = new WorldCache(environment.creatures.PLAYER);
		_callouts = callouts;
		// We just expose this lookup here for the UiStateManager to use.
		this.blockLookup = _worldCache.blockLookup;
		
		long currentTimeMillis = System.currentTimeMillis();
		ParticleEngine particleEngine = new ParticleEngine(gl, screenBrightness, resources, currentTimeMillis);
		this.animationManager = new AnimationManager(environment, particleEngine, _worldCache, currentTimeMillis);
		this.ghostManager = new GhostManager(_worldCache);
		this.scene = new SceneRenderer(environment
			, gl
			, screenBrightness
			, particleEngine
			, resources
			, _worldCache
			, this.animationManager
			, this.ghostManager
		);
		this.scene.rebuildProjection(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		
		this.eyeEffect = new EyeEffect(gl, resources);
		this.movement = new MovementControl();
		this.scene.updatePosition(this.movement.computeEye(), this.movement.computeTarget(), this.movement.computeUpVector());
		
		Map<Block, Prism> specialBlockBounds = this.scene.getModelBoundingBoxes();
		this.selectionManager = new SelectionManager(environment, specialBlockBounds, _worldCache);
		
		try
		{
			this.client = new ClientWrapper(environment
				, new _UpdateConsumer()
				, _worldCache
				, clientName
				, startingViewDistance
				, serverSocketAddress
				, localWorldDirectory
				, worldGeneratorName
				, defaultPlayerMode
				, difficulty
				, basicWorldGeneratorSeed
			);
		}
		catch (ConnectException e)
		{
			// Shut down the scene since we are going to fail in this start-up.
			this.scene.shutdown();
			throw e;
		}
		
		// Load the audio.
		this.audioManager = new AudioManager(environment, resources, _worldCache);
	}

	public void finishStartup()
	{
		this.client.finishStartup();
	}

	public boolean isConnectionReady()
	{
		return this.client.isConnectionReady();
	}

	public void shutdown()
	{
		// Disconnect from the server.
		this.client.disconnect();
		
		// Shut down the other components which have any non-heap resources.
		this.scene.shutdown();
		this.audioManager.shutdown();
	}


	/**
	 * These are implemented by the UI state manager since it needs to be able to display information about the current
	 * entity and other clients.  These aren't used for game logic (the state manager doesn't contain any), just
	 * displaying information and determining when to change top-level UI state.
	 */
	public static interface ICallouts
	{
		void didConnect(int currentViewDistance);
		void didDisconnect();
		
		void thisEntityUpdated(Entity projectedEntity);
		void otherClientJoined(int clientId, String name);
		void otherClientLeft(int clientId);
	}


	private class _UpdateConsumer implements ClientWrapper.IUpdateConsumer
	{
		@Override
		public void didConnect(int currentViewDistance)
		{
			_callouts.didConnect(currentViewDistance);
		}
		@Override
		public void didDisconnect()
		{
			_callouts.didDisconnect();
		}
		@Override
		public void loadNew(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
		{
			GameSession.this.scene.setCuboid(cuboid, heightMap, null);
		}
		@Override
		public void updateExisting(IReadOnlyCuboidData cuboid
			, ColumnHeightMap heightMap
			, Set<BlockAddress> changedBlocks
			, Set<Aspect<?, ?>> changedAspects
		)
		{
			GameSession.this.scene.setCuboid(cuboid, heightMap, changedBlocks);
			
			// Since we need to pass events on to the AnimationManager, synthesize events for any crafting/enchanting changes.
			if (changedAspects.contains(AspectRegistry.CRAFTING))
			{
				GameSession.this.animationManager.craftingBlockChanged(cuboid, changedBlocks);
			}
			if (changedAspects.contains(AspectRegistry.ENCHANTING))
			{
				GameSession.this.animationManager.enchantingBlockChanged(cuboid, changedBlocks);
			}
		}
		@Override
		public void unload(CuboidAddress address)
		{
			GameSession.this.scene.removeCuboid(address);
		}
		@Override
		public void thisEntityUpdated(Entity projectedEntity)
		{
			EntityLocation eyeLocation = SpatialHelpers.getEyeLocation(MutableEntity.existing(projectedEntity));
			GameSession.this.movement.setEye(Vector.fromEntityLocation(eyeLocation));
			Vector eye = GameSession.this.movement.computeEye();
			Vector target = GameSession.this.movement.computeTarget();
			Vector upVector = GameSession.this.movement.computeUpVector();
			GameSession.this.scene.updatePosition(eye, target, upVector);
			GameSession.this.selectionManager.updatePosition(eye, target);
			GameSession.this.selectionManager.setThisEntity(projectedEntity);
			GameSession.this.eyeEffect.setHealth(projectedEntity.health());
			_callouts.thisEntityUpdated(projectedEntity);
		}
		@Override
		public void thisEntityHurt()
		{
			GameSession.this.eyeEffect.thisEntityHurt();
			GameSession.this.audioManager.thisEntityHurt();
		}
		@Override
		public void otherClientJoined(int clientId, String name)
		{
			_callouts.otherClientJoined(clientId, name);
		}
		@Override
		public void otherClientLeft(int clientId)
		{
			_callouts.otherClientLeft(clientId);
		}
		@Override
		public void otherEntityWillUpdate(PartialEntity entity)
		{
			GameSession.this.animationManager.otherEntityWillUpdate(entity);
		}
		@Override
		public void otherEntityDidUnload(int id)
		{
			GameSession.this.animationManager.removeEntity(id);
		}
		@Override
		public void otherEntityHurt(int id, AbsoluteLocation location)
		{
			GameSession.this.animationManager.entityHurt(id);
			GameSession.this.audioManager.otherEntityHurt(location, id);
		}
		@Override
		public void otherEntityKilled(int id, AbsoluteLocation location)
		{
			// NOTE:  ServerStateManager will always notify us of entity death, but only sends the location if we have seen them (to notify us that a player died, for example).
			if (null != location)
			{
				GameSession.this.audioManager.otherEntityKilled(location, id);
				GameSession.this.ghostManager.entityWasKilled(System.currentTimeMillis(), id);
			}
		}
		@Override
		public void tickDidComplete(long gameTick, float skyLightMultiplier, float dayProgression)
		{
			GameSession.this.scene.setDayTime(dayProgression, skyLightMultiplier);
			GameSession.this.animationManager.setEndOfTickTime(System.currentTimeMillis());
			GameSession.this.audioManager.tickCompleted();
		}
		@Override
		public void blockPlaced(AbsoluteLocation location)
		{
			GameSession.this.audioManager.blockPlaced(location);
		}
		@Override
		public void blockBroken(AbsoluteLocation location)
		{
			GameSession.this.audioManager.blockBroken(location);
		}
		@Override
		public void craftInInventoryComplete(int entityId)
		{
			GameSession.this.animationManager.craftInInventoryComplete(entityId);
		}
		@Override
		public void craftInBlockComplete(AbsoluteLocation location)
		{
			GameSession.this.animationManager.craftInBlockComplete(location);
		}
		@Override
		public void enchantComplete(AbsoluteLocation location)
		{
			GameSession.this.animationManager.enchantComplete(location);
		}
		@Override
		public void passiveWasPickedUp(int passiveId, int pickingUpEntityId)
		{
			GameSession.this.ghostManager.passiveWasPickedUp(System.currentTimeMillis(), passiveId, pickingUpEntityId);
		}
	}
}
