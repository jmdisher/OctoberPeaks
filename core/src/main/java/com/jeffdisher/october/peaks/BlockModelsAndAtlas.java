package com.jeffdisher.october.peaks;

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
		Map<Block, Short> blockToIndex = new HashMap<>();
		List<FileHandle> modelHandles = new ArrayList<>();
		List<FileHandle> textureHandles = new ArrayList<>();
		for (Block block : blocks)
		{
			String itemId = block.item().id();
			String modelFile = "model_" + itemId + ".obj";
			String textureFile = "model_" + itemId + ".png";
			
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
			
			if (null != modelHandle)
			{
				blockToIndex.put(block, (short)modelHandles.size());
				modelHandles.add(modelHandle);
				textureHandles.add(textureHandle);
			}
		}
		
		// Load the models.
		ModelBuffer[] models = new ModelBuffer[modelHandles.size()];
		for (int i = 0; i < modelHandles.size(); ++i)
		{
			FileHandle handle = modelHandles.get(i);
			String text = handle.readString();
			models[i] = ModelBuffer.buildFromWavefront(text);
		}
		
		// Assemble the atlas.
		FileHandle[] handles = textureHandles.toArray((int size) -> new FileHandle[size]);
		TextureAtlas<ItemVariant> atlas = TextureHelpers.loadModelAtlasFromHandles(gl, handles);
		
		return new BlockModelsAndAtlas(blockToIndex, models, atlas);
	}

	public static BlockModelsAndAtlas testInstance(Map<Block, Short> blockToIndex, ModelBuffer[] models, TextureAtlas<ItemVariant> atlas)
	{
		return new BlockModelsAndAtlas(blockToIndex, models, atlas);
	}


	private final Map<Block, Short> _blockToIndex;
	private final ModelBuffer[] _models;
	private final TextureAtlas<ItemVariant> _atlas;

	private BlockModelsAndAtlas(Map<Block, Short> blockToIndex, ModelBuffer[] models, TextureAtlas<ItemVariant> atlas)
	{
		_blockToIndex = Collections.unmodifiableMap(blockToIndex);
		_models = models;
		_atlas = atlas;
	}

	public Set<Block> getBlockSet()
	{
		return _blockToIndex.keySet();
	}

	public ModelBuffer getModelForBlock(Block block)
	{
		short index = _blockToIndex.get(block);
		return _models[index];
	}

	public int getModelAtlasTexture()
	{
		return _atlas.texture;
	}

	public float[] baseOfModelTexture(Block block)
	{
		short index = _blockToIndex.get(block);
		return _atlas.baseOfTexture(index, ItemVariant.NONE);
	}

	public float getCoordinateSize()
	{
		return _atlas.coordinateSize;
	}

	public void shutdown(GL20 gl)
	{
		_atlas.shutdown(gl);
	}
}
