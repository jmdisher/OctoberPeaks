package com.jeffdisher.october.peaks;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.utils.Assert;


/**
 * Maintains the high-level representation of a texture atlas.
 */
public class TextureAtlas<T extends Enum<?>>
{
	// We will assume a fixed texture size of 32-square.
	public static int TEXTURE_EDGE_PIXELS = 32;

	public static <T extends Enum<?>> TextureAtlas<T> loadAtlas(GL20 gl
			, BufferedImage images[]
			, Class<T> variants
	) throws IOException
	{
		// Verify our size assumptions.
		for (BufferedImage image : images)
		{
			Assert.assertTrue(TEXTURE_EDGE_PIXELS == image.getWidth());
			Assert.assertTrue(TEXTURE_EDGE_PIXELS == image.getHeight());
		}
		int tileTexturesPerRow = _texturesPerRow(images.length);
		int tileTexture = gl.glGenTexture();
		int variantsPerIndex = variants.getEnumConstants().length;
		boolean[] nonOpaqueVector = _createTextureAtlas(gl, tileTexture, images, tileTexturesPerRow, variantsPerIndex);
		
		return new TextureAtlas<T>(tileTexture, tileTexturesPerRow, nonOpaqueVector, variantsPerIndex);
	}


	private static int _texturesPerRow(int textureCount)
	{
		// We essentially just want the base2 logarithm of the array length rounded to the nearest power of 2.
		// (a faster and generalizable algorithm using leading zeros could be used but is less obvious than this, for now)
		int texturesPerRow = 1;
		if (textureCount > 1)
		{
			texturesPerRow = 2;
			if (textureCount > 4)
			{
				texturesPerRow = 4;
				if (textureCount > 16)
				{
					texturesPerRow = 8;
					if (textureCount > 64)
					{
						texturesPerRow = 16;
						Assert.assertTrue(textureCount <= 256);
					}
				}
			}
		}
		return texturesPerRow;
	}

	private static boolean[] _createTextureAtlas(GL20 gl, int texture, BufferedImage loadedTextures[], int texturesPerRow, int variantsPerIndex) throws IOException
	{
		int width = texturesPerRow * TEXTURE_EDGE_PIXELS;
		int height = texturesPerRow * TEXTURE_EDGE_PIXELS;
		
		// 4 bytes per pixel since we are storing pixels as RGBA.
		int bytesToAllocate = width * height * 4;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(bytesToAllocate);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		boolean[] nonOpaqueVector = new boolean[loadedTextures.length / variantsPerIndex];
		// We need to flip the height when loading textures since BufferedImage defines 0,0 as top-left while OpenGL defines it as bottom-left.
		for (int y = height - 1; y >= 0; --y)
		{
			int yIndex = height - 1 - y;
			for (int x = 0; x < width; ++x)
			{
				// Figure out which texture to pull from.
				int textureIndex = (yIndex / TEXTURE_EDGE_PIXELS * texturesPerRow) + (x / TEXTURE_EDGE_PIXELS);
				if (textureIndex < loadedTextures.length)
				{
					BufferedImage loadedTexture = loadedTextures[textureIndex];
					int localX = x % TEXTURE_EDGE_PIXELS;
					int localY = y % TEXTURE_EDGE_PIXELS;
					int pixel = loadedTexture.getRGB(localX, localY);
					// This data is pulled out as ARGB but we need to upload it as RGBA.
					byte a = (byte)((0xFF000000 & pixel) >> 24);
					byte r = (byte)((0x00FF0000 & pixel) >> 16);
					byte g = (byte)((0x0000FF00 & pixel) >> 8);
					byte b = (byte) (0x000000FF & pixel);
					textureBufferData.put(new byte[] { r, g, b, a });
					if (Byte.toUnsignedInt(a) < 255)
					{
						nonOpaqueVector[textureIndex / variantsPerIndex] = true;
					}
				}
				else
				{
					textureBufferData.put(new byte[4]);
				}
			}
		}
		((java.nio.Buffer) textureBufferData).flip();
		
		// Upload the texture.
		gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, width, height, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
		
		// We want to configure the mipmap usage so it will work for a texture atlas:
		// -do some blending between mipmaps at min zoom to keep the detail in things like stone brick
		// -only pick the nearest pixel as max zoom to avoid blending in other tiles
		// For texture atlas usage, it seems like GL_NEAREST* should be ok while GL_LINEAR* could allow textures to bleed between tiles.
		// In the future, we may want to manually compute the mipmap levels but that is probably not necessary in that
		// the texture atlas and all tiles within it are powers of 2.
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST_MIPMAP_LINEAR);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
		gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
		return nonOpaqueVector;
	}


	public final int texture;
	public final float coordinateSize;
	private final int _texturesPerRow;
	private final boolean[] _nonOpaqueVector;
	private final int _variantsPerIndex;

	private TextureAtlas(int tileTextures, int tileTexturesPerRow, boolean[] nonOpaqueVector, int variantsPerIndex)
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
}
