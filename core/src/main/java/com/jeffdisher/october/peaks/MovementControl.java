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
		_locationZ =  2.50f;
		_rotateX = -2.35f;
		_rotateY = -1.25f;
	}

	public void walk(float forwardDistance)
	{
		_locationY += forwardDistance;
	}

	public void strafeRight(float rightDistance)
	{
		_locationX += rightDistance;
	}

	public void jump(float deltaZ)
	{
		_locationZ += deltaZ;
	}

	public void rotate(int distanceRight, int distanceDown)
	{
		float xRadians = ((float)distanceRight) / 300.0f;
		float yRadians = ((float)distanceDown) / 300.0f;
		// Y is backward since we rotate left and consider positive sin of Y.
		_rotateX += xRadians;
		_rotateY -= yRadians;
		float pi = (float)Math.PI;
		float pi2 = 2.0f * pi;
		float piHalf = pi / 2.0f;
		if (_rotateX > pi2)
		{
			_rotateX -= pi2;
		}
		else if (_rotateX < -pi2)
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
		// We will assume that we are looking at (0, 1, 0) when at 0 rotation.
		float lookX = - (float)Math.sin(_rotateX);
		float lookY = (float)Math.cos(_rotateX);
		float lookZ = (float)Math.sin(_rotateY);
		Vector looking = new Vector(lookX, lookY, lookZ).normalize();
		return new Vector(_locationX + looking.x(), _locationY + looking.y(), _locationZ + looking.z());
	}
}
