package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
		Map<Block, short[]> blockToBytes = new HashMap<>();
		List<ModelBuffer> modelList = new ArrayList<>();
		List<FileHandle> textureHandleList = new ArrayList<>();
		for (Block block : blocks)
		{
			// Figure out which model variant type this is (active/inactive/down or block-defined-byte - can be at most one).
			String itemId = block.item().id();
			_ModelPair inactive = _loadPair("model_" + itemId);
			_ModelPair blockDefinedByte = _loadPair("model_" + itemId + "_byte0");
			Assert.assertTrue((null == inactive) || (null == blockDefinedByte));
			
			if (null != inactive)
			{
				short inactiveIndex = (short)modelList.size();
				String text = inactive.model.readString();
				ModelBuffer model = ModelBuffer.buildFromWavefront(text);
				modelList.add(model);
				textureHandleList.add(inactive.texture);
				
				_ModelPair active = _loadPair("model_" + itemId + "_ACTIVE");
				short activeIndex = inactiveIndex;
				if (null != active)
				{
					activeIndex = (short)modelList.size();
					text = active.model.readString();
					model = ModelBuffer.buildFromWavefront(text);
					modelList.add(model);
					textureHandleList.add(active.texture);
				}
				
				_ModelPair down = _loadPair("model_" + itemId + "_DOWN");
				short downIndex = -1;
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
			else if (null != blockDefinedByte)
			{
				// The block-defined byte case is kind of hacked in here (block interpretation may need a redesign) but we will load all defined variants in order.
				short baseIndex =(short)modelList.size();
				int count = 0;
				while (null != blockDefinedByte)
				{
					String text = blockDefinedByte.model.readString();
					ModelBuffer model = ModelBuffer.buildFromWavefront(text);
					modelList.add(model);
					textureHandleList.add(blockDefinedByte.texture);
					
					count += 1;
					blockDefinedByte = _loadPair("model_" + itemId + "_byte" + count);
				}
				
				short[] indices = new short[count];
				for (int i = 0; i < count; ++i)
				{
					indices[i] = (short)(baseIndex + i);
				}
				blockToBytes.put(block, indices);
			}
		}
		
		// Assemble the atlas.
		FileHandle[] handles = textureHandleList.toArray((int size) -> new FileHandle[size]);
		RawTextureAtlas atlas = TextureHelpers.loadRawAtlasFromModelTextureHandles(gl, handles);
		
		ModelBuffer[] models = modelList.toArray((int size) -> new ModelBuffer[size]);
		return new BlockModelsAndAtlas(blockToIndex, blockToBytes, models, atlas);
	}

	public static BlockModelsAndAtlas testInstance(Map<Block, Indices> blockToIndex, ModelBuffer[] models, RawTextureAtlas atlas)
	{
		return new BlockModelsAndAtlas(blockToIndex, Map.of(), models, atlas);
	}


	private final Set<Block> _blockSet;
	private final Map<Block, Indices> _blockToIndex;
	private final Map<Block, short[]> _blockToBytes;
	private final ModelBuffer[] _models;
	private final RawTextureAtlas _atlas;

	private BlockModelsAndAtlas(Map<Block, Indices> blockToIndex
		, Map<Block, short[]> blockToBytes
		, ModelBuffer[] models
		, RawTextureAtlas atlas
	)
	{
		Set<Block> blocks = new HashSet<>();
		blocks.addAll(blockToIndex.keySet());
		blocks.addAll(blockToBytes.keySet());
		
		_blockSet = Collections.unmodifiableSet(blocks);
		_blockToIndex = Collections.unmodifiableMap(blockToIndex);
		_blockToBytes = Collections.unmodifiableMap(blockToBytes);
		_models = models;
		_atlas = atlas;
	}

	public Set<Block> getBlockSet()
	{
		return _blockSet;
	}

	public ModelBuffer getModelForBlock(Block block, boolean isActive, boolean isDown, byte blockDefinedByte)
	{
		short index = getCommonIndexForBlock(block, isActive, isDown, blockDefinedByte);
		return _models[index];
	}

	public int getModelAtlasTexture()
	{
		return _atlas.texture;
	}

	public boolean hasDownModel(Block block)
	{
		Indices indices = _blockToIndex.get(block);
		boolean hasSpecialDown = (null != indices) && (-1 != indices.down);
		return hasSpecialDown;
	}

	public float[] baseOfModelTexture(Block block, boolean isActive, boolean isDown, byte blockDefinedByte)
	{
		short index = getCommonIndexForBlock(block, isActive, isDown, blockDefinedByte);
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
			Prism bounds = _buildBoundsForModelIndex(index);
			boxes.put(block, bounds);
		}
		for (Map.Entry<Block, short[]> elt : _blockToBytes.entrySet())
		{
			Block block = elt.getKey();
			short[] indices = elt.getValue();
			
			// We will take the "superset" of all the variants for this block.
			Prism maxPrism = new Prism(1.0f
				, 1.0f
				, 1.0f
				, 0.0f
				, 0.0f
				, 0.0f
			);
			for (short index : indices)
			{
				Prism bounds = _buildBoundsForModelIndex(index);
				maxPrism = new Prism(Math.min(maxPrism.west(), bounds.west())
					, Math.min(maxPrism.south(), bounds.south())
					, Math.min(maxPrism.bottom(), bounds.bottom())
					, Math.max(maxPrism.east(), bounds.east())
					, Math.max(maxPrism.north(), bounds.north())
					, Math.max(maxPrism.top(), bounds.top())
				);
			}
			boxes.put(block, maxPrism);
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
		float east = -Float.MAX_VALUE;
		float south = Float.MAX_VALUE;
		float north = -Float.MAX_VALUE;
		float bottom = Float.MAX_VALUE;
		float top = -Float.MAX_VALUE;
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

	private static _ModelPair _loadPair(String baseName)
	{
		String modelFile = baseName + ".obj";
		FileHandle modelHandle = Gdx.files.internal(modelFile);
		if (!modelHandle.exists())
		{
			modelHandle = null;
		}
		String textureFile = baseName + ".png";
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

	private Prism _buildBoundsForModelIndex(short index)
	{
		ModelBuffer buffer = _models[index];
		Prism bounds = _buildBounds(buffer);
		
		// We still want multi-blocks to be selected as individual blocks, so clamp the range of each axis to a block.
		if ((bounds.east() - bounds.west()) > 1.0f)
		{
			bounds = new Prism(0.0f, bounds.south(), bounds.bottom(), 1.0f, bounds.north(), bounds.top());
		}
		if ((bounds.north() - bounds.south()) > 1.0f)
		{
			bounds = new Prism(bounds.west(), 0.0f, bounds.bottom(), bounds.east(), 1.0f, bounds.top());
		}
		if ((bounds.top() - bounds.bottom()) > 1.0f)
		{
			bounds = new Prism(bounds.west(), bounds.south(), 0.0f, bounds.east(), bounds.north(), 1.0f);
		}
		return bounds;
	}

	private short getCommonIndexForBlock(Block block, boolean isActive, boolean isDown, byte blockDefinedByte)
	{
		Indices indices = _blockToIndex.get(block);
		short index;
		if (null != indices)
		{
			boolean hasSpecialDown = (-1 != indices.down);
			index = isActive
				? indices.active
				: (isDown && hasSpecialDown) ? indices.down : indices.inactive
			;
		}
		else
		{
			short[] variants = _blockToBytes.get(block);
			index = variants[blockDefinedByte];
		}
		return index;
	}


	// This is only public for testing reasons.
	public static record Indices(short inactive, short active, short down) {}

	private static record _ModelPair(FileHandle model, FileHandle texture) {}
}
