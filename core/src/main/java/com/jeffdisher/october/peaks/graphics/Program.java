package com.jeffdisher.october.peaks.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

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
	public static Program fullyLinkedProgram(GL20 gl, String vertexSource, String fragmentSource, SourceAttribute[] attributesInOrder)
	{
		// Allocate our out-params.
		// NOTE:  These IntBuffer out-params are directly written so we don't clear/flip like a normal Buffer type.
		IntBuffer outSize = _allocOutParam();
		IntBuffer outType = _allocOutParam();
		
		int program = gl.glCreateProgram();
		_compileAndAttachShader(gl, program, GL20.GL_VERTEX_SHADER, vertexSource);
		_compileAndAttachShader(gl, program, GL20.GL_FRAGMENT_SHADER, fragmentSource);
		gl.glLinkProgram(program);
		
		// Notes about how attribs are set up:
		// -there is substantial confusion in the GL documentation related to an attribute "location" or "index"
		// -the attribute "index" is used for getting information about the attribute
		// -the attribute "location" is what is required when calling glEnableVertexAttribArray or glVertexAttribPointer
		// -using "glBindAttribLocation" doesn't even always provide a result in "glGetAttribLocation", so these are useless
		// -instead, we will need to check all "active attributes" and compare them to our source array in order to create the expected output array
		gl.glGetProgramiv(program, GL20.GL_ACTIVE_ATTRIBUTES, outSize);
		int activeAtributeCount = outSize.get(0);
		
		Map<String, Integer> nameToAttributeArrayIndex = new HashMap<>();
		for (int i = 0; i < attributesInOrder.length; ++i)
		{
			Object out = nameToAttributeArrayIndex.put(attributesInOrder[i].name(), i);
			Assert.assertTrue(null == out);
		}
		
		Attribute[] attributes = new Attribute[attributesInOrder.length];
		for (int i = 0; i < activeAtributeCount; ++i)
		{
			String name = gl.glGetActiveAttrib(program, i, outSize, outType);
			Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
			
			int arrayIndex = nameToAttributeArrayIndex.get(name);
			SourceAttribute source = attributesInOrder[arrayIndex];
			Assert.assertTrue(source.name().equals(name));
			int location = gl.glGetAttribLocation(program, name);
			
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
			Assert.assertTrue(source.floats() == floatsInAttribute);
			attributes[arrayIndex] = new Attribute(source.name(), location, source.floats());
		}
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
