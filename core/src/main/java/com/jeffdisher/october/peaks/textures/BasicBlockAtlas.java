package com.jeffdisher.october.peaks.textures;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.utils.Assert;


/**
 * A high-level wrapper over RawTextureAtlas for the use-case of basic (cube-only) block face textures.
 */
public class BasicBlockAtlas
{
	private final RawTextureAtlas _blockTextures;
	private final Faces[] _inactiveLookupByBlock;
	private final Faces[] _activeLookupByBlock;
	private final Faces[][] _blockDefinedByteLookupByBlock;
	private final boolean[] _nonOpaque_block;

	public BasicBlockAtlas(RawTextureAtlas blockTextures
		, Faces[] inactiveLookupByBlock
		, Faces[] activeLookupByBlock
		, Faces[][] blockDefinedByteLookupByBlock
		, boolean[] nonOpaque_block
	)
	{
		// Note that the BasicBlockCollector, which calls this constructor, has intimate knowledge of this class's implementation.
		// These arrays must be the same length but the active variant entries are usually null.
		Assert.assertTrue(inactiveLookupByBlock.length == activeLookupByBlock.length);
		Assert.assertTrue(inactiveLookupByBlock.length == blockDefinedByteLookupByBlock.length);
		
		_blockTextures = blockTextures;
		_inactiveLookupByBlock = inactiveLookupByBlock;
		_activeLookupByBlock = activeLookupByBlock;
		_blockDefinedByteLookupByBlock = blockDefinedByteLookupByBlock;
		_nonOpaque_block = nonOpaque_block;
	}

	/**
	 * Returns true if this block is defined in the basic atlas.  If not, this is likely a complex model.
	 * 
	 * @param value The item number.
	 * @return True if this block is in the basic atlas.
	 */
	public boolean isInBasicAtlas(short value)
	{
		boolean isIn;
		if (value < _inactiveLookupByBlock.length)
		{
			// There is always an inactive variant or byte0 variant, if the block is a basic block.
			isIn = (null != _inactiveLookupByBlock[value])
				|| (null != _blockDefinedByteLookupByBlock[value])
			;
		}
		else
		{
			isIn = false;
		}
		return isIn;
	}

	/**
	 * Returns true if the texture at index contains any pixels which are not fully-opaque.
	 * 
	 * @param value The item number.
	 * @return True if there are any pixels in this texture which are not fully-opaque.
	 */
	public boolean textureHasNonOpaquePixels(short value)
	{
		return _nonOpaque_block[value];
	}

	public float getCoordinateSize()
	{
		return _blockTextures.coordinateSize;
	}

	public float[] baseOfTopTexture(boolean isActive, short value, byte blockDefinedByte)
	{
		Faces faces = _getFacesForBlock(isActive, value, blockDefinedByte);
		int rawIndex = faces.top;
		return _blockTextures.baseOfTexture(rawIndex);
	}

	public float[] baseOfBottomTexture(boolean isActive, short value, byte blockDefinedByte)
	{
		Faces faces = _getFacesForBlock(isActive, value, blockDefinedByte);
		int rawIndex = faces.bottom;
		return _blockTextures.baseOfTexture(rawIndex);
	}

	public float[] baseOfSideTexture(boolean isActive, short value, byte blockDefinedByte)
	{
		Faces faces = _getFacesForBlock(isActive, value, blockDefinedByte);
		int rawIndex = faces.side;
		return _blockTextures.baseOfTexture(rawIndex);
	}

	public int getAtlasTexture()
	{
		return _blockTextures.texture;
	}

	public void shutdown(GL20 gl)
	{
		_blockTextures.shutdown(gl);
	}


	private Faces _getFacesForBlock(boolean isActive, short value, byte blockDefinedByte)
	{
		Faces faces;
		
		// Prioritize the active/inactive split.
		if (isActive)
		{
			faces = _activeLookupByBlock[value];
			if (null == faces)
			{
				// If there is no active variant, default to inactive.
				faces = _inactiveLookupByBlock[value];
			}
		}
		else
		{
			faces = _inactiveLookupByBlock[value];
		}
		
		if (null == faces)
		{
			// If we didn't find this, try the block-defined-byte variations.
			faces = _blockDefinedByteLookupByBlock[value][blockDefinedByte];
		}
		return faces;
	}


	/**
	 * The index of the faces of a given block in the texture atlas.
	 * These are always all valid (or the object would be null).
	 */
	public static record Faces(int top
		, int side
		, int bottom
	) {}
}
