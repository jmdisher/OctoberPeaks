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
	private static Item WATER_SOURCE;
	private static Item WATER_STRONG;
	private static Item WATER_WEAK;
	private static Attribute[] ATTRIBUTES;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		WATER_SOURCE = ENV.items.getItemById("op.water_source");
		WATER_STRONG = ENV.items.getItemById("op.water_strong");
		WATER_WEAK = ENV.items.getItemById("op.water_weak");
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
		// We will just check the z locations for instances of 0.5 since that is the interesting case.
		int matchCount = 0;
		for (_Vertex vertex : vertices)
		{
			if (0.5f == vertex.z)
			{
				matchCount += 1;
			}
		}
		// TODO:  Change this check once we fix the cross-cuboid stitching of water levels.
		Assert.assertEquals(4, matchCount);
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
		// TODO:  This is currently a bug caused by the top surface being eliminated by the liquid above it so change this when it is fixed.
		Assert.assertEquals(4, vertices.size());
	}


	private static BufferBuilder.Buffer _buildWaterBuffer(CuboidData cuboid, CuboidData optionalUp, CuboidData optionalNorth)
	{
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		
		BufferBuilder builder = new BufferBuilder(buffer, ATTRIBUTES);
		Block[] blocks = new Block[] {
				ENV.special.AIR,
				ENV.blocks.fromItem(WATER_SOURCE),
				ENV.blocks.fromItem(WATER_STRONG),
				ENV.blocks.fromItem(WATER_WEAK),
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
				, WATER_SOURCE.number()
				, WATER_STRONG.number()
				, WATER_WEAK.number()
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
		int verticesPerQuad = 6;
		int quads = 10;
		float[] vertexData = new float[floatsPerVertex * verticesPerQuad * quads];
		waterBuffer.testGetFloats(vertexData);
		// Extract this position data as vertices.
		Set<_Vertex> vertices = new HashSet<>();
		for (int offset = 0; offset < (verticesPerQuad * quads); ++offset)
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

	private static record _Vertex(float x, float y, float z) {}
}
