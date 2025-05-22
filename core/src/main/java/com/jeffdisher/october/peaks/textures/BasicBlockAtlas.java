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

	public float[] baseOfTopTexture(boolean isActive, short value)
	{
		int[] variants = _indexLookup_block_variant[value];
		Variant variant = isActive ? Variant.ACTIVE_TOP : Variant.INACTIVE_TOP;
		int rawIndex = variants[variant.ordinal()];
		return _blockTextures.baseOfTexture(rawIndex);
	}

	public float[] baseOfBottomTexture(boolean isActive, short value)
	{
		int[] variants = _indexLookup_block_variant[value];
		Variant variant = isActive ? Variant.ACTIVE_BOTTOM : Variant.INACTIVE_BOTTOM;
		int rawIndex = variants[variant.ordinal()];
		return _blockTextures.baseOfTexture(rawIndex);
	}

	public float[] baseOfSideTexture(boolean isActive, short value)
	{
		int[] variants = _indexLookup_block_variant[value];
		Variant variant = isActive ? Variant.ACTIVE_SIDE : Variant.INACTIVE_SIDE;
		int rawIndex = variants[variant.ordinal()];
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


	/**
	 * Blocks can have different variants for different faces, but also whether they are active or not.
	 */
	public static enum Variant
	{
		INACTIVE_TOP,
		INACTIVE_BOTTOM,
		INACTIVE_SIDE,
		ACTIVE_TOP,
		ACTIVE_BOTTOM,
		ACTIVE_SIDE,
		;
		public static int FIRST_ACTIVE_INDEX = ACTIVE_TOP.ordinal();
	}
}
