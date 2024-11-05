package com.jeffdisher.october.peaks;

import java.util.Collection;
import java.util.function.Predicate;

import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.PartialEntity;


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

	public static Vector computeUpVector(float yawRadians, float pitchRadians)
	{
		// We will assume that we are looking at (0, 1, 0) when at 0 rotation.
		float pitchUp = pitchRadians + ((float)Math.PI * 0.5f);
		float lookX = -(float)Math.sin(yawRadians);
		float lookY = (float)Math.cos(yawRadians);
		float lookZ = (float)Math.sin(pitchUp);
		float distanceZ = (float)Math.cos(pitchUp);
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

	/**
	 * Finds the closest entity in entities to start which is within the ray up to end.
	 * 
	 * @param start The starting-point of the ray.
	 * @param end The end-point of the ray.
	 * @param entities The collection of entities to consider.
	 * @return The closest entity description or null if none were within this ray.
	 */
	public static SelectedEntity findSelectedEntity(Vector start, Vector end, Collection<PartialEntity> entities)
	{
		// We will check each entity for intersections with the ray and AABB using the common "Slab Method".
		// Good summary found here:  https://en.wikipedia.org/wiki/Slab_method
		
		// Extract the axis-aligned components of the ray.
		// (we will handle the axis-parallel rays as special-cases)
		boolean isFixedX = (end.x() == start.x());
		boolean isFixedY = (end.y() == start.y());
		boolean isFixedZ = (end.z() == start.z());
		float compX = isFixedX
				? start.x()
				: end.x() - start.x()
		;
		float compY = isFixedY
				? start.y()
				: end.y() - start.y()
		;
		float compZ = isFixedZ
				? start.z()
				: end.z() - start.z()
		;
		
		PartialEntity closest = null;
		float distance = Float.MAX_VALUE;
		for (PartialEntity entity : entities)
		{
			// Extract the axis-aligned bounding-box from the entity.
			EntityLocation base = entity.location();
			EntityVolume volume = EntityConstants.getVolume(entity.type());
			float baseX = base.x();
			float edgeX = baseX + volume.width();
			float baseY = base.y();
			float edgeY = baseY + volume.width();
			float baseZ = base.z();
			float edgeZ = baseZ + volume.height();
			
			// We will calculate the t-values relative to the end of the vector so any match will be when all axes have t values in [0..1].
			float closeX = Float.MIN_VALUE;
			float farX;
			if (isFixedX)
			{
				farX = ((baseX <= compX) && (compX <= edgeX))
						? Float.MAX_VALUE
						: Float.MIN_VALUE
				;
			}
			else
			{
				float txLow = (baseX - start.x()) / compX;
				float txHigh = (edgeX - start.x()) / compX;
				closeX = Math.min(txLow, txHigh);
				farX = Math.max(txLow, txHigh);
			}
			float closeY = Float.MIN_VALUE;
			float farY;
			if (isFixedY)
			{
				farY = ((baseY <= compY) && (compY <= edgeY))
						? Float.MAX_VALUE
						: Float.MIN_VALUE
				;
			}
			else
			{
				float tyLow = (baseY - start.y()) / compY;
				float tyHigh = (edgeY - start.y()) / compY;
				closeY = Math.min(tyLow, tyHigh);
				farY = Math.max(tyLow, tyHigh);
			}
			float closeZ = Float.MIN_VALUE;
			float farZ;
			if (isFixedZ)
			{
				farZ = ((baseZ <= compZ) && (compZ <= edgeZ))
						? Float.MAX_VALUE
						: Float.MIN_VALUE
				;
			}
			else
			{
				float tzLow = (baseZ - start.z()) / compZ;
				float tzHigh = (edgeZ - start.z()) / compZ;
				closeZ = Math.min(tzLow, tzHigh);
				farZ = Math.max(tzLow, tzHigh);
			}
			
			float close = Math.max(closeX, Math.max(closeY, closeZ));
			float far = Math.min(farX, Math.min(farY, farZ));
			
			if ((close <= far) && (close >= 0.0f) && (close < distance))
			{
				closest = entity;
				distance = close;
			}
		}
		
		// If we have a closest, find the distance to it.
		SelectedEntity selected = null;
		if (null != closest)
		{
			// We will interpret the entity bounding box as a cylinder so compute the z distance first.
			EntityVolume volume = EntityConstants.getVolume(closest.type());
			float halfWidth = volume.width() / 2.0f;
			EntityLocation location = closest.location();
			EntityLocation centreFoot = new EntityLocation(location.x() + halfWidth, location.y() + halfWidth, location.z());
			float zDistance = centreFoot.z() - start.z();
			if (zDistance < 0.0f)
			{
				zDistance = Math.min(0.0f, zDistance + volume.height());
			}
			// Compute the distance in the XY plane.
			float deltaX = centreFoot.x() - start.x();
			float deltaY = centreFoot.y() - start.y();
			float horizontalDistance = Math.max(0.0f, (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY) - halfWidth);
			float totalDistance = (float)Math.sqrt(horizontalDistance * horizontalDistance + zDistance * zDistance);
			selected = new SelectedEntity(closest, totalDistance);
		}
		return selected;
	}

	public static AbsoluteLocation locationFromVector(Vector vector)
	{
		return _locationFromVector(vector);
	}

	public static AbsoluteLocation getCentreAtFeet(Entity entity)
	{
		EntityLocation entityLocation = entity.location();
		// (we want the block under our centre).
		float widthOffset = EntityConstants.VOLUME_PLAYER.width() / 2.0f;
		EntityLocation centre = new EntityLocation(entityLocation.x() + widthOffset, entityLocation.y() + widthOffset, entityLocation.z());
		return centre.getBlockLocation();
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

	public static record SelectedEntity(PartialEntity entity
			, float distance
	) {}
}
