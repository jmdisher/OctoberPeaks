package com.jeffdisher.october.peaks.graphics;

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
import com.jeffdisher.october.peaks.BlockVariant;
import com.jeffdisher.october.peaks.ItemVariant;
import com.jeffdisher.october.peaks.TextureAtlas;
import com.jeffdisher.october.peaks.graphics.BufferBuilder.Buffer;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestCuboidMeshManager
{
	private static Environment ENV;
	private static Attribute[] ATTRIBUTES;
	private static short STONE_VALUE;
	private static short WATER_VALUE;
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
		STONE_VALUE = ENV.items.getItemById("op.stone").number();
		WATER_VALUE = ENV.items.getItemById("op.water_source").number();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void empty() throws Throwable
	{
		CuboidMeshManager manager = new CuboidMeshManager(ENV, null, null, null, null, null);
		Assert.assertEquals(0, manager.viewCuboids().size());
		manager.shutdown();
	}

	@Test
	public void addOne() throws Throwable
	{
		_Gpu testingGpu = new _Gpu();
		int textureCount = STONE_VALUE + 1;
		TextureAtlas<ItemVariant> itemAtlas = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], ItemVariant.class);
		TextureAtlas<BlockVariant> blockTextures = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], BlockVariant.class);
		TextureAtlas<SceneMeshHelpers.AuxVariant> auxBlockTextures = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], SceneMeshHelpers.AuxVariant.class);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, blockTextures, auxBlockTextures);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)6, (byte)7), STONE_VALUE);
		ColumnHeightMap heightMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(cuboid), cuboid.getCuboidAddress().z()).freeze();
		
		manager.setCuboid(cuboid, heightMap, null);
		Assert.assertEquals(1, manager.viewCuboids().size());
		
		// We shouldn't see the finished result, yet.
		CuboidMeshManager.CuboidMeshes data = manager.viewCuboids().iterator().next();
		Assert.assertNull(data.opaqueArray());
		Assert.assertNull(data.itemsOnGroundArray());
		Assert.assertNull(data.transparentArray());
		
		// Process until we see these.
		while (null == manager.viewCuboids().iterator().next().opaqueArray())
		{
			manager.processBackground();
		}
		Assert.assertEquals(36, manager.viewCuboids().iterator().next().opaqueArray().totalVertices);
		
		manager.shutdown();
	}

	@Test
	public void adjacentCuboids() throws Throwable
	{
		_Gpu testingGpu = new _Gpu();
		int textureCount = WATER_VALUE + 1;
		TextureAtlas<ItemVariant> itemAtlas = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], ItemVariant.class);
		TextureAtlas<BlockVariant> blockTextures = TextureAtlas.testBuildAtlas(textureCount, buildNonOpaqueVector(), BlockVariant.class);
		TextureAtlas<SceneMeshHelpers.AuxVariant> auxBlockTextures = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], SceneMeshHelpers.AuxVariant.class);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, blockTextures, auxBlockTextures);
		
		CuboidAddress lowAddress = new CuboidAddress((short)0, (short)0, (short)0);
		CuboidData lowCuboid = CuboidGenerator.createFilledCuboid(lowAddress, ENV.special.AIR);
		BlockAddress lowBlock = new BlockAddress((byte)31, (byte)31, (byte)31);
		lowCuboid.setData15(AspectRegistry.BLOCK, lowBlock, WATER_VALUE);
		ColumnHeightMap lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress.z()).freeze();
		
		CuboidAddress highAddress = new CuboidAddress((short)0, (short)0, (short)1);
		CuboidData highCuboid = CuboidGenerator.createFilledCuboid(highAddress, ENV.special.AIR);
		BlockAddress highBlock = new BlockAddress((byte)31, (byte)31, (byte)0);
		highCuboid.setData15(AspectRegistry.BLOCK, highBlock, WATER_VALUE);
		ColumnHeightMap highMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(highCuboid), highAddress.z()).freeze();
		
		manager.setCuboid(lowCuboid, lowMap, null);
		Assert.assertNull(_readCuboidWater(manager, lowAddress));
		VertexArray lowWaterArray = _waitForWaterChange(manager, lowAddress, null);
		Assert.assertEquals(36, lowWaterArray.totalVertices);
		
		manager.setCuboid(highCuboid, highMap, null);
		Assert.assertNull(_readCuboidWater(manager, highAddress));
		VertexArray highWaterArray = _waitForWaterChange(manager, highAddress, null);
		Assert.assertEquals(30, highWaterArray.totalVertices);
		
		// Wait for the re-bake of the low cuboid.
		lowWaterArray = _waitForWaterChange(manager, lowAddress, lowWaterArray);
		Assert.assertEquals(30, lowWaterArray.totalVertices);
		
		// Now change one of these blocks and observe the updates in both blocks.
		lowCuboid = CuboidData.mutableClone(lowCuboid);
		lowCuboid.setData15(AspectRegistry.BLOCK, lowBlock, STONE_VALUE);
		lowMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(lowCuboid), lowAddress.z()).freeze();
		manager.setCuboid(lowCuboid, lowMap, Set.of(lowBlock));
		
		// Note that the water array should disappear now and be replaced with opaque.
		Assert.assertNull(_waitForWaterChange(manager, lowAddress, lowWaterArray));
		VertexArray lowOpaqueArray = _waitForOpaqueChange(manager, lowAddress, null);
		Assert.assertEquals(36, lowOpaqueArray.totalVertices);
		highWaterArray = _waitForWaterChange(manager, highAddress, highWaterArray);
		Assert.assertEquals(36, highWaterArray.totalVertices);
		
		manager.shutdown();
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

	private VertexArray _readCuboidOpaque(CuboidMeshManager manager, CuboidAddress address)
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

	private boolean[] buildNonOpaqueVector()
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


	private static class _Gpu implements CuboidMeshManager.IGpu
	{
		@Override
		public VertexArray uploadBuffer(Buffer buffer)
		{
			return new VertexArray(1, buffer.vertexCount, ATTRIBUTES);
		}
		@Override
		public void deleteBuffer(VertexArray array)
		{
		}
	}
}
