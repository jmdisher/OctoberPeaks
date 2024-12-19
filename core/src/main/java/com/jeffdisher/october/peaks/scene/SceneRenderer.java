package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
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
	private final BlockRenderer _blockRenderer;
	private final EntityRenderer _entityRenderer;

	private Matrix _viewMatrix;
	private final Matrix _projectionMatrix;
	private Vector _eye;
	private float _skyLightMultiplier;

	public SceneRenderer(Environment environment, GL20 gl, TextureAtlas<ItemVariant> itemAtlas) throws IOException
	{
		_blockRenderer = new BlockRenderer(environment, gl, itemAtlas);
		_entityRenderer = new EntityRenderer(gl);
		
		_viewMatrix = Matrix.identity();
		_projectionMatrix = Matrix.perspective(90.0f, 1.0f, 0.1f, 100.0f);
		_eye = new Vector(0.0f, 0.0f, 0.0f);
	}

	public Map<Block, Prism> getModelBoundingBoxes()
	{
		return _blockRenderer.getModelBoundingBoxes();
	}

	public void updatePosition(Vector eye, Vector target, Vector upVector)
	{
		_eye = eye;
		_viewMatrix = Matrix.lookAt(eye, target, upVector);
	}

	public void render(PartialEntity selectedEntity, AbsoluteLocation selectedBlock, Block selectedType)
	{
		_blockRenderer.renderOpaqueBlocks(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier);
		_entityRenderer.renderEntities(_viewMatrix, _projectionMatrix, _eye, _skyLightMultiplier);
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
		
		_blockRenderer.handleEndOfFrame();
	}

	public void setCuboid(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
	{
		_blockRenderer.setCuboid(cuboid, heightMap, changedBlocks);
	}

	public void removeCuboid(CuboidAddress address)
	{
		_blockRenderer.removeCuboid(address);
	}

	public void setEntity(PartialEntity entity)
	{
		_entityRenderer.setEntity(entity);
	}

	public void removeEntity(int id)
	{
		_entityRenderer.removeEntity(id);
	}

	public void entityHurt(int id)
	{
		_entityRenderer.entityHurt(id);
	}

	public void setSkyLightMultiplier(float skyLightMultiplier)
	{
		_skyLightMultiplier = skyLightMultiplier;
	}

	public void shutdown()
	{
		_blockRenderer.shutdown();
		_entityRenderer.shutdown();
	}
}
