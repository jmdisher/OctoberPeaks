package com.jeffdisher.october.peaks;

import java.util.function.Predicate;

import com.jeffdisher.october.types.AbsoluteLocation;


public class GeometryHelpers
{
	public static Vector computeFacingVector(float yawRadians, float pitchRadians)
	{
		// We will assume that we are looking at (0, 1, 0) when at 0 rotation.
		float lookX = -(float)Math.sin(yawRadians);
		float lookY = (float)Math.cos(yawRadians);
		float lookZ = (float)Math.sin(pitchRadians);
		float distanceZ = (float)Math.cos(pitchRadians);
		return new Vector(lookX * distanceZ, lookY * distanceZ, lookZ).normalize();
	}

	/**
	 * Essentially a 3D digital differential analysis (DDA) algorithm.  It will walk the ray from start to end until
	 * stopPredicate returns true, returning a record of the last 2 blocks (null if it never return true).
	 * The point of the algorithm is to walk the locations the ray passes through, in-order.
	 * At a minimum, this will call stopPredicate once (if start/end are in the same location).
	 * 
	 * @param start The starting-point of the ray.
	 * @param end The end-point of the ray.
	 * @param stopPredicate Returns true when the search should stop.
	 * @return The result containing the stop block and previous block or null, if stopPredicate never returned true.
	 */
	public static RayResult findFirstCollision(Vector start, Vector end, Predicate<AbsoluteLocation> stopPredicate)
	{
		AbsoluteLocation startBlock = _locationFromVector(start);
		AbsoluteLocation endBlock = _locationFromVector(end);
		
		// Get the components of the vector in all 3 dimensions.
		float rayX = end.x() - start.x();
		float rayY = end.y() - start.y();
		float rayZ = end.z() - start.z();
		
		// Derive the length of the vector travelled between faces in all 3 dimensions.
		float deltaX = Math.abs(1.0f / rayX);
		float deltaY = Math.abs(1.0f / rayY);
		float deltaZ = Math.abs(1.0f / rayZ);
		
		// Get the distance from the start to the nearest face, in all 3 dimensions.
		float startX = start.x() - (float)Math.floor(start.x());
		float startY = start.y() - (float)Math.floor(start.y());
		float startZ = start.z() - (float)Math.floor(start.z());
		
		// Determine the magnitude of each starting step, based on reaching the nearest face.
		float stepX = ((rayX < 0.0f) ? startX : (1.0f - startX)) * deltaX;
		float stepY = ((rayY < 0.0f) ? startY : (1.0f - startY)) * deltaY;
		float stepZ = ((rayZ < 0.0f) ? startZ : (1.0f - startZ)) * deltaZ;
		
		// Determine the corresponding logical step directions.
		int dirX = (rayX > 0.0f) ? 1 : -1;
		int dirY = (rayY > 0.0f) ? 1 : -1;
		int dirZ = (rayZ > 0.0f) ? 1 : -1;
		
		AbsoluteLocation thisStep = startBlock;
		AbsoluteLocation lastFalse = null;
		boolean stop = stopPredicate.test(thisStep);
		while (!stop && !thisStep.equals(endBlock))
		{
			lastFalse = thisStep;
			
			if (stepX < stepY)
			{
				// Y is not the smallest.
				// See which is smaller, the X or Z.
				if (stepX < stepZ)
				{
					stepX += deltaX;
					thisStep = thisStep.getRelative(dirX, 0, 0);
				}
				else
				{
					stepZ += deltaZ;
					thisStep = thisStep.getRelative(0, 0, dirZ);
				}
			}
			else
			{
				// X is not the smallest.
				// See which is smaller, the Y or Z.
				if (stepY < stepZ)
				{
					stepY += deltaY;
					thisStep = thisStep.getRelative(0, dirY, 0);
				}
				else
				{
					stepZ += deltaZ;
					thisStep = thisStep.getRelative(0, 0, dirZ);
				}
			}
			
			stop = stopPredicate.test(thisStep);
		}
		return stop
				? new RayResult(thisStep, lastFalse)
				: null
		;
	}

	public static AbsoluteLocation locationFromVector(Vector vector)
	{
		return _locationFromVector(vector);
	}


	private static AbsoluteLocation _locationFromVector(Vector vector)
	{
		return new AbsoluteLocation((int)Math.floor(vector.x())
				, (int)Math.floor(vector.y())
				, (int)Math.floor(vector.z())
		);
	}


	public static record RayResult(AbsoluteLocation stopBlock
			, AbsoluteLocation preStopBlock
	) {}
}
