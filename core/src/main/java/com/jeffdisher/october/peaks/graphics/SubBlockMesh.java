package com.jeffdisher.october.peaks.graphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jeffdisher.october.peaks.scene.AlignedFaceBuilder;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.SubBlock;


/**
 * Converts a sub-block mask into a mesh and then stores it.
 */
public class SubBlockMesh
{
	/**
	 * The blocks we are dealing with are within the 0..1 range but the rotation we applied assumes that the vertices
	 * are rotating about the origin so this bias adjusts it.  We store the biased values to avoid redundant
	 * calculation.
	 */
	public static float FACE_BIAS = 0.5f;

	/**
	 * In order to access the correct faces, we need to determine what requested face resolves to under each kind of rotation.
	 */
	public static final Map<FacingDirection, Map<AlignedFaceBuilder.Normal, AlignedFaceBuilder.Normal>> RUBIK
		= Map.of(FacingDirection.NORTH, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.DOWN
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.UP
		)
		, FacingDirection.WEST, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.DOWN
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.UP
		)
		, FacingDirection.SOUTH, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.DOWN
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.UP
		)
		, FacingDirection.EAST, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.DOWN
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.UP
		)
		, FacingDirection.DOWN, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.UP
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.DOWN
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.SOUTH
		)
		, FacingDirection.UP, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.DOWN
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.UP
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.NORTH
		)
		, FacingDirection.FLIPPED_NORTH, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.UP
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.DOWN
		)
		, FacingDirection.FLIPPED_WEST, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.UP
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.DOWN
		)
		, FacingDirection.FLIPPED_SOUTH, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.UP
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.DOWN
		)
		, FacingDirection.FLIPPED_EAST, Map.of(AlignedFaceBuilder.Normal.NORTH, AlignedFaceBuilder.Normal.WEST
			, AlignedFaceBuilder.Normal.WEST, AlignedFaceBuilder.Normal.SOUTH
			, AlignedFaceBuilder.Normal.SOUTH, AlignedFaceBuilder.Normal.EAST
			, AlignedFaceBuilder.Normal.EAST, AlignedFaceBuilder.Normal.NORTH
			, AlignedFaceBuilder.Normal.DOWN, AlignedFaceBuilder.Normal.UP
			, AlignedFaceBuilder.Normal.UP, AlignedFaceBuilder.Normal.DOWN
		)
	);

	/**
	 * A factory method to create a sub-block mesh instance from the given mask.
	 * 
	 * @param subBlockMask The mask as used with SubBlock (each set bit is a sub-block to include).
	 * @return The mesh for a sub-block defined with the given mask.
	 */
	public static SubBlockMesh builder(long subBlockMask)
	{
		// We find where the surfaces are, in all 6 axis-aligned faces.
		byte[] minX = _filledFaceArray(Byte.MAX_VALUE);
		byte[] maxX = _filledFaceArray(Byte.MIN_VALUE);
		byte[] minY = _filledFaceArray(Byte.MAX_VALUE);
		byte[] maxY = _filledFaceArray(Byte.MIN_VALUE);
		byte[] minZ = _filledFaceArray(Byte.MAX_VALUE);
		byte[] maxZ = _filledFaceArray(Byte.MIN_VALUE);
		for (int z = 0; z < SubBlock.SUB_BLOCK_EDGE; ++z)
		{
			for (int y = 0; y < SubBlock.SUB_BLOCK_EDGE; ++y)
			{
				for (int x = 0; x < SubBlock.SUB_BLOCK_EDGE; ++x)
				{
					long bit = SubBlock.fromInt(x, y, z).getMask();
					if (0L != (subBlockMask & bit))
					{
						// XY
						int xy = y * SubBlock.SUB_BLOCK_EDGE + x;
						minZ[xy] = (byte)Math.min(minZ[xy], z);
						maxZ[xy] = (byte)Math.max(maxZ[xy], z);
						
						// XZ
						int xz = z * SubBlock.SUB_BLOCK_EDGE + x;
						minY[xz] = (byte)Math.min(minY[xz], y);
						maxY[xz] = (byte)Math.max(maxY[xz], y);
						
						// YZ
						int yz = z * SubBlock.SUB_BLOCK_EDGE + y;
						minX[yz] = (byte)Math.min(minX[yz], x);
						maxX[yz] = (byte)Math.max(maxX[yz], x);
					}
				}
			}
		}
		
		// Walk these surfaces to find all quads on each face.
		// NOTE:  We currently use a very simple approach to this where a face can only spawn a single row (this should be optimized in the future).
		
		// XY.
		List<Face> xyp = new ArrayList<>();
		_buildFaces((int major, int minorBase, int minorEdge, int value) -> {
			Face face = _handleRow(minorBase, major, value + 1, minorEdge, major + 1, value + 1);
			xyp.add(face);
		}, maxZ, Byte.MIN_VALUE);
		List<Face> xyn = new ArrayList<>();
		_buildFaces((int major, int minorBase, int minorEdge, int value) -> {
			Face face = _handleRow(minorBase, major, value, minorEdge, major + 1, value);
			xyn.add(face);
		}, minZ, Byte.MAX_VALUE);
		
		// YZ.
		List<Face> yzp = new ArrayList<>();
		_buildFaces((int major, int minorBase, int minorEdge, int value) -> {
			Face face = _handleRow(value + 1, minorBase, major, value + 1, minorEdge, major + 1);
			yzp.add(face);
		}, maxX, Byte.MIN_VALUE);
		List<Face> yzn = new ArrayList<>();
		_buildFaces((int major, int minorBase, int minorEdge, int value) -> {
			Face face = _handleRow(value, minorBase, major, value, minorEdge, major + 1);
			yzn.add(face);
		}, minX, Byte.MAX_VALUE);
		
		// XZ.
		List<Face> xzp = new ArrayList<>();
		_buildFaces((int major, int minorBase, int minorEdge, int value) -> {
			Face face = _handleRow(minorBase, value + 1, major, minorEdge, value + 1, major + 1);
			xzp.add(face);
		}, maxY, Byte.MIN_VALUE);
		List<Face> xzn = new ArrayList<>();
		_buildFaces((int major, int minorBase, int minorEdge, int value) -> {
			Face face = _handleRow(minorBase, value, major, minorEdge, value, major + 1);
			xzn.add(face);
		}, minY, Byte.MAX_VALUE);
		
		return new SubBlockMesh(Collections.unmodifiableList(xyp)
			, Collections.unmodifiableList(xyn)
			, Collections.unmodifiableList(yzp)
			, Collections.unmodifiableList(yzn)
			, Collections.unmodifiableList(xzp)
			, Collections.unmodifiableList(xzn)
		);
	}


	private static void _buildFaces(_RowHandler handler, byte[] values, byte sentinel)
	{
		for (int y = 0; y < SubBlock.SUB_BLOCK_EDGE; ++y)
		{
			byte height = sentinel;
			int rowStart = -1;
			for (int x = 0; x < SubBlock.SUB_BLOCK_EDGE; ++x)
			{
				int xy = y * SubBlock.SUB_BLOCK_EDGE + x;
				byte h = values[xy];
				if (sentinel != h)
				{
					if (height != h)
					{
						// This is a new height.
						if (rowStart >= 0)
						{
							handler._handleRow(y, rowStart, x, height);
						}
						rowStart = x;
						height = h;
					}
					else
					{
						// This is continuing a surface.
					}
				}
				else
				{
					// This is the end of a surface.
					if (rowStart >= 0)
					{
						handler._handleRow(y, rowStart, x, height);
						rowStart = -1;
						height = sentinel;
					}
				}
			}
			if (rowStart >= 0)
			{
				handler._handleRow(y, rowStart, SubBlock.SUB_BLOCK_EDGE, height);
			}
		}
	}

	private static Face _handleRow(int bx, int by, int bz, int ex, int ey, int ez)
	{
		float[] base = new float[] { bx / 4.0f - FACE_BIAS
			, by / 4.0f - FACE_BIAS
			, bz / 4.0f - FACE_BIAS
		};
		float[] edge = new float[] { ex / 4.0f - FACE_BIAS
			, ey / 4.0f - FACE_BIAS
			, ez / 4.0f - FACE_BIAS
		};
		return new Face(base, edge);
	}

	private static byte[] _filledFaceArray(byte fill)
	{
		byte[] array = new byte[SubBlock.SUB_BLOCK_EDGE * SubBlock.SUB_BLOCK_EDGE];
		for (int i = 0; i < array.length; ++i)
		{
			array[i] = fill;
		}
		return array;
	}


	private final Map<AlignedFaceBuilder.Normal, List<Face>> _faces;

	private SubBlockMesh(List<Face> xyp
		, List<Face> xyn
		, List<Face> yzp
		, List<Face> yzn
		, List<Face> xzp
		, List<Face> xzn
	)
	{
		_faces = Map.of(AlignedFaceBuilder.Normal.UP, xyp
			, AlignedFaceBuilder.Normal.DOWN, xyn
			, AlignedFaceBuilder.Normal.EAST, yzp
			, AlignedFaceBuilder.Normal.WEST, yzn
			, AlignedFaceBuilder.Normal.NORTH, xzp
			, AlignedFaceBuilder.Normal.SOUTH, xzn
		);
	}

	/**
	 * Gets the list of faces for this mesh which are facing requestedFace once the mesh has been rotated by
	 * blockRotation.  All the vertices are in the [0.0..1.0] range.
	 * 
	 * @param requestedFace The face of the mesh to request (post-rotation).
	 * @param blockRotation The direction to rotate the block (both the faces and all vertices).
	 * @return The list of faces on the requestedFace after applying blockRotation.
	 */
	public List<Face> getFaces(AlignedFaceBuilder.Normal requestedFace, FacingDirection blockRotation)
	{
		// We need to reverse-rotate the selected face by blockRotation so that we select the vertices which will end up rotated onto the requested face,
		AlignedFaceBuilder.Normal faceToSelct = RUBIK.get(blockRotation).get(requestedFace);
		List<Face> faces = _faces.get(faceToSelct);
		return faces.stream().map((Face face) -> {
			float[] rotBase = blockRotation.rotateTripletAboutZ(face.base3);
			float[] rotEdge = blockRotation.rotateTripletAboutZ(face.edge3);
			return new Face(new float[] { rotBase[0] + FACE_BIAS
				, rotBase[1] + FACE_BIAS
				, rotBase[2] + FACE_BIAS
			}, new float[] { rotEdge[0] + FACE_BIAS
				, rotEdge[1] + FACE_BIAS
				, rotEdge[2] + FACE_BIAS
			});
		}).toList();
	}


	public static class Face
	{
		public final float[] base3;
		public final float[] edge3;
		public Face(float[] base3, float[] edge3)
		{
			this.base3 = base3;
			this.edge3 = edge3;
		}
	}

	private static interface _RowHandler
	{
		void _handleRow(int major, int minorBase, int minorEdge, int value);
	}
}
