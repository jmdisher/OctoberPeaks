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
	public static Program fullyLinkedProgram(GL20 gl, String vertexSource, String fragmentSource, SourceAttribute[] attributesInOrder)
	{
		int program = gl.glCreateProgram();
		_compileAndAttachShader(gl, program, GL20.GL_VERTEX_SHADER, vertexSource);
		_compileAndAttachShader(gl, program, GL20.GL_FRAGMENT_SHADER, fragmentSource);
		// Note:  We used to use glBindAttribLocation here, which did cause the attributes to end up at their requested
		// indices, but only if they weren't entirely removed (despite binding the location, they can still end up with
		// -1).  As a result, we just link and then look up by SourceAttribute.
		gl.glLinkProgram(program);
		
		// We now want to extract the attribute data.
		// Note that glGetActiveAttrib takes "attribute index" while glGetAttribLocation takes "attribute location" (set
		// by glBindAttribLocation).
		// However, this is further complicated by some attributes still not being present in the linked program despite
		// glBindAttribLocation being called.  This means we will need to use the name to find the type information and
		// its location (if it has one).
		IntBuffer outSize = _allocOutParam();
		IntBuffer outType = _allocOutParam();
		
		// The output Attribute objects are kept in the same order, since that is how the source applies them, but we
		// will keep their indices internal, knowing that some may even be missing.
		Attribute[] attributes = new Attribute[attributesInOrder.length];
		for (int index = 0; index < attributesInOrder.length; ++index)
		{
			SourceAttribute source = attributesInOrder[index];
			int location = gl.glGetAttribLocation(program, source.name());
			
			// Only get information if there is a location.
			if (location >= 0)
			{
				String name = gl.glGetActiveAttrib(program, location, outSize, outType);
				Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
				Assert.assertTrue(source.name().equals(name));
				
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
			}
			
			attributes[index] = new Attribute(source.name(), location, source.floats());
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
