package com.jeffdisher.october.peaks;

import java.util.Arrays;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.types.Block;


/**
 * A high-level wrapper over TextureAtlas<BlockVariant> since there are needs specific to that use-case.
 */
public class BasicBlockAtlas
{
	public static final short NOT_MAPPED = -1;

	private final TextureAtlas<BlockVariant> _blockTextures;
	private final short[] _itemToBlockMap;
	private final boolean[] _nonOpaqueVector;

	public BasicBlockAtlas(Block[] blocksIncluded, TextureAtlas<BlockVariant> blockTextures, boolean[] nonOpaqueVector)
	{
		// We only care about the blocks we are given so build the mapping from items to these block indices.
		int maxItemNumber = 0;
		for (Block block : blocksIncluded)
		{
			maxItemNumber = Math.max(maxItemNumber, block.item().number());
		}
		short[] itemToBlockMap = new short[maxItemNumber + 1];
		Arrays.fill(itemToBlockMap, NOT_MAPPED);
		for (int i = 0; i < blocksIncluded.length; ++i)
		{
			short itemNumber = blocksIncluded[i].item().number();
			itemToBlockMap[itemNumber] = (short)i;
		}
		
		_blockTextures = blockTextures;
		_itemToBlockMap = itemToBlockMap;
		_nonOpaqueVector = nonOpaqueVector;
	}

	/**
	 * Returns true if this block is defined in the basic atlas.  If not, this is likely a complex model.
	 * 
	 * @param value The item number.
	 * @return True if this block is in the basic atlas.
	 */
	public boolean isInBasicAtlas(short value)
	{
		short index = _itemToBlockMap[value];
		return (NOT_MAPPED != index);
	}

	/**
	 * Returns true if the texture at index contains any pixels which are not fully-opaque.
	 * 
	 * @param value The item number.
	 * @return True if there are any pixels in this texture which are not fully-opaque.
	 */
	public boolean textureHasNonOpaquePixels(short value)
	{
		short index = _itemToBlockMap[value];
		return _nonOpaqueVector[index];
	}

	public float getCoordinateSize()
	{
		return _blockTextures.coordinateSize;
	}

	public float[] baseOfTopTexture(short value)
	{
		short index = _itemToBlockMap[value];
		return _blockTextures.baseOfTexture(index, BlockVariant.TOP);
	}

	public float[] baseOfBottomTexture(short value)
	{
		short index = _itemToBlockMap[value];
		return _blockTextures.baseOfTexture(index, BlockVariant.BOTTOM);
	}

	public float[] baseOfSideTexture(short value)
	{
		short index = _itemToBlockMap[value];
		return _blockTextures.baseOfTexture(index, BlockVariant.SIDE);
	}

	public int getAtlasTexture()
	{
		return _blockTextures.texture;
	}

	public void shutdown(GL20 gl)
	{
		_blockTextures.shutdown(gl);
	}
}
