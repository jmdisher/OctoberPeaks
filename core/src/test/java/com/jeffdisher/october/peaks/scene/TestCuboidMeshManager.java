package com.jeffdisher.october.peaks.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.AuxilliaryTextureAtlas;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.textures.RawTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.wavefront.ModelBuffer;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestCuboidMeshManager
{
	private static Environment ENV;
	private static Attribute[] ATTRIBUTES;
	private static int FLOATS_PER_VERTEX;
	private static Block STONE_BLOCK;
	private static short STONE_VALUE;
	private static short WATER_VALUE;
	private static Block[] ALL_BLOCKS;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		ATTRIBUTES = new Attribute[] { new Attribute("aPosition", 3)
				, new Attribute("aNormal", 3)
				, new Attribute("aTexture0", 2)
				, new Attribute("aTexture1", 2)
				, new Attribute("aBlockLightMultiplier", 1)
				, new Attribute("aSkyLightMultiplier", 1)
		};
		for (Attribute attr : ATTRIBUTES)
		{
			FLOATS_PER_VERTEX += attr.floats();
		}
		Item stoneItem = ENV.items.getItemById("op.stone");
		STONE_BLOCK = ENV.blocks.fromItem(stoneItem);
		STONE_VALUE = stoneItem.number();
		WATER_VALUE = ENV.items.getItemById("op.water_source").number();
		ALL_BLOCKS = Arrays.stream(ENV.items.ITEMS_BY_TYPE)
				.map((Item item) -> ENV.blocks.fromItem(item))
				.filter((Block block) -> null != block)
				.toArray((int size) -> new Block[size])
		;
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void empty() throws Throwable
	{
		CuboidMeshManager manager = new CuboidMeshManager(ENV, null, null, null, null, null, null);
		Assert.assertEquals(0, manager.viewCuboids().size());
		manager.shutdown();
	}

	@Test
	public void addOne() throws Throwable
	{
		_Gpu testingGpu = new _Gpu();
		int textureCount = STONE_VALUE + 1;
		ItemTextureAtlas itemAtlas = _buildItemAtlas(textureCount);
		AuxilliaryTextureAtlas auxBlockTextures = _buildAuxAtlas();
		BlockModelsAndAtlas models = _buildBlockModelsAndAtlas(textureCount, Map.of(), new ModelBuffer[0]);
		BasicBlockAtlas blockAtlas = _buildBlockAtlas(textureCount, ALL_BLOCKS, new boolean[ALL_BLOCKS.length]);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, models, blockAtlas, auxBlockTextures);
		
		CuboidAddress address = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(address, ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)6, (byte)7), STONE_VALUE);
		ColumnHeightMap heightMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid), cuboid.getCuboidAddress()).freeze();
		
		manager.setCuboid(cuboid, heightMap, null);
		Assert.assertEquals(1, manager.viewCuboids().size());
		
		// We shouldn't see the finished result, yet.
		CuboidMeshManager.CuboidMeshes data = manager.viewCuboids().iterator().next();
		Assert.assertNull(data.opaqueArray());
		Assert.assertNull(data.itemsOnGroundArray());
		Assert.assertNull(data.transparentArray());
		
		_waitForOpaqueArray(manager, address);
		Assert.assertEquals(36, manager.viewCuboids().iterator().next().opaqueArray().totalVertices);
		
		manager.shutdown();
	}

	@Test
	public void adjacentCuboids() throws Throwable
	{
		_Gpu testingGpu = new _Gpu();
		int textureCount = WATER_VALUE + 1;
		ItemTextureAtlas itemAtlas = _buildItemAtlas(textureCount);
		AuxilliaryTextureAtlas auxBlockTextures = _buildAuxAtlas();
		BlockModelsAndAtlas models = _buildBlockModelsAndAtlas(textureCount, Map.of(), new ModelBuffer[0]);
		BasicBlockAtlas blockAtlas = _buildBlockAtlas(textureCount, ALL_BLOCKS, buildNonOpaqueVector());
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, models, blockAtlas, auxBlockTextures);
		
		CuboidAddress lowAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData lowCuboid = CuboidGenerator.createFilledCuboid(lowAddress, ENV.special.AIR);
		BlockAddress lowBlock = new BlockAddress((byte)31, (byte)31, (byte)31);
		lowCuboid.setData15(AspectRegistry.BLOCK, lowBlock, WATER_VALUE);
		ColumnHeightMap lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress).freeze();
		
		CuboidAddress highAddress = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData highCuboid = CuboidGenerator.createFilledCuboid(highAddress, ENV.special.AIR);
		BlockAddress highBlock = new BlockAddress((byte)31, (byte)31, (byte)0);
		highCuboid.setData15(AspectRegistry.BLOCK, highBlock, WATER_VALUE);
		ColumnHeightMap highMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(highCuboid), highAddress).freeze();
		
		manager.setCuboid(lowCuboid, lowMap, null);
		Assert.assertNull(_readCuboidWater(manager, lowAddress));
		VertexArray lowWaterArray = _waitForWaterChange(manager, lowAddress, null);
		// Note that we draw both sides of the water surface.
		Assert.assertEquals(72, lowWaterArray.totalVertices);
		
		manager.setCuboid(highCuboid, highMap, null);
		Assert.assertNull(_readCuboidWater(manager, highAddress));
		VertexArray highWaterArray = _waitForWaterChange(manager, highAddress, null);
		// Note that we draw both sides of the water surface.
		Assert.assertEquals(60, highWaterArray.totalVertices);
		
		// Wait for the re-bake of the low cuboid.
		lowWaterArray = _waitForWaterChange(manager, lowAddress, lowWaterArray);
		// Note that we draw both sides of the water surface.
		Assert.assertEquals(60, lowWaterArray.totalVertices);
		
		// Now change one of these blocks and observe the updates in both blocks.
		lowCuboid = CuboidData.mutableClone(lowCuboid);
		lowCuboid.setData15(AspectRegistry.BLOCK, lowBlock, STONE_VALUE);
		lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress).freeze();
		manager.setCuboid(lowCuboid, lowMap, Set.of(lowBlock));
		
		// Note that the water array should disappear now and be replaced with opaque.
		Assert.assertNull(_waitForWaterChange(manager, lowAddress, lowWaterArray));
		VertexArray lowOpaqueArray = _waitForOpaqueChange(manager, lowAddress, null);
		Assert.assertEquals(36, lowOpaqueArray.totalVertices);
		highWaterArray = _waitForWaterChange(manager, highAddress, highWaterArray);
		// The water face toward the opaque block is skipped.
		// Note that we draw both sides of the water surface.
		Assert.assertEquals(60, highWaterArray.totalVertices);
		
		manager.shutdown();
	}

	@Test
	public void skyLightUpdates() throws Throwable
	{
		// We want to test 2 cuboids, one stacked on top of the other, to see how we handle the height map updates.
		_Gpu testingGpu = new _Gpu();
		int textureCount = STONE_VALUE + 1;
		ItemTextureAtlas itemAtlas = _buildItemAtlas(textureCount);
		AuxilliaryTextureAtlas auxBlockTextures = _buildAuxAtlas();
		BlockModelsAndAtlas models = _buildBlockModelsAndAtlas(textureCount, Map.of(), new ModelBuffer[0]);
		BasicBlockAtlas blockAtlas = _buildBlockAtlas(textureCount, ALL_BLOCKS, new boolean[ALL_BLOCKS.length]);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, models, blockAtlas, auxBlockTextures);
		
		// We want to put a single solid block at the top of the low cuboid so we can verify the vertex values.
		CuboidAddress lowAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData lowCuboid = CuboidGenerator.createFilledCuboid(lowAddress, ENV.special.AIR);
		lowCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 30, 30), STONE_VALUE);
		ColumnHeightMap lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress).freeze();
		CuboidAddress highAddress = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData highCuboid = CuboidGenerator.createFilledCuboid(highAddress, STONE_BLOCK);
		ColumnHeightMap highMap = ColumnHeightMap.build()
				.consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress)
				.consume(HeightMapHelpers.buildHeightMap(highCuboid), highAddress)
				.freeze();
		
		// Request the bottom one first and verify it.
		manager.setCuboid(lowCuboid, lowMap, null);
		Assert.assertEquals(1, manager.viewCuboids().size());
		VertexArray opaque = _waitForOpaqueArray(manager, lowAddress);
		Assert.assertEquals(36, opaque.totalVertices);
		Assert.assertEquals(1, testingGpu.uploadedBuffers.size());
		Assert.assertEquals(36, testingGpu.uploadedBuffers.get(0).vertexCount);
		float[] initial = new float[FLOATS_PER_VERTEX * testingGpu.uploadedBuffers.get(0).vertexCount];
		testingGpu.uploadedBuffers.get(0).testGetFloats(initial);
		testingGpu.uploadedBuffers.clear();
		
		// Now, request the top one and verify that both are changed and that the low one has different sky multiplier values.
		manager.setCuboid(highCuboid, highMap, null);
		Assert.assertEquals(2, manager.viewCuboids().size());
		testingGpu.processUntilBufferCount(manager, 2);
		VertexArray other = _waitForOpaqueArray(manager, highAddress);
		opaque = _waitForOpaqueArray(manager, lowAddress);
		Assert.assertEquals(36, opaque.totalVertices);
		Assert.assertEquals(2, testingGpu.uploadedBuffers.size());
		Assert.assertEquals(other.totalVertices, testingGpu.uploadedBuffers.get(0).vertexCount);
		Assert.assertEquals(36, testingGpu.uploadedBuffers.get(1).vertexCount);
		float[] updated = new float[FLOATS_PER_VERTEX * testingGpu.uploadedBuffers.get(1).vertexCount];
		testingGpu.uploadedBuffers.get(1).testGetFloats(updated);
		testingGpu.uploadedBuffers.clear();
		
		manager.shutdown();
		
		// The top and side faces should switch to 0 sky multiplier.
		int multiplier1 = 0;
		int multiplierHalf = 0;
		int multiplier0 = 0;
		for (int vertex = 0; vertex < 36; ++vertex)
		{
			int base = vertex * FLOATS_PER_VERTEX;
			
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 0));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 1));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 2));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 3));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 4));
			
			float initialSkyMultiplier = _extractField(initial, base, ATTRIBUTES, 5)[0];
			float updatedSkyMultiplier = _extractField(updated, base, ATTRIBUTES, 5)[0];
			if (1.0f == initialSkyMultiplier)
			{
				multiplier1 += 1;
			}
			else if (0.5f == initialSkyMultiplier)
			{
				multiplierHalf += 1;
			}
			else if (0.0f == initialSkyMultiplier)
			{
				multiplier0 += 1;
			}
			Assert.assertEquals(0.0f, updatedSkyMultiplier, 0.01f);
		}
		Assert.assertEquals(6, multiplier1);
		Assert.assertEquals(24, multiplierHalf);
		Assert.assertEquals(6, multiplier0);
	}

	@Test
	public void skyLightUpdatesBoundary() throws Throwable
	{
		// We want to test 2 cuboids, one stacked on top of the other, to see how we handle the height map updates.
		_Gpu testingGpu = new _Gpu();
		int textureCount = STONE_VALUE + 1;
		ItemTextureAtlas itemAtlas = _buildItemAtlas(textureCount);
		AuxilliaryTextureAtlas auxBlockTextures = _buildAuxAtlas();
		BlockModelsAndAtlas models = _buildBlockModelsAndAtlas(textureCount, Map.of(), new ModelBuffer[0]);
		BasicBlockAtlas blockAtlas = _buildBlockAtlas(textureCount, ALL_BLOCKS, new boolean[ALL_BLOCKS.length]);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, models, blockAtlas, auxBlockTextures);
		
		// We want to put a single solid block at the top of the low cuboid so we can verify the vertex values.
		CuboidAddress lowAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData lowCuboid = CuboidGenerator.createFilledCuboid(lowAddress, ENV.special.AIR);
		lowCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 30, 31), STONE_VALUE);
		ColumnHeightMap lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress).freeze();
		CuboidAddress highAddress = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData highCuboid = CuboidGenerator.createFilledCuboid(highAddress, STONE_BLOCK);
		highCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 30, 0), ENV.special.AIR.item().number());
		ColumnHeightMap highMap = ColumnHeightMap.build()
				.consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress)
				.consume(HeightMapHelpers.buildHeightMap(highCuboid), highAddress)
				.freeze();
		
		// Request the bottom one first and verify it.
		manager.setCuboid(lowCuboid, lowMap, null);
		Assert.assertEquals(1, manager.viewCuboids().size());
		VertexArray opaque = _waitForOpaqueArray(manager, lowAddress);
		Assert.assertEquals(36, opaque.totalVertices);
		Assert.assertEquals(1, testingGpu.uploadedBuffers.size());
		Assert.assertEquals(36, testingGpu.uploadedBuffers.get(0).vertexCount);
		float[] initial = new float[FLOATS_PER_VERTEX * testingGpu.uploadedBuffers.get(0).vertexCount];
		testingGpu.uploadedBuffers.get(0).testGetFloats(initial);
		testingGpu.uploadedBuffers.clear();
		
		// Now, request the top one and verify that both are changed and that the low one has different sky multiplier values.
		manager.setCuboid(highCuboid, highMap, null);
		Assert.assertEquals(2, manager.viewCuboids().size());
		testingGpu.processUntilBufferCount(manager, 2);
		VertexArray other = _waitForOpaqueArray(manager, highAddress);
		opaque = _waitForOpaqueArray(manager, lowAddress);
		Assert.assertEquals(36, opaque.totalVertices);
		Assert.assertEquals(2, testingGpu.uploadedBuffers.size());
		Assert.assertEquals(other.totalVertices, testingGpu.uploadedBuffers.get(0).vertexCount);
		Assert.assertEquals(36, testingGpu.uploadedBuffers.get(1).vertexCount);
		float[] updated = new float[FLOATS_PER_VERTEX * testingGpu.uploadedBuffers.get(1).vertexCount];
		testingGpu.uploadedBuffers.get(1).testGetFloats(updated);
		testingGpu.uploadedBuffers.clear();
		
		manager.shutdown();
		
		// The top and side faces should switch to 0 sky multiplier.
		int multiplier1 = 0;
		int multiplierHalf = 0;
		int multiplier0 = 0;
		for (int vertex = 0; vertex < 36; ++vertex)
		{
			int base = vertex * FLOATS_PER_VERTEX;
			
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 0));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 1));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 2));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 3));
			Assert.assertTrue(_compareVertexFields(initial, updated, base, ATTRIBUTES, 4));
			
			float initialSkyMultiplier = _extractField(initial, base, ATTRIBUTES, 5)[0];
			float updatedSkyMultiplier = _extractField(updated, base, ATTRIBUTES, 5)[0];
			if (1.0f == initialSkyMultiplier)
			{
				multiplier1 += 1;
			}
			else if (0.5f == initialSkyMultiplier)
			{
				multiplierHalf += 1;
			}
			else if (0.0f == initialSkyMultiplier)
			{
				multiplier0 += 1;
			}
			Assert.assertEquals(0.0f, updatedSkyMultiplier, 0.01f);
		}
		Assert.assertEquals(6, multiplier1);
		Assert.assertEquals(24, multiplierHalf);
		Assert.assertEquals(6, multiplier0);
	}

	@Test
	public void skyLightUpdatesMultiple() throws Throwable
	{
		// We want to test 2 cuboids, one stacked on top of the other, but enqueue both of them to make sure the lower height map is correct.
		_Gpu testingGpu = new _Gpu();
		int textureCount = STONE_VALUE + 1;
		ItemTextureAtlas itemAtlas = _buildItemAtlas(textureCount);
		AuxilliaryTextureAtlas auxBlockTextures = _buildAuxAtlas();
		BlockModelsAndAtlas models = _buildBlockModelsAndAtlas(textureCount, Map.of(), new ModelBuffer[0]);
		BasicBlockAtlas blockAtlas = _buildBlockAtlas(textureCount, ALL_BLOCKS, new boolean[ALL_BLOCKS.length]);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, models, blockAtlas, auxBlockTextures);
		
		// We want to put a single solid block at the top of the low cuboid so we can verify the vertex values.
		CuboidAddress lowAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData lowCuboid = CuboidGenerator.createFilledCuboid(lowAddress, ENV.special.AIR);
		lowCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 30, 31), STONE_VALUE);
		ColumnHeightMap lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress).freeze();
		CuboidAddress highAddress = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData highCuboid = CuboidGenerator.createFilledCuboid(highAddress, STONE_BLOCK);
		highCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(30, 30, 0), ENV.special.AIR.item().number());
		ColumnHeightMap highMap = ColumnHeightMap.build()
				.consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress)
				.consume(HeightMapHelpers.buildHeightMap(highCuboid), highAddress)
				.freeze();
		
		// Upload both of these.
		manager.setCuboid(lowCuboid, lowMap, null);
		manager.setCuboid(highCuboid, highMap, null);
		Assert.assertEquals(2, manager.viewCuboids().size());
		testingGpu.processUntilBufferCount(manager, 2);
		
		// Get the lower data.
		VertexArray opaque = _waitForOpaqueArray(manager, lowAddress);
		Assert.assertEquals(36, opaque.totalVertices);
		Assert.assertEquals(36, testingGpu.uploadedBuffers.get(0).vertexCount);
		float[] raw = new float[FLOATS_PER_VERTEX * testingGpu.uploadedBuffers.get(0).vertexCount];
		testingGpu.uploadedBuffers.get(0).testGetFloats(raw);
		testingGpu.uploadedBuffers.clear();
		
		manager.shutdown();
		
		// This block is entirely in shadow so all multipliers should be 0.0.
		for (int vertex = 0; vertex < 36; ++vertex)
		{
			int base = vertex * FLOATS_PER_VERTEX;
			
			float skyMultiplier = _extractField(raw, base, ATTRIBUTES, 5)[0];
			Assert.assertEquals(0.0f, skyMultiplier, 0.01f);
		}
	}

	@Test
	public void singleComplexBlock() throws Throwable
	{
		// We will just load in a single block and claim it uses a complex mesh.
		String string = "v 0.828934 -1.421580 0.697664\n"
				+ "v 0.828934 -1.421580 1.994554\n"
				+ "v 0.828934 -0.124690 0.697664\n"
				+ "vn -1.0000 -0.0000 -0.0000\n"
				+ "vt 0.656723 0.765970\n"
				+ "vt 0.791192 0.631300\n"
				+ "vt 0.791192 0.765970\n"
				+ "f 2/1/1 3/2/1 1/3/1\n"
		;
		Block waterSource = ENV.blocks.fromItem(ENV.items.getItemById("op.water_source"));
		Block waterStrong = ENV.blocks.fromItem(ENV.items.getItemById("op.water_strong"));
		Block waterWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.water_weak"));
		Block lavaSource = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_source"));
		Block lavaStrong = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_strong"));
		Block lavaWeak = ENV.blocks.fromItem(ENV.items.getItemById("op.lava_weak"));
		_Gpu testingGpu = new _Gpu();
		int textureCount = STONE_VALUE + 1;
		ItemTextureAtlas itemAtlas = _buildItemAtlas(textureCount);
		AuxilliaryTextureAtlas auxBlockTextures = _buildAuxAtlas();
		ModelBuffer[] modelBuffers = new ModelBuffer[] { ModelBuffer.buildFromWavefront(string) };
		BlockModelsAndAtlas models = _buildBlockModelsAndAtlas(textureCount, Map.of(STONE_BLOCK, (short)0), modelBuffers);
		// Note that the Block[] must minimally include water sources.
		Block[] basicBlocks = new Block[] {waterSource, waterStrong, waterWeak, lavaSource, lavaStrong, lavaWeak};
		BasicBlockAtlas blockAtlas = _buildBlockAtlas(0, basicBlocks, new boolean[basicBlocks.length]);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, models, blockAtlas, auxBlockTextures);
		
		CuboidAddress lowAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData lowCuboid = CuboidGenerator.createFilledCuboid(lowAddress, ENV.special.AIR);
		lowCuboid.setData15(AspectRegistry.BLOCK, BlockAddress.fromInt(5, 5, 5), STONE_VALUE);
		ColumnHeightMap lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress).freeze();
		
		manager.setCuboid(lowCuboid, lowMap, null);
		Assert.assertEquals(1, manager.viewCuboids().size());
		VertexArray modelArray = _waitForModelArray(manager, lowAddress);
		Assert.assertEquals(3, modelArray.totalVertices);
		Assert.assertEquals(1, testingGpu.uploadedBuffers.size());
		Assert.assertEquals(3, testingGpu.uploadedBuffers.get(0).vertexCount);
		float[] initial = new float[FLOATS_PER_VERTEX * testingGpu.uploadedBuffers.get(0).vertexCount];
		testingGpu.uploadedBuffers.get(0).testGetFloats(initial);
		testingGpu.uploadedBuffers.clear();
		
		manager.shutdown();
		
		// Just verify that one of the vertices looks correct - index 0 should be vertex 2 (shifted by block location), normal 1, and texture 1 (adjusted for atlas).
		int base = 0;
		float position[] = _extractField(initial, base, ATTRIBUTES, 0);
		float normal[] = _extractField(initial, base, ATTRIBUTES, 1);
		float texture0[] = _extractField(initial, base, ATTRIBUTES, 2);
		float texture1[] = _extractField(initial, base, ATTRIBUTES, 3);
		float blockLight[] = _extractField(initial, base, ATTRIBUTES, 4);
		float skyLight[] = _extractField(initial, base, ATTRIBUTES, 5);
		Assert.assertArrayEquals(new float[] { 5.828934f, 3.5784202f, 6.994554f }, position, 0.01f);
		Assert.assertArrayEquals(new float[] { -1.0f, -0.0f, -0.0f }, normal, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.3283615f, 0.382985f }, texture0, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.656723f * auxBlockTextures.coordinateSize, 0.76597f * auxBlockTextures.coordinateSize }, texture1, 0.01f);
		Assert.assertArrayEquals(new float[] { 0.1f }, blockLight, 0.01f);
		// Since this is a model-based block at the top of the column, it is given partial sky light.
		Assert.assertArrayEquals(new float[] { 0.5f }, skyLight, 0.01f);
	}


	private VertexArray _waitForWaterChange(CuboidMeshManager manager, CuboidAddress lowAddress, VertexArray previous)
	{
		while (previous == _readCuboidWater(manager, lowAddress))
		{
			manager.processBackground();
		}
		return _readCuboidWater(manager, lowAddress);
	}

	private VertexArray _waitForOpaqueChange(CuboidMeshManager manager, CuboidAddress lowAddress, VertexArray previous)
	{
		while (previous == _readCuboidOpaque(manager, lowAddress))
		{
			manager.processBackground();
		}
		return _readCuboidOpaque(manager, lowAddress);
	}

	private VertexArray _readCuboidWater(CuboidMeshManager manager, CuboidAddress address)
	{
		VertexArray waterArray = null;
		for (CuboidMeshManager.CuboidMeshes data : manager.viewCuboids())
		{
			if (address.equals(data.address()))
			{
				waterArray = data.waterArray();
				break;
			}
		}
		return waterArray;
	}

	private static VertexArray _readCuboidOpaque(CuboidMeshManager manager, CuboidAddress address)
	{
		VertexArray waterArray = null;
		for (CuboidMeshManager.CuboidMeshes data : manager.viewCuboids())
		{
			if (address.equals(data.address()))
			{
				waterArray = data.opaqueArray();
				break;
			}
		}
		return waterArray;
	}

	private static boolean[] buildNonOpaqueVector()
	{
		// We just want to add stone as our only opaque value.
		int size = 0;
		int indexToFalse = -1;
		for (int i = 0; i < ENV.items.ITEMS_BY_TYPE.length; ++ i)
		{
			Item item = ENV.items.ITEMS_BY_TYPE[i];
			if (null != ENV.blocks.fromItem(item))
			{
				if (item.number() == STONE_VALUE)
				{
					indexToFalse = size;
				}
				size += 1;
			}
		}
		boolean[] vector = new boolean[size];
		for (int i = 0; i < vector.length; ++i)
		{
			vector[i] = true;
		}
		vector[indexToFalse] = false;
		return vector;
	}

	private static VertexArray _waitForOpaqueArray(CuboidMeshManager manager, CuboidAddress address)
	{
		VertexArray foundMesh = null;
		while (null == foundMesh)
		{
			manager.processBackground();
			Iterator<CuboidMeshManager.CuboidMeshes> iterator = manager.viewCuboids().iterator();
			
			while (iterator.hasNext())
			{
				CuboidMeshManager.CuboidMeshes mesh = iterator.next();
				if (address.equals(mesh.address()))
				{
					if (null != mesh.opaqueArray())
					{
						foundMesh = mesh.opaqueArray();
					}
				}
			}
		}
		return foundMesh;
	}

	private static VertexArray _waitForModelArray(CuboidMeshManager manager, CuboidAddress address)
	{
		VertexArray foundMesh = null;
		while (null == foundMesh)
		{
			manager.processBackground();
			Iterator<CuboidMeshManager.CuboidMeshes> iterator = manager.viewCuboids().iterator();
			
			while (iterator.hasNext())
			{
				CuboidMeshManager.CuboidMeshes mesh = iterator.next();
				if (address.equals(mesh.address()))
				{
					if (null != mesh.modelArray())
					{
						foundMesh = mesh.modelArray();
					}
				}
			}
		}
		return foundMesh;
	}

	private static boolean _compareVertexFields(float[] initial, float[] updated, int vertexBase, Attribute[] attributes, int field)
	{
		float[] initialPosition = _extractField(initial, vertexBase, attributes, field);
		float[] updatedPosition = _extractField(updated, vertexBase, attributes, field);
		return Arrays.equals(initialPosition, updatedPosition);
	}

	private static float[] _extractField(float[] array, int vertexBase, Attribute[] attributes, int field)
	{
		int offset = 0;
		for (int i = 0; i < field; ++i)
		{
			offset += ATTRIBUTES[i].floats();
		}
		int start = vertexBase + offset;
		int end = start + ATTRIBUTES[field].floats();
		return Arrays.copyOfRange(array, start, end);
	}

	private static ItemTextureAtlas _buildItemAtlas(int textureCount)
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(textureCount);
		return new ItemTextureAtlas(raw);
	}

	private static AuxilliaryTextureAtlas _buildAuxAtlas()
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(AuxilliaryTextureAtlas.Variant.values().length);
		return new AuxilliaryTextureAtlas(raw);
	}

	private static BasicBlockAtlas _buildBlockAtlas(int textureCount, Block[] blocksIncluded, boolean[] nonOpaqueVector)
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(textureCount);
		return new BasicBlockAtlas(blocksIncluded, raw, nonOpaqueVector);
	}

	private static BlockModelsAndAtlas _buildBlockModelsAndAtlas(int textureCount, Map<Block, Short> blockToIndex, ModelBuffer[] models)
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(textureCount);
		return BlockModelsAndAtlas.testInstance(blockToIndex, models, raw);
	}


	private static class _Gpu implements CuboidMeshManager.IGpu
	{
		public final List<BufferBuilder.Buffer> uploadedBuffers = new ArrayList<>();
		public void processUntilBufferCount(CuboidMeshManager manager, int count)
		{
			while (this.uploadedBuffers.size() < count)
			{
				manager.processBackground();
			}
		}
		@Override
		public VertexArray uploadBuffer(BufferBuilder.Buffer buffer)
		{
			this.uploadedBuffers.add(buffer);
			return new VertexArray(1, buffer.vertexCount, ATTRIBUTES);
		}
		@Override
		public void deleteBuffer(VertexArray array)
		{
		}
	}
}
