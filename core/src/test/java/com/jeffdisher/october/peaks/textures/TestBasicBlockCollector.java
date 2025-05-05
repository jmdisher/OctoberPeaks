package com.jeffdisher.october.peaks.textures;

import java.awt.image.BufferedImage;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Block;


public class TestBasicBlockCollector
{
	private static Environment ENV;
	private static Block STONE;
	private static Block DIRT;
	private static Block LOG;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		DIRT = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basic() throws Throwable
	{
		BufferedImage missingTexture = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage stoneFallback = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage dirtFallback = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BasicBlockCollector collector = new BasicBlockCollector(missingTexture);
		collector.setBlockFallback(STONE, stoneFallback);
		collector.setBlockFallback(DIRT, dirtFallback);
		
		BufferedImage stoneTop = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		collector.addVariant(STONE, BasicBlockAtlas.Variant.TOP, stoneTop);
		
		Block[] blockOrder = new Block[] { STONE, DIRT, LOG };
		BufferedImage[] textureOrder = collector.getImagesInOrder(blockOrder);
		Assert.assertEquals(4, textureOrder.length);
		Assert.assertEquals(missingTexture, textureOrder[0]);
		Assert.assertEquals(stoneFallback, textureOrder[1]);
		Assert.assertEquals(stoneTop, textureOrder[2]);
		Assert.assertEquals(dirtFallback, textureOrder[3]);
		
		RawTextureAtlas raw = new RawTextureAtlas(4, 2);
		boolean[] nonOpaqueVectorByTexture = new boolean[textureOrder.length];
		BasicBlockAtlas atlas = collector.buildBlockAtlas(raw, blockOrder, nonOpaqueVectorByTexture);
		
		float[] uvStoneSide = atlas.baseOfSideTexture(STONE.item().number());
		float[] uvStoneTop = atlas.baseOfTopTexture(STONE.item().number());
		float[] uvLogSide = atlas.baseOfSideTexture(LOG.item().number());
		
		Assert.assertArrayEquals(new float[] { 0.5f, 0.0f }, uvStoneSide, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.0f, 0.5f }, uvStoneTop, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.0f, 0.0f }, uvLogSide, 0.01f);
	}
}
