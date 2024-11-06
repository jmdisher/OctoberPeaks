package com.jeffdisher.october.peaks.graphics;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;


/**
 * A helper utility class which walks the block types in a cuboid to determine the minimal surface of opaque or
 * non-opaque blocks by identifying the relevant faces.
 */
public class FaceBuilder
{
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
					// Note that the caller is trying to draw a unit cube at so the edge is already 1 over from the base.
					byte edgeX = (byte)(baseX + size - 1);
					byte edgeY = (byte)(baseY + size - 1);
					byte edgeZ = (byte)(baseZ + size - 1);
					
					// X-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						byte thisZ = (byte)(baseZ + z);
						for (byte y = 0; y < size; ++y)
						{
							byte thisY = (byte)(baseY + y);
							writer.writeYZPlane(baseX, thisY, thisZ, false, value);
							writer.writeYZPlane(edgeX, thisY, thisZ, true, value);
						}
					}
					// Y-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						byte thisZ = (byte)(baseZ + z);
						for (byte x = 0; x < size; ++x)
						{
							byte thisX = (byte)(baseX + x);
							writer.writeXZPlane(thisX, baseY, thisZ, false, value);
							writer.writeXZPlane(thisX, edgeY, thisZ, true, value);
						}
					}
					// Z-normal plane.
					for (byte y = 0; y < size; ++y)
					{
						byte thisY = (byte)(baseY + y);
						for (byte x = 0; x < size; ++x)
						{
							byte thisX = (byte)(baseX + x);
							writer.writeXYPlane(thisX, thisY, baseZ, false, value);
							writer.writeXYPlane(thisX, thisY, edgeZ, true, value);
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
}
