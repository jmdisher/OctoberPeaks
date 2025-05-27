package com.jeffdisher.october.peaks.scene;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.textures.AuxilliaryTextureAtlas;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.RawTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.wavefront.ModelBuffer;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestSceneMeshHelpers
{
	private static Environment ENV;
	private static Item STONE;
	private static Item WATER_SOURCE;
	private static Item WATER_STRONG;
	private static Item WATER_WEAK;
	private static Item LAVA_SOURCE;
	private static Item LAVA_STRONG;
	private static Item LAVA_WEAK;
	private static Attribute[] ATTRIBUTES;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.items.getItemById("op.stone");
		WATER_SOURCE = ENV.items.getItemById("op.water_source");
		WATER_STRONG = ENV.items.getItemById("op.water_strong");
		WATER_WEAK = ENV.items.getItemById("op.water_weak");
		LAVA_SOURCE = ENV.items.getItemById("op.lava_source");
		LAVA_STRONG = ENV.items.getItemById("op.lava_strong");
		LAVA_WEAK = ENV.items.getItemById("op.lava_weak");
		ATTRIBUTES = new Attribute[] {
				new Attribute("aPosition", 3),
				new Attribute("aNormal", 3),
				new Attribute("aTexture0", 2),
				new Attribute("aTexture1", 2),
				new Attribute("aBlockLightMultiplier", 1),
				new Attribute("aSkyLightMultiplier", 1),
		};
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
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress sourceBlock = new BlockAddress((byte)5, (byte)5, (byte)5);
		BlockAddress flowBlock = new BlockAddress((byte)5, (byte)5, (byte)4);
		BlockAddress bottomBlock = new BlockAddress((byte)5, (byte)5, (byte)3);
		BlockAddress spillBlock = new BlockAddress((byte)5, (byte)6, (byte)3);
		cuboid.setData15(AspectRegistry.BLOCK, sourceBlock, WATER_SOURCE.number());
		cuboid.setData15(AspectRegistry.BLOCK, flowBlock, WATER_WEAK.number());
		cuboid.setData15(AspectRegistry.BLOCK, bottomBlock, WATER_STRONG.number());
		cuboid.setData15(AspectRegistry.BLOCK, spillBlock, WATER_WEAK.number());
		
		BufferBuilder.Buffer waterBuffer = _buildWaterBuffer(cuboid, null, null);
		int quadsWritten = _countQuadsInBuffer(waterBuffer);
		Assert.assertEquals(36, quadsWritten);
	}

	@Test
	public void waterSourceEdge() throws Throwable
	{
		// Check the callbacks we get for a water block at the edge of the cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress sourceBlock = new BlockAddress((byte)0, (byte)0, (byte)0);
		cuboid.setData15(AspectRegistry.BLOCK, sourceBlock, WATER_SOURCE.number());
		
		BufferBuilder.Buffer waterBuffer = _buildWaterBuffer(cuboid, null, null);
		int quadsWritten = _countQuadsInBuffer(waterBuffer);
		// We should see 6 quads, double-sided.
		Assert.assertEquals(12, quadsWritten);
		
		Set<_Vertex> vertices = _collectVerticesInBuffer(waterBuffer);
		Assert.assertEquals(8, vertices.size());
		int matchCount = 0;
		for (_Vertex vertex : vertices)
		{
			if (0.9f == vertex.z)
			{
				matchCount += 1;
			}
		}
		Assert.assertEquals(4, matchCount);
	}

	@Test
	public void waterWeakEdge() throws Throwable
	{
		// Check the callbacks we get for a water block at the edge of the cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress sourceBlock = new BlockAddress((byte)0, (byte)0, (byte)0);
		cuboid.setData15(AspectRegistry.BLOCK, sourceBlock, WATER_WEAK.number());
		
		BufferBuilder.Buffer waterBuffer = _buildWaterBuffer(cuboid, null, null);
		int quadsWritten = _countQuadsInBuffer(waterBuffer);
		// We should see 6 quads, double-sided.
		Assert.assertEquals(12, quadsWritten);
		
		Set<_Vertex> vertices = _collectVerticesInBuffer(waterBuffer);
		Assert.assertEquals(8, vertices.size());
		int matchCount = 0;
		for (_Vertex vertex : vertices)
		{
			if (0.1f == vertex.z)
			{
				matchCount += 1;
			}
		}
		Assert.assertEquals(4, matchCount);
	}

	@Test
	public void waterOnCuboidEdges() throws Throwable
	{
		// Check what we render when flowing water at an edge meets another water block in the north cuboid.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)31, (byte)0), WATER_STRONG.number());
		CuboidData north = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)1, (short)0), ENV.special.AIR);
		north.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)0, (byte)0, (byte)0), WATER_SOURCE.number());
		
		BufferBuilder.Buffer waterBuffer = _buildWaterBuffer(cuboid, null, north);
		// We should see 5 quads, double-sided, since the other neighbour will cause one side not to generate.
		Assert.assertEquals(10, _countQuadsInBuffer(waterBuffer));
		Set<_Vertex> vertices = _collectVerticesInBuffer(waterBuffer);
		Assert.assertEquals(8, vertices.size());
		int matchCountSource = 0;
		int matchCountStrong = 0;
		for (_Vertex vertex : vertices)
		{
			if (0.9f == vertex.z)
			{
				matchCountSource += 1;
			}
			else if (0.5f == vertex.z)
			{
				matchCountStrong += 1;
			}
		}
		Assert.assertEquals(2, matchCountSource);
		Assert.assertEquals(2, matchCountStrong);
	}

	@Test
	public void waterOnVerticalEdges() throws Throwable
	{
		// Check what we render when flowing water at the top of one cuboid is under a source above it.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)31), WATER_STRONG.number());
		CuboidData up = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)1), ENV.special.AIR);
		up.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)5, (byte)5, (byte)0), WATER_SOURCE.number());
		
		BufferBuilder.Buffer waterBuffer = _buildWaterBuffer(cuboid, up, null);
		// We should see 5 quads, double-sided, since the other neighbour will cause one side not to generate.
		Assert.assertEquals(10, _countQuadsInBuffer(waterBuffer));
		Set<_Vertex> vertices = _collectVerticesInBuffer(waterBuffer);
		Assert.assertEquals(8, vertices.size());
		
		// Verify that these will render the full cube.
		int matchLow = 0;
		int matchHigh = 0;
		for (_Vertex vertex : vertices)
		{
			if (31.0f == vertex.z)
			{
				matchLow += 1;
			}
			else if (32.0f == vertex.z)
			{
				matchHigh += 1;
			}
		}
		Assert.assertEquals(4, matchLow);
		Assert.assertEquals(4, matchHigh);
	}

	@Test
	public void rotatedMultiBlock() throws Throwable
	{
		// Verify the rendering of a rotated multi-block model.
		Item multiDoor = ENV.items.getItemById("op.double_door_base");
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		AbsoluteLocation root = cuboid.getCuboidAddress().getBase().getRelative(5, 5, 5);
		cuboid.setData15(AspectRegistry.BLOCK, root.getBlockAddress(), multiDoor.number());
		cuboid.setData7(AspectRegistry.ORIENTATION, root.getBlockAddress(), OrientationAspect.directionToByte(OrientationAspect.Direction.WEST));
		cuboid.setData15(AspectRegistry.BLOCK, root.getRelative(0, 1, 0).getBlockAddress(), multiDoor.number());
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, root.getRelative(0, 1, 0).getBlockAddress(), root);
		cuboid.setData15(AspectRegistry.BLOCK, root.getRelative(0, 1, 1).getBlockAddress(), multiDoor.number());
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, root.getRelative(0, 1, 1).getBlockAddress(), root);
		cuboid.setData15(AspectRegistry.BLOCK, root.getRelative(0, 0, 1).getBlockAddress(), multiDoor.number());
		cuboid.setDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, root.getRelative(0, 0, 1).getBlockAddress(), root);
		
		// We will just use a single triangle as the model, to keep this simple.
		String string = "v 0.0 0.0 0.0\n"
				+ "v 2.0 0.0 0.0\n"
				+ "v 2.0 1.0 0.0\n"
				+ "vn -0.0000 -0.0000 1.0000\n"
				+ "vt 0.0 0.0\n"
				+ "vt 0.0 1.0\n"
				+ "vt 1.0 1.0\n"
				+ "f 1/1/1 2/2/1 3/3/1\n"
		;
		Map<Block, BlockModelsAndAtlas.Indices> blockToIndex = Map.of(ENV.blocks.fromItem(multiDoor), new BlockModelsAndAtlas.Indices((short)0, (short)0, (short)0));
		ModelBuffer[] models = new ModelBuffer[] { ModelBuffer.buildFromWavefront(string) };
		int textureCount = 1;
		BlockModelsAndAtlas modelsAndAtlas = _buildBlockModelsAndAtlas(textureCount, blockToIndex, models);
		
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		BufferBuilder builder = new BufferBuilder(buffer, ATTRIBUTES);
		SparseShortProjection<AuxilliaryTextureAtlas.Variant> projection = new SparseShortProjection<>(AuxilliaryTextureAtlas.Variant.NONE, Map.of());
		AuxilliaryTextureAtlas auxAtlas = _buildAuxAtlas();
		SceneMeshHelpers.MeshInputData inputData = new SceneMeshHelpers.MeshInputData(cuboid, ColumnHeightMap.build().freeze()
				, null, null
				, null, null
				, null, null
				, null, null
				, null, null
				, null, null
		);
		SceneMeshHelpers.populateBufferWithComplexModels(ENV, builder, modelsAndAtlas, projection, auxAtlas, inputData);
		BufferBuilder.Buffer finished = builder.finishOne();
		Set<_Vertex> vertices = _collectVerticesInBuffer(finished);
		Assert.assertEquals(3, vertices.size());
		Assert.assertTrue(vertices.contains(new _Vertex(6.0f, 5.0f, 5.0f)));
		Assert.assertTrue(vertices.contains(new _Vertex(6.0f, 7.0f, 5.0f)));
		Assert.assertTrue(vertices.contains(new _Vertex(5.0f, 7.0f, 5.0f)));
	}

	@Test
	public void waterSourceUnderBlock() throws Throwable
	{
		// Check the callbacks we get for a water block with a solid block above it.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress sourceBlock = new BlockAddress((byte)5, (byte)5, (byte)5);
		cuboid.setData15(AspectRegistry.BLOCK, sourceBlock, WATER_SOURCE.number());
		BlockAddress stoneBlock = sourceBlock.getRelativeInt(0, 0, 1);
		cuboid.setData15(AspectRegistry.BLOCK, stoneBlock, STONE.number());
		
		BufferBuilder.Buffer waterBuffer = _buildWaterBuffer(cuboid, null, null);
		int quadsWritten = _countQuadsInBuffer(waterBuffer);
		// We should see 6 quads, double-sided.
		Assert.assertEquals(12, quadsWritten);
		
		Set<_Vertex> vertices = _collectVerticesInBuffer(waterBuffer);
		Assert.assertEquals(8, vertices.size());
		int matchCount = 0;
		for (_Vertex vertex : vertices)
		{
			if (5.9f == vertex.z)
			{
				matchCount += 1;
			}
		}
		Assert.assertEquals(4, matchCount);
	}

	@Test
	public void lavaSourceLight() throws Throwable
	{
		// We just want to demonstrate that we take the maximum light of a surface and source, for liquids.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress sourceBlock = BlockAddress.fromInt(2, 3, 4);
		cuboid.setData15(AspectRegistry.BLOCK, sourceBlock, LAVA_SOURCE.number());
		cuboid.setData7(AspectRegistry.LIGHT, sourceBlock, ENV.lighting.getLightEmission(ENV.blocks.fromItem(LAVA_SOURCE), false));
		// Set an adjacent block to something less and something greater.
		cuboid.setData7(AspectRegistry.LIGHT, sourceBlock.getRelativeInt(0, 0, 1), (byte)15);
		cuboid.setData7(AspectRegistry.LIGHT, sourceBlock.getRelativeInt(0, 1, 0), (byte)1);
		
		BufferBuilder.Buffer liquidBuffer = _buildLavaBuffer(cuboid);
		int quadsWritten = _countQuadsInBuffer(liquidBuffer);
		// We should see 6 quads, double-sided.
		Assert.assertEquals(12, quadsWritten);
		
		float[] blockLights = _collectBlockLightVerticesInBuffer(liquidBuffer);
		Assert.assertEquals(6 * 12, blockLights.length);
		int highCount = 0;
		int lowCount = 0;
		for (float light : blockLights)
		{
			if (1.1f == light)
			{
				highCount += 1;
			}
			else if (0.6333334f == light)
			{
				lowCount += 1;
			}
			else
			{
				Assert.fail();
			}
		}
		Assert.assertEquals(12, highCount);
		Assert.assertEquals(5 * 12, lowCount);
	}


	private static BufferBuilder.Buffer _buildWaterBuffer(CuboidData cuboid, CuboidData optionalUp, CuboidData optionalNorth)
	{
		return _buildLiquidBuffer(WATER_SOURCE, WATER_STRONG, WATER_WEAK, cuboid, optionalUp, optionalNorth);
	}

	private static BufferBuilder.Buffer _buildLavaBuffer(CuboidData cuboid)
	{
		return _buildLiquidBuffer(LAVA_SOURCE, LAVA_STRONG, LAVA_WEAK, cuboid, null, null);
	}

	private static BufferBuilder.Buffer _buildLiquidBuffer(Item source, Item strong, Item weak, CuboidData cuboid, CuboidData optionalUp, CuboidData optionalNorth)
	{
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		
		BufferBuilder builder = new BufferBuilder(buffer, ATTRIBUTES);
		Block[] blocks = new Block[] {
				ENV.special.AIR,
				ENV.blocks.fromItem(source),
				ENV.blocks.fromItem(strong),
				ENV.blocks.fromItem(weak),
		};
		boolean[] nonOpaqueVector = new boolean[] {
				true,
				true,
				true,
				true,
		};
		BasicBlockAtlas blockAtlas = _buildBlockAtlas(4, blocks, nonOpaqueVector);
		SparseShortProjection<AuxilliaryTextureAtlas.Variant> projection = new SparseShortProjection<>(AuxilliaryTextureAtlas.Variant.NONE, Map.of());
		AuxilliaryTextureAtlas auxAtlas = _buildAuxAtlas();
		SceneMeshHelpers.MeshInputData inputData = new SceneMeshHelpers.MeshInputData(cuboid, ColumnHeightMap.build().freeze()
				, optionalUp, (null != optionalUp) ?  ColumnHeightMap.build().freeze() : null
				, null, null
				, optionalNorth, (null != optionalNorth) ? ColumnHeightMap.build().freeze() : null
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
				, source.number()
				, strong.number()
				, weak.number()
				, true
		);
		return builder.finishOne();
	}

	private static int _countQuadsInBuffer(BufferBuilder.Buffer waterBuffer)
	{
		int verticesPerQuad = 6;
		int quadsWritten = waterBuffer.vertexCount / verticesPerQuad;
		return quadsWritten;
	}

	private static Set<_Vertex> _collectVerticesInBuffer(BufferBuilder.Buffer waterBuffer)
	{
		// We also want to look at the shape of the vertices to see how this is joining between cuboids.
		int floatsPerVertex = Arrays.stream(ATTRIBUTES).collect(Collectors.summingInt((Attribute attr) -> attr.floats()));
		float[] vertexData = new float[floatsPerVertex * waterBuffer.vertexCount];
		waterBuffer.testGetFloats(vertexData);
		// Extract this position data as vertices.
		Set<_Vertex> vertices = new HashSet<>();
		for (int offset = 0; offset < waterBuffer.vertexCount; ++offset)
		{
			int fromIndex = offset * floatsPerVertex;
			float x = vertexData[fromIndex + 0];
			float y = vertexData[fromIndex + 1];
			float z = vertexData[fromIndex + 2];
			_Vertex vertex = new _Vertex(x, y, z);
			vertices.add(vertex);
		}
		return vertices;
	}

	private static float[] _collectBlockLightVerticesInBuffer(BufferBuilder.Buffer waterBuffer)
	{
		// We will collect the block light of each vertex.
		// We also want to look at the shape of the vertices to see how this is joining between cuboids.
		int floatsPerVertex = Arrays.stream(ATTRIBUTES).collect(Collectors.summingInt((Attribute attr) -> attr.floats()));
		float[] vertexData = new float[floatsPerVertex * waterBuffer.vertexCount];
		waterBuffer.testGetFloats(vertexData);
		float[] blockLights = new float[waterBuffer.vertexCount];
		for (int offset = 0; offset < waterBuffer.vertexCount; ++offset)
		{
			// We know that this is the 10th float.
			int blockLightFloat = 10;
			int fromIndex = offset * floatsPerVertex + blockLightFloat;
			blockLights[offset] = vertexData[fromIndex];
		}
		return blockLights;
	}

	private static AuxilliaryTextureAtlas _buildAuxAtlas()
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(AuxilliaryTextureAtlas.Variant.values().length);
		return new AuxilliaryTextureAtlas(raw);
	}

	private static BasicBlockAtlas _buildBlockAtlas(int textureCount, Block[] blocksIncluded, boolean[] nonOpaqueVector)
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(textureCount);
		int maxItemNumber = 0;
		for (Block block : blocksIncluded)
		{
			maxItemNumber = Math.max(maxItemNumber, block.item().number());
		}
		int index = 0;
		int[][] mapping = new int[maxItemNumber + 1][];
		for (Block block : blocksIncluded)
		{
			mapping[block.item().number()] = new int[] { index, index, index };
			index += 1;
		}
		return new BasicBlockAtlas(raw, mapping, nonOpaqueVector);
	}

	private static BlockModelsAndAtlas _buildBlockModelsAndAtlas(int textureCount, Map<Block, BlockModelsAndAtlas.Indices> blockToIndex, ModelBuffer[] models)
	{
		RawTextureAtlas raw = TextureHelpers.testRawAtlas(textureCount);
		return BlockModelsAndAtlas.testInstance(blockToIndex, models, raw);
	}


	private static record _Vertex(float x, float y, float z) {}
}
