package com.jeffdisher.october.peaks.scene;

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

	@Test
	public void waterFlow() throws Throwable
	{
		// Create some downward flowing water to see what callbacks we get for faces.
		short waterSource = 3;
		short waterStrong = 2;
		short waterWeak = 1;
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder((Short value) -> true, waterSource, waterStrong, waterWeak);
		
		BlockAddress sourceBlock = new BlockAddress((byte)5, (byte)5, (byte)5);
		BlockAddress flowBlock = new BlockAddress((byte)5, (byte)5, (byte)4);
		BlockAddress bottomBlock = new BlockAddress((byte)5, (byte)5, (byte)3);
		BlockAddress spillBlock = new BlockAddress((byte)5, (byte)6, (byte)3);
		
		// We should see only the external faces but the internal ones will be skipped.
		surface.writeXYPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), true, waterSource);
		surface.writeXZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), true, waterSource);
		surface.writeXZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), false, waterSource);
		surface.writeYZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), true, waterSource);
		surface.writeYZPlane(sourceBlock.x(), sourceBlock.y(), sourceBlock.z(), false, waterSource);
		
		surface.writeXZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), true, waterWeak);
		surface.writeXZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), false, waterWeak);
		surface.writeYZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), true, waterWeak);
		surface.writeYZPlane(flowBlock.x(), flowBlock.y(), flowBlock.z(), false, waterWeak);
		
		surface.writeXYPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), false, waterStrong);
		surface.writeXZPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), false, waterStrong);
		surface.writeYZPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), true, waterStrong);
		surface.writeYZPlane(bottomBlock.x(), bottomBlock.y(), bottomBlock.z(), false, waterStrong);
		
		surface.writeXYPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), true, waterWeak);
		surface.writeXYPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), false, waterWeak);
		surface.writeXZPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), true, waterWeak);
		surface.writeYZPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), true, waterWeak);
		surface.writeYZPlane(spillBlock.x(), spillBlock.y(), spillBlock.z(), false, waterWeak);
		
		_NormalCounter counter = new _NormalCounter();
		surface.writeVertices(counter);
		Assert.assertEquals(2, counter.up);
		Assert.assertEquals(2, counter.down);
		Assert.assertEquals(3, counter.north);
		Assert.assertEquals(3, counter.south);
		Assert.assertEquals(4, counter.east);
		Assert.assertEquals(4, counter.west);
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
