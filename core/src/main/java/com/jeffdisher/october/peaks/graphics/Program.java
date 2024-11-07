package com.jeffdisher.october.peaks.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.utils.Assert;

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
		
		// Verify that the attribute locations are what we requested.
		for (int index = 0; index < attributesInOrder.length; ++index)
		{
			int loc = gl.glGetAttribLocation(program, attributesInOrder[index]);
			Assert.assertTrue(index == loc);
		}
		
		// We now want to extract the attribute data.
		// Note that glGetActiveAttrib takes "attribute index" while glGetAttribLocation takes "attribute location" (set
		// by glBindAttribLocation) so we will need to use the name to find the type information, not the location.
		// See this for more information:  https://www.devgem.io/posts/understanding-glgetattriblocation-and-glgetactiveattrib-in-opengl
		IntBuffer outSize = _allocOutParam();
		IntBuffer outType = _allocOutParam();
		
		// First, find out the attribute indices.
		gl.glGetProgramiv(program, GL20.GL_ACTIVE_ATTRIBUTES, outSize);
		int attributeCount = outSize.get(0);
		Assert.assertTrue(attributeCount == attributesInOrder.length);
		
		// Now, walk these to find the name in order to get the type/size.
		Attribute[] attributes = new Attribute[attributesInOrder.length];
		for (int index = 0; index < attributeCount; ++index)
		{
			gl.glEnableVertexAttribArray(index);
			String name = gl.glGetActiveAttrib(program, index, outSize, outType);
			Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
			
			// NOTE:  These IntBuffer out-params are directly written so we don't clear/flip like a normal Buffer type.
			int floatsInType;
			switch (outType.get(0))
			{
			case GL20.GL_FLOAT:
				floatsInType = 1;
				break;
			case GL20.GL_FLOAT_VEC2:
				floatsInType = 2;
				break;
			case GL20.GL_FLOAT_VEC3:
				floatsInType = 3;
				break;
				default:
					// We need to add handling for this.
					throw Assert.unreachable();
			}
			int floatsInAttribute = outSize.get(0) * floatsInType;
			
			// Find the stored index and write the attribute (this is n^2 but on a very small list and only once).
			int storedIndex = -1;
			for (int i = 0; (-1 == storedIndex) && (i < attributesInOrder.length); ++i)
			{
				if (name.equals(attributesInOrder[i]))
				{
					storedIndex = i;
				}
			}
			Assert.assertTrue(storedIndex >= 0);
			Assert.assertTrue(null == attributes[storedIndex]);
			attributes[storedIndex] = new Attribute(name, floatsInAttribute);
		}
		Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
		return new Program(gl, program, attributes);
	}

	private static int _compileAndAttachShader(GL20 gl, int program, int shaderType, String source)
	{
		int shader = gl.glCreateShader(shaderType);
		gl.glShaderSource(shader, source);
		gl.glCompileShader(shader);
		IntBuffer buffer = _allocOutParam();
		gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, buffer);
		if (1 != buffer.get())
		{
			String log = gl.glGetShaderInfoLog(shader);
			throw new AssertionError("Failed to compile: " + log);
		}
		gl.glAttachShader(program, shader);
		return shader;
	}

	private static IntBuffer _allocOutParam()
	{
		ByteBuffer buf = ByteBuffer.allocateDirect(Integer.BYTES);
		buf.order(ByteOrder.nativeOrder());
		return buf.asIntBuffer();
	}


	private final GL20 _gl;
	private final int _program;
	public final Attribute[] attributes;

	private Program(GL20 gl, int program, Attribute[] attributes)
	{
		_gl = gl;
		_program = program;
		this.attributes = attributes;
	}

	public void useProgram()
	{
		_gl.glUseProgram(_program);
	}

	public int getUniformLocation(String name)
	{
		return _gl.glGetUniformLocation(_program, name);
	}

	public void delete()
	{
		_gl.glDeleteProgram(_program);
	}
}
