package com.jeffdisher.october.peaks;

import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.PartialEntity;


/**
 * The bounds describing an axis-aligned rectangular prism.
 */
public record Prism(float west
		, float south
		, float bottom
		, float east
		, float north
		, float top
) {
	public static Prism getBoundsAtOrigin(float x, float y, float z)
	{
		return new Prism(0.0f
				, 0.0f
				, 0.0f
				, x
				, y
				, z
		);
	}

	public static Prism getLocationBoundsForEntity(PartialEntity entity)
	{
		EntityLocation base = entity.location();
		EntityVolume volume = EntityConstants.getVolume(entity.type());
		float baseX = base.x();
		float edgeX = baseX + volume.width();
		float baseY = base.y();
		float edgeY = baseY + volume.width();
		float baseZ = base.z();
		float edgeZ = baseZ + volume.height();
		return new Prism(baseX
				, baseY
				, baseZ
				, edgeX
				, edgeY
				, edgeZ
		);
	}

	public final Prism getRelative(float rx, float ry, float rz)
	{
		return new Prism(this.west + rx
				, this.south + ry
				, this.bottom + rz
				, this.east + rx
				, this.north + ry
				, this.top + rz
		);
	}
}
