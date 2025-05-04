package com.jeffdisher.october.peaks.textures;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Item;


public class TestTextureAtlas
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE_ITEM = ENV.items.getItemById("op.stone");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void basic() throws Throwable
	{
		int textureCount = STONE_ITEM.number() + 1;
		ItemTextureAtlas itemAtlas = _buildItemAtlas(textureCount);
		Assert.assertEquals(0.5f, itemAtlas.coordinateSize, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.5f, 0.0f }, itemAtlas.baseOfTexture(STONE_ITEM.number()), 0.01f);
	}


	private static ItemTextureAtlas _buildItemAtlas(int textureCount)
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(textureCount);
		return new ItemTextureAtlas(raw);
	}
}
