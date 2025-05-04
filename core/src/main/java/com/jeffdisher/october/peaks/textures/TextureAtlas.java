package com.jeffdisher.october.peaks.textures;

import com.badlogic.gdx.graphics.GL20;


/**
 * Maintains the high-level representation of a texture atlas.
 */
public class TextureAtlas<T extends Enum<?>>
{
	public final int texture;
	public final float coordinateSize;
	private final RawTextureAtlas _raw;
	private final int _variantsPerIndex;

	public TextureAtlas(RawTextureAtlas raw, int variantsPerIndex)
	{
		this.texture = raw.texture;
		this.coordinateSize = raw.coordinateSize;
		_raw = raw;
		_variantsPerIndex = variantsPerIndex;
	}

	/**
	 * Returns the UV base coordinates of the texture with the given index.
	 * 
	 * @param index The texture index.
	 * @param variant The texture variant to look up.
	 * @return {u, v} of texture base coordinates.
	 */
	public float[] baseOfTexture(short index, T variant)
	{
		int localIndex = (index * _variantsPerIndex) + variant.ordinal();
		return _raw.baseOfTexture(localIndex);
	}

	public void shutdown(GL20 gl)
	{
		_raw.shutdown(gl);
	}
}
