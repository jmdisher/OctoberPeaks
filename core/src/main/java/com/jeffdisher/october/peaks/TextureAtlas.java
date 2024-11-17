package com.jeffdisher.october.peaks;

import com.badlogic.gdx.graphics.GL20;


/**
 * Maintains the high-level representation of a texture atlas.
 */
public class TextureAtlas<T extends Enum<?>>
{
	public final int texture;
	public final float coordinateSize;
	private final int _texturesPerRow;
	private final boolean[] _nonOpaqueVector;
	private final int _variantsPerIndex;

	public TextureAtlas(int tileTextures, int tileTexturesPerRow, boolean[] nonOpaqueVector, int variantsPerIndex)
	{
		this.texture = tileTextures;
		this.coordinateSize = 1.0f / (float)tileTexturesPerRow;
		_texturesPerRow = tileTexturesPerRow;
		_nonOpaqueVector = nonOpaqueVector;
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
		int row = localIndex / _texturesPerRow;
		int column = localIndex % _texturesPerRow;
		float u = this.coordinateSize * (float)column;
		float v = this.coordinateSize * (float)row;
		return new float[] {u, v};
	}

	/**
	 * Returns true if the texture at index contains any pixels which are not fully-opaque.
	 * 
	 * @param index The texture index.
	 * @return True if there are any pixels in this texture which are not fully-opaque.
	 */
	public boolean textureHasNonOpaquePixels(short index)
	{
		return _nonOpaqueVector[index];
	}

	public void shutdown(GL20 gl)
	{
		gl.glDeleteTexture(this.texture);
	}
}
