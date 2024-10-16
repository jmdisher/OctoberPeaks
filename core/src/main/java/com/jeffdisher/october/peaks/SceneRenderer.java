package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the OpenGL scene of the world (does not include UI elements, windows, etc).
 */
public class SceneRenderer
{
	public static final int BUFFER_SIZE = 64 * 1024 * 1024;

	private final GL20 _gl;
	private final int _program;
	private final int _uModelMatrix;
	private final int _uViewMatrix;
	private final int _uProjectionMatrix;
	private final int _uWorldLightLocation;
	private final int _uTexture0;
	private final int _image;
	private final Map<CuboidAddress, _CuboidData> _cuboids;
	private final FloatBuffer _meshBuffer;

	private Matrix _viewMatrix;
	private final Matrix _projectionMatrix;
	private Vector _eye;

	public SceneRenderer(GL20 gl) throws IOException
	{
		_gl = gl;
		
		// Create the shader program.
		_program = GraphicsHelpers.fullyLinkedProgram(_gl
				, _readUtf8Asset("scene.vert")
				, _readUtf8Asset("scene.frag")
				, new String[] {
						"aPosition",
						"aNormal",
						"aTexture0",
				}
		);
		_uModelMatrix = _gl.glGetUniformLocation(_program, "uModelMatrix");
		_uViewMatrix = _gl.glGetUniformLocation(_program, "uViewMatrix");
		_uProjectionMatrix = _gl.glGetUniformLocation(_program, "uProjectionMatrix");
		_uWorldLightLocation = _gl.glGetUniformLocation(_program, "uWorldLightLocation");
		_uTexture0 = _gl.glGetUniformLocation(_program, "uTexture0");
		_image = GraphicsHelpers.loadInternalRGBA(_gl, "missing_texture.png");
		_cuboids = new HashMap<>();
		ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
		direct.order(ByteOrder.nativeOrder());
		_meshBuffer = direct.asFloatBuffer();
		
		_viewMatrix = Matrix.identity();
		_projectionMatrix = Matrix.perspective(90.0f, 1.0f, 0.1f, 100.0f);
		_eye = new Vector(0.0f, 0.0f, 0.0f);
	}

	public void updatePosition(Vector eye, Vector target)
	{
		_eye = eye;
		_viewMatrix = Matrix.lookAt(eye, target, new Vector(0.0f, 0.0f, 1.0f));
	}

	public void render()
	{
		_gl.glUseProgram(_program);
		
		// Make sure that the texture is active.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _image);
		_gl.glUniform1i(_uTexture0, 0);
		
		// Set the matrices.
		_viewMatrix.uploadAsUniform(_gl, _uViewMatrix);
		_projectionMatrix.uploadAsUniform(_gl, _uProjectionMatrix);
		_gl.glUniform3f(_uWorldLightLocation, _eye.x(), _eye.y(), _eye.z());
		for (Map.Entry<CuboidAddress, _CuboidData> elt : _cuboids.entrySet())
		{
			CuboidAddress key = elt.getKey();
			_CuboidData value = elt.getValue();
			Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, value.array);
			_gl.glEnableVertexAttribArray(0);
			_gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 0);
			_gl.glEnableVertexAttribArray(1);
			_gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
			_gl.glEnableVertexAttribArray(2);
			_gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
			_gl.glDrawArrays(GL20.GL_TRIANGLES, 0, value.vertexCount);
		}
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		// Delete any previous.
		CuboidAddress address = cuboid.getCuboidAddress();
		_CuboidData previous = _cuboids.remove(address);
		if (null != previous)
		{
			_gl.glDeleteBuffer(previous.array);
		}
		
		_meshBuffer.clear();
		cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<Short>() {
			@Override
			public void visit(BlockAddress base, byte size, Short value)
			{
				// TODO:  Replace this with something which uses the size to make a single large cube.
				for (byte z = 0; z < size; ++z)
				{
					for (byte y = 0; y < size; ++y)
					{
						for (byte x = 0; x < size; ++x)
						{
							float baseX = (base.x() + x);
							float baseY = (base.y() + y);
							float baseZ = (base.z() + z);
							GraphicsHelpers.drawCube(_meshBuffer, new float[] { baseX, baseY, baseZ });
						}
					}
				}
			}
		}, (short)0);
		
		// Note that the position() in a FloatBuffer is in units of floats.
		int vertexCount = _meshBuffer.position() / GraphicsHelpers.FLOATS_PER_VERTEX;
		_meshBuffer.flip();
		// We only bother building a buffer is it will have some contents.
		if (_meshBuffer.hasRemaining())
		{
			int entityBuffer = _gl.glGenBuffer();
			Assert.assertTrue(entityBuffer > 0);
			_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, entityBuffer);
			// WARNING:  Since we are using a FloatBuffer in glBufferData, the size is ignored and only remaining is considered.
			_gl.glBufferData(GL20.GL_ARRAY_BUFFER, 0, _meshBuffer, GL20.GL_STATIC_DRAW);
			_cuboids.put(address, new _CuboidData(entityBuffer, vertexCount));
		}
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	public void removeCuboid(CuboidAddress address)
	{
		_CuboidData removed = _cuboids.remove(address);
		// Note that this will be null if the cuboid was empty.
		if (null != removed)
		{
			_gl.glDeleteBuffer(removed.array);
			Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		}
	}


	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static record _CuboidData(int array
			, int vertexCount
	) {}
}
