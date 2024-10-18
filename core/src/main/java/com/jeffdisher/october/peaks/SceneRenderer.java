package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.PartialEntity;
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
	private final Map<Integer, PartialEntity> _entities;
	private final Map<EntityType, Integer> _entityMeshes;
	private final int _highlightTexture;
	private final int _highlightCube;

	private Matrix _viewMatrix;
	private final Matrix _projectionMatrix;
	private Vector _eye;
	private Vector _target;

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
		_entities = new HashMap<>();
		Map<EntityType, Integer> entityMeshes = new HashMap<>();
		for (EntityType type : EntityType.values())
		{
			if (EntityType.ERROR != type)
			{
				EntityVolume volume = EntityConstants.getVolume(type);
				int buffer = _gl.glGenBuffer();
				Assert.assertTrue(buffer > 0);
				_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
				
				// WARNING:  Since we are using a FloatBuffer in glBufferData, the size is ignored and only remaining is considered.
				_meshBuffer.clear();
				GraphicsHelpers.drawRectangularPrism(_meshBuffer, new float[] {volume.width(), volume.width(), volume.height()});
				_meshBuffer.flip();
				_gl.glBufferData(GL20.GL_ARRAY_BUFFER, 0, _meshBuffer, GL20.GL_STATIC_DRAW);
				
				Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
				entityMeshes.put(type, buffer);
			}
		}
		_entityMeshes = Collections.unmodifiableMap(entityMeshes);
		_highlightTexture = GraphicsHelpers.loadSinglePixelImageRGBA(_gl, new byte[] {(byte)0xff, (byte)0xff, (byte)0xff, 0x7f});
		
		int buffer = _gl.glGenBuffer();
		Assert.assertTrue(buffer > 0);
		_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
		_meshBuffer.clear();
		GraphicsHelpers.drawRectangularPrism(_meshBuffer, new float[] {1.0f, 1.0f, 1.0f});
		_meshBuffer.flip();
		_gl.glBufferData(GL20.GL_ARRAY_BUFFER, 0, _meshBuffer, GL20.GL_STATIC_DRAW);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		_highlightCube = buffer;
		
		_viewMatrix = Matrix.identity();
		_projectionMatrix = Matrix.perspective(90.0f, 1.0f, 0.1f, 100.0f);
		_eye = new Vector(0.0f, 0.0f, 0.0f);
		_target = new Vector(0.0f, 1.0f, 0.0f);
	}

	public void updatePosition(Vector eye, Vector target)
	{
		_eye = eye;
		_target = target;
		_viewMatrix = Matrix.lookAt(eye, target, new Vector(0.0f, 0.0f, 1.0f));
	}

	public void render()
	{
		_gl.glDepthFunc(GL20.GL_LESS);
		// Find any selected entity or block.
		PartialEntity selectedEntity = GeometryHelpers.findSelectedEntity(_eye, _target, _entities.values());
		GeometryHelpers.RayResult selection = GeometryHelpers.findFirstCollision(_eye, _target, (AbsoluteLocation location) -> {
			_CuboidData data = _cuboids.get(location.getCuboidAddress());
			boolean shouldStop = true;
			if (null != data)
			{
				shouldStop = false;
				IReadOnlyCuboidData cuboid = data.data;
				short block = cuboid.getData15(AspectRegistry.BLOCK, location.getBlockAddress());
				// Check against the air block.
				if (0 != block)
				{
					shouldStop = true;
				}
			}
			return shouldStop;
		});
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
		
		// Render any entities.
		for (PartialEntity entity : _entities.values())
		{
			EntityLocation location = entity.location();
			Matrix model = Matrix.translate(location.x(), location.y(), location.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _entityMeshes.get(entity.type()));
			_gl.glEnableVertexAttribArray(0);
			_gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 0);
			_gl.glEnableVertexAttribArray(1);
			_gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
			_gl.glEnableVertexAttribArray(2);
			_gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
			_gl.glDrawArrays(GL20.GL_TRIANGLES, 0, GraphicsHelpers.RECTANGULAR_PRISM_VERTEX_COUNT);
		}
		
		// Highlight the selected entity or block.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _highlightTexture);
		_gl.glDepthFunc(GL20.GL_LEQUAL);
		if (null != selectedEntity)
		{
			EntityLocation location = selectedEntity.location();
			Matrix model = Matrix.translate(location.x(), location.y(), location.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _entityMeshes.get(selectedEntity.type()));
			_gl.glEnableVertexAttribArray(0);
			_gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 0);
			_gl.glEnableVertexAttribArray(1);
			_gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
			_gl.glEnableVertexAttribArray(2);
			_gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
			_gl.glDrawArrays(GL20.GL_TRIANGLES, 0, GraphicsHelpers.RECTANGULAR_PRISM_VERTEX_COUNT);
		}
		if (null != selection)
		{
			AbsoluteLocation solid = selection.stopBlock();
			Matrix model = Matrix.translate(solid.x(), solid.y(), solid.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _highlightCube);
			_gl.glEnableVertexAttribArray(0);
			_gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 0);
			_gl.glEnableVertexAttribArray(1);
			_gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
			_gl.glEnableVertexAttribArray(2);
			_gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
			_gl.glDrawArrays(GL20.GL_TRIANGLES, 0, GraphicsHelpers.RECTANGULAR_PRISM_VERTEX_COUNT);
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
				GraphicsHelpers.drawCube(_meshBuffer
						, new float[] { (float)base.x(), (float)base.y(), (float)base.z()}
						, size
				);
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
			_cuboids.put(address, new _CuboidData(entityBuffer, vertexCount, cuboid));
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

	public void setEntity(PartialEntity entity)
	{
		_entities.put(entity.id(), entity);
	}

	public void removeEntity(int id)
	{
		_entities.remove(id);
	}


	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static record _CuboidData(int array
			, int vertexCount
			, IReadOnlyCuboidData data
	) {}
}
