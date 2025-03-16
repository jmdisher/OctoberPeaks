package com.jeffdisher.october.peaks.scene;

import java.util.function.Predicate;

import com.jeffdisher.october.peaks.graphics.FaceBuilder;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


/**
 * The water surface is the only cube-built part of the world mesh which has dynamic surface shape so we need to listen
 * to the face callbacks for an entire cuboid to find the surface before we can actually build the mesh.
 * 
 * This is somewhat complicated:
 * -we store each Z-layer (since water surfaces are typically horizontal) as a single array for x/y values
 * -we will store a value in every slot where a surface exists, based on the strength of flow there
 * -we will store a special value for vertical surfaces (and bottom) as other flags in the byte as a bitfield
 * -when we build the mesh, we will look at the 3x3 grid around each block to determine the height of the water on each
 *  of the four corners
 */
public class WaterSurfaceBuilder implements FaceBuilder.IWriter
{
	/**
	 * We need to reserve an extra 2 spots so we can peek into the neighbouring cuboids.
	 */
	public static final int EDGE_SIZE = Encoding.CUBOID_EDGE_SIZE + 2;
	public static final int LAYER_SIZE = EDGE_SIZE * EDGE_SIZE;
	public static final float QUAD_HEIGHT_SOURCE = 0.9f;
	public static final float QUAD_HEIGHT_STRONG = 0.5f;
	public static final float QUAD_HEIGHT_WEAK = 0.1f;

	// Note that the up-normal will need to be dynamically made to be completely correct.
	public static final float[] NORMAL_UP = new float[] { 0.0f, 0.0f, 1.0f };
	public static final float[] NORMAL_DOWN = new float[] { 0.0f, 0.0f, -1.0f };
	public static final float[] NORMAL_NORTH = new float[] { 0.0f, 1.0f, 0.0f };
	public static final float[] NORMAL_SOUTH = new float[] { 0.0f, -1.0f, 0.0f };
	public static final float[] NORMAL_EAST = new float[] { 1.0f, 0.0f, 0.0f };
	public static final float[] NORMAL_WEST = new float[] { -1.0f, 0.0f, 0.0f };

	public static final byte BYTE_MASK_TYPE = 0x3;
	public static final byte BYTE_NONE = 0x0;
	public static final byte BYTE_SOURCE = 0x1;
	public static final byte BYTE_STRONG = 0x2;
	public static final byte BYTE_WEAK = 0x3;

	public static final byte BYTE_MASK_OTHER = 0x7C;
	public static final byte BYTE_NORTH = 0x4;
	public static final byte BYTE_SOUTH = 0x8;
	public static final byte BYTE_EAST = 0x10;
	public static final byte BYTE_WEST = 0x20;
	public static final byte BYTE_DOWN = 0x40;

	private final Predicate<Short> _shouldInclude;
	private final short _valueSource;
	private final short _valueStrong;
	private final short _valueWeak;
	private final byte[][] _zLayers;

	public WaterSurfaceBuilder(Predicate<Short> shouldInclude
			, short valueSource
			, short valueStrong
			, short valueWeak
	)
	{
		_shouldInclude = shouldInclude;
		_valueSource = valueSource;
		_valueStrong = valueStrong;
		_valueWeak = valueWeak;
		_zLayers = new byte[EDGE_SIZE][];
	}

	@Override
	public boolean shouldInclude(short value)
	{
		return _shouldInclude.test(value);
	}

	@Override
	public void writeXYPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
	{
		byte toWrite;
		if (isPositiveNormal)
		{
			if (_valueSource == value)
			{
				toWrite = BYTE_SOURCE;
			}
			else if (_valueStrong == value)
			{
				toWrite = BYTE_STRONG;
			}
			else if (_valueWeak == value)
			{
				toWrite = BYTE_WEAK;
			}
			else
			{
				// We are only expecting water flow values here.
				throw Assert.unreachable();
			}
		}
		else
		{
			toWrite = BYTE_DOWN;
		}
		_writeValue(baseX, baseY, baseZ, toWrite);
	}

	@Override
	public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
	{
		byte toWrite = isPositiveNormal ? BYTE_NORTH : BYTE_SOUTH;
		_writeValue(baseX, baseY, baseZ, toWrite);
	}

	@Override
	public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
	{
		byte toWrite = isPositiveNormal ? BYTE_EAST : BYTE_WEST;
		_writeValue(baseX, baseY, baseZ, toWrite);
	}

	public void setEdgeValue(byte baseX, byte baseY, byte baseZ, short value)
	{
		byte toWrite;
		if (_valueSource == value)
		{
			toWrite = BYTE_SOURCE;
		}
		else if (_valueStrong == value)
		{
			toWrite = BYTE_STRONG;
		}
		else if (_valueWeak == value)
		{
			toWrite = BYTE_WEAK;
		}
		else
		{
			// We are only expecting water flow values here.
			throw Assert.unreachable();
		}
		_writeValue(baseX, baseY, baseZ, toWrite);
	}

	public void writeVertices(IQuadWriter writer)
	{
		for (byte z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
		{
			// Adjust for the extra z-value for the down peek.
			byte[] layer = _zLayers[z + 1];
			byte[] above = _zLayers[z + 2];
			if (null != layer)
			{
				for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
				{
					for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
					{
						byte value = layer[_getIndex(x, y)];
						if (BYTE_NONE != value)
						{
							BlockAddress address = new BlockAddress(x, y, z);
							byte north = (byte)(y + 1);
							byte south = (byte)(y - 1);
							byte east = (byte)(x + 1);
							byte west = (byte)(x - 1);
							boolean liquidAbove = ((null != above) && (BYTE_NONE != above[_getIndex(x, y)]));
							float hC = liquidAbove
									? 1.0f
									: _mapToHeight(value)
							;
							float hN = _getSurfaceHeight(layer, above, x, north, hC);
							float hNE = _getSurfaceHeight(layer, above, east, north, hC);
							float hE = _getSurfaceHeight(layer, above, east, y, hC);
							float hSE = _getSurfaceHeight(layer, above, east, south, hC);
							float hS = _getSurfaceHeight(layer, above, x, south, hC);
							float hSW = _getSurfaceHeight(layer, above, west, south, hC);
							float hW = _getSurfaceHeight(layer, above, west, y, hC);
							float hNW = _getSurfaceHeight(layer, above, west, north, hC);
							
							float topLeft = Math.max(Math.max(hNW, hN), Math.max(hW, hC));
							float bottomLeft = Math.max(Math.max(hW, hC), Math.max(hSW, hS));
							float bottomRight = Math.max(Math.max(hC, hE), Math.max(hS, hSE));
							float topRight = Math.max(Math.max(hN, hNE), Math.max(hC, hE));
							
							if (!liquidAbove && (hC > 0.0f))
							{
								float[][] vertices = new float[][] {
									{(float)x, (float)y + 1.0f, (float)z + topLeft},
									{(float)x, (float)y, (float)z + bottomLeft},
									{(float)x + 1.0f, (float)y, (float)z + bottomRight},
									{(float)x + 1.0f, (float)y + 1.0f, (float)z + topRight},
								};
								writer.writeQuad(address
										, new BlockAddress(x, y, (byte)(z + 1))
										, vertices
										, NORMAL_UP
								);
							}
							
							if (0 != (value & BYTE_NORTH))
							{
								float[][] vertices = new float[][] {
									{(float)x + 1.0f, (float)y + 1.0f, (float)z + topRight},
									{(float)x + 1.0f, (float)y + 1.0f, (float)z},
									{(float)x, (float)y + 1.0f, (float)z},
									{(float)x, (float)y + 1.0f, (float)z + topLeft},
								};
								writer.writeQuad(address
										, new BlockAddress(x, (byte)(y + 1), z)
										, vertices
										, NORMAL_NORTH
								);
							}
							if (0 != (value & BYTE_SOUTH))
							{
								float[][] vertices = new float[][] {
									{(float)x, (float)y, (float)z + bottomLeft},
									{(float)x, (float)y, (float)z},
									{(float)x + 1.0f, (float)y, (float)z},
									{(float)x + 1.0f, (float)y, (float)z + bottomRight},
								};
								writer.writeQuad(address
										, new BlockAddress(x, (byte)(y - 1), z)
										, vertices
										, NORMAL_SOUTH
								);
							}
							if (0 != (value & BYTE_EAST))
							{
								float[][] vertices = new float[][] {
									{(float)x + 1.0f, (float)y, (float)z + bottomRight},
									{(float)x + 1.0f, (float)y, (float)z},
									{(float)x + 1.0f, (float)y + 1.0f, (float)z},
									{(float)x + 1.0f, (float)y + 1.0f, (float)z + topRight},
								};
								writer.writeQuad(address
										, new BlockAddress((byte)(x + 1), y, z)
										, vertices
										, NORMAL_EAST
								);
							}
							if (0 != (value & BYTE_WEST))
							{
								float[][] vertices = new float[][] {
									{(float)x, (float)y + 1.0f, (float)z + topLeft},
									{(float)x, (float)y + 1.0f, (float)z},
									{(float)x, (float)y, (float)z},
									{(float)x, (float)y, (float)z + bottomLeft},
								};
								writer.writeQuad(address
										, new BlockAddress((byte)(x - 1), y, z)
										, vertices
										, NORMAL_WEST
								);
							}
							if (0 != (value & BYTE_DOWN))
							{
								float[][] vertices = new float[][] {
									{(float)x + 1.0f, (float)y + 1.0f, (float)z},
									{(float)x + 1.0f, (float)y, (float)z},
									{(float)x, (float)y, (float)z},
									{(float)x, (float)y + 1.0f, (float)z},
								};
								writer.writeQuad(address
										, new BlockAddress(x, y, (byte)(z - 1))
										, vertices
										, NORMAL_DOWN
								);
							}
						}
					}
				}
			}
		}
	}


	private void _writeValue(byte baseX, byte baseY, byte baseZ, byte value)
	{
		int index = _getIndex(baseX, baseY);
		byte[] layer = _zLayers[baseZ + 1];
		if (null == layer)
		{
			layer = new byte[LAYER_SIZE];
			_zLayers[baseZ + 1] = layer;
		}
		byte old = layer[index];
		layer[index] = (byte)(old | value);
	}

	private static float _getSurfaceHeight(byte[] layer, byte[] above, byte x, byte y, float missingHeight)
	{
		float height;
		if ((null != above) && (BYTE_NONE != above[_getIndex(x, y)]))
		{
			height = 1.0f;
		}
		else
		{
			byte value = layer[_getIndex(x, y)];
			height = _mapToHeight(value);
		}
		return height;
	}

	private static float _mapToHeight(byte value)
	{
		float height;
		switch (BYTE_MASK_TYPE & value)
		{
		case BYTE_NONE:
			height = 0.0f;
			break;
		case BYTE_SOURCE:
			height = QUAD_HEIGHT_SOURCE;
			break;
		case BYTE_STRONG:
			height = QUAD_HEIGHT_STRONG;
			break;
		case BYTE_WEAK:
			height = QUAD_HEIGHT_WEAK;
			break;
			default:
				// Not a valid value.
				throw Assert.unreachable();
		}
		return height;
	}

	private static int _getIndex(byte x, byte y)
	{
		return (y + 1) * Encoding.CUBOID_EDGE_SIZE + (x + 1);
	}


	public static interface IQuadWriter
	{
		public void writeQuad(BlockAddress address
				, BlockAddress externalBlock
				, float[][] counterClockWiseVertices
				, float[] normal
		);
	}
}
