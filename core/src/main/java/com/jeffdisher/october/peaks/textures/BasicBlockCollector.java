package com.jeffdisher.october.peaks.textures;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
	private final Map<Block, Map<BlockFace, BufferedImage>> _blockInactiveTextures;
	private final Map<Block, Map<BlockFace, BufferedImage>> _blockActiveTextures;
	private final Map<Block, List<Map<BlockFace, BufferedImage>>> _blockDefinedByteTextures;
	private final Map<Block, BufferedImage> _blockFallbackTextures;
	private int _textureCount;

	public BasicBlockCollector(BufferedImage missingTexture)
	{
		_missingTexture = missingTexture;
		_blockInactiveTextures = new HashMap<>();
		_blockActiveTextures = new HashMap<>();
		_blockDefinedByteTextures = new HashMap<>();
		_blockFallbackTextures = new HashMap<>();
		_textureCount = 1;
	}

	public void setBlockFallback(Block block, BufferedImage image)
	{
		Assert.assertTrue(!_blockInactiveTextures.containsKey(block));
		Assert.assertTrue(!_blockActiveTextures.containsKey(block));
		Assert.assertTrue(!_blockDefinedByteTextures.containsKey(block));
		Assert.assertTrue(!_blockFallbackTextures.containsKey(block));
		
		_blockFallbackTextures.put(block, image);
		_textureCount += 1;
	}

	public void addFace(Block block, boolean isActive, BlockFace face, BufferedImage image)
	{
		// We can't use the block-defined and active/inactive at the same time.
		Assert.assertTrue(!_blockDefinedByteTextures.containsKey(block));
		
		Map<Block, Map<BlockFace, BufferedImage>> container = isActive
			? _blockActiveTextures
			: _blockInactiveTextures
		;
		if (!container.containsKey(block))
		{
			container.put(block, new HashMap<>());
		}
		BufferedImage old = container.get(block).put(face, image);
		Assert.assertTrue(null == old);
		
		// If this filled all faces for at least the inactive version, then we can drop the fallback since we will always have this one.
		if (!isActive && _blockFallbackTextures.containsKey(block) && (BlockFace.values().length == _blockInactiveTextures.get(block).size()))
		{
			old = _blockFallbackTextures.remove(block);
			Assert.assertTrue(null != old);
		}
		else
		{
			_textureCount += 1;
		}
	}

	public void addFaceForByte(Block block, byte blockDefined, BlockFace face, BufferedImage image)
	{
		// We can't use the block-defined and active/inactive at the same time.
		Assert.assertTrue(!_blockInactiveTextures.containsKey(block));
		Assert.assertTrue(!_blockActiveTextures.containsKey(block));
		
		if (!_blockDefinedByteTextures.containsKey(block))
		{
			_blockDefinedByteTextures.put(block, new ArrayList<>());
		}
		List<Map<BlockFace, BufferedImage>> byteList = _blockDefinedByteTextures.get(block);
		// We expect these to be entered in order.
		if (byteList.size() == blockDefined)
		{
			byteList.add(new HashMap<>());
		}
		
		BufferedImage old = byteList.get(blockDefined).put(face, image);
		Assert.assertTrue(null == old);
		
		// If this filled all faces for at least the byte0 version, then we can drop the fallback since we will always have this one.
		if ((0 == blockDefined) && _blockFallbackTextures.containsKey(block) && (BlockFace.values().length == byteList.get(blockDefined).size()))
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
			// If we need a fallback, we write that first, for the block.
			if (_blockFallbackTextures.containsKey(block))
			{
				images[index] = _blockFallbackTextures.get(block);
				index += 1;
			}
			
			// Write any inactive faces, in enum order.
			Map<BlockFace, BufferedImage> inactive = _blockInactiveTextures.get(block);
			if (null != inactive)
			{
				for (BlockFace face : BlockFace.values())
				{
					BufferedImage image = inactive.get(face);
					if (null != image)
					{
						images[index] = image;
						index += 1;
					}
				}
			}
			
			// Write any active faces, in enum order.
			Map<BlockFace, BufferedImage> active = _blockActiveTextures.get(block);
			if (null != active)
			{
				for (BlockFace face : BlockFace.values())
				{
					BufferedImage image = active.get(face);
					if (null != image)
					{
						images[index] = image;
						index += 1;
					}
				}
			}
			
			// Write any of the block-defined-byte faces.
			List<Map<BlockFace, BufferedImage>> byteList = _blockDefinedByteTextures.get(block);
			if (null != byteList)
			{
				for (Map<BlockFace, BufferedImage> map : byteList)
				{
					for (BlockFace face : BlockFace.values())
					{
						BufferedImage image = map.get(face);
						if (null != image)
						{
							images[index] = image;
							index += 1;
						}
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
		BasicBlockAtlas.Faces[] inactiveLookupByBlock = new BasicBlockAtlas.Faces[maxIndex + 1];
		BasicBlockAtlas.Faces[] activeLookupByBlock = new BasicBlockAtlas.Faces[maxIndex + 1];
		BasicBlockAtlas.Faces[][] blockDefinedByteLookupByBlock = new BasicBlockAtlas.Faces[maxIndex + 1][];
		boolean[] nonOpaque_block = new boolean[maxIndex + 1];
		for (Block block : blockOrder)
		{
			boolean isNotOpaque = false;
			
			// If we need a fallback, we write that first, for the block.
			int fallback = MISSING_TEXTURE_INDEX;
			if (_blockFallbackTextures.containsKey(block))
			{
				isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
				fallback = textureIndex;
				textureIndex += 1;
			}
			
			// We need to know if there are block-defined-bytes since that changes fallback.
			List<Map<BlockFace, BufferedImage>> byteList = _blockDefinedByteTextures.get(block);
			
			// Write any inactive faces, in enum order.
			Map<BlockFace, BufferedImage> inactive = _blockInactiveTextures.get(block);
			BasicBlockAtlas.Faces fallbackFaces = new BasicBlockAtlas.Faces(fallback, fallback, fallback);
			if (null != inactive)
			{
				int top = fallbackFaces.top();
				if (inactive.containsKey(BlockFace.TOP))
				{
					isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
					top = textureIndex;
					textureIndex += 1;
				}
				int side = fallbackFaces.side();
				if (inactive.containsKey(BlockFace.SIDE))
				{
					isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
					side = textureIndex;
					textureIndex += 1;
				}
				int bottom = fallbackFaces.bottom();
				if (inactive.containsKey(BlockFace.BOTTOM))
				{
					isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
					bottom = textureIndex;
					textureIndex += 1;
				}
				fallbackFaces = new BasicBlockAtlas.Faces(top, side, bottom);
			}
			if (null == byteList)
			{
				// Even if nothing was loaded for inactive, we store something.
				inactiveLookupByBlock[block.item().number()] = fallbackFaces;
			}
			
			// Write any active faces, in enum order.
			Map<BlockFace, BufferedImage> active = _blockActiveTextures.get(block);
			if (null != active)
			{
				int top = fallbackFaces.top();
				if (active.containsKey(BlockFace.TOP))
				{
					isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
					top = textureIndex;
					textureIndex += 1;
				}
				int side = fallbackFaces.side();
				if (active.containsKey(BlockFace.SIDE))
				{
					isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
					side = textureIndex;
					textureIndex += 1;
				}
				int bottom = fallbackFaces.bottom();
				if (active.containsKey(BlockFace.BOTTOM))
				{
					isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
					bottom = textureIndex;
					textureIndex += 1;
				}
				activeLookupByBlock[block.item().number()] = new BasicBlockAtlas.Faces(top, side, bottom);
			}
			
			// Write any of the block-defined-byte faces.
			if (null != byteList)
			{
				BasicBlockAtlas.Faces[] blockDefined = new BasicBlockAtlas.Faces[byteList.size()];
				int index = 0;
				for (Map<BlockFace, BufferedImage> map : byteList)
				{
					int top = fallbackFaces.top();
					if (map.containsKey(BlockFace.TOP))
					{
						isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
						top = textureIndex;
						textureIndex += 1;
					}
					int side = fallbackFaces.side();
					if (map.containsKey(BlockFace.SIDE))
					{
						isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
						side = textureIndex;
						textureIndex += 1;
					}
					int bottom = fallbackFaces.bottom();
					if (map.containsKey(BlockFace.BOTTOM))
					{
						isNotOpaque |= nonOpaqueVectorByTexture[textureIndex];
						bottom = textureIndex;
						textureIndex += 1;
					}
					blockDefined[index] = new BasicBlockAtlas.Faces(top, side, bottom);
					
					// We only set fallback on byte0 (it is equivalent to inactive).
					if (0 == index)
					{
						fallbackFaces = blockDefined[index];
					}
					index += 1;
				}
				blockDefinedByteLookupByBlock[block.item().number()] = blockDefined;
			}
			
			nonOpaque_block[block.item().number()] = isNotOpaque;
		}
		return new BasicBlockAtlas(rawAtlas
			, inactiveLookupByBlock
			, activeLookupByBlock
			, blockDefinedByteLookupByBlock
			, nonOpaque_block
		);
	}


	public static enum BlockFace
	{
		TOP,
		SIDE,
		BOTTOM,
		;
	}
}
