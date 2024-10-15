package com.jeffdisher.october.peaks;


/**
 * This helper class is meant to encapsulate the logic for how the first-person perspective is managed and updated.
 * Note that this will need to be re-worked once the system is integrated with the OctoberProject client.
 */
public class MovementControl
{
	private float _locationX;
	private float _locationY;
	private float _locationZ;
	private float _rotateX;
	private float _rotateY;

	public MovementControl()
	{
		_locationX = -1.75f;
		_locationY = 1.75f;
		_locationZ =  15.0f;
		_rotateX = 4.07f;
		_rotateY = -0.83f;
	}

	public void walk(float forwardDistance)
	{
		_Direction direction = _findFacing();
		switch (direction)
		{
		case NORTH:
			_locationY += forwardDistance;
			break;
		case EAST:
			_locationX += forwardDistance;
			break;
		case SOUTH:
			_locationY -= forwardDistance;
			break;
		case WEST:
			_locationX -= forwardDistance;
			break;
		}
	}

	public void strafeRight(float rightDistance)
	{
		_Direction direction = _findFacing();
		switch (direction)
		{
		case NORTH:
			_locationX += rightDistance;
			break;
		case EAST:
			_locationY -= rightDistance;
			break;
		case SOUTH:
			_locationX -= rightDistance;
			break;
		case WEST:
			_locationY += rightDistance;
			break;
		}
	}

	public void jump(float deltaZ)
	{
		_locationZ += deltaZ;
	}

	public void rotate(int distanceRight, int distanceDown)
	{
		float xRadians = ((float)distanceRight) / 300.0f;
		float yRadians = ((float)distanceDown) / 300.0f;
		// Note that _rotateX is counter-clockwise around the positive Z (yaw - positive X turns to the left).
		_rotateX -= xRadians;
		// Note that _rotateY is counter-clockwise around the positive X (pitch - positive Y looks up).
		_rotateY -= yRadians;
		float pi = (float)Math.PI;
		float pi2 = 2.0f * pi;
		float piHalf = pi / 2.0f;
		if (_rotateX > pi2)
		{
			_rotateX -= pi2;
		}
		else if (_rotateX < 0.0f)
		{
			_rotateX += pi2;
		}
		if (_rotateY > piHalf)
		{
			_rotateY = piHalf;
		}
		else if (_rotateY < -piHalf)
		{
			_rotateY = -piHalf;
		}
	}

	public Vector computeEye()
	{
		return new Vector(_locationX, _locationY, _locationZ);
	}

	public Vector computeTarget()
	{
		Vector looking = GeometryHelpers.computeFacingVector(_rotateX, _rotateY);
		return new Vector(_locationX + looking.x(), _locationY + looking.y(), _locationZ + looking.z());
	}


	private _Direction _findFacing()
	{
		// We will take the facing direction into account but always walk in cardinal directions (since the movement mutation requires that).
		float pi = (float)Math.PI;
		float half = pi / 2.0f;
		float quarter = pi / 4.0f;
		int quadrant = (int)Math.floor((_rotateX + quarter) % (pi * 2.0f) / half);
		_Direction direction = _Direction.values()[quadrant];
		return direction;
	}


	// These are in this order since the rotation is counter-clockwise.
	private static enum _Direction
	{
		NORTH,
		WEST,
		SOUTH,
		EAST,
	}
}
