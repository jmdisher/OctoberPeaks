package com.jeffdisher.october.peaks.scene;

import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Encoding;


/**
 * An object which can filter what needs to be rendered using frustum culling.
 */
public class FrustumFilter
{
	public static final float HALF_CUBOID_EDGE = (Encoding.CUBOID_EDGE_SIZE / 2.0f);
	public static final float CUBOID_RADIUS = (float) Math.sqrt(3.0f * HALF_CUBOID_EDGE * HALF_CUBOID_EDGE);

	private final _Plane _left;
	private final _Plane _right;
	private final _Plane _bottom;
	private final _Plane _top;
	private final _Plane _near;
	private final _Plane _far;
	
	/**
	 * Creates the frustum culling filter.
	 * 
	 * @param projection The scene's projection matrix.
	 * @param view The scene's view matrix.
	 */
	public FrustumFilter(Matrix projection, Matrix view)
	{
		Matrix out = Matrix.multiply(projection, view);
		
		_left = new _Plane(out.get(3, 0) + out.get(0, 0)
			, out.get(3, 1) + out.get(0, 1)
			, out.get(3, 2) + out.get(0, 2)
			, out.get(3, 3) + out.get(0, 3)
		);
		_right = new _Plane(out.get(3, 0) - out.get(0, 0)
			, out.get(3, 1) - out.get(0, 1)
			, out.get(3, 2) - out.get(0, 2)
			, out.get(3, 3) - out.get(0, 3)
		);
		_bottom = new _Plane(out.get(3, 0) + out.get(1, 0)
			, out.get(3, 1) + out.get(1, 1)
			, out.get(3, 2) + out.get(1, 2)
			, out.get(3, 3) + out.get(1, 3)
		);
		_top = new _Plane(out.get(3, 0) - out.get(1, 0)
			, out.get(3, 1) - out.get(1, 1)
			, out.get(3, 2) - out.get(1, 2)
			, out.get(3, 3) - out.get(1, 3)
		);
		_near = new _Plane(out.get(3, 0) + out.get(2, 0)
			, out.get(3, 1) + out.get(2, 1)
			, out.get(3, 2) + out.get(2, 2)
			, out.get(3, 3) + out.get(2, 3)
		);
		_far = new _Plane(out.get(3, 0) - out.get(2, 0)
			, out.get(3, 1) - out.get(2, 1)
			, out.get(3, 2) - out.get(2, 2)
			, out.get(3, 3) - out.get(2, 3)
		);
	}

	/**
	 * Checks if a cuboid should be drawn (that is, is visible in the view frustum).
	 * 
	 * @param address The cuboid address.
	 * @return True if this cuboid should be rendered.
	 */
	public boolean shouldIncludeCuboid(CuboidAddress address)
	{
		EntityLocation base = address.getBase().toEntityLocation();
		float cX = base.x() + HALF_CUBOID_EDGE;
		float cY = base.y() + HALF_CUBOID_EDGE;
		float cZ = base.z() + HALF_CUBOID_EDGE;
		
		return _left.isInFront(cX, cY, cZ, CUBOID_RADIUS)
			&& _right.isInFront(cX, cY, cZ, CUBOID_RADIUS)
			&& _bottom.isInFront(cX, cY, cZ, CUBOID_RADIUS)
			&& _top.isInFront(cX, cY, cZ, CUBOID_RADIUS)
			&& _near.isInFront(cX, cY, cZ, CUBOID_RADIUS)
			&& _far.isInFront(cX, cY, cZ, CUBOID_RADIUS)
		;
	}


	private static class _Plane
	{
		private final float _x;
		private final float _y;
		private final float _z;
		private final float _d;
		public _Plane(float x, float y, float z, float d)
		{
			float magnitude = (float)Math.sqrt(x * x + y * y + z * z);
			_x = x / magnitude;
			_y = y / magnitude;
			_z = z / magnitude;
			_d = d / magnitude;
		}
		public boolean isInFront(float x, float y, float z, float radius)
		{
			float dot = _x * x + _y * y + _z * z;
			return (dot + radius + _d) >= 0.0f;
		}
	}
}
