package com.jeffdisher.october.peaks.scene;

import java.nio.FloatBuffer;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.BlockVariant;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestSceneMeshHelpers
{
	private static Environment ENV;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void waterFlow() throws Throwable
	{
		// Create some downward flowing water to see what callbacks we get for faces.
		Item waterSource = ENV.items.getItemById("op.water_source");
		Item waterStrong = ENV.items.getItemById("op.water_strong");
		Item waterWeak = ENV.items.getItemById("op.water_weak");
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress sourceBlock = new BlockAddress((byte)5, (byte)5, (byte)5);
		BlockAddress flowBlock = new BlockAddress((byte)5, (byte)5, (byte)4);
		BlockAddress bottomBlock = new BlockAddress((byte)5, (byte)5, (byte)3);
		BlockAddress spillBlock = new BlockAddress((byte)5, (byte)6, (byte)3);
		cuboid.setData15(AspectRegistry.BLOCK, sourceBlock, waterSource.number());
		cuboid.setData15(AspectRegistry.BLOCK, flowBlock, waterWeak.number());
		cuboid.setData15(AspectRegistry.BLOCK, bottomBlock, waterStrong.number());
		cuboid.setData15(AspectRegistry.BLOCK, spillBlock, waterWeak.number());
		
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		Attribute[] attributes = new Attribute[] {
				new Attribute("aPosition", 3),
				new Attribute("aNormal", 3),
				new Attribute("aTexture0", 2),
				new Attribute("aTexture1", 2),
				new Attribute("aBlockLightMultiplier", 1),
				new Attribute("aSkyLightMultiplier", 1),
		};
		
		BufferBuilder builder = new BufferBuilder(buffer, attributes);
		Block[] blocks = new Block[] {
				ENV.special.AIR,
				ENV.blocks.fromItem(waterSource),
				ENV.blocks.fromItem(waterStrong),
				ENV.blocks.fromItem(waterWeak),
		};
		TextureAtlas<BlockVariant> blockTextures = new TextureAtlas<>(4, 2, 1);
		boolean[] nonOpaqueVector = new boolean[] {
				true,
				true,
				true,
				true,
		};
		BasicBlockAtlas blockAtlas = new BasicBlockAtlas(blocks, blockTextures, nonOpaqueVector);
		SparseShortProjection<SceneMeshHelpers.AuxVariant> projection = new SparseShortProjection<>(SceneMeshHelpers.AuxVariant.NONE, Map.of());
		TextureAtlas<SceneMeshHelpers.AuxVariant> auxAtlas = new TextureAtlas<>(1, 1, 1);
		SceneMeshHelpers.MeshInputData inputData = new SceneMeshHelpers.MeshInputData(cuboid, ColumnHeightMap.build().freeze()
				, null, null
				, null, null
				, null, null
				, null, null
				, null, null
				, null, null
		);
		SceneMeshHelpers.populateWaterMeshBufferForCuboid(ENV
				, builder
				, blockAtlas
				, projection
				, auxAtlas
				, inputData
				, waterSource.number()
				, waterStrong.number()
				, waterWeak.number()
				, true
		);
		BufferBuilder.Buffer waterBuffer = builder.finishOne();
		int verticesPerQuad = 6;
		int quadsWritten = waterBuffer.vertexCount / verticesPerQuad;
		Assert.assertEquals(36, quadsWritten);
	}
}
