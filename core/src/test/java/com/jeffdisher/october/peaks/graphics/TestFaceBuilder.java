package com.jeffdisher.october.peaks.graphics;

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
import com.jeffdisher.october.worldgen.CuboidGenerator;


public class TestFaceBuilder
{
	private static Environment ENV;
	private static Item STONE_ITEM;
	@BeforeClass
	public static void setup()
	{
		ENV = Environment.createSharedInstance();
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
}
