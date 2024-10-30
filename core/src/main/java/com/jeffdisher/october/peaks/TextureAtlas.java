package com.jeffdisher.october.peaks;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.utils.Assert;


/**
 * Maintains the high-level representation of a texture atlas.
 */
public class TextureAtlas
{
	public static TextureAtlas loadAtlas(GL20 gl
			, String[] fileNames
			, String missingTextureName
	) throws IOException
	{
		// We will assume a fixed texture size of 32-square.
		int eachTextureEdge = 32;
		
		int tileTexturesPerRow = _texturesPerRow(fileNames.length);
		int tileTexture = gl.glGenTexture();
		boolean[] nonOpaqueVector = _createTextureAtlas(gl, tileTexture, fileNames, missingTextureName, tileTexturesPerRow, eachTextureEdge);
		
		return new TextureAtlas(tileTexture, tileTexturesPerRow, nonOpaqueVector);
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

	private static boolean[] _createTextureAtlas(GL20 gl, int texture, String[] imageNames, String missingTextureName, int texturesPerRow, int eachTextureEdge) throws IOException
	{
		int width = texturesPerRow * eachTextureEdge;
		int height = texturesPerRow * eachTextureEdge;
		
		// 4 bytes per pixel since we are storing pixels as RGBA.
		int bytesToAllocate = width * height * 4;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(bytesToAllocate);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		// Load all the images and walk across them to fill the buffer.
		BufferedImage loadedTextures[] = new BufferedImage[imageNames.length];
		for (int i = 0; i < imageNames.length; ++i)
		{
			String name = imageNames[i];
			
			// If this is missing, load the missing texture, instead.
			FileHandle unknownTextureFile;
			if (null != name)
			{
				unknownTextureFile = Gdx.files.internal(name);
				// If this is missing, load the missing texture, instead.
				if (!unknownTextureFile.exists())
				{
					unknownTextureFile = Gdx.files.internal(missingTextureName);
				}
			}
			else
			{
				unknownTextureFile = Gdx.files.internal(missingTextureName);
			}
			BufferedImage loadedTexture = ImageIO.read(unknownTextureFile.read());
			// We require all textures to be of fixed square size.
			Assert.assertTrue(loadedTexture.getWidth() == eachTextureEdge);
			Assert.assertTrue(loadedTexture.getHeight() == eachTextureEdge);
			loadedTextures[i] = loadedTexture;
		}
		
		boolean[] nonOpaqueVector = new boolean[loadedTextures.length];
		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				// Figure out which texture to pull from.
				int textureIndex = (y / eachTextureEdge * texturesPerRow) + (x / eachTextureEdge);
				if (textureIndex < loadedTextures.length)
				{
					BufferedImage loadedTexture = loadedTextures[textureIndex];
					int localX = x % eachTextureEdge;
					int localY = y % eachTextureEdge;
					int pixel = loadedTexture.getRGB(localX, localY);
					// This data is pulled out as ARGB but we need to upload it as RGBA.
					byte a = (byte)((0xFF000000 & pixel) >> 24);
					byte r = (byte)((0x00FF0000 & pixel) >> 16);
					byte g = (byte)((0x0000FF00 & pixel) >> 8);
					byte b = (byte) (0x000000FF & pixel);
					textureBufferData.put(new byte[] { r, g, b, a });
					if (Byte.toUnsignedInt(a) < 255)
					{
						nonOpaqueVector[textureIndex] = true;
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

	private TextureAtlas(int tileTextures, int tileTexturesPerRow, boolean[] nonOpaqueVector)
	{
		this.texture = tileTextures;
		this.coordinateSize = 1.0f / (float)tileTexturesPerRow;
		_texturesPerRow = tileTexturesPerRow;
		_nonOpaqueVector = nonOpaqueVector;
	}

	/**
	 * Returns the UV base coordinates of the texture with the given index.
	 * 
	 * @param index The texture index.
	 * @return {u, v} of texture base coordinates.
	 */
	public float[] baseOfTexture(short index)
	{
		int row = index / _texturesPerRow;
		int column = index % _texturesPerRow;
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
