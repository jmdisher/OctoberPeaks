package com.jeffdisher.october.peaks.graphics;

import java.nio.FloatBuffer;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.utils.Assert;


/**
 * A helper class to populate the buffer used by a vertex array.
 * Note that these instances are intended to be ephemeral as they rely on an injected native-memory backing-store.
 */
public class BufferBuilder
{
	// We always have position, normal, and texture0.
	public static final int INDEX_POSITION = 0;

	private final FloatBuffer _sharedBackingStore;
	private final Attribute _position;
	private final Attribute _normal;
	private final Attribute _texture0;
	private final Attribute _texture1;
	private final Attribute _blockLight;
	private final Attribute _skyLight;
	private final Attribute[] _attributesIncluded;
	private int _nextAttribute;
	private int _lastStartPosition;
	private int _verticesWritten;

	public BufferBuilder(FloatBuffer sharedBackingStore
		, Attribute position
		, Attribute normal
		, Attribute texture0
		, Attribute texture1
		, Attribute blockLight
		, Attribute skyLight
	)
	{
		Assert.assertTrue(null != position);
		Assert.assertTrue(position.location() >= 0);
		
		// We want to drop any attributes which were eliminated from the shader.
		if ((null != normal) && (-1 == normal.location()))
		{
			normal = null;
		}
		if ((null != texture0) && (-1 == texture0.location()))
		{
			texture0 = null;
		}
		if ((null != texture1) && (-1 == texture1.location()))
		{
			texture1 = null;
		}
		if ((null != blockLight) && (-1 == blockLight.location()))
		{
			blockLight = null;
		}
		if ((null != skyLight) && (-1 == skyLight.location()))
		{
			skyLight = null;
		}
		
		_sharedBackingStore = sharedBackingStore;
		_position = position;
		_normal = normal;
		_texture0 = texture0;
		_texture1 = texture1;
		_blockLight = blockLight;
		_skyLight = skyLight;
		int attributeCount = 1;
		if (null != _normal)
		{
			attributeCount += 1;
		}
		if (null != _texture0)
		{
			attributeCount += 1;
		}
		if (null != _texture1)
		{
			attributeCount += 1;
		}
		if (null != _blockLight)
		{
			attributeCount += 1;
		}
		if (null != _skyLight)
		{
			attributeCount += 1;
		}
		Attribute[] attributesIncluded = new Attribute[attributeCount];
		attributesIncluded[INDEX_POSITION] = _position;
		int index = 1;
		if (null != _normal)
		{
			attributesIncluded[index] = _normal;
			index += 1;
		}
		if (null != _texture0)
		{
			attributesIncluded[index] = _texture0;
			index += 1;
		}
		if (null != _texture1)
		{
			attributesIncluded[index] = _texture1;
			index += 1;
		}
		if (null != _blockLight)
		{
			attributesIncluded[index] = _blockLight;
			index += 1;
		}
		if (null != _skyLight)
		{
			attributesIncluded[index] = _skyLight;
			index += 1;
		}
		_attributesIncluded = attributesIncluded;
		
		_nextAttribute = 0;
		_lastStartPosition = 0;
		_verticesWritten = 0;
		
		_sharedBackingStore.clear();
	}

	public void position(float[] data)
	{
		_append(INDEX_POSITION, data);
	}

	public void normal(float[] data)
	{
		if (null != _normal)
		{
			_append(_nextAttribute, data);
		}
	}

	public void texture0(float[] data)
	{
		if (null != _texture0)
		{
			_append(_nextAttribute, data);
		}
	}

	public void texture1(float[] data)
	{
		if (null != _texture1)
		{
			_append(_nextAttribute, data);
		}
	}

	public void blockLight(float f)
	{
		if (null != _blockLight)
		{
			float[] data = new float[] { f };
			_append(_nextAttribute, data);
		}
	}

	public void skyLight(float f)
	{
		if (null != _skyLight)
		{
			float[] data = new float[] { f };
			_append(_nextAttribute, data);
		}
	}

	/**
	 * Carves off the current contents of the buffer as a buffer ready to upload.  The receiver can continue creating
	 * the next buffer.
	 * 
	 * @return The frozen Buffer object or null if there were no vertices written.
	 */
	public Buffer finishOne()
	{
		Buffer buffer = null;
		// We only bother building a buffer is it will have some contents.
		if (_verticesWritten > 0)
		{
			int nextStartPosition = _sharedBackingStore.position();
			FloatBuffer copy = _sharedBackingStore.duplicate();
			// We are done writing so flip the buffer.
			copy.flip();
			copy.position(_lastStartPosition);
			copy.limit(nextStartPosition);
			buffer = new Buffer(copy, _verticesWritten, _attributesIncluded);
			_lastStartPosition = nextStartPosition;
			_verticesWritten = 0;
		}
		return buffer;
	}


	private void _append(int attribute, float[] data)
	{
		Assert.assertTrue(_nextAttribute == attribute);
		Assert.assertTrue(_attributesIncluded[attribute].floats() == data.length);
		
		_sharedBackingStore.put(data);
		
		_nextAttribute = attribute + 1;
		if (_nextAttribute == _attributesIncluded.length)
		{
			_nextAttribute = 0;
			_verticesWritten += 1;
		}
	}


	/**
	 * A frozen snapshot of the buffer extent where a single stream of vertices was written.
	 * NOTE:  This shares the underlying backing-store with the parent BufferBuilder so it should be used/discarded
	 * before that backing-store can be reused.
	 */
	public static class Buffer
	{
		private final FloatBuffer _flippedBuffer;
		public final int vertexCount;
		private final Attribute[] _attributes;
		
		public Buffer(FloatBuffer flippedBuffer, int vertexCount, Attribute[] attributes)
		{
			_flippedBuffer = flippedBuffer;
			this.vertexCount = vertexCount;
			_attributes = attributes;
		}
		
		/**
		 * Flushes the accumulated vertex data to a new buffer in GL.
		 * 
		 * @param gl The GL interface.
		 * @return The new VertexArray object (null if there were no vertices written).
		 */
		public VertexArray flush(GL20 gl)
		{
			Assert.assertTrue(_flippedBuffer.hasRemaining());
			int buffer = gl.glGenBuffer();
			Assert.assertTrue(buffer > 0);
			
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
			// WARNING:  Since we are using a FloatBuffer in glBufferData, the size is ignored and only remaining is considered.
			gl.glBufferData(GL20.GL_ARRAY_BUFFER, 0, _flippedBuffer, GL20.GL_STATIC_DRAW);
			Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
			
			return new VertexArray(buffer, this.vertexCount, _attributes);
		}
		/**
		 * This is just a testing helper and shouldn't used in a normal run.
		 */
		public float[] testGetFloats(float[] buffer)
		{
			_flippedBuffer.get(buffer);
			return buffer;
		}
	}
}
