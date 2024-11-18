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
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * A collection of helpers related to texture loading.
 */
public class TextureHelpers
{
	// Most textures are 32-square.
	public static int COMMON_TEXTURE_EDGE_PIXELS = 32;
	// The block model textures are 128-square.
	public static int BLOCK_MODEL_TEXTURE_EDGE_PIXELS = 128;

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

	public static TextureAtlas<ItemVariant> loadAtlasForItems(GL20 gl
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
		BufferedImage[] images = _loadAllImages(primaryNames, missingTextureName, COMMON_TEXTURE_EDGE_PIXELS);
		return _loadAtlas(gl, images, COMMON_TEXTURE_EDGE_PIXELS, ItemVariant.class);
	}

	public static BasicBlockAtlas loadAtlasForBlocks(GL20 gl
			, Block[] blockItems
			, String missingTextureName
	) throws IOException
	{
		// Just grab the names of the items, assuming they are all PNGs.
		// TODO:  Change this once we create the multi-sided textures.
		// (for now, we are just redundantly loading the item for every variant).
		int variants = BlockVariant.values().length;
		String[] primaryNames = new String[blockItems.length * variants];
		for (int i = 0; i < blockItems.length; ++i)
		{
			Block block = blockItems[i];
			String itemName = "item_" + block.item().id() + ".png";
			for (BlockVariant variant : BlockVariant.values())
			{
				String name = "block_" + block.item().id() + "_" + variant.name() + ".png";
				if (!Gdx.files.internal(name).exists())
				{
					name = itemName;
				}
				primaryNames[i * variants + variant.ordinal()] = name;
			}
		}
		int textureEdgePixels = COMMON_TEXTURE_EDGE_PIXELS;
		BufferedImage[] images = _loadAllImages(primaryNames, missingTextureName, textureEdgePixels);
		
		// Verify our size assumptions.
		for (BufferedImage image : images)
		{
			Assert.assertTrue(textureEdgePixels == image.getWidth());
			Assert.assertTrue(textureEdgePixels == image.getHeight());
		}
		int tileTexturesPerRow = _texturesPerRow(images.length);
		int variantsPerIndex = BlockVariant.class.getEnumConstants().length;
		boolean[] nonOpaqueVector = new boolean[images.length / variantsPerIndex];
		ByteBuffer textureBufferData = _loadTexturesIntoAtlas(nonOpaqueVector, images, textureEdgePixels, tileTexturesPerRow, variantsPerIndex);
		((java.nio.Buffer) textureBufferData).flip();
		int textureAtlasEdge = tileTexturesPerRow * textureEdgePixels;
		int tileTexture = _uploadTextureAtlas(gl, textureBufferData, textureAtlasEdge);
		
		TextureAtlas<BlockVariant> atlas = new TextureAtlas<BlockVariant>(tileTexture, tileTexturesPerRow, variantsPerIndex);
		return new BasicBlockAtlas(blockItems, atlas, nonOpaqueVector);
	}

	public static <T extends Enum<?>> TextureAtlas<T> loadAtlasForVariants(GL20 gl
			, String baseName
			, Class<T> clazz
			, String missingTextureName
	) throws IOException
	{
		// We will assume everything is a PNG and just load the variant name on the baseName.
		T[] variants = clazz.getEnumConstants();
		String[] primaryNames = new String[variants.length];
		for (int i = 0; i < variants.length; ++i)
		{
			primaryNames[i] = baseName + variants[i].name() + ".png";
		}
		BufferedImage[] images = _loadAllImages(primaryNames, missingTextureName, COMMON_TEXTURE_EDGE_PIXELS);
		return _loadAtlas(gl, images, COMMON_TEXTURE_EDGE_PIXELS, clazz);
	}

	public static TextureAtlas<ItemVariant> loadModelAtlasFromHandles(GL20 gl
			, FileHandle[] handles
	) throws IOException
	{
		BufferedImage[] images = _loadFileHandles(handles, BLOCK_MODEL_TEXTURE_EDGE_PIXELS);
		return _loadAtlas(gl, images, BLOCK_MODEL_TEXTURE_EDGE_PIXELS, ItemVariant.class);
	}

	public static <T extends Enum<?>> TextureAtlas<T> testBuildAtlas(int tileTextures, Class<T> variants) throws IOException
	{
		int tileTexturesPerRow = _texturesPerRow(tileTextures);
		int variantsPerIndex = variants.getEnumConstants().length;
		return new TextureAtlas<T>(1, tileTexturesPerRow, variantsPerIndex);
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
		
		// We need to flip the height when loading textures since BufferedImage defines 0,0 as top-left while OpenGL defines it as bottom-left.
		for (int y = height - 1; y >= 0; --y)
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

	private static BufferedImage[] _loadAllImages(String[] imageNames, String missingTextureName, int textureEdgePixels) throws IOException
	{
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
			Assert.assertTrue(loadedTexture.getWidth() == textureEdgePixels);
			Assert.assertTrue(loadedTexture.getHeight() == textureEdgePixels);
			loadedTextures[i] = loadedTexture;
		}
		return loadedTextures;
	}

	private static BufferedImage[] _loadFileHandles(FileHandle[] handles, int textureEdgePixels) throws IOException
	{
		BufferedImage loadedTextures[] = new BufferedImage[handles.length];
		for (int i = 0; i < handles.length; ++i)
		{
			FileHandle handle = handles[i];
			Assert.assertTrue(handle.exists());
			BufferedImage loadedTexture = ImageIO.read(handle.read());
			// We require all textures to be of fixed square size.
			Assert.assertTrue(loadedTexture.getWidth() == textureEdgePixels);
			Assert.assertTrue(loadedTexture.getHeight() == textureEdgePixels);
			loadedTextures[i] = loadedTexture;
		}
		return loadedTextures;
	}

	private static <T extends Enum<?>> TextureAtlas<T> _loadAtlas(GL20 gl
			, BufferedImage images[]
			, int textureEdgePixels
			, Class<T> variants
	) throws IOException
	{
		// Verify our size assumptions.
		for (BufferedImage image : images)
		{
			Assert.assertTrue(textureEdgePixels == image.getWidth());
			Assert.assertTrue(textureEdgePixels == image.getHeight());
		}
		int tileTexturesPerRow = _texturesPerRow(images.length);
		int variantsPerIndex = variants.getEnumConstants().length;
		boolean[] nonOpaqueVector = new boolean[images.length / variantsPerIndex];
		ByteBuffer textureBufferData = _loadTexturesIntoAtlas(nonOpaqueVector, images, textureEdgePixels, tileTexturesPerRow, variantsPerIndex);
		((java.nio.Buffer) textureBufferData).flip();
		int textureAtlasEdge = tileTexturesPerRow * textureEdgePixels;
		int tileTexture = _uploadTextureAtlas(gl, textureBufferData, textureAtlasEdge);
		
		return new TextureAtlas<T>(tileTexture, tileTexturesPerRow, variantsPerIndex);
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

	private static ByteBuffer _loadTexturesIntoAtlas(boolean[] out_nonOpaqueVector, BufferedImage loadedTextures[], int textureEdgePixels, int texturesPerRow, int variantsPerIndex) throws IOException
	{
		Assert.assertTrue(out_nonOpaqueVector.length == (loadedTextures.length / variantsPerIndex));
		int width = texturesPerRow * textureEdgePixels;
		int height = texturesPerRow * textureEdgePixels;
		
		// 4 bytes per pixel since we are storing pixels as RGBA.
		int bytesToAllocate = width * height * 4;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(bytesToAllocate);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		// We need to flip the height when loading textures since BufferedImage defines 0,0 as top-left while OpenGL defines it as bottom-left.
		for (int y = height - 1; y >= 0; --y)
		{
			int yIndex = height - 1 - y;
			for (int x = 0; x < width; ++x)
			{
				// Figure out which texture to pull from.
				int textureIndex = (yIndex / textureEdgePixels * texturesPerRow) + (x / textureEdgePixels);
				if (textureIndex < loadedTextures.length)
				{
					BufferedImage loadedTexture = loadedTextures[textureIndex];
					int localX = x % textureEdgePixels;
					int localY = y % textureEdgePixels;
					int pixel = loadedTexture.getRGB(localX, localY);
					// This data is pulled out as ARGB but we need to upload it as RGBA.
					byte a = (byte)((0xFF000000 & pixel) >> 24);
					byte r = (byte)((0x00FF0000 & pixel) >> 16);
					byte g = (byte)((0x0000FF00 & pixel) >> 8);
					byte b = (byte) (0x000000FF & pixel);
					textureBufferData.put(new byte[] { r, g, b, a });
					if (Byte.toUnsignedInt(a) < 255)
					{
						out_nonOpaqueVector[textureIndex / variantsPerIndex] = true;
					}
				}
				else
				{
					textureBufferData.put(new byte[4]);
				}
			}
		}
		return textureBufferData;
	}

	private static int _uploadTextureAtlas(GL20 gl, ByteBuffer textureBufferData, int textureAtlasEdge) throws IOException
	{
		int width = textureAtlasEdge;
		int height = textureAtlasEdge;
		
		// Upload the texture.
		int tileTexture = gl.glGenTexture();
		gl.glBindTexture(GL20.GL_TEXTURE_2D, tileTexture);
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
		return tileTexture;
	}
}
