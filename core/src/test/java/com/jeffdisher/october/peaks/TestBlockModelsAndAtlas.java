package com.jeffdisher.october.peaks;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.peaks.wavefront.ModelBuffer;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;


public class TestBlockModelsAndAtlas
{
	@Test
	public void basicUsage() throws Throwable
	{
		String string = "v 0.828934 -1.421580 0.697664\n"
				+ "v 0.828934 -1.421580 1.994554\n"
				+ "v 0.828934 -0.124690 0.697664\n"
				+ "vn -1.0000 -0.0000 -0.0000\n"
				+ "vt 0.656723 0.765970\n"
				+ "vt 0.791192 0.631300\n"
				+ "vt 0.791192 0.765970\n"
				+ "f 2/1/1 3/2/1 1/3/1\n"
		;
		Item item = new Item("test.id", "Test", (short)1);
		Block block = new Block(item);
		Map<Block, Short> blockToIndex = Map.of(block, (short)0);
		ModelBuffer[] models = new ModelBuffer[] { ModelBuffer.buildFromWavefront(string) };
		TextureAtlas<ItemVariant> atlas = TextureHelpers.testBuildAtlas(1, ItemVariant.class);
		BlockModelsAndAtlas modelsAndAtlas = BlockModelsAndAtlas.testInstance(blockToIndex, models, atlas);
		
		Assert.assertEquals(1, modelsAndAtlas.getBlockSet().size());
		Assert.assertEquals(models[0], modelsAndAtlas.getModelForBlock(block));
		Assert.assertEquals(1, modelsAndAtlas.getModelAtlasTexture());
		Assert.assertArrayEquals(new float[] { 0.0f, 0.0f }, modelsAndAtlas.baseOfModelTexture(block), 0.01f);
		Assert.assertEquals(1.0f, modelsAndAtlas.getCoordinateSize(), 0.01f);
	}
}
