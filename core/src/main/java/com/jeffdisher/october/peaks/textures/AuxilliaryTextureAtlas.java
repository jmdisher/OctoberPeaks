package com.jeffdisher.october.peaks.textures;

import com.badlogic.gdx.graphics.GL20;


/**
 * Maintains the high-level representation of a texture atlas.
 */
public class AuxilliaryTextureAtlas
{
	public final int texture;
	public final float coordinateSize;
	private final RawTextureAtlas _raw;

	public AuxilliaryTextureAtlas(RawTextureAtlas raw)
	{
		this.texture = raw.texture;
		this.coordinateSize = raw.coordinateSize;
		_raw = raw;
	}

	/**
	 * Returns the UV base coordinates of the texture for the given variant.
	 * 
	 * @param variant The texture variant to look up.
	 * @return {u, v} of texture base coordinates.
	 */
	public float[] baseOfTexture(Variant variant)
	{
		return _raw.baseOfTexture(variant.ordinal());
	}

	public void shutdown(GL20 gl)
	{
		_raw.shutdown(gl);
	}


	public static enum Variant
	{
		NONE,
		BREAK_LOW,
		BREAK_MEDIUM,
		BREAK_HIGH,
	}
}
