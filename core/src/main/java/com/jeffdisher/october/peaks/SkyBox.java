package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the logic to draw the sky.
 * The sky is a basic sky box, using a cubemap texture.
 */
public class SkyBox
{
	public static final int SKY_PIXEL_EDGE = 128;
	public static final int TEXTURE_BUFFER_SIZE = 4 * SKY_PIXEL_EDGE * SKY_PIXEL_EDGE;

	private final GL20 _gl;
	private final Program _program;
	private final int _uModelMatrix;
	private final int _uViewMatrix;
	private final int _uProjectionMatrix;
	private final int _uTexture0;
	private final int _skyTexture;
	private final VertexArray _cubeMesh;
	private Matrix _viewMatrix;
	private Matrix _dayTimeModelMatrix;

	public SkyBox(GL20 gl)
	{
		_gl = gl;
		_program = Program.fullyLinkedProgram(gl
				, _readUtf8Asset("sky.vert")
				, _readUtf8Asset("sky.frag")
				, new String[] {
						"aPosition",
				}
		);
		_uModelMatrix = _program.getUniformLocation("uModelMatrix");
		_uViewMatrix = _program.getUniformLocation("uViewMatrix");
		_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
		_uTexture0 = _program.getUniformLocation("uTexture0");
		_skyTexture = gl.glGenTexture();
		
		gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, _skyTexture);
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(TEXTURE_BUFFER_SIZE);
		textureBufferData.order(ByteOrder.nativeOrder());
		try
		{
			TextureHelpers.populateCubeMapInternalRGBA(textureBufferData, "sky_horizon.png");
			textureBufferData.flip();
			gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X, 0, GL20.GL_RGBA, SKY_PIXEL_EDGE, SKY_PIXEL_EDGE, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
			gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, 0, GL20.GL_RGBA, SKY_PIXEL_EDGE, SKY_PIXEL_EDGE, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
			gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, 0, GL20.GL_RGBA, SKY_PIXEL_EDGE, SKY_PIXEL_EDGE, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
			gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, 0, GL20.GL_RGBA, SKY_PIXEL_EDGE, SKY_PIXEL_EDGE, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
			
			textureBufferData.clear();
			TextureHelpers.populateCubeMapInternalRGBA(textureBufferData, "sky_up.png");
			textureBufferData.flip();
			gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, 0, GL20.GL_RGBA, SKY_PIXEL_EDGE, SKY_PIXEL_EDGE, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
			
			textureBufferData.clear();
			TextureHelpers.populateCubeMapInternalRGBA(textureBufferData, "sky_down.png");
			textureBufferData.flip();
			gl.glTexImage2D(GL20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, 0, GL20.GL_RGBA, SKY_PIXEL_EDGE, SKY_PIXEL_EDGE, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
		gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
		gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR);
		gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL20.GL_TEXTURE_CUBE_MAP, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
		gl.glGenerateMipmap(GL20.GL_TEXTURE_CUBE_MAP);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We will just reuse this buffer for the cube upload.
		textureBufferData.clear();
		FloatBuffer meshBuffer = textureBufferData.asFloatBuffer();
		_cubeMesh = _defineCubeVertices(gl, _program, meshBuffer);
		_viewMatrix = Matrix.identity();
		_dayTimeModelMatrix = Matrix.identity();
	}

	public void updateView(Vector eye, Vector target, Vector upVector)
	{
		Vector origin = new Vector(0.0f, 0.0f, 0.0f);
		Vector relative = new Vector(target.x() - eye.x(), target.y() - eye.y(), target.z() - eye.z());
		_viewMatrix = Matrix.lookAt(origin, relative, upVector);
	}

	public void setDayProgression(float dayProgression)
	{
		// The day starts at day, which is 0 radians rotated from the horizon.
		float dayStart = 0.0f;
		// The day progression is [0.0 .. 1.0] so map that on to the 2pi radian arc of the sky.
		float dayOffset = dayProgression * 2.0f * (float)Math.PI;
		// We rotate around the X axis, due to how the cube maps interpret Y as "up" and Z is normally our "up".
		_dayTimeModelMatrix = Matrix.rotateX(dayStart + dayOffset);
	}

	public void render(Matrix projectionMatrix)
	{
		_program.useProgram();
		_dayTimeModelMatrix.uploadAsUniform(_gl, _uModelMatrix);
		_viewMatrix.uploadAsUniform(_gl, _uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _uProjectionMatrix);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 1 texture.
		_gl.glUniform1i(_uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_CUBE_MAP, _skyTexture);
		_cubeMesh.drawAllTriangles(_gl);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	public void shutdown()
	{
		_program.delete();
		_gl.glDeleteTexture(_skyTexture);
		_cubeMesh.delete(_gl);
	}


	private static VertexArray _defineCubeVertices(GL20 gl, Program program, FloatBuffer meshBuffer)
	{
		BufferBuilder builder = new BufferBuilder(meshBuffer, program.attributes);
		// Positive Z (Sky).
		builder.appendVertex(new float[] {1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, -1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, 1.0f});
		
		// Negative Z (Floor).
		builder.appendVertex(new float[] {1.0f, -1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {-1.0f, -1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, -1.0f});
		
		// Positive X (East).
		builder.appendVertex(new float[] {1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, 1.0f});
		builder.appendVertex(new float[] {1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, 1.0f});
		
		// Negative X (West).
		builder.appendVertex(new float[] {-1.0f, -1.0f, -1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, -1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, -1.0f, -1.0f});
		
		// Positive Y (North).
		builder.appendVertex(new float[] {1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, 1.0f});
		builder.appendVertex(new float[] {-1.0f, 1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, 1.0f, -1.0f});
		
		// Negative Y (South).
		builder.appendVertex(new float[] {-1.0f, -1.0f, 1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, 1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, -1.0f});
		builder.appendVertex(new float[] {1.0f, -1.0f, -1.0f});
		builder.appendVertex(new float[] {-1.0f, -1.0f, -1.0f});
		builder.appendVertex(new float[] {-1.0f, -1.0f, 1.0f});
		return builder.finishOne().flush(gl);
	}

	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}
}
