package com.jeffdisher.october.peaks.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.badlogic.gdx.graphics.GL20;

/**
 * A high-level representation of an OpenGL shader program.
 * This exists so that we can hang high-level helpers off of it while also giving it a distinct type.
 */
public class Program
{
	/**
	 * Factory method to create a new Program object from shader sources.
	 * 
	 * @param gl The GL interface.
	 * @param vertexSource The source for the vertex shader.
	 * @param fragmentSource The source for the fragment shader.
	 * @param attributesInOrder The names of the expected attributes, in the order the caller wants to address them.
	 * @return The new instance.
	 */
	public static Program fullyLinkedProgram(GL20 gl, String vertexSource, String fragmentSource, String[] attributesInOrder)
	{
		int program = gl.glCreateProgram();
		_compileAndAttachShader(gl, program, GL20.GL_VERTEX_SHADER, vertexSource);
		_compileAndAttachShader(gl, program, GL20.GL_FRAGMENT_SHADER, fragmentSource);
		for (int index = 0; index < attributesInOrder.length; ++index)
		{
			gl.glBindAttribLocation(program, index, attributesInOrder[index]);
		}
		gl.glLinkProgram(program);
		return new Program(gl, program);
	}

	private static int _compileAndAttachShader(GL20 gl, int program, int shaderType, String source)
	{
		int shader = gl.glCreateShader(shaderType);
		gl.glShaderSource(shader, source);
		gl.glCompileShader(shader);
		ByteBuffer direct = ByteBuffer.allocateDirect(Integer.BYTES);
		direct.order(ByteOrder.nativeOrder());
		IntBuffer buffer = direct.asIntBuffer();
		buffer.put(-1);
		((java.nio.Buffer) buffer).flip();
		gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, buffer);
		if (1 != buffer.get())
		{
			String log = gl.glGetShaderInfoLog(shader);
			throw new AssertionError("Failed to compile: " + log);
		}
		gl.glAttachShader(program, shader);
		return shader;
	}


	private final GL20 _gl;
	private final int _program;

	private Program(GL20 gl, int program)
	{
		_gl = gl;
		_program = program;
	}

	public void useProgram()
	{
		_gl.glUseProgram(_program);
	}

	public int getUniformLocation(String name)
	{
		return _gl.glGetUniformLocation(_program, name);
	}
}
