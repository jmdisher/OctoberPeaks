package com.jeffdisher.october.peaks.graphics;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.types.Vector;


public class Matrix
{
	public static Matrix identity()
	{
		float[] rowInner = new float[] {
				1.0f, 0.0f, 0.0f, 0.0f,
				0.0f, 1.0f, 0.0f, 0.0f,
				0.0f, 0.0f, 1.0f, 0.0f,
				0.0f, 0.0f, 0.0f, 1.0f,
		};
		return new Matrix(rowInner);
	}

	public static Matrix translate(float x, float y, float z)
	{
		float[] rowInner = new float[] {
				1.0f, 0.0f, 0.0f, x,
				0.0f, 1.0f, 0.0f, y,
				0.0f, 0.0f, 1.0f, z,
				0.0f, 0.0f, 0.0f, 1.0f,
		};
		return new Matrix(rowInner);
	}

	public static Matrix scale(float x, float y, float z)
	{
		float[] rowInner = new float[] {
				x, 0.0f, 0.0f, 0.0f,
				0.0f, y, 0.0f, 0.0f,
				0.0f, 0.0f, z, 0.0f,
				0.0f, 0.0f, 0.0f, 1.0f,
		};
		return new Matrix(rowInner);
	}

	public static Matrix rotateX(float radians)
	{
		return _rotateX(radians);
	}

	public static Matrix rotateY(float radians)
	{
		return _rotateY(radians);
	}

	public static Matrix rotateZ(float radians)
	{
		return _rotateZ(radians);
	}

	public static Matrix frustum(float left, float right, float bottom, float top, float zNear, float zFar)
	{
		return _frustum(left, right, bottom, top, zNear, zFar);
	}

	public static Matrix perspective(float fieldOfViewDegreesX, float aspectX, float zNear, float zFar)
	{
		// We expect the field of view to be the full field so we will use half of that to get half the frustum top.
		double fieldInRadians = fieldOfViewDegreesX / 180.0f * Math.PI;
		// Solve for opposite (halfXTop) by multiplying tan by adjacent (zNear).
		float halfXTop = zNear * (float)Math.tan(fieldInRadians / 2.0);
		float halfYTop = halfXTop / aspectX;
		return _frustum(-halfXTop, halfXTop, -halfYTop, halfYTop, zNear, zFar);
	}

	public static Matrix lookAt(Vector eye, Vector target, Vector up)
	{
		// Much of this logic is based on this excellent break-down:  https://songho.ca/opengl/gl_camera.html
		// However, it keeps the "forward" vector forward (from eye to target).
		Vector forward = Vector.delta(eye, target).normalize();
		Vector side = Vector.cross(forward, up).normalize();
		Vector normalUp = Vector.cross(side, forward).normalize();
		float translateX = -(side.x() * eye.x()) - (side.y() * eye.y()) - (side.z() * eye.z());
		float translateY = -(normalUp.x() * eye.x()) - (normalUp.y() * eye.y()) - (normalUp.z() * eye.z());
		float translateZ = -(-forward.x() * eye.x()) - (-forward.y() * eye.y()) - (-forward.z() * eye.z());
		float[] rowInner = new float[] {
				side.x(), side.y(), side.z(), translateX,
				normalUp.x(), normalUp.y(), normalUp.z(), translateY,
				-forward.x(), -forward.y(), -forward.z(), translateZ,
				0.0f, 0.0f, 0.0f, 1.0f
		};
		return new Matrix(rowInner);
	}

	public static Matrix multiply(Matrix left, Matrix right)
	{
		return _multiply(left, right);
	}

	public static Matrix rotateToFace(Vector facingVector)
	{
		// We will determine the length of the XY vector, first.
		float xySquared = (facingVector.x() * facingVector.x()) + (facingVector.y() * facingVector.y());
		float xyLength = (float)Math.sqrt(xySquared);
		double pitchRadians = (xyLength > 0.0f)
			? Math.atan(facingVector.z() / xyLength)
			: Math.signum(facingVector.z()) * 0.5f * Math.PI
		;
		Matrix rotatePitch = _rotateX((float)pitchRadians);
		
		// Note that the Y is North and the X is East, but the yaw is counter-clockwise radians from North, so we need to negate X.
		double yawRadians = Math.atan(-facingVector.x() / facingVector.y());
		if (facingVector.y() < 0.0f)
		{
			yawRadians += Math.PI;
		}
		Matrix rotateYaw = _rotateZ((float)yawRadians);
		
		Matrix rotate = _multiply(rotateYaw, rotatePitch);
		return rotate;
	}


	private static Matrix _frustum(float left, float right, float bottom, float top, float near, float far)
	{
		// The way that this frustrum projection matrix is defined seems to subtly vary across documentation and
		// examples but  this implementation seems to produce believable results.
		// It is based on the description and derivation shown here:  https://songho.ca/opengl/gl_projectionmatrix.html
		float width = right - left;
		float height = top - bottom;
		float depth = far - near;
		float near2 = 2.0f * near;
		float e00 = near2 / width;
		float e11 = near2 / height;
		float a = (right + left) / width;
		float b = (top + bottom) / height;
		float c = -(far + near) / depth;
		float d = -(near2 * far) / depth;
		float[] rowInner = new float[] {
				 e00, 0.0f,    a, 0.0f,
				0.0f,  e11,    b, 0.0f,
				0.0f, 0.0f,    c,    d,
				0.0f, 0.0f, -1.0f, 0.0f,
		};
		return new Matrix(rowInner);
	}

	private static Matrix _rotateX(float radians)
	{
		float sin = (float)Math.sin(radians);
		float cos = (float)Math.cos(radians);
		float[] rowInner = new float[] {
			1.0f, 0.0f, 0.0f, 0.0f,
			0.0f, cos, -sin, 0.0f,
			0.0f, sin, cos, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f,
		};
		return new Matrix(rowInner);
	}

	private static Matrix _rotateY(float radians)
	{
		float sin = (float)Math.sin(radians);
		float cos = (float)Math.cos(radians);
		float[] rowInner = new float[] {
			cos, 0.0f, sin, 0.0f,
			0.0f, 1.0f, 0.0f, 0.0f,
			-sin, 0.0f, cos, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f,
		};
		return new Matrix(rowInner);
	}

	private static Matrix _rotateZ(float radians)
	{
		float sin = (float)Math.sin(radians);
		float cos = (float)Math.cos(radians);
		float[] rowInner = new float[] {
			cos, -sin, 0.0f, 0.0f,
			sin, cos, 0.0f, 0.0f,
			0.0f, 0.0f, 1.0f, 0.0f,
			0.0f, 0.0f, 0.0f, 1.0f,
		};
		return new Matrix(rowInner);
	}

	private static Matrix _multiply(Matrix left, Matrix right)
	{
		float[] l = left._rowInner4x4;
		float[] r = right._rowInner4x4;
		float[] rowInner = new float[] {
			(l[0] * r[0]) + (l[1] * r[4]) + (l[2] * r[8]) + (l[3] * r[12]),
				(l[0] * r[1]) + (l[1] * r[5]) + (l[2] * r[9]) + (l[3] * r[13]),
				(l[0] * r[2]) + (l[1] * r[6]) + (l[2] * r[10]) + (l[3] * r[14]),
				(l[0] * r[3]) + (l[1] * r[7]) + (l[2] * r[11]) + (l[3] * r[15]),
			(l[4] * r[0]) + (l[5] * r[4]) + (l[6] * r[8]) + (l[7] * r[12]),
				(l[4] * r[1]) + (l[5] * r[5]) + (l[6] * r[9]) + (l[7] * r[13]),
				(l[4] * r[2]) + (l[5] * r[6]) + (l[6] * r[10]) + (l[7] * r[14]),
				(l[4] * r[3]) + (l[5] * r[7]) + (l[6] * r[11]) + (l[7] * r[15]),
			(l[8] * r[0]) + (l[9] * r[4]) + (l[10] * r[8]) + (l[11] * r[12]),
				(l[8] * r[1]) + (l[9] * r[5]) + (l[10] * r[9]) + (l[11] * r[13]),
				(l[8] * r[2]) + (l[9] * r[6]) + (l[10] * r[10]) + (l[11] * r[14]),
				(l[8] * r[3]) + (l[9] * r[7]) + (l[10] * r[11]) + (l[11] * r[15]),
			(l[12] * r[0]) + (l[13] * r[4]) + (l[14] * r[8]) + (l[15] * r[12]),
				(l[12] * r[1]) + (l[13] * r[5]) + (l[14] * r[9]) + (l[15] * r[13]),
				(l[12] * r[2]) + (l[13] * r[6]) + (l[14] * r[10]) + (l[15] * r[14]),
				(l[12] * r[3]) + (l[13] * r[7]) + (l[14] * r[11]) + (l[15] * r[15]),
		};
		return new Matrix(rowInner);
	}


	private final float[] _rowInner4x4;

	private Matrix(float[] rowInner4x4)
	{
		_rowInner4x4 = rowInner4x4;
	}

	public void uploadAsUniform(GL20 gl, int uniform)
	{
		// We want to use the transposition since we use a row-inner representation.
		gl.glUniformMatrix4fv(uniform, 1, true, _rowInner4x4, 0);
	}

	public float[] multiplyVector(float[] vec4)
	{
		return new float[] {
				(_rowInner4x4[0] * vec4[0]) + (_rowInner4x4[1] * vec4[1]) + (_rowInner4x4[2] * vec4[2]) + (_rowInner4x4[3] * vec4[3]),
				(_rowInner4x4[4] * vec4[0]) + (_rowInner4x4[5] * vec4[1]) + (_rowInner4x4[6] * vec4[2]) + (_rowInner4x4[7] * vec4[3]),
				(_rowInner4x4[8] * vec4[0]) + (_rowInner4x4[9] * vec4[1]) + (_rowInner4x4[10] * vec4[2]) + (_rowInner4x4[11] * vec4[3]),
				(_rowInner4x4[12] * vec4[0]) + (_rowInner4x4[13] * vec4[1]) + (_rowInner4x4[14] * vec4[2]) + (_rowInner4x4[15] * vec4[3]),
		};
	}

	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		for (int y = 0; y < 4; ++y)
		{
			for (int x = 0; x < 4; ++x)
			{
				builder.append(_rowInner4x4[y * 4 + x]);
				builder.append(", ");
			}
			builder.append("\n");
		}
		return builder.toString();
	}
}
