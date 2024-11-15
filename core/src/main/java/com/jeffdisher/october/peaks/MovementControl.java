package com.jeffdisher.october.peaks;


/**
 * This helper class is meant to encapsulate the logic for how the first-person perspective is managed and updated.
 * The location is now handled by the common client logic but the rotation of where the player is looking isn't yet part
 * of that system so this exists to stitch in that logic.
 * This will either be completely removed or substantially scaled-back once that "facing" information is integrated into
 * the common system.
 */
public class MovementControl
{
	private Vector _eyeLocation;
	private float _rotateX;
	private float _rotateY;

	public MovementControl()
	{
		_eyeLocation = new Vector(0.0f, 0.0f, 0.0f);
		_rotateX = 4.07f;
		_rotateY = -0.83f;
	}

	public float rotateYaw(int distanceRight)
	{
		float xRadians = ((float)distanceRight) / 300.0f;
		// Note that _rotateX is counter-clockwise around the positive Z (yaw - positive X turns to the left).
		_rotateX -= xRadians;
		float pi = (float)Math.PI;
		float pi2 = 2.0f * pi;
		if (_rotateX > pi2)
		{
			_rotateX -= pi2;
		}
		else if (_rotateX < 0.0f)
		{
			_rotateX += pi2;
		}
		return _rotateX;
	}

	public float rotatePitch(int distanceDown)
	{
		float yRadians = ((float)distanceDown) / 300.0f;
		// Note that _rotateY is counter-clockwise around the positive X (pitch - positive Y looks up).
		_rotateY -= yRadians;
		float pi = (float)Math.PI;
		float piHalf = pi / 2.0f;
		if (_rotateY > piHalf)
		{
			_rotateY = piHalf;
		}
		else if (_rotateY < -piHalf)
		{
			_rotateY = -piHalf;
		}
		return _rotateY;
	}

	public void setEye(Vector eyeLocation)
	{
		_eyeLocation = eyeLocation;
	}

	public Vector computeEye()
	{
		return _eyeLocation;
	}

	public Vector computeTarget()
	{
		Vector looking = GeometryHelpers.computeFacingVector(_rotateX, _rotateY);
		return new Vector(_eyeLocation.x() + looking.x(), _eyeLocation.y() + looking.y(), _eyeLocation.z() + looking.z());
	}

	public Vector computeUpVector()
	{
		return GeometryHelpers.computeUpVector(_rotateX, _rotateY);
	}
}
