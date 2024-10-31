package com.jeffdisher.october.peaks;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * A collection of helpers related to texture loading.
 */
public class TextureHelpers
{
	public static int loadInternalRGBA(GL20 gl, String imageName) throws IOException
	{
		FileHandle textureFile = Gdx.files.internal(imageName);
		return _loadHandleRGBA(gl, textureFile);
	}

	public static int loadHandleRGBA(GL20 gl, FileHandle textureFile) throws IOException
	{
		Assert.assertTrue(textureFile.exists());
		return _loadHandleRGBA(gl, textureFile);
	}

	public static int loadSinglePixelImageRGBA(GL20 gl, byte[] rawPixel)
	{
		Assert.assertTrue(4 == rawPixel.length);
		int bytesToAllocate = 4;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(bytesToAllocate);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		// We store the raw pixel data as RGBA.
		textureBufferData.put(rawPixel);
		((java.nio.Buffer) textureBufferData).flip();
		
		return _uploadNewTexture(gl, 1, 1, textureBufferData);
	}

	public static TextureAtlas loadAtlasForItems(GL20 gl
			, Item[] tileItems
			, String missingTextureName
	) throws IOException
	{
		// Just grab the names of the items, assuming they are all PNGs.
		// We prefix "item_" to them in order to distinguish them from other block textures.
		String[] primaryNames = Arrays.stream(tileItems).map(
				(Item item) -> ("item_" + item.id() + ".png")
		).toArray(
				(int size) -> new String[size]
		);
		return TextureAtlas.loadAtlas(gl, primaryNames, missingTextureName);
	}


	private static int _loadHandleRGBA(GL20 gl, FileHandle textureFile) throws IOException
	{
		BufferedImage loadedTexture = ImageIO.read(textureFile.read());
		
		int height = loadedTexture.getHeight();
		int width = loadedTexture.getWidth();
		
		// 4 bytes per pixel since we are storing pixels as RGBA.
		int bytesToAllocate = width * height * 4;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(bytesToAllocate);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				int pixel = loadedTexture.getRGB(x, y);
				// This data is pulled out as ARGB but we need to upload it as RGBA.
				byte a = (byte)((0xFF000000 & pixel) >> 24);
				byte r = (byte)((0x00FF0000 & pixel) >> 16);
				byte g = (byte)((0x0000FF00 & pixel) >> 8);
				byte b = (byte) (0x000000FF & pixel);
				textureBufferData.put(new byte[] { r, g, b, a });
			}
		}
		((java.nio.Buffer) textureBufferData).flip();
		
		return _uploadNewTexture(gl, height, width, textureBufferData);
	}

	private static int _uploadNewTexture(GL20 gl, int height, int width, ByteBuffer textureBufferData)
	{
		// Create the texture and upload.
		int texture = gl.glGenTexture();
		gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, width, height, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
		
		// In this case, we just use the default mipmap behaviour.
		gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
		return texture;
	}
}
