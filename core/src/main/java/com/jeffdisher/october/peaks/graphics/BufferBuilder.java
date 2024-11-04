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
	private final FloatBuffer _sharedBackingStore;
	private final Attribute[] _attributes;
	private int _nextAttribute;
	private int _lastStartPosition;
	private int _verticesWritten;

	public BufferBuilder(FloatBuffer sharedBackingStore, Attribute[] attributes)
	{
		_sharedBackingStore = sharedBackingStore;
		_attributes = attributes;
		_nextAttribute = 0;
		_lastStartPosition = 0;
		_verticesWritten = 0;
		
		_sharedBackingStore.clear();
	}

	public void append(int attribute, float[] data)
	{
		_append(attribute, data);
	}

	public void appendVertex(float[]... data)
	{
		int attribute = 0;
		for (float[] elt : data)
		{
			_append(attribute, elt);
			attribute += 1;
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
			buffer = new Buffer(copy, _verticesWritten, _attributes);
			_lastStartPosition = nextStartPosition;
			_verticesWritten = 0;
		}
		return buffer;
	}


	private void _append(int attribute, float[] data)
	{
		Assert.assertTrue(_nextAttribute == attribute);
		Assert.assertTrue(_attributes[attribute].floats() == data.length);
		
		_sharedBackingStore.put(data);
		
		_nextAttribute = attribute + 1;
		if (_nextAttribute == _attributes.length)
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
