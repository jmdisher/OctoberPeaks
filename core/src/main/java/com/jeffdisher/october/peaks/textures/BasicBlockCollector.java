package com.jeffdisher.october.peaks.textures;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.utils.Assert;


/**
 * Used in the creation of BasicBlockAtlas to collect the Block sides or modes for use in a single atlas.
 */
public class BasicBlockCollector
{
	public static final int MISSING_TEXTURE_INDEX = 0;

	private final BufferedImage _missingTexture;
	private final Map<Block, Map<BasicBlockAtlas.Variant, BufferedImage>> _blockVariantTextures;
	private final Map<Block, BufferedImage> _blockFallbackTextures;
	private int _textureCount;

	public BasicBlockCollector(BufferedImage missingTexture)
	{
		_missingTexture = missingTexture;
		_blockVariantTextures = new HashMap<>();
		_blockFallbackTextures = new HashMap<>();
		_textureCount = 1;
	}

	public void setBlockFallback(Block block, BufferedImage image)
	{
		Assert.assertTrue(!_blockVariantTextures.containsKey(block));
		Assert.assertTrue(!_blockFallbackTextures.containsKey(block));
		
		_blockFallbackTextures.put(block, image);
		_textureCount += 1;
	}

	public void addVariant(Block block, BasicBlockAtlas.Variant variant, BufferedImage image)
	{
		if (!_blockVariantTextures.containsKey(block))
		{
			_blockVariantTextures.put(block, new HashMap<>());
		}
		BufferedImage old = _blockVariantTextures.get(block).put(variant, image);
		Assert.assertTrue(null == old);
		
		// If this filled all variants, remove the fallback.
		if (BasicBlockAtlas.Variant.values().length == _blockVariantTextures.get(block).size())
		{
			old = _blockFallbackTextures.remove(block);
			Assert.assertTrue(null != old);
		}
		else
		{
			_textureCount += 1;
		}
	}

	public BufferedImage[] getImagesInOrder(Block[] blockOrder)
	{
		BufferedImage[] images = new BufferedImage[_textureCount];
		images[MISSING_TEXTURE_INDEX] = _missingTexture;
		int index = MISSING_TEXTURE_INDEX + 1;
		for (Block block : blockOrder)
		{
			if (_blockFallbackTextures.containsKey(block))
			{
				images[index] = _blockFallbackTextures.get(block);
				index += 1;
			}
			
			Map<BasicBlockAtlas.Variant, BufferedImage> map = _blockVariantTextures.get(block);
			if (null != map)
			{
				for (BasicBlockAtlas.Variant variant : BasicBlockAtlas.Variant.values())
				{
					BufferedImage image = map.get(variant);
					if (null != image)
					{
						images[index] = image;
						index += 1;
					}
				}
			}
		}
		return images;
	}

	public BasicBlockAtlas buildBlockAtlas(RawTextureAtlas rawAtlas, Block[] blockOrder, boolean[] nonOpaqueVectorByTexture)
	{
		// We are responsible for building the internal data structure used by BasicBlockAtlas (this function knows its implementation).
		
		// First, fine the highest block number since we need it for indexing.
		int maxIndex = 0;
		for (Block block : blockOrder)
		{
			maxIndex = Math.max(maxIndex, block.item().number());
		}
		
		// NOTE:  This textureIndex MUST match the order of images returned in getImagesInOrder().
		int textureIndex = MISSING_TEXTURE_INDEX + 1;
		int[][] indexLookup_block_variant = new int[maxIndex + 1][];
		boolean[] nonOpaque_block = new boolean[maxIndex + 1];
		for (Block block : blockOrder)
		{
			int fallback = MISSING_TEXTURE_INDEX;
			if (_blockFallbackTextures.containsKey(block))
			{
				fallback = textureIndex;
				textureIndex += 1;
			}
			Map<BasicBlockAtlas.Variant, BufferedImage> map = _blockVariantTextures.get(block);
			int[] variantIndices = new int[BasicBlockAtlas.Variant.values().length];
			boolean isNotOpaque = false;
			for (BasicBlockAtlas.Variant variant : BasicBlockAtlas.Variant.values())
			{
				BufferedImage image = (null != map)
						? map.get(variant)
						: null
				;
				int variantIndex = variant.ordinal();
				if (null != image)
				{
					// We have a specific index for this one so use it.
					variantIndices[variantIndex] = textureIndex;
					isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
					textureIndex += 1;
				}
				else
				{
					// We know this block should be in the atlas but there isn't a texture for this variant so try the
					// fallback or, failing that, missing texture.
					variantIndices[variantIndex] = fallback;
					isNotOpaque |= nonOpaqueVectorByTexture[fallback];
				}
			}
			indexLookup_block_variant[block.item().number()] = variantIndices;
			nonOpaque_block[block.item().number()] = isNotOpaque;
		}
		return new BasicBlockAtlas(rawAtlas, indexLookup_block_variant, nonOpaque_block);
	}
}
