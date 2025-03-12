package com.jeffdisher.october.peaks.graphics;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;


public class TestFaceBuilder
{
	private static Environment ENV;
	private static Item DIRT_ITEM;
	private static Item STONE_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
		DIRT_ITEM = ENV.items.getItemById("op.dirt");
		STONE_ITEM = ENV.items.getItemById("op.stone");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void singleBlock() throws Throwable
	{
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		BlockAddress blockAddress = new BlockAddress((byte)1, (byte)2, (byte)3);
		cuboid.setData15(AspectRegistry.BLOCK, blockAddress, STONE_ITEM.number());
		int[] counts = new int[3];
		FaceBuilder builder = new FaceBuilder();
		builder.populateMasks(cuboid, (Short value) -> true);
		builder.buildFaces(cuboid, new FaceBuilder.IWriter() {
			@Override
			public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
			{
				counts[0] += 1;
				Assert.assertEquals(blockAddress, new BlockAddress(baseX, baseY, baseZ));
			}
			@Override
			public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
			{
				counts[1] += 1;
				Assert.assertEquals(blockAddress, new BlockAddress(baseX, baseY, baseZ));
			}
			@Override
			public void writeXYPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
			{
				counts[2] += 1;
				Assert.assertEquals(blockAddress, new BlockAddress(baseX, baseY, baseZ));
			}
			@Override
			public boolean shouldInclude(short value)
			{
				Assert.assertEquals(STONE_ITEM.number(), value);
				return true;
			}
		});
		
		Assert.assertArrayEquals(new int[] { 2, 2, 2 }, counts);
	}

	@Test
	public void twoBlocks() throws Throwable
	{
		// Draw 2 blocks which should share a single face.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)1, (byte)2, (byte)3), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)1, (byte)2, (byte)4), DIRT_ITEM.number());
		FaceBuilder builder = new FaceBuilder();
		builder.populateMasks(cuboid, (Short value) -> true);
		_CountingWriter counter = new _CountingWriter();
		builder.buildFaces(cuboid, counter);
		
		Assert.assertEquals(2, counter.xy);
		Assert.assertEquals(4, counter.xz);
		Assert.assertEquals(4, counter.yz);
	}

	@Test
	public void largerBlock() throws Throwable
	{
		// Draw 2 blocks which should share a single face.
		CuboidData cuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)2, (byte)2), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)2, (byte)3), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)3, (byte)2), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)3, (byte)3), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)2, (byte)2), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)2, (byte)3), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)3, (byte)2), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)3, (byte)3), STONE_ITEM.number());
		cuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)3, (byte)4), DIRT_ITEM.number());
		FaceBuilder builder = new FaceBuilder();
		builder.populateMasks(cuboid, (Short value) -> true);
		_CountingWriter counter = new _CountingWriter();
		builder.buildFaces(cuboid, counter);
		
		Assert.assertEquals(8, counter.xy);
		Assert.assertEquals(10, counter.xz);
		Assert.assertEquals(10, counter.yz);
	}

	@Test
	public void largeBlockOnBoundary() throws Throwable
	{
		// Create 2 cuboids, each with blocks around the boundary to see that the shared face is still detected.
		CuboidData lowCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)0), ENV.special.AIR);
		CuboidData highCuboid = CuboidGenerator.createFilledCuboid(new CuboidAddress((short)0, (short)0, (short)1), ENV.special.AIR);
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)2, (byte)30), STONE_ITEM.number());
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)2, (byte)31), STONE_ITEM.number());
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)3, (byte)30), STONE_ITEM.number());
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)2, (byte)3, (byte)31), STONE_ITEM.number());
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)2, (byte)30), STONE_ITEM.number());
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)2, (byte)31), STONE_ITEM.number());
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)3, (byte)30), STONE_ITEM.number());
		lowCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)3, (byte)31), STONE_ITEM.number());
		highCuboid.setData15(AspectRegistry.BLOCK, new BlockAddress((byte)3, (byte)3, (byte)0), DIRT_ITEM.number());
		
		byte omit = -1;
		byte zero = 0;
		byte edge = Encoding.CUBOID_EDGE_SIZE;
		Predicate<Short> always = (Short value) -> true;
		
		FaceBuilder builder = new FaceBuilder();
		builder.preSeedMasks(highCuboid, always, zero, omit, omit, omit, omit, omit);
		builder.populateMasks(lowCuboid, always);
		_CountingWriter counter = new _CountingWriter();
		builder.buildFaces(lowCuboid, counter);
		Assert.assertEquals(7, counter.xy);
		Assert.assertEquals(8, counter.xz);
		Assert.assertEquals(8, counter.yz);
		
		builder = new FaceBuilder();
		builder.preSeedMasks(lowCuboid, always, omit, edge, omit, omit, omit, omit);
		builder.populateMasks(highCuboid, always);
		counter = new _CountingWriter();
		builder.buildFaces(highCuboid, counter);
		Assert.assertEquals(1, counter.xy);
		Assert.assertEquals(2, counter.xz);
		Assert.assertEquals(2, counter.yz);
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
		
		Set<BlockAddress> positiveYZ = new HashSet<>();
		Set<BlockAddress> negativeYZ = new HashSet<>();
		Set<BlockAddress> positiveXZ = new HashSet<>();
		Set<BlockAddress> negativeXZ = new HashSet<>();
		Set<BlockAddress> positiveXY = new HashSet<>();
		Set<BlockAddress> negativeXY = new HashSet<>();
		FaceBuilder builder = new FaceBuilder();
		builder.populateMasks(cuboid, (Short value) -> true);
		builder.buildFaces(cuboid, new FaceBuilder.IWriter() {
			@Override
			public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
			{
				Assert.assertTrue((value == waterSource.number()) || (value == waterStrong.number()) || (value == waterWeak.number()));
				BlockAddress address = new BlockAddress(baseX, baseY, baseZ);
				if (isPositiveNormal)
				{
					Assert.assertTrue(positiveYZ.add(address));
				}
				else
				{
					Assert.assertTrue(negativeYZ.add(address));
				}
			}
			@Override
			public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
			{
				Assert.assertTrue((value == waterSource.number()) || (value == waterStrong.number()) || (value == waterWeak.number()));
				BlockAddress address = new BlockAddress(baseX, baseY, baseZ);
				if (isPositiveNormal)
				{
					Assert.assertTrue(positiveXZ.add(address));
				}
				else
				{
					Assert.assertTrue(negativeXZ.add(address));
				}
			}
			@Override
			public void writeXYPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
			{
				Assert.assertTrue((value == waterSource.number()) || (value == waterStrong.number()) || (value == waterWeak.number()));
				BlockAddress address = new BlockAddress(baseX, baseY, baseZ);
				if (isPositiveNormal)
				{
					Assert.assertTrue(positiveXY.add(address));
				}
				else
				{
					Assert.assertTrue(negativeXY.add(address));
				}
			}
			@Override
			public boolean shouldInclude(short value)
			{
				Assert.assertTrue((value == waterSource.number()) || (value == waterStrong.number()) || (value == waterWeak.number()));
				return true;
			}
		});
		// We should see only the external faces but the internal ones will be skipped.
		Assert.assertEquals(4, positiveYZ.size());
		Assert.assertEquals(4, negativeYZ.size());
		Assert.assertEquals(3, positiveXZ.size());
		Assert.assertEquals(3, negativeXZ.size());
		Assert.assertEquals(2, positiveXY.size());
		Assert.assertEquals(2, negativeXY.size());
	}


	private static class _CountingWriter implements FaceBuilder.IWriter
	{
		public int yz;
		public int xz;
		public int xy;
		
		@Override
		public boolean shouldInclude(short value)
		{
			return true;
		}
		@Override
		public void writeXYPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			this.xy += 1;
		}
		@Override
		public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			this.xz += 1;
		}
		@Override
		public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			this.yz += 1;
		}
	}
}
