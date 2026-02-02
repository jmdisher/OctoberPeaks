package com.jeffdisher.october.peaks.scene;

import com.jeffdisher.october.logic.SparseByteCube;
import com.jeffdisher.october.types.IByteLookup;
import com.jeffdisher.october.utils.Encoding;


/**
 * Contains the SparseByteArray and bit constants for tracking which faces of a block are visible and on fire.
 */
public class FireFaceBuilder
{
	public static final byte FACE_NORTH = 0x1;
	public static final byte FACE_SOUTH = 0x2;
	public static final byte FACE_EAST = 0x4;
	public static final byte FACE_WEST = 0x8;
	public static final byte FACE_UP = 0x10;
	public static final byte FACE_DOWN = 0x20;

	public static boolean isBitSet(byte value, byte flag)
	{
		return (flag == (value & flag));
	}


	private final SparseByteCube _fireFaces = new SparseByteCube(Encoding.CUBOID_EDGE_SIZE);
	private boolean _hasContents;

	/**
	 * Sets the given bit for the underlying cuboid block location (x, y, z), doing nothing if already set.
	 * 
	 * @param x The local x coordinate.
	 * @param y The local y coordinate.
	 * @param z The local z coordinate.
	 * @param bit The bit to set.
	 */
	public void setBit(byte x, byte y, byte z, byte bit)
	{
		byte bits = _fireFaces.get(x, y, z);
		
		// If this is not found, just treat it as an empty bit set.
		if (IByteLookup.NOT_FOUND == bits)
		{
			bits = 0x0;
		}
		
		// Only set the bit if it wasn't set before.
		if (0x0 == (bits & bit))
		{
			bits |= bit;
			_fireFaces.set(x, y, z, bits);
			_hasContents = true;
		}
	}

	/**
	 * Extracts the underlying SparseByteCube if any bits were set by the receiver (otherwise, returns null).
	 * Note that continued interactions with the receiver will have undefined impacts on the returned value after this
	 * call so no further calls should be made.
	 * 
	 * @return The underlying SparseByteCube or null, if nothing was written to it.
	 */
	public SparseByteCube extractNonEmptyCollection()
	{
		return _hasContents
			? _fireFaces
			: null
		;
	}
}
