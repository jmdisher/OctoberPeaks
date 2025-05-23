package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.textures.RawTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.wavefront.ModelBuffer;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains information about what blocks have 3D models and unwrapped textures, internally maintaining the information
 * describing these so that they can be baked into a mesh, when required.
 */
public class BlockModelsAndAtlas
{
	public static BlockModelsAndAtlas loadForItems(GL20 gl
			, Block[] blocks
	) throws IOException
	{
		// We will look for "model_ITEM_ID.obj" and "model_ITEM_ID.png" - both or neither must be present.
		// We will then store the vertex data for these models (in Java heap), and store the textures into an atlas.
		Map<Block, Indices> blockToIndex = new HashMap<>();
		List<ModelBuffer> modelList = new ArrayList<>();
		List<FileHandle> textureHandleList = new ArrayList<>();
		for (Block block : blocks)
		{
			String itemId = block.item().id();
			_ModelPair inactive = _loadPair("model_" + itemId + ".obj", "model_" + itemId + ".png");
			if (null != inactive)
			{
				short inactiveIndex = (short)modelList.size();
				String text = inactive.model.readString();
				ModelBuffer model = ModelBuffer.buildFromWavefront(text);
				modelList.add(model);
				textureHandleList.add(inactive.texture);
				
				_ModelPair active = _loadPair("model_" + itemId + "_ACTIVE.obj", "model_" + itemId + "_ACTIVE.png");
				short activeIndex = inactiveIndex;
				if (null != active)
				{
					activeIndex = (short)modelList.size();
					text = active.model.readString();
					model = ModelBuffer.buildFromWavefront(text);
					modelList.add(model);
					textureHandleList.add(active.texture);
				}
				
				_ModelPair down = _loadPair("model_" + itemId + "_DOWN.obj", "model_" + itemId + "_DOWN.png");
				short downIndex = inactiveIndex;
				if (null != down)
				{
					downIndex = (short)modelList.size();
					text = down.model.readString();
					model = ModelBuffer.buildFromWavefront(text);
					modelList.add(model);
					textureHandleList.add(down.texture);
				}
				
				blockToIndex.put(block, new Indices(inactiveIndex, activeIndex, downIndex));
			}
		}
		
		// Assemble the atlas.
		FileHandle[] handles = textureHandleList.toArray((int size) -> new FileHandle[size]);
		RawTextureAtlas atlas = TextureHelpers.loadRawAtlasFromModelTextureHandles(gl, handles);
		
		ModelBuffer[] models = modelList.toArray((int size) -> new ModelBuffer[size]);
		return new BlockModelsAndAtlas(blockToIndex, models, atlas);
	}

	public static BlockModelsAndAtlas testInstance(Map<Block, Indices> blockToIndex, ModelBuffer[] models, RawTextureAtlas atlas)
	{
		return new BlockModelsAndAtlas(blockToIndex, models, atlas);
	}


	private final Map<Block, Indices> _blockToIndex;
	private final ModelBuffer[] _models;
	private final RawTextureAtlas _atlas;

	private BlockModelsAndAtlas(Map<Block, Indices> blockToIndex, ModelBuffer[] models, RawTextureAtlas atlas)
	{
		_blockToIndex = Collections.unmodifiableMap(blockToIndex);
		_models = models;
		_atlas = atlas;
	}

	public Set<Block> getBlockSet()
	{
		return _blockToIndex.keySet();
	}

	public ModelBuffer getModelForBlock(Block block, boolean isActive, boolean isDown)
	{
		Indices indices = _blockToIndex.get(block);
		short index = isActive
				? indices.active
				: isDown ? indices.down : indices.inactive
		;
		return _models[index];
	}

	public int getModelAtlasTexture()
	{
		return _atlas.texture;
	}

	public float[] baseOfModelTexture(Block block, boolean isActive, boolean isDown)
	{
		Indices indices = _blockToIndex.get(block);
		short index = isActive
				? indices.active
				: isDown ? indices.down : indices.inactive
		;
		return _atlas.baseOfTexture(index);
	}

	public float getCoordinateSize()
	{
		return _atlas.coordinateSize;
	}

	public Map<Block, Prism> buildModelBoundingBoxes()
	{
		Map<Block, Prism> boxes = new HashMap<>();
		for (Map.Entry<Block, Indices> elt : _blockToIndex.entrySet())
		{
			Block block = elt.getKey();
			// We will assume that the active and inactive are the same bounds.
			short index = elt.getValue().inactive;
			ModelBuffer buffer = _models[index];
			Prism bounds = _buildBounds(buffer);
			// For now, we don't want to bother with orientation of multi-blocks so we will just default to single-block checks.
			if (((bounds.east() - bounds.west()) > 1.0f)
					|| ((bounds.north() - bounds.south()) > 1.0f)
					|| ((bounds.top() - bounds.bottom()) > 1.0f)
			)
			{
				bounds = new Prism(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
			}
			boxes.put(block, bounds);
		}
		return Collections.unmodifiableMap(boxes);
	}

	public void shutdown(GL20 gl)
	{
		_atlas.shutdown(gl);
	}


	private static Prism _buildBounds(ModelBuffer buffer)
	{
		float west = Float.MAX_VALUE;
		float east = Float.MIN_VALUE;
		float south = Float.MAX_VALUE;
		float north = Float.MIN_VALUE;
		float bottom = Float.MAX_VALUE;
		float top = Float.MIN_VALUE;
		for (int i = 0; i < buffer.vertexCount; ++i)
		{
			int index = 3 * i;
			float x = buffer.positionValues[index + 0];
			float y = buffer.positionValues[index + 1];
			float z = buffer.positionValues[index + 2];
			
			west = Math.min(west, x);
			east = Math.max(east, x);
			south = Math.min(south, y);
			north = Math.max(north, y);
			bottom = Math.min(bottom, z);
			top = Math.max(top, z);
		}
		return new Prism(west, south, bottom, east, north, top);
	}

	private static _ModelPair _loadPair(String modelFile, String textureFile)
	{
		FileHandle modelHandle = Gdx.files.internal(modelFile);
		if (!modelHandle.exists())
		{
			modelHandle = null;
		}
		FileHandle textureHandle = Gdx.files.internal(textureFile);
		if (!textureHandle.exists())
		{
			textureHandle = null;
		}
		// Both or neither must be present.
		Assert.assertTrue((null != modelHandle) == (null != textureHandle));
		
		return (null != modelHandle)
			? new _ModelPair(modelHandle, textureHandle)
			: null
		;
	}


	// This is only public for testing reasons.
	public static record Indices(short inactive, short active, short down) {}

	private static record _ModelPair(FileHandle model, FileHandle texture) {}
}
