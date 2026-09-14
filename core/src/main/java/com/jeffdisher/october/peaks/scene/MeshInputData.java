package com.jeffdisher.october.peaks.scene;

import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;


/**
 * Packaged-up data passed into the mesh generation helpers.
 * This record exists to give names to the inputs, instead of just a long parameter list.
 * Note that any of the fields can be null except for "cuboid".
 */
public record MeshInputData(IReadOnlyCuboidData cuboid
	, ColumnHeightMap height
	, IReadOnlyCuboidData up
	, ColumnHeightMap upHeight
	, IReadOnlyCuboidData down
	, ColumnHeightMap downHeight
	, IReadOnlyCuboidData north
	, ColumnHeightMap northHeight
	, IReadOnlyCuboidData south
	, ColumnHeightMap southHeight
	, IReadOnlyCuboidData east
	, ColumnHeightMap eastHeight
	, IReadOnlyCuboidData west
	, ColumnHeightMap westHeight
	
	, IReadOnlyCuboidData[][][] cuboidsXYZ
	, ColumnHeightMap[][] columnHeightXY
)
{
}
