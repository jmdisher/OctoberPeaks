package com.jeffdisher.october.peaks.textures;

import com.badlogic.gdx.graphics.GL20;


/**
 * Exposes an interface for finding texture coordinates within an atlas.
 * This is intended to be wrapped by a higher-level interface which provides a more context-specific high-level
 * interface.
 */
public class RawTextureAtlas
{
	public final int texture;
	public final float coordinateSize;
	private final int _texturesPerRow;

	public RawTextureAtlas(int tileTextureObject, int tileTexturesPerRow)
	{
		this.texture = tileTextureObject;
		this.coordinateSize = 1.0f / (float)tileTexturesPerRow;
		_texturesPerRow = tileTexturesPerRow;
	}

	/**
	 * Returns the UV base coordinates of the texture with the given index.
	 * 
	 * @param index The texture index.
	 * @return {u, v} of texture base coordinates.
	 */
	public float[] baseOfTexture(int index)
	{
		int row = index / _texturesPerRow;
		int column = index % _texturesPerRow;
		float u = this.coordinateSize * (float)column;
		float v = this.coordinateSize * (float)row;
		return new float[] {u, v};
	}

	public void shutdown(GL20 gl)
	{
		gl.glDeleteTexture(this.texture);
	}
}
