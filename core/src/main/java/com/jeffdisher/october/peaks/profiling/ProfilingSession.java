package com.jeffdisher.october.peaks.profiling;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.EyeEffect;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.animation.AnimationManager;
import com.jeffdisher.october.peaks.animation.GhostManager;
import com.jeffdisher.october.peaks.animation.ParticleEngine;
import com.jeffdisher.october.peaks.scene.SceneRenderer;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Very similar to GameSession, but only includes what is required for profiling runs.
 */
public class ProfilingSession
{
	private final WorldCache _worldCache;
	public final SceneRenderer scene;
	public final EyeEffect eyeEffect;
	public final AnimationManager animationManager;
	public final GhostManager ghostManager;

	public ProfilingSession(Environment environment
		, GL20 gl
		, Binding<Float> screenBrightness
		, LoadedResources resources
	)
	{
		_worldCache = new WorldCache(environment.creatures.PLAYER);
		
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
		
		// We don't bother with movement control so just set it to something fixed.
		Vector eyeLocation = new Vector(0.0f, 0.0f, 1.8f);
		Vector targetLocation = new Vector(1.0f, 1.0f, 1.8f);
		Vector upVector = new Vector(0.0f, 0.0f, 1.0f);
		this.scene.updatePosition(eyeLocation, targetLocation, upVector);
	}

	public void shutdown()
	{
		// We only shut down a profiling session when we want to exit to se just stop threads.
		this.scene.shutdown();
	}

	public void addOtherEntity(PartialEntity entity)
	{
		_worldCache.addOtherEntity(entity);
	}

	public void addCuboid(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap)
	{
		this.scene.setCuboid(cuboid, heightMap, null);
	}
}
