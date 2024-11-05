package com.jeffdisher.october.peaks.graphics;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
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
	private static Item STONE_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		ATTRIBUTES = new Attribute[] { new Attribute("aPosition", 3),  new Attribute("aNormal", 3),  new Attribute("aTexture0", 2),  new Attribute("aTexture1", 2) };
		STONE_ITEM = ENV.items.getItemById("op.stone");
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
		int textureCount = STONE_ITEM.number() + 1;
		TextureAtlas<ItemVariant> itemAtlas = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], ItemVariant.class);
		TextureAtlas<BlockVariant> blockTextures = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], BlockVariant.class);
		TextureAtlas<SceneMeshHelpers.AuxVariant> auxBlockTextures = TextureAtlas.testBuildAtlas(textureCount, new boolean[textureCount], SceneMeshHelpers.AuxVariant.class);
		CuboidMeshManager manager = new CuboidMeshManager(ENV, testingGpu, ATTRIBUTES, itemAtlas, blockTextures, auxBlockTextures);
		
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)6, (byte)7), STONE_ITEM.number());
		
		manager.setCuboid(cuboid);
		Assert.assertEquals(1, manager.viewCuboids().size());
		
		manager.shutdown();
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
