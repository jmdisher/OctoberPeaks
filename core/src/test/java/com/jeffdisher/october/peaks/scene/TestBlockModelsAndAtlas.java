package com.jeffdisher.october.peaks.scene;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.peaks.textures.RawTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Prism;
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
		Map<Block, BlockModelsAndAtlas.Indices> blockToIndex = Map.of(block, new BlockModelsAndAtlas.Indices((short)0, (short)0, (short)0));
		ModelBuffer[] models = new ModelBuffer[] { ModelBuffer.buildFromWavefront(string) };
		BlockModelsAndAtlas modelsAndAtlas = _buildBlockModelsAndAtlas(1, blockToIndex, models);
		
		Assert.assertEquals(1, modelsAndAtlas.getBlockSet().size());
		Assert.assertEquals(models[0], modelsAndAtlas.getModelForBlock(block, false, false));
		Assert.assertEquals(1, modelsAndAtlas.getModelAtlasTexture());
		Assert.assertArrayEquals(new float[] { 0.0f, 0.0f }, modelsAndAtlas.baseOfModelTexture(block, false, false), 0.01f);
		Assert.assertEquals(1.0f, modelsAndAtlas.getCoordinateSize(), 0.01f);
	}

	@Test
	public void boundingBox() throws Throwable
	{
		String string = "v 0.888646 0.888646 0.855030\n"
				+ "v 0.888646 0.888646 0.119816\n"
				+ "v 0.888646 0.153431 0.855030\n"
				+ "v 0.888646 0.153431 0.119816\n"
				+ "v 0.153431 0.888646 0.855030\n"
				+ "v 0.153431 0.888646 0.119816\n"
				+ "v 0.153431 0.153431 0.855030\n"
				+ "v 0.153431 0.153431 0.119816\n"
				+ "vn -0.0000 -0.0000 1.0000\n"
				+ "vn -0.0000 -1.0000 -0.0000\n"
				+ "vn -1.0000 -0.0000 -0.0000\n"
				+ "vn -0.0000 -0.0000 -1.0000\n"
				+ "vn 1.0000 -0.0000 -0.0000\n"
				+ "vn -0.0000 1.0000 -0.0000\n"
				+ "vt 0.875000 0.500000\n"
				+ "vt 0.625000 0.750000\n"
				+ "vt 0.625000 0.500000\n"
				+ "vt 0.375000 1.000000\n"
				+ "vt 0.375000 0.750000\n"
				+ "vt 0.625000 0.000000\n"
				+ "vt 0.375000 0.250000\n"
				+ "vt 0.375000 0.000000\n"
				+ "vt 0.375000 0.500000\n"
				+ "vt 0.125000 0.750000\n"
				+ "vt 0.125000 0.500000\n"
				+ "vt 0.625000 0.250000\n"
				+ "vt 0.875000 0.750000\n"
				+ "vt 0.625000 1.000000\n"
				+ "f 5/1/1 3/2/1 1/3/1\n"
				+ "f 3/2/2 8/4/2 4/5/2\n"
				+ "f 7/6/3 6/7/3 8/8/3\n"
				+ "f 2/9/4 8/10/4 6/11/4\n"
				+ "f 1/3/5 4/5/5 2/9/5\n"
				+ "f 5/12/6 2/9/6 6/7/6\n"
				+ "f 5/1/1 7/13/1 3/2/1\n"
				+ "f 3/2/2 7/14/2 8/4/2\n"
				+ "f 7/6/3 5/12/3 6/7/3\n"
				+ "f 2/9/4 4/5/4 8/10/4\n"
				+ "f 1/3/5 3/2/5 4/5/5\n"
				+ "f 5/12/6 1/3/6 2/9/6\n"
		;
		Item item = new Item("test.id", "Test", (short)1);
		Block block = new Block(item);
		Map<Block, BlockModelsAndAtlas.Indices> blockToIndex = Map.of(block, new BlockModelsAndAtlas.Indices((short)0, (short)0, (short)0));
		ModelBuffer[] models = new ModelBuffer[] { ModelBuffer.buildFromWavefront(string) };
		BlockModelsAndAtlas modelsAndAtlas = _buildBlockModelsAndAtlas(1, blockToIndex, models);
		
		Map<Block, Prism> boundingBoxes = modelsAndAtlas.buildModelBoundingBoxes();
		Assert.assertEquals(1, boundingBoxes.size());
		Prism prism = boundingBoxes.get(block);
		Assert.assertEquals(0.15f, prism.west(), 0.01f);
		Assert.assertEquals(0.15f, prism.south(), 0.01f);
		Assert.assertEquals(0.12f, prism.bottom(), 0.01f);
		Assert.assertEquals(0.89f, prism.east(), 0.01f);
		Assert.assertEquals(0.89f, prism.north(), 0.01f);
		Assert.assertEquals(0.86f, prism.top(), 0.01f);
	}


	private static BlockModelsAndAtlas _buildBlockModelsAndAtlas(int textureCount, Map<Block, BlockModelsAndAtlas.Indices> blockToIndex, ModelBuffer[] models)
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(textureCount);
		return BlockModelsAndAtlas.testInstance(blockToIndex, models, raw);
	}
}
