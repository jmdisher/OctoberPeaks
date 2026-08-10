package com.jeffdisher.october.peaks.scene;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.BlockAddress;


public class TestWaterSurfaceBuilder
{
	@Test
	public void singleSource() throws Throwable
	{
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder((short)3);
		surface.writeXYPlane((byte)5, (byte)6, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_SOURCE);
		_NormalCounter counter = new _NormalCounter();
		surface.writeVertices(counter);
		Assert.assertEquals(1, counter.up);
		Assert.assertEquals(0, counter.down);
		Assert.assertEquals(0, counter.north);
		Assert.assertEquals(0, counter.south);
		Assert.assertEquals(0, counter.east);
		Assert.assertEquals(0, counter.west);
	}

	@Test
	public void simple() throws Throwable
	{
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder((short)3);
		surface.writeXYPlane((byte)5, (byte)6, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_SOURCE);
		surface.writeXZPlane((byte)5, (byte)6, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_SOURCE);
		surface.writeYZPlane((byte)5, (byte)6, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_SOURCE);
		surface.writeXYPlane((byte)5, (byte)6, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_SOURCE);
		
		surface.writeXYPlane((byte)6, (byte)6, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		surface.writeXZPlane((byte)6, (byte)6, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		surface.writeYZPlane((byte)6, (byte)6, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		surface.writeXYPlane((byte)6, (byte)6, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		
		surface.writeXYPlane((byte)5, (byte)7, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		surface.writeXZPlane((byte)5, (byte)7, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		surface.writeYZPlane((byte)5, (byte)7, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		surface.writeXYPlane((byte)5, (byte)7, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_STRONG);
		
		surface.writeXYPlane((byte)6, (byte)7, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_WEAK);
		surface.writeXZPlane((byte)6, (byte)7, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_WEAK);
		surface.writeYZPlane((byte)6, (byte)7, (byte)7, true, (short)3, WaterSurfaceBuilder.FLOW_BYTE_WEAK);
		surface.writeXYPlane((byte)6, (byte)7, (byte)7, false, (short)3, WaterSurfaceBuilder.FLOW_BYTE_WEAK);
		
		_NormalCounter counter = new _NormalCounter();
		surface.writeVertices(counter);
		Assert.assertEquals(4, counter.up);
		Assert.assertEquals(4, counter.down);
		Assert.assertEquals(2, counter.north);
		Assert.assertEquals(2, counter.south);
		Assert.assertEquals(2, counter.east);
		Assert.assertEquals(2, counter.west);
	}

	@Test
	public void waterFlow() throws Throwable
	{
		// Create some downward flowing water to see what callbacks we get for faces.
		short value = 1;
		byte waterSource = WaterSurfaceBuilder.FLOW_BYTE_SOURCE;
		byte waterStrong = WaterSurfaceBuilder.FLOW_BYTE_STRONG;
		byte waterWeak = WaterSurfaceBuilder.FLOW_BYTE_WEAK;
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder(value);
		
		BlockAddress sourceBlock = new BlockAddress((byte)5, (byte)5, (byte)5);
		BlockAddress flowBlock = new BlockAddress((byte)5, (byte)5, (byte)4);
		BlockAddress bottomBlock = new BlockAddress((byte)5, (byte)5, (byte)3);
		BlockAddress spillBlock = new BlockAddress((byte)5, (byte)6, (byte)3);
		
		// We should see only the external faces but the internal ones will be skipped.
		surface.writeXYPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), true, value, waterSource);
		surface.writeXZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), true, value, waterSource);
		surface.writeXZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), false, value, waterSource);
		surface.writeYZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), true, value, waterSource);
		surface.writeYZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), false, value, waterSource);
		
		surface.writeXZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), true, value, waterWeak);
		surface.writeXZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), false, value, waterWeak);
		surface.writeYZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), true, value, waterWeak);
		surface.writeYZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), false, value, waterWeak);
		
		surface.writeXYPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), false, value, waterStrong);
		surface.writeXZPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), false, value, waterStrong);
		surface.writeYZPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), true, value, waterStrong);
		surface.writeYZPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), false, value, waterStrong);
		
		surface.writeXYPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), true, value, waterWeak);
		surface.writeXYPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), false, value, waterWeak);
		surface.writeXZPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), true, value, waterWeak);
		surface.writeYZPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), true, value, waterWeak);
		surface.writeYZPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), false, value, waterWeak);
		
		int[] counters = new int[6];
		surface.writeVertices(new WaterSurfaceBuilder.IQuadWriter() {
			@Override
			public void writeQuad(BlockAddress address, BlockAddress externalBlock, float[][] counterClockWiseVertices, float[] normal)
			{
				if (1.0f == normal[2])
				{
					// Up.
					counters[0] += 1;
				}
				else if (-1.0f == normal[2])
				{
					// Down.
					counters[1] += 1;
				}
				else if (1.0f == normal[1])
				{
					// North.
					counters[2] += 1;
					if (address.equals(flowBlock))
					{
						// Verify this side to make sure we render the sides of water flows.
						Assert.assertArrayEquals(new float[] {6.0f, 6.0f, 5.0f}, counterClockWiseVertices[0], 0.01f);
						Assert.assertArrayEquals(new float[] {6.0f, 6.0f, 4.0f}, counterClockWiseVertices[1], 0.01f);
						Assert.assertArrayEquals(new float[] {5.0f, 6.0f, 4.0f}, counterClockWiseVertices[2], 0.01f);
						Assert.assertArrayEquals(new float[] {5.0f, 6.0f, 5.0f}, counterClockWiseVertices[3], 0.01f);
					}
				}
				else if (-1.0f == normal[1])
				{
					// South.
					counters[3] += 1;
				}
				else if (1.0f == normal[0])
				{
					// East.
					counters[4] += 1;
				}
				else if (-1.0f == normal[0])
				{
					// West.
					counters[5] += 1;
				}
				else
				{
					// We currently don't use any angular normals.
					Assert.fail();
				}
			}
		});
		Assert.assertEquals(2, counters[0]);
		Assert.assertEquals(2, counters[1]);
		Assert.assertEquals(3, counters[2]);
		Assert.assertEquals(3, counters[3]);
		Assert.assertEquals(4, counters[4]);
		Assert.assertEquals(4, counters[5]);
	}


	private static class _NormalCounter implements WaterSurfaceBuilder.IQuadWriter
	{
		public int up;
		public int down;
		public int north;
		public int south;
		public int east;
		public int west;
		
		@Override
		public void writeQuad(BlockAddress address, BlockAddress externalBlock, float[][] counterClockWiseVertices, float[] normal)
		{
			if (WaterSurfaceBuilder.NORMAL_UP == normal)
			{
				this.up += 1;
			}
			else if (WaterSurfaceBuilder.NORMAL_DOWN == normal)
			{
				this.down += 1;
			}
			else if (WaterSurfaceBuilder.NORMAL_NORTH == normal)
			{
				this.north += 1;
			}
			else if (WaterSurfaceBuilder.NORMAL_SOUTH == normal)
			{
				this.south += 1;
			}
			else if (WaterSurfaceBuilder.NORMAL_EAST == normal)
			{
				this.east += 1;
			}
			else if (WaterSurfaceBuilder.NORMAL_WEST == normal)
			{
				this.west += 1;
			}
			else
			{
				throw new AssertionError("Unknown normal");
			}
		}
	}
}
