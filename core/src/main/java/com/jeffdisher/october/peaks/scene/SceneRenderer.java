package com.jeffdisher.october.peaks.scene;

import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.AnimationManager;
import com.jeffdisher.october.peaks.GhostManager;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.SkyBox;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Responsible for rendering the OpenGL scene of the world (does not include UI elements, windows, etc).
 */
public class SceneRenderer
{
	private final GL20 _gl;
	private final BlockRenderer _blockRenderer;
	private final EntityRenderer _entityRenderer;
	private final PassiveRenderer _passiveRenderer;
	private final ParticleEngine _particleEngine;
	private final SkyBox _skyBox;

	private Matrix _viewMatrix;
	private Matrix _projectionMatrix;
	private Vector _eye;
	private float _skyLightMultiplier;

	public SceneRenderer(Environment environment
		, GL20 gl
		, Binding<Float> screenBrightness
		, ParticleEngine particleEngine
		, LoadedResources resources
		, WorldCache worldCache
		, AnimationManager animationManager
		, GhostManager ghostManager
	)
	{
		_gl = gl;
		_blockRenderer = new BlockRenderer(environment, gl, screenBrightness, resources);
		_entityRenderer = new EntityRenderer(gl, screenBrightness, resources, worldCache, animationManager, ghostManager);
		_passiveRenderer = new PassiveRenderer(gl, screenBrightness, resources, worldCache);
		_particleEngine = particleEngine;
		_skyBox = new SkyBox(gl, resources);
		
		_viewMatrix = Matrix.identity();
		_eye = new Vector(0.0f, 0.0f, 0.0f);
	}

	public void rebuildProjection(int width, int height)
	{
		float xMajorAspect = (float)width / (float)height;
		_projectionMatrix = Matrix.perspective(90.0f, xMajorAspect, 0.1f, 200.0f);
	}

	public Map<Block, Prism> getModelBoundingBoxes()
	{
		return _blockRenderer.getModelBoundingBoxes();
	}

	public void updatePosition(Vector eye, Vector target, Vector upVector)
	{
		_eye = eye;
		_viewMatrix = Matrix.lookAt(eye, target, upVector);
		_skyBox.updateView(eye, target, upVector);
	}

	public void render(PartialEntity selectedEntity, AbsoluteLocation selectedBlock, Block selectedType)
	{
		long currentTimeMillis = System.currentTimeMillis();
		
		// We will begin with the sky box since we don't know if there are transparent blocks to render on top of it.
		_skyBox.render(_projectionMatrix);
		
		// Now we can render the world, opaque blocks first, transparent ones last.
		// Note that we NEVER want to blend against the background when rendering opaque vertices since we don't want to
		// see the background bleed through when the triangles become small in the distance.
		_gl.glDisable(GL20.GL_BLEND);
		_blockRenderer.renderOpaqueBlocks(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier);
		_entityRenderer.renderEntities(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier);
		_gl.glEnable(GL20.GL_BLEND);
		
		// Render the particles, then transparent blocks (since we will make the depth buffer read-only for particles).
		_gl.glDepthMask(false);
		_particleEngine.renderAllParticles(_viewMatrix, _projectionMatrix, currentTimeMillis);
		_gl.glDepthMask(true);
		_blockRenderer.renderTransparentBlocks(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier);
		
		// Highlight the selected entity or block - prioritize the block since the entity will restrict the block check distance.
		if (null != selectedBlock)
		{
			_blockRenderer.renderSelectedBlock(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier, selectedBlock, selectedType);
		}
		else if (null != selectedEntity)
		{
			_entityRenderer.renderSelectedEntity(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier, selectedEntity);
		}
		_blockRenderer.renderItemSlots(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier);
		_passiveRenderer.renderEntities(_viewMatrix, _projectionMatrix, _eye);
		
		// Do any other end-of-frame cleanup or processing.
		_blockRenderer.handleEndOfFrame();
		_particleEngine.freeDeadParticles(currentTimeMillis);
	}

	public void setCuboid(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
	{
		_blockRenderer.setCuboid(cuboid, heightMap, changedBlocks);
	}

	public void removeCuboid(CuboidAddress address)
	{
		_blockRenderer.removeCuboid(address);
	}

	public void setDayTime(float dayProgression, float skyLightMultiplier)
	{
		_skyBox.setDayProgression(dayProgression, skyLightMultiplier);
		
		// We want to artificially brighten the scene so we will manipulate this normalized light multiplier.
		// This may be made into a user option in the future.
		if (skyLightMultiplier > 0.6f)
		{
			skyLightMultiplier = 1.0f;
		}
		else if (skyLightMultiplier < 0.4f)
		{
			skyLightMultiplier = 0.0f;
		}
		else
		{
			skyLightMultiplier = 5.0f * (skyLightMultiplier - 0.4f);
		}
		float minimumBias = 0.3f;
		_skyLightMultiplier = minimumBias + skyLightMultiplier;
	}

	public void shutdown()
	{
		_blockRenderer.shutdown();
	}
}
