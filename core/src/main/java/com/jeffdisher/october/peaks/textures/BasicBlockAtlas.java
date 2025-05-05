package com.jeffdisher.october.peaks.textures;

import com.badlogic.gdx.graphics.GL20;


/**
 * A high-level wrapper over RawTextureAtlas for the use-case of basic (cube-only) block face textures.
 */
public class BasicBlockAtlas
{
	private final RawTextureAtlas _blockTextures;
	private final int[][] _indexLookup_block_variant;
	private final boolean[] _nonOpaque_block;

	public BasicBlockAtlas(RawTextureAtlas blockTextures, int[][] indexLookup_block_variant, boolean[] nonOpaque_block)
	{
		// Note that the BasicBlockCollector, which calls this constructor, has intimate knowledge of this class's implementation.
		_blockTextures = blockTextures;
		_indexLookup_block_variant = indexLookup_block_variant;
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
		if (value < _indexLookup_block_variant.length)
		{
			int[] variants = _indexLookup_block_variant[value];
			isIn = (null != variants);
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

	public float[] baseOfTopTexture(short value)
	{
		int[] variants = _indexLookup_block_variant[value];
		int rawIndex = variants[Variant.TOP.ordinal()];
		return _blockTextures.baseOfTexture(rawIndex);
	}

	public float[] baseOfBottomTexture(short value)
	{
		int[] variants = _indexLookup_block_variant[value];
		int rawIndex = variants[Variant.BOTTOM.ordinal()];
		return _blockTextures.baseOfTexture(rawIndex);
	}

	public float[] baseOfSideTexture(short value)
	{
		int[] variants = _indexLookup_block_variant[value];
		int rawIndex = variants[Variant.SIDE.ordinal()];
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


	public static enum Variant
	{
		TOP,
		BOTTOM,
		SIDE,
	}
}
