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
	private final int[] _floatsPerAttribute;
	private int _nextAttribute;
	private int _verticesWritten;

	public BufferBuilder(FloatBuffer sharedBackingStore, int[] floatsPerAttribute)
	{
		_sharedBackingStore = sharedBackingStore;
		_floatsPerAttribute = floatsPerAttribute;
		_nextAttribute = 0;
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

	public int getVertexCount()
	{
		return _verticesWritten;
	}

	/**
	 * Flushes the accumulated vertex data to a new buffer in GL.
	 * 
	 * @param gl The GL interface.
	 * @return The new buffer number (0 if no buffer was produces).
	 */
	public int flush(GL20 gl)
	{
		// We are done writing so flip the buffer.
		_sharedBackingStore.flip();
		
		// We only bother building a buffer is it will have some contents.
		int buffer = 0;
		if (_sharedBackingStore.hasRemaining())
		{
			buffer = gl.glGenBuffer();
			Assert.assertTrue(buffer > 0);
			
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
			// WARNING:  Since we are using a FloatBuffer in glBufferData, the size is ignored and only remaining is considered.
			gl.glBufferData(GL20.GL_ARRAY_BUFFER, 0, _sharedBackingStore, GL20.GL_STATIC_DRAW);
			Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
		}
		
		return buffer;
	}


	private void _append(int attribute, float[] data)
	{
		Assert.assertTrue(_nextAttribute == attribute);
		Assert.assertTrue(_floatsPerAttribute[attribute] == data.length);
		
		_sharedBackingStore.put(data);
		
		_nextAttribute = attribute + 1;
		if (_nextAttribute == _floatsPerAttribute.length)
		{
			_nextAttribute = 0;
			_verticesWritten += 1;
		}
	}
}
