package com.jeffdisher.october.peaks;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;


/**
 * A collection of helpers for interacting with OpenGL.
 */
public class GraphicsHelpers
{
	public static int fullyLinkedProgram(GL20 gl, String vertexSource, String fragmentSource, String[] attributesInOrder)
	{
		int program = gl.glCreateProgram();
		_compileAndAttachShader(gl, program, GL20.GL_VERTEX_SHADER, vertexSource);
		_compileAndAttachShader(gl, program, GL20.GL_FRAGMENT_SHADER, fragmentSource);
		for (int index = 0; index < attributesInOrder.length; ++index)
		{
			gl.glBindAttribLocation(program, index, attributesInOrder[index]);
		}
		gl.glLinkProgram(program);
		return program;
	}

	public static int loadInternalRGBA(GL20 gl, String imageName) throws IOException
	{
		FileHandle textureFile = Gdx.files.internal(imageName);
		BufferedImage loadedTexture = ImageIO.read(textureFile.read());
		
		int height = loadedTexture.getHeight();
		int width = loadedTexture.getWidth();
		
		// 4 bytes per pixel since we are storing pixels as RGBA.
		int bytesToAllocate = width * height * 4;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(bytesToAllocate);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				int pixel = loadedTexture.getRGB(x, y);
				// This data is pulled out as ARGB but we need to upload it as RGBA.
				byte a = (byte)((0xFF000000 & pixel) >> 24);
				byte r = (byte)((0x00FF0000 & pixel) >> 16);
				byte g = (byte)((0x0000FF00 & pixel) >> 8);
				byte b = (byte) (0x000000FF & pixel);
				textureBufferData.put(new byte[] { r, g, b, a });
			}
		}
		((java.nio.Buffer) textureBufferData).flip();
		
		// Create the texture and upload.
		int texture = gl.glGenTexture();
		gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, width, height, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
		
		// In this case, we just use the default mipmap behaviour.
		gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
		return texture;
	}

	public static int buildTestingScene(GL20 gl)
	{
		int cubeCount = 4;
		int quadsPerCube = 6;
		int verticesPerQuad = 6;
		int floatsPerVertex = 3 + 3 + 2;
		int totalBytes = cubeCount * quadsPerCube * verticesPerQuad * floatsPerVertex * Float.BYTES;
		ByteBuffer direct = ByteBuffer.allocateDirect(totalBytes);
		
		direct.order(ByteOrder.nativeOrder());
		FloatBuffer floats = direct.asFloatBuffer();
		drawCube(floats, new float[] {0.0f, 0.0f, 0.0f});
		drawCube(floats, new float[] {-1.0f, 0.0f, 0.0f});
		drawCube(floats, new float[] {0.0f, 1.0f, 0.0f});
		drawCube(floats, new float[] {0.0f, 0.0f, 1.0f});
		((java.nio.Buffer) direct).position(0);
		
		int entityBuffer = gl.glGenBuffer();
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, entityBuffer);
		gl.glBufferData(GL20.GL_ARRAY_BUFFER, totalBytes, direct.asFloatBuffer(), GL20.GL_STATIC_DRAW);
		gl.glEnableVertexAttribArray(0);
		gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 0);
		gl.glEnableVertexAttribArray(1);
		gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
		gl.glEnableVertexAttribArray(2);
		gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
		return entityBuffer;
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

	private static void _populateQuad(FloatBuffer buffer, float[] base, float[][] vertices, float[] normal)
	{
		float[] bottomLeft = new float[] {
				base[0] + vertices[0][0],
				base[1] + vertices[0][1],
				base[2] + vertices[0][2],
		};
		float[] bottomRight = new float[] {
				base[0] + vertices[1][0],
				base[1] + vertices[1][1],
				base[2] + vertices[1][2],
		};
		float[] topRight = new float[] {
				base[0] + vertices[2][0],
				base[1] + vertices[2][1],
				base[2] + vertices[2][2],
		};
		float[] topLeft = new float[] {
				base[0] + vertices[3][0],
				base[1] + vertices[3][1],
				base[2] + vertices[3][2],
		};
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// NOTE:  We invert the textures coordinates here (probably not ideal).
		float[] mesh = new float[] {
				// Left Bottom.
				bottomLeft[0], bottomLeft[1], bottomLeft[2],
				normal[0], normal[1], normal[2],
				0.0f, 1.0f,
				// Right Bottom.
				bottomRight[0], bottomRight[1], bottomRight[2],
				normal[0], normal[1], normal[2],
				1.0f, 1.0f,
				// Right Top.
				topRight[0], topRight[1], topRight[2],
				normal[0], normal[1], normal[2],
				1.0f, 0.0f,
				
				// Left Bottom.
				bottomLeft[0], bottomLeft[1], bottomLeft[2],
				normal[0], normal[1], normal[2],
				0.0f, 1.0f,
				// Right Top.
				topRight[0], topRight[1], topRight[2],
				normal[0], normal[1], normal[2],
				1.0f, 0.0f,
				// Left Top.
				topLeft[0], topLeft[1], topLeft[2],
				normal[0], normal[1], normal[2],
				0.0f, 0.0f,
		};
		
		buffer.put(mesh);
	}

	private static void drawCube(FloatBuffer floats, float[] base)
	{
		float[] v001 = new float[] { 0.0f, 0.0f, 1.0f };
		float[] v101 = new float[] { 1.0f, 0.0f, 1.0f };
		float[] v111 = new float[] { 1.0f, 1.0f, 1.0f };
		float[] v011 = new float[] { 0.0f, 1.0f, 1.0f };
		float[] v000 = new float[] { 0.0f, 0.0f, 0.0f };
		float[] v100 = new float[] { 1.0f, 0.0f, 0.0f };
		float[] v110 = new float[] { 1.0f, 1.0f, 0.0f };
		float[] v010 = new float[] { 0.0f, 1.0f, 0.0f };
		_populateQuad(floats, base, new float[][] {
			v000, v100, v101, v001
		}, new float[] {0.0f, -1.0f,0.0f});
		_populateQuad(floats, base, new float[][] {
			v110, v010, v011, v111
		}, new float[] {0.0f, 1.0f, 0.0f});
		_populateQuad(floats, base, new float[][] {
			v001, v000, v010, v011
		}, new float[] {-1.0f, 0.0f, 0.0f});
		_populateQuad(floats, base, new float[][] {
			v101, v100, v110, v111
		}, new float[] {1.0f, 0.0f, 0.0f});
		_populateQuad(floats, base, new float[][] {
			v000, v010, v110, v100
		}, new float[] {0.0f, 0.0f, -1.0f});
		_populateQuad(floats, base, new float[][] {
			v011, v001, v101, v111
		}, new float[] {0.0f, 0.0f, 1.0f});
	}
}
