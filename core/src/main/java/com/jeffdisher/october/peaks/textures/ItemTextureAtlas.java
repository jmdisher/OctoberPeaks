package com.jeffdisher.october.peaks.textures;

import com.badlogic.gdx.graphics.GL20;


/**
 * High-level wrapper over the textures for in-inventory items.
 * This is a very simple wrapper as every item type has a texture which is indexed directly (missing textures are
 * duplicated in the atlas).
 */
public class ItemTextureAtlas
{
	public final int texture;
	public final float coordinateSize;
	private final RawTextureAtlas _raw;

	public ItemTextureAtlas(RawTextureAtlas raw)
	{
		this.texture = raw.texture;
		this.coordinateSize = raw.coordinateSize;
		_raw = raw;
	}

	/**
	 * Returns the UV base coordinates of the texture with the given index.
	 * 
	 * @param index The texture index.
	 * @return {u, v} of texture base coordinates.
	 */
	public float[] baseOfTexture(short index)
	{
		return _raw.baseOfTexture(index);
	}

	public void shutdown(GL20 gl)
	{
		_raw.shutdown(gl);
	}
}
