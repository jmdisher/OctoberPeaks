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
	private static Block TILLED_SOIL;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.blocks.fromItem(ENV.items.getItemById("op.stone"));
		DIRT = ENV.blocks.fromItem(ENV.items.getItemById("op.dirt"));
		LOG = ENV.blocks.fromItem(ENV.items.getItemById("op.log"));
		TILLED_SOIL = ENV.blocks.fromItem(ENV.items.getItemById("op.tilled_soil"));
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
		collector.addFace(STONE, false, BasicBlockCollector.BlockFace.TOP, stoneTop);
		
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
		
		float[] uvStoneSide = atlas.baseOfSideTexture(false, STONE.item().number(), (byte)0);
		float[] uvStoneTop = atlas.baseOfTopTexture(false, STONE.item().number(), (byte)0);
		float[] uvLogSide = atlas.baseOfSideTexture(false, LOG.item().number(), (byte)0);
		
		Assert.assertArrayEquals(new float[] { 0.5f, 0.0f }, uvStoneSide, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.0f, 0.5f }, uvStoneTop, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.0f, 0.0f }, uvLogSide, 0.01f);
	}

	@Test
	public void blockDefinedByte() throws Throwable
	{
		BufferedImage missingTexture = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage soil = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BasicBlockCollector collector = new BasicBlockCollector(missingTexture);
		collector.setBlockFallback(TILLED_SOIL, soil);
		
		BufferedImage dryTop = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage drySide = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage dryBottom = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage wetTop = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage wetSide = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		BufferedImage wetBottom = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		collector.addFaceForByte(TILLED_SOIL, (byte)0, BasicBlockCollector.BlockFace.TOP, dryTop);
		collector.addFaceForByte(TILLED_SOIL, (byte)0, BasicBlockCollector.BlockFace.SIDE, drySide);
		collector.addFaceForByte(TILLED_SOIL, (byte)0, BasicBlockCollector.BlockFace.BOTTOM, dryBottom);
		collector.addFaceForByte(TILLED_SOIL, (byte)1, BasicBlockCollector.BlockFace.TOP, wetTop);
		collector.addFaceForByte(TILLED_SOIL, (byte)1, BasicBlockCollector.BlockFace.SIDE, wetSide);
		collector.addFaceForByte(TILLED_SOIL, (byte)1, BasicBlockCollector.BlockFace.BOTTOM, wetBottom);
		
		Block[] blockOrder = new Block[] { STONE, TILLED_SOIL };
		BufferedImage[] textureOrder = collector.getImagesInOrder(blockOrder);
		Assert.assertEquals(7, textureOrder.length);
		Assert.assertEquals(missingTexture, textureOrder[0]);
		Assert.assertEquals(dryTop, textureOrder[1]);
		Assert.assertEquals(drySide, textureOrder[2]);
		Assert.assertEquals(dryBottom, textureOrder[3]);
		Assert.assertEquals(wetTop, textureOrder[4]);
		Assert.assertEquals(wetSide, textureOrder[5]);
		Assert.assertEquals(wetBottom, textureOrder[6]);
		
		RawTextureAtlas raw = new RawTextureAtlas(16, 4);
		boolean[] nonOpaqueVectorByTexture = new boolean[textureOrder.length];
		BasicBlockAtlas atlas = collector.buildBlockAtlas(raw, blockOrder, nonOpaqueVectorByTexture);
		
		float[] uvStoneSide = atlas.baseOfSideTexture(false, STONE.item().number(), (byte)0);
		float[] uvStoneTop = atlas.baseOfTopTexture(false, STONE.item().number(), (byte)0);
		float[] uvDrySide = atlas.baseOfSideTexture(false, TILLED_SOIL.item().number(), (byte)0);
		float[] uvWetSide = atlas.baseOfSideTexture(false, TILLED_SOIL.item().number(), (byte)1);
		
		Assert.assertArrayEquals(new float[] { 0.0f, 0.0f }, uvStoneSide, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.0f, 0.0f }, uvStoneTop, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.5f, 0.0f }, uvDrySide, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.25f, 0.25f }, uvWetSide, 0.01f);
	}
}
