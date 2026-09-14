package com.jeffdisher.october.peaks.scene;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Encoding;


/**
 * Helpers related to reading and interpreting block and sky light values from cuboids around a mesh being constructed.
 */
public class LightReadingHelpers
{
	/**
	 * The light value we will see for block light in the case of "total darkness".  Actual block light is added on top
	 * of this.
	 */
	public static final float MINIMUM_LIGHT = 0.1f;
	public static final float SKY_LIGHT_SHADOW = 0.0f;
	public static final float SKY_LIGHT_PARTIAL = 0.5f;
	public static final float SKY_LIGHT_DIRECT = 1.0f;

	/**
	 * Gets the block light value of the given relative offsets into the central cuboid as a byte value.  Returns 0 if
	 * this is not in the central cuboid and it isn't loaded.
	 * 
	 * @param data The relevant cuboid and height map data used in generating the current mesh.
	 * @param baseX The local x-offset from the central cuboid.
	 * @param baseY The local y-offset from the central cuboid.
	 * @param baseZ The local z-offset from the central cuboid.
	 * @return The block light value (0 if not loaded).
	 */
	public static byte getBlockLight(MeshInputData data, byte baseX, byte baseY, byte baseZ)
	{
		int indexX = 1;
		int indexY = 1;
		int indexZ = 1;
		
		if (baseX < 0)
		{
			baseX = (byte)(baseX + Encoding.CUBOID_EDGE_SIZE);
			indexX -= 1;
		}
		else if (baseX >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseX = (byte)(baseX - Encoding.CUBOID_EDGE_SIZE);
			indexX += 1;
		}
		
		if (baseY < 0)
		{
			baseY = (byte)(baseY + Encoding.CUBOID_EDGE_SIZE);
			indexY -= 1;
		}
		else if (baseY >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseY = (byte)(baseY - Encoding.CUBOID_EDGE_SIZE);
			indexY += 1;
		}
		
		if (baseZ < 0)
		{
			baseZ = (byte)(baseZ + Encoding.CUBOID_EDGE_SIZE);
			indexZ -= 1;
		}
		else if (baseZ >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseZ = (byte)(baseZ - Encoding.CUBOID_EDGE_SIZE);
			indexZ += 1;
		}
		
		IReadOnlyCuboidData toRead = data.cuboidsXYZ()[indexX][indexY][indexZ];
		return (null != toRead)
			? toRead.getData7(AspectRegistry.LIGHT, new BlockAddress(baseX, baseY, baseZ))
			: 0
		;
	}

	/**
	 * Checks the sky light value shining directly down onto the block with the given local coordatines.
	 * 
	 * @param data The relevant cuboid and height map data used in generating the current mesh.
	 * @param baseX The local x-offset from the central cuboid.
	 * @param baseY The local y-offset from the central cuboid.
	 * @param baseZ The local z-offset from the central cuboid.
	 * @return The light intensity, either being direct or shadow, as a float in the range [0.0..1.0].
	 */
	public static float getUpFacingSkyMultipler(MeshInputData data, byte baseX, byte baseY, byte baseZ)
	{
		int indexX = 1;
		int indexY = 1;
		
		if (baseX < 0)
		{
			baseX = (byte)(baseX + Encoding.CUBOID_EDGE_SIZE);
			indexX -= 1;
		}
		else if (baseX >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseX = (byte)(baseX - Encoding.CUBOID_EDGE_SIZE);
			indexX += 1;
		}
		
		if (baseY < 0)
		{
			baseY = (byte)(baseY + Encoding.CUBOID_EDGE_SIZE);
			indexY -= 1;
		}
		else if (baseY >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseY = (byte)(baseY - Encoding.CUBOID_EDGE_SIZE);
			indexY += 1;
		}
		
		ColumnHeightMap toRead = data.columnHeightXY()[indexX][indexY];
		int realZ = data.cuboid().getCuboidAddress().getBase().z() + baseZ - 1;
		
		boolean isLit;
		if (null != toRead)
		{
			isLit = (realZ >= toRead.getHeight(baseX, baseY));
		}
		else
		{
			isLit = true;
		}
		
		return isLit
			? SKY_LIGHT_DIRECT
			: SKY_LIGHT_SHADOW
		;
	}

	/**
	 * Checks the sky light value shining onto a block with the given local coordinates, returning aboveOrMatchLight if
	 * it is in the light or SKY_LIGHT_SHADOW if it is in the shadow.
	 * 
	 * @param data The relevant cuboid and height map data used in generating the current mesh.
	 * @param baseX The local x-offset from the central cuboid.
	 * @param baseY The local y-offset from the central cuboid.
	 * @param baseZ The local z-offset from the central cuboid.
	 * @param aboveOrMatchLight The value to return if this block is lit.
	 * @return The light intensity, either aboveOrMatchLight or SKY_LIGHT_SHADOW.
	 */
	public static float getSkyLightMultiplier(MeshInputData data, byte baseX, byte baseY, byte baseZ, float aboveOrMatchLight)
	{
		int realZ = data.cuboid().getCuboidAddress().getBase().z() + baseZ - 1;
		
		boolean isLit;
		if (baseX < 0)
		{
			if (null != data.westHeight())
			{
				isLit = (realZ >= data.westHeight().getHeight(baseX + Encoding.CUBOID_EDGE_SIZE, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseX >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.eastHeight())
			{
				isLit = (realZ >= data.eastHeight().getHeight(baseX - Encoding.CUBOID_EDGE_SIZE, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseY < 0)
		{
			if (null != data.southHeight())
			{
				isLit = (realZ >= data.southHeight().getHeight(baseX, baseY + Encoding.CUBOID_EDGE_SIZE));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseY >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.northHeight())
			{
				isLit = (realZ >= data.northHeight().getHeight(baseX, baseY - Encoding.CUBOID_EDGE_SIZE));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseZ < 0)
		{
			if (null != data.downHeight())
			{
				isLit = (realZ >= data.downHeight().getHeight(baseX, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseZ >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.upHeight())
			{
				isLit = (realZ >= data.upHeight().getHeight(baseX, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else
		{
			isLit = (realZ >= data.height().getHeight(baseX, baseY));
		}
		return isLit
			? aboveOrMatchLight
			: SKY_LIGHT_SHADOW
		;
	}
}
