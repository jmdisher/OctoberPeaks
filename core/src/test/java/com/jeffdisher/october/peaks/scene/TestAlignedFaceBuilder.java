package com.jeffdisher.october.peaks.scene;

import java.nio.FloatBuffer;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestAlignedFaceBuilder
{
	private static Environment ENV;
	private static Item STONE;
	private static Attribute[] ATTRIBUTES;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.items.getItemById("op.stone");
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
	public void partialShadowFaceUp() throws Throwable
	{
		// Render a face which is partially obscured from the sky and is only partially lit by a block.
		AbsoluteLocation targetBlock = new AbsoluteLocation(5, 6, 7);
		CuboidData central = CuboidGenerator.createFilledCuboid(targetBlock.getCuboidAddress(), ENV.special.AIR);
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(0, 0, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(0, 1, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(1, 0, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(1, 1, 2).getBlockAddress(), STONE.number());
		central.setData7(AspectRegistry.LIGHT, targetBlock.getRelative(1, 1, 1).getBlockAddress(), (byte)10);
		MeshInputData data = _createMeshInput(central, null);
		float[] uvBase = new float[] { 0.0f, 0.25f };
		float uvSize = 0.25f;
		float[] auxBase = new float[] { 0.0f, 0.5f };
		float auxSize = 0.5f;
		AlignedFaceBuilder builder = new AlignedFaceBuilder(data
			, uvBase
			, uvSize
			, auxBase
			, auxSize
			, targetBlock
			, AlignedFaceBuilder.Normal.UP
		);
		
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		BufferBuilder bufferBuilder = new BufferBuilder(buffer, ATTRIBUTES);
		MeshHelperBufferBuilder meshBufferBuilder = new MeshHelperBufferBuilder(bufferBuilder, MeshHelperBufferBuilder.USE_ALL_ATTRIBUTES);
		builder.generateQuad(meshBufferBuilder, new float[] {0.0f, 0.0f, 1.0f}, new float[] {1.0f, 1.0f, 1.0f});
		
		Assert.assertEquals(6 * 12, buffer.position());
		float[] outFloats = new float[buffer.position() / 6];
		buffer.flip();
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// aux_U, aux_V
		// blockLight
		// skyLight
		float[] v0 = new float[] {
			5.0f, 6.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.25f,
			0.0f, 0.5f,
			0.1f,
			0.75f,
		};
		float[] v1 = new float[] {
			6.0f, 6.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.25f, 0.25f,
			0.5f, 0.5f,
			0.1f,
			0.5f,
		};
		float[] v2 = new float[] {
			6.0f, 7.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.25f, 0.5f,
			0.5f, 1.0f,
			0.77f,
			0.0f,
		};
		float[] v3 = new float[] {
			5.0f, 7.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.5f,
			0.0f, 1.0f,
			0.1f,
			0.5f,
		};
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v1, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v3, outFloats, 0.01f);
	}

	@Test
	public void partialShadowFaceWest() throws Throwable
	{
		// Render a face which is should be partially sky-illuminated and partially block-illuminated.
		AbsoluteLocation targetBlock = new AbsoluteLocation(5, 6, 7);
		CuboidData central = CuboidGenerator.createFilledCuboid(targetBlock.getCuboidAddress(), ENV.special.AIR);
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(0, 0, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(0, 1, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(1, 0, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(1, 1, 2).getBlockAddress(), STONE.number());
		central.setData7(AspectRegistry.LIGHT, targetBlock.getRelative(-1, -1, -1).getBlockAddress(), (byte)10);
		MeshInputData data = _createMeshInput(central, null);
		float[] uvBase = new float[] { 0.0f, 0.25f };
		float uvSize = 0.25f;
		float[] auxBase = new float[] { 0.0f, 0.5f };
		float auxSize = 0.5f;
		AlignedFaceBuilder builder = new AlignedFaceBuilder(data
			, uvBase
			, uvSize
			, auxBase
			, auxSize
			, targetBlock
			, AlignedFaceBuilder.Normal.WEST
		);
		
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		BufferBuilder bufferBuilder = new BufferBuilder(buffer, ATTRIBUTES);
		MeshHelperBufferBuilder meshBufferBuilder = new MeshHelperBufferBuilder(bufferBuilder, MeshHelperBufferBuilder.USE_ALL_ATTRIBUTES);
		builder.generateQuad(meshBufferBuilder, new float[] {0.0f, 0.0f, 0.0f}, new float[] {0.0f, 1.0f, 1.0f});
		
		Assert.assertEquals(6 * 12, buffer.position());
		float[] outFloats = new float[buffer.position() / 6];
		buffer.flip();
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// aux_U, aux_V
		// blockLight
		// skyLight
		float[] v0 = new float[] {
			5.0f, 7.0f, 7.0f,
			-1.0f, 0.0f, 0.0f,
			0.25f, 0.25f,
			0.5f, 0.5f,
			0.1f,
			0.5f,
		};
		float[] v1 = new float[] {
			5.0f, 6.0f, 7.0f,
			-1.0f, 0.0f, 0.0f,
			0.0f, 0.25f,
			0.0f, 0.5f,
			0.77f,
			0.5f,
		};
		float[] v2 = new float[] {
			5.0f, 6.0f, 8.0f,
			-1.0f, 0.0f, 0.0f,
			0.0f, 0.5f,
			0.0f, 1.0f,
			0.1f,
			0.5f,
		};
		float[] v3 = new float[] {
			5.0f, 7.0f, 8.0f,
			-1.0f, 0.0f, 0.0f,
			0.25f, 0.5f,
			0.5f, 1.0f,
			0.1f,
			0.5f,
		};
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v1, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v3, outFloats, 0.01f);
	}

	@Test
	public void subBlockFaceSouth() throws Throwable
	{
		// Render a sub-block face which is should be partially sky-illuminated and partially block-illuminated.
		AbsoluteLocation targetBlock = new AbsoluteLocation(5, 6, 7);
		CuboidData central = CuboidGenerator.createFilledCuboid(targetBlock.getCuboidAddress(), ENV.special.AIR);
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(0, 0, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(0, 1, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(1, 0, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(1, 1, 2).getBlockAddress(), STONE.number());
		central.setData7(AspectRegistry.LIGHT, targetBlock.getRelative(-1, -1, -1).getBlockAddress(), (byte)10);
		MeshInputData data = _createMeshInput(central, null);
		float[] uvBase = new float[] { 0.0f, 0.25f };
		float uvSize = 0.25f;
		float[] auxBase = new float[] { 0.0f, 0.5f };
		float auxSize = 0.5f;
		AlignedFaceBuilder builder = new AlignedFaceBuilder(data
			, uvBase
			, uvSize
			, auxBase
			, auxSize
			, targetBlock
			, AlignedFaceBuilder.Normal.SOUTH
		);
		
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		BufferBuilder bufferBuilder = new BufferBuilder(buffer, ATTRIBUTES);
		MeshHelperBufferBuilder meshBufferBuilder = new MeshHelperBufferBuilder(bufferBuilder, MeshHelperBufferBuilder.USE_ALL_ATTRIBUTES);
		builder.generateQuad(meshBufferBuilder, new float[] {0.0f, 1.0f, 0.0f}, new float[] {1.0f, 1.0f, 0.5f});
		
		Assert.assertEquals(6 * 12, buffer.position());
		float[] outFloats = new float[buffer.position() / 6];
		buffer.flip();
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// aux_U, aux_V
		// blockLight
		// skyLight
		float[] v0 = new float[] {
			5.0f, 7.0f, 7.0f,
			0.0f, -1.0f, 0.0f,
			0.0f, 0.25f,
			0.0f, 0.5f,
			0.77f,
			0.5f,
		};
		float[] v1 = new float[] {
			6.0f, 7.0f, 7.0f,
			0.0f, -1.0f, 0.0f,
			0.25f, 0.25f,
			0.5f, 0.5f,
			0.1f,
			0.5f,
		};
		float[] v2 = new float[] {
			6.0f, 7.0f, 7.5f,
			0.0f, -1.0f, 0.0f,
			0.25f, 0.375f,
			0.5f, 0.75f,
			0.1f,
			0.5f,
		};
		float[] v3 = new float[] {
			5.0f, 7.0f, 7.5f,
			0.0f, -1.0f, 0.0f,
			0.0f, 0.375f,
			0.0f, 0.75f,
			0.1f,
			0.5f,
		};
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v1, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v3, outFloats, 0.01f);
	}

	@Test
	public void skyShadow() throws Throwable
	{
		// Block some of the sky and see what the shadow looks like.
		AbsoluteLocation targetBlock = new AbsoluteLocation(5, 6, 7);
		CuboidData central = CuboidGenerator.createFilledCuboid(targetBlock.getCuboidAddress(), ENV.special.AIR);
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(-1, 1, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(0, 1, 2).getBlockAddress(), STONE.number());
		central.setData15(AspectRegistry.BLOCK, targetBlock.getRelative(1, 1, 2).getBlockAddress(), STONE.number());
		MeshInputData data = _createMeshInput(central, null);
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float uvSize = 0.5f;
		float[] auxBase = new float[] { 0.0f, 0.0f };
		float auxSize = 1.0f;
		AlignedFaceBuilder builder = new AlignedFaceBuilder(data
			, uvBase
			, uvSize
			, auxBase
			, auxSize
			, targetBlock
			, AlignedFaceBuilder.Normal.UP
		);
		
		FloatBuffer buffer = FloatBuffer.allocate(4096);
		BufferBuilder bufferBuilder = new BufferBuilder(buffer, ATTRIBUTES);
		MeshHelperBufferBuilder meshBufferBuilder = new MeshHelperBufferBuilder(bufferBuilder, MeshHelperBufferBuilder.USE_ALL_ATTRIBUTES);
		builder.generateQuad(meshBufferBuilder, new float[] {0.0f, 0.0f, 1.0f}, new float[] {1.0f, 1.0f, 1.0f});
		
		Assert.assertEquals(6 * 12, buffer.position());
		float[] outFloats = new float[buffer.position() / 6];
		buffer.flip();
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// aux_U, aux_V
		// blockLight
		// skyLight
		float[] v0 = new float[] {
			5.0f, 6.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.0f,
			0.0f, 0.0f,
			0.1f,
			1.0f,
		};
		float[] v1 = new float[] {
			6.0f, 6.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.5f, 0.0f,
			1.0f, 0.0f,
			0.1f,
			1.0f,
		};
		float[] v2 = new float[] {
			6.0f, 7.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.5f, 0.5f,
			1.0f, 1.0f,
			0.1f,
			0.5f,
		};
		float[] v3 = new float[] {
			5.0f, 7.0f, 8.0f,
			0.0f, 0.0f, 1.0f,
			0.0f, 0.5f,
			0.0f, 1.0f,
			0.1f,
			0.5f,
		};
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v1, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v0, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v2, outFloats, 0.01f);
		
		buffer.get(outFloats);
		Assert.assertArrayEquals(v3, outFloats, 0.01f);
	}


	private static MeshInputData _createMeshInput(CuboidData central, CuboidData north)
	{
		ColumnHeightMap heightMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(central), central.getCuboidAddress()).freeze();
		ColumnHeightMap northHeight = (null != north)
			? ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(north), north.getCuboidAddress()).freeze()
			: null
		;
		return new MeshInputData(central, heightMap
			, null, null
			, null, null
			, north, northHeight
			, null, null
			, null, null
			, null, null
			, new IReadOnlyCuboidData[][][] {
				new IReadOnlyCuboidData[][] {
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
				},
				new IReadOnlyCuboidData[][] {
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[] { null, central, null },
					new IReadOnlyCuboidData[] { null, north, null },
				},
				new IReadOnlyCuboidData[][] {
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
				},
			}
			, new ColumnHeightMap[][] {
				new ColumnHeightMap[] {
					null,
					null,
					null,
				},
				new ColumnHeightMap[] {
					null,
					heightMap,
					northHeight,
				},
				new ColumnHeightMap[] {
					null,
					null,
					null,
				},
			}
		);
	}
}
