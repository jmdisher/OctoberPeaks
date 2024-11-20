package com.jeffdisher.october.peaks;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.PartialEntity;


public class TestGeometryHelpers
{
	@Test
	public void facingVector() throws Throwable
	{
		float halfPi = (float)(Math.PI / 2.0f);
		
		// Test facing forward.
		float yaw = 0.0f;
		float pitch = 0.0f;
		Vector forward = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, 1.0f, 0.0f), forward);
		
		// Left - rotation of half pi.
		yaw = halfPi;
		Vector left = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(-1.0f, 0.0f, 0.0f), left);
		
		// Backward, rotation of full pi.
		yaw = (float)Math.PI;
		Vector back = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, -1.0f, 0.0f), back);
		
		// Back and up.
		yaw = (float)Math.PI;
		pitch = 0.2f;
		Vector backUp = GeometryHelpers.computeFacingVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, -0.98f, 0.2f), backUp);
	}

	@Test
	public void positiveRayNoMatch() throws Throwable
	{
		Vector start = new Vector(-2.0f, -3.0f, -4.0f);
		Vector end = new Vector(2.0f, 3.0f, 4.0f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		GeometryHelpers.RayResult result = GeometryHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return false;
		});
		Assert.assertNull(result);
	}

	@Test
	public void negativeAlignedRayMatch() throws Throwable
	{
		Vector start = new Vector(0.0f, 5.0f, 0.0f);
		Vector end = new Vector(0.0f, -2.0f, 0.0f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		GeometryHelpers.RayResult result = GeometryHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return l.equals(GeometryHelpers.locationFromVector(end));
		});
		Assert.assertEquals(new AbsoluteLocation(0, -2, 0), result.stopBlock());
		Assert.assertEquals(new AbsoluteLocation(0, -1, 0), result.preStopBlock());
	}

	@Test
	public void shortVector() throws Throwable
	{
		Vector start = new Vector(29.34f, -1.26f, 10.9f);
		Vector end = new Vector(30.24f, -0.97f, 10.59f);
		AbsoluteLocation[] holder = new AbsoluteLocation[1];
		GeometryHelpers.RayResult result = GeometryHelpers.findFirstCollision(start, end, (AbsoluteLocation l) -> {
			if (null != holder[0])
			{
				Assert.assertEquals(1, Math.abs(holder[0].x() - l.x())
						+ Math.abs(holder[0].y() - l.y())
						+ Math.abs(holder[0].z() - l.z())
				);
			}
			holder[0] = l;
			return false;
		});
		Assert.assertNull(result);
	}

	@Test
	public void entityTrivial() throws Throwable
	{
		Vector start = new Vector(1.0f, 5.0f, 1.0f);
		Vector end = new Vector(0.0f, -2.0f, 0.0f);
		PartialEntity entity = _getTestCow(-1, new EntityLocation(0.0f, 1.0f, 0.0f));
		GeometryHelpers.SelectedEntity closest = GeometryHelpers.findSelectedEntity(start, end, List.of(entity));
		Assert.assertEquals(entity, closest.entity());
		Assert.assertEquals(3.22f, closest.distance(), 0.01f);
	}

	@Test
	public void entityParallelRayHit() throws Throwable
	{
		Vector start = new Vector(0.0f, 5.0f, 0.0f);
		Vector end = new Vector(0.0f, -2.0f, 0.0f);
		PartialEntity entity = _getTestCow(-1, new EntityLocation(0.0f, 1.0f, 0.0f));
		GeometryHelpers.SelectedEntity closest = GeometryHelpers.findSelectedEntity(start, end, List.of(entity));
		Assert.assertEquals(entity, closest.entity());
		Assert.assertEquals(3.20f, closest.distance(), 0.01f);
	}

	@Test
	public void entityParallelRayMiss() throws Throwable
	{
		Vector start = new Vector(0.0f, 5.0f, 0.0f);
		Vector end = new Vector(0.0f, -2.0f, 0.0f);
		PartialEntity entity = _getTestCow(-1, new EntityLocation(1.0f, 1.0f, 0.0f));
		GeometryHelpers.SelectedEntity closest = GeometryHelpers.findSelectedEntity(start, end, List.of(entity));
		Assert.assertNull(closest);
	}

	@Test
	public void entityParallelRayMultipleEntities() throws Throwable
	{
		Vector start = new Vector(0.0f, 5.0f, 1.0f);
		Vector end = new Vector(0.0f, -2.0f, 0.0f);
		PartialEntity entity0 = _getTestCow(-1, new EntityLocation(0.0f, 1.0f, 0.0f));
		PartialEntity entity1 = _getTestCow(-2, new EntityLocation(0.0f, 2.0f, 0.5f));
		PartialEntity entity2 = _getTestCow(-3, new EntityLocation(1.0f, 3.0f, 0.5f));
		GeometryHelpers.SelectedEntity closest = GeometryHelpers.findSelectedEntity(start, end, List.of(entity2, entity1, entity0));
		Assert.assertEquals(entity1, closest.entity());
		Assert.assertEquals(2.20f, closest.distance(), 0.01f);
	}

	@Test
	public void entityBehind() throws Throwable
	{
		Vector start = new Vector(0.0f, 0.0f, 0.0f);
		Vector end = new Vector(2.0f, 2.0f, 2.0f);
		PartialEntity entity = _getTestCow(-1, new EntityLocation(-1.0f, -1.0f, -1.0f));
		GeometryHelpers.SelectedEntity closest = GeometryHelpers.findSelectedEntity(start, end, List.of(entity));
		Assert.assertNull(closest);
	}

	@Test
	public void upVectpr() throws Throwable
	{
		float halfPi = (float)(Math.PI / 2.0f);
		
		// Test facing forward.
		float yaw = 0.0f;
		float pitch = 0.0f;
		Vector forward = GeometryHelpers.computeUpVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, 0.0f, 1.0f), forward);
		
		// Left - rotation of half pi.
		yaw = halfPi;
		Vector left = GeometryHelpers.computeUpVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, 0.0f, 1.0f), left);
		
		// Back and up.
		yaw = (float)Math.PI;
		pitch = 0.2f;
		Vector backUp = GeometryHelpers.computeUpVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, 0.20f, 0.98f), backUp);
		
		// Forward straight up.
		yaw = 0.0f;
		pitch = (float)Math.PI * 0.5f;
		Vector forwardStraightUp = GeometryHelpers.computeUpVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, -1.0f, 0.0f), forwardStraightUp);
		
		// Forward straight down.
		yaw = 0.0f;
		pitch = (float)Math.PI * -0.5f;
		Vector forwardStraightDown = GeometryHelpers.computeUpVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, 1.0f, 0.0f), forwardStraightDown);
		
		// Backward straight up.
		yaw = (float)Math.PI;
		pitch = (float)Math.PI * 0.5f;
		Vector backwardStraightUp = GeometryHelpers.computeUpVector(yaw, pitch);
		_vectorEquals(new Vector(0.0f, 1.0f, 0.0f), backwardStraightUp);
	}

	@Test
	public void intersectPrism() throws Throwable
	{
		Vector start = new Vector(0.0f, 0.0f, 0.0f);
		Vector end = new Vector(2.0f, 2.0f, 2.0f);
		Prism prism = new Prism(1.0f, 1.0f, 1.0f, 2.0f, 2.0f, 2.0f);
		Assert.assertTrue(GeometryHelpers.doesIntersect(start, end, prism));
	}

	@Test
	public void prismBehind() throws Throwable
	{
		Vector start = new Vector(0.0f, 0.0f, 0.0f);
		Vector end = new Vector(2.0f, 2.0f, 2.0f);
		Prism prism = new Prism(-2.0f, -2.0f, -2.0f, -1.5f, -1.5f, -1.5f);
		Assert.assertFalse(GeometryHelpers.doesIntersect(start, end, prism));
	}


	private static void _vectorEquals(Vector expected, Vector test)
	{
		Assert.assertEquals(expected.x(), test.x(), 0.01f);
		Assert.assertEquals(expected.y(), test.y(), 0.01f);
		Assert.assertEquals(expected.z(), test.z(), 0.01f);
	}

	private static PartialEntity _getTestCow(int id, EntityLocation location)
	{
		return new PartialEntity(id
				, EntityType.COW
				, location
				, OrientationHelpers.YAW_NORTH
				, OrientationHelpers.PITCH_FLAT
				, (byte)1
		);
	}
}
