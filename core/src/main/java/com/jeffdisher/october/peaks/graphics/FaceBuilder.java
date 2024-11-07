package com.jeffdisher.october.peaks.graphics;

import java.util.function.Predicate;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Encoding;


/**
 * A helper utility class which walks the block types in a cuboid to determine the minimal surface of opaque or
 * non-opaque blocks by identifying the relevant faces.
 */
public class FaceBuilder
{
	private final _ColumnBits _xyColumn = new _ColumnBits();
	private final _ColumnBits _xzColumn = new _ColumnBits();
	private final _ColumnBits _yzColumn = new _ColumnBits();

	public void populateMasks(IReadOnlyCuboidData cuboid, Predicate<Short> shouldInclude)
	{
		cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<Short>() {
			@Override
			public void visit(BlockAddress base, byte size, Short object)
			{
				if (shouldInclude.test(object))
				{
					byte baseX = base.x();
					byte baseY = base.y();
					byte baseZ = base.z();
					byte edgeX = (byte)(baseX + size);
					byte edgeY = (byte)(baseY + size);
					byte edgeZ = (byte)(baseZ + size);
					
					// X-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						byte thisZ = (byte)(baseZ + z);
						for (byte y = 0; y < size; ++y)
						{
							byte thisY = (byte)(baseY + y);
							_yzColumn.toggle(thisY, thisZ, baseX);
							_yzColumn.toggle(thisY, thisZ, edgeX);
						}
					}
					// Y-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						byte thisZ = (byte)(baseZ + z);
						for (byte x = 0; x < size; ++x)
						{
							byte thisX = (byte)(baseX + x);
							_xzColumn.toggle(thisX, thisZ, baseY);
							_xzColumn.toggle(thisX, thisZ, edgeY);
						}
					}
					// Z-normal plane.
					for (byte y = 0; y < size; ++y)
					{
						byte thisY = (byte)(baseY + y);
						for (byte x = 0; x < size; ++x)
						{
							byte thisX = (byte)(baseX + x);
							_xyColumn.toggle(thisX, thisY, baseZ);
							_xyColumn.toggle(thisX, thisY, edgeZ);
						}
					}
				}
			}
		}, (short)0);
	}

	public void buildFaces(IReadOnlyCuboidData cuboid, IWriter writer)
	{
		cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<Short>() {
			@Override
			public void visit(BlockAddress base, byte size, Short object)
			{
				short value = object;
				if (writer.shouldInclude(value))
				{
					byte baseX = base.x();
					byte baseY = base.y();
					byte baseZ = base.z();
					byte edgeX = (byte)(baseX + size);
					byte edgeY = (byte)(baseY + size);
					byte edgeZ = (byte)(baseZ + size);
					
					// X-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						byte thisZ = (byte)(baseZ + z);
						for (byte y = 0; y < size; ++y)
						{
							byte thisY = (byte)(baseY + y);
							if (_yzColumn.get(thisY, thisZ, baseX))
							{
								writer.writeYZPlane(baseX, thisY, thisZ, false, value);
							}
							if (_yzColumn.get(thisY, thisZ, edgeX))
							{
								// Note that the caller is trying to draw a unit cube at so the edge is already 1 over from the base.
								writer.writeYZPlane((byte)(edgeX - 1), thisY, thisZ, true, value);
							}
						}
					}
					// Y-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						byte thisZ = (byte)(baseZ + z);
						for (byte x = 0; x < size; ++x)
						{
							byte thisX = (byte)(baseX + x);
							if (_xzColumn.get(thisX, thisZ, baseY))
							{
								writer.writeXZPlane(thisX, baseY, thisZ, false, value);
							}
							if (_xzColumn.get(thisX, thisZ, edgeY))
							{
								// Note that the caller is trying to draw a unit cube at so the edge is already 1 over from the base.
								writer.writeXZPlane(thisX, (byte)(edgeY - 1), thisZ, true, value);
							}
						}
					}
					// Z-normal plane.
					for (byte y = 0; y < size; ++y)
					{
						byte thisY = (byte)(baseY + y);
						for (byte x = 0; x < size; ++x)
						{
							byte thisX = (byte)(baseX + x);
							if (_xyColumn.get(thisX, thisY, baseZ))
							{
								writer.writeXYPlane(thisX, thisY, baseZ, false, value);
							}
							if (_xyColumn.get(thisX, thisY, edgeZ))
							{
								// Note that the caller is trying to draw a unit cube at so the edge is already 1 over from the base.
								writer.writeXYPlane(thisX, thisY, (byte)(edgeZ - 1), true, value);
							}
						}
					}
				}
			}
		}, (short)0);
	}


	public interface IWriter
	{
		boolean shouldInclude(short value);
		void writeXYPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value);
		void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value);
		void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value);
	}

	private class _ColumnBits
	{
		private byte[][][] _bits;
		public void toggle(byte one, byte two, byte three)
		{
			if (null == _bits)
			{
				_bits = new byte[Encoding.CUBOID_EDGE_SIZE][][];
			}
			byte[][] c1 = _bits[one];
			if (null == c1)
			{
				c1 = new byte[Encoding.CUBOID_EDGE_SIZE][];
				_bits[one] = c1;
			}
			byte[] c2 = c1[two];
			if (null == c2)
			{
				c2 = new byte[Encoding.CUBOID_EDGE_SIZE + 1];
				c1[two] = c2;
			}
			int byteOffset = three / 8;
			int bitOffset = three % 8;
			byte mask = (byte)(0x1 << bitOffset);
			c2[byteOffset] = (byte)(c2[byteOffset] ^ mask);
		}
		public boolean get(byte one, byte two, byte three)
		{
			boolean value = false;
			if (null != _bits)
			{
				byte[][] c1 = _bits[one];
				if (null != c1)
				{
					byte[] c2 = c1[two];
					if (null != c2)
					{
						int byteOffset = three / 8;
						int bitOffset = three % 8;
						byte mask = (byte)(0x1 << bitOffset);
						value = (0 != (byte)(c2[byteOffset] & mask));
					}
				}
			}
			return value;
		}
	}
}
