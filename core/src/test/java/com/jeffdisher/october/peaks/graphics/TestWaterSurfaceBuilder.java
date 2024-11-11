package com.jeffdisher.october.peaks.graphics;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.BlockAddress;



public class TestWaterSurfaceBuilder
{
	@Test
	public void singleSource() throws Throwable
	{
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder((Short value) -> true, (short)3, (short)2, (short)1);
		surface.writeXYPlane((byte)5, (byte)6, (byte)7, true, (short)3);
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
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder((Short value) -> true, (short)3, (short)2, (short)1);
		surface.writeXYPlane((byte)5, (byte)6, (byte)7, true, (short)3);
		surface.writeXZPlane((byte)5, (byte)6, (byte)7, false, (short)3);
		surface.writeYZPlane((byte)5, (byte)6, (byte)7, false, (short)3);
		surface.writeXYPlane((byte)5, (byte)6, (byte)7, false, (short)3);
		
		surface.writeXYPlane((byte)6, (byte)6, (byte)7, true, (short)2);
		surface.writeXZPlane((byte)6, (byte)6, (byte)7, false, (short)2);
		surface.writeYZPlane((byte)6, (byte)6, (byte)7, true, (short)2);
		surface.writeXYPlane((byte)6, (byte)6, (byte)7, false, (short)2);
		
		surface.writeXYPlane((byte)5, (byte)7, (byte)7, true, (short)2);
		surface.writeXZPlane((byte)5, (byte)7, (byte)7, true, (short)2);
		surface.writeYZPlane((byte)5, (byte)7, (byte)7, false, (short)2);
		surface.writeXYPlane((byte)5, (byte)7, (byte)7, false, (short)2);
		
		surface.writeXYPlane((byte)6, (byte)7, (byte)7, true, (short)1);
		surface.writeXZPlane((byte)6, (byte)7, (byte)7, true, (short)1);
		surface.writeYZPlane((byte)6, (byte)7, (byte)7, true, (short)1);
		surface.writeXYPlane((byte)6, (byte)7, (byte)7, false, (short)1);
		
		_NormalCounter counter = new _NormalCounter();
		surface.writeVertices(counter);
		Assert.assertEquals(4, counter.up);
		Assert.assertEquals(4, counter.down);
		Assert.assertEquals(2, counter.north);
		Assert.assertEquals(2, counter.south);
		Assert.assertEquals(2, counter.east);
		Assert.assertEquals(2, counter.west);
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
