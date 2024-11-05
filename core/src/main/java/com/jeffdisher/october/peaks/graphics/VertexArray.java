package com.jeffdisher.october.peaks.graphics;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.badlogic.gdx.graphics.GL20;


/**
 * A high-level abstraction over and OpenGL buffer containing a vertex array.
 */
public class VertexArray
{
	private final int _buffer;
	private final int _totalVertices;
	private final Attribute[] _attributes;
	private final int _totalFloats;

	public VertexArray(int buffer, int totalVertices, Attribute[] attributes)
	{
		_buffer = buffer;
		_totalVertices = totalVertices;
		_attributes = attributes;
		_totalFloats = Arrays.stream(_attributes)
				.collect(Collectors.summingInt((Attribute a) -> a.floats()))
		;
	}

	public void drawAllTriangles(GL20 gl)
	{
		_setupBuffer(gl);
		gl.glDrawArrays(GL20.GL_TRIANGLES, 0, _totalVertices);
	}

	public void drawAllLines(GL20 gl)
	{
		_setupBuffer(gl);
		gl.glDrawArrays(GL20.GL_LINES, 0, _totalVertices);
	}

	public void delete(GL20 gl)
	{
		gl.glDeleteBuffer(_buffer);
	}


	private void _setupBuffer(GL20 gl)
	{
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _buffer);
		int floatOffset = 0;
		for (int i = 0; i < _attributes.length; ++i)
		{
			Attribute attribute = _attributes[i];
			gl.glEnableVertexAttribArray(i);
			gl.glVertexAttribPointer(i, attribute.floats(), GL20.GL_FLOAT, false, _totalFloats * Float.BYTES, floatOffset * Float.BYTES);
			floatOffset += attribute.floats();
		}
	}
}
