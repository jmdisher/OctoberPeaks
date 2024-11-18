package com.jeffdisher.october.peaks;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Item;


/**
 * A high-level wrapper over TextureAtlas<BlockVariant> since there are needs specific to that use-case.
 */
public class BasicBlockAtlas
{
	private final TextureAtlas<BlockVariant> _blockTextures;
	private final short[] _itemToBlockMap;
	private final boolean[] _nonOpaqueVector;

	public BasicBlockAtlas(Environment env, TextureAtlas<BlockVariant> blockTextures, boolean[] nonOpaqueVector)
	{
		// Extract the items which are blocks and create the index mapping function so we can pack the block atlas.
		short[] itemToBlockMap = new short[env.items.ITEMS_BY_TYPE.length];
		short nextIndex = 0;
		for (int i = 0; i < env.items.ITEMS_BY_TYPE.length; ++ i)
		{
			Item item = env.items.ITEMS_BY_TYPE[i];
			if (null != env.blocks.fromItem(item))
			{
				itemToBlockMap[i] = nextIndex;
				nextIndex += 1;
			}
			else
			{
				itemToBlockMap[i] = -1;
			}
		}
		
		_blockTextures = blockTextures;
		_itemToBlockMap = itemToBlockMap;
		_nonOpaqueVector = nonOpaqueVector;
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
