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
import com.jeffdisher.october.utils.Assert;


/**
 * A collection of helpers for interacting with OpenGL.
 */
public class GraphicsHelpers
{
	public static final int FLOATS_PER_VERTEX = 3 + 3 + 2;
	// 6 sizes, each with 2 triangles, each with 3 vertices.
	public static final int RECTANGULAR_PRISM_VERTEX_COUNT = 6 * 2 * 3;

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
		
		return _uploadNewTexture(gl, height, width, textureBufferData);
	}

	public static int loadSinglePixelImageRGBA(GL20 gl, byte[] rawPixel)
	{
		Assert.assertTrue(4 == rawPixel.length);
		int bytesToAllocate = 4;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(bytesToAllocate);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		// We store the raw pixel data as RGBA.
		textureBufferData.put(rawPixel);
		((java.nio.Buffer) textureBufferData).flip();
		
		return _uploadNewTexture(gl, 1, 1, textureBufferData);
	}

	public static void drawCube(FloatBuffer floats, float[] base, byte scale, float[] uvBase, float textureSize)
	{
		_drawCube(floats
				, base
				, scale
				, uvBase
				, textureSize
		);
	}

	public static void drawRectangularPrism(FloatBuffer floats, float[] edge, float[] uvBase, float textureSize)
	{
		_drawRectangularPrism(floats
				, new float[] { 0.0f, 0.0f, 0.0f }
				, (byte)1
				, new float[] { 0.0f, 0.0f, 0.0f }
				, edge
				, uvBase
				, textureSize
		);
	}

	public static void renderStandardArray(GL20 gl, int bufferElement, int vertexCount)
	{
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, bufferElement);
		gl.glEnableVertexAttribArray(0);
		gl.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 0);
		gl.glEnableVertexAttribArray(1);
		gl.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
		gl.glEnableVertexAttribArray(2);
		gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
		gl.glDrawArrays(GL20.GL_TRIANGLES, 0, vertexCount);
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

	private static void _populateQuad(FloatBuffer buffer, float[] base, float[][] vertices, float[] normal, float[] uvBase, float textureSize)
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
		float u = uvBase[0];
		float v = uvBase[1];
		float uEdge = u + textureSize;
		float vEdge = v + textureSize;
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// NOTE:  We invert the textures coordinates here (probably not ideal).
		float[] mesh = new float[] {
				// Left Bottom.
				bottomLeft[0], bottomLeft[1], bottomLeft[2],
				normal[0], normal[1], normal[2],
				u, vEdge,
				// Right Bottom.
				bottomRight[0], bottomRight[1], bottomRight[2],
				normal[0], normal[1], normal[2],
				uEdge, vEdge,
				// Right Top.
				topRight[0], topRight[1], topRight[2],
				normal[0], normal[1], normal[2],
				uEdge, v,
				
				// Left Bottom.
				bottomLeft[0], bottomLeft[1], bottomLeft[2],
				normal[0], normal[1], normal[2],
				u, vEdge,
				// Right Top.
				topRight[0], topRight[1], topRight[2],
				normal[0], normal[1], normal[2],
				uEdge, v,
				// Left Top.
				topLeft[0], topLeft[1], topLeft[2],
				normal[0], normal[1], normal[2],
				u, v,
		};
		
		buffer.put(mesh);
	}

	private static void _drawCube(FloatBuffer floats, float[] base, byte scale, float[] uvBase, float textureSize)
	{
		// Note that no matter the scale, the quad vertices are the same magnitudes.
		_drawRectangularPrism(floats
				, base
				, scale
				, new float[] { 0.0f, 0.0f, 0.0f }
				, new float[] { 1.0f, 1.0f, 1.0f }
				, uvBase
				, textureSize
		);
	}

	private static void _drawRectangularPrism(FloatBuffer floats
			, float[] base
			, byte scale
			, float[] prismBase
			, float[] prismEdge
			, float[] uvBase
			, float textureSize
	)
	{
		// Note that no matter the scale, the quad vertices are the same magnitudes.
		float[] v001 = new float[] { prismBase[0], prismBase[1], prismEdge[2] };
		float[] v101 = new float[] { prismEdge[0], prismBase[1], prismEdge[2] };
		float[] v111 = new float[] { prismEdge[0], prismEdge[1], prismEdge[2] };
		float[] v011 = new float[] { prismBase[0], prismEdge[1], prismEdge[2] };
		float[] v000 = new float[] { prismBase[0], prismBase[1], prismBase[2] };
		float[] v100 = new float[] { prismEdge[0], prismBase[1], prismBase[2] };
		float[] v110 = new float[] { prismEdge[0], prismEdge[1], prismBase[2] };
		float[] v010 = new float[] { prismBase[0], prismEdge[1], prismBase[2] };
		
		// We will fill in each quad by multiple instances, offset by different bases, by tiling along each plane up to scale.
		// We subtract one from the base scale since we would double-count the top "1.0f".
		float baseScale = (float)scale - 1.0f;
		
		// X-normal plane.
		for (byte z = 0; z < scale; ++z)
		{
			float zBase = base[2] + (float)z;
			for (byte y = 0; y < scale; ++y)
			{
				float yBase = base[1] + (float)y;
				float[] localBase = new float[] { base[0], yBase, zBase};
				_populateQuad(floats, localBase, new float[][] {
					v010, v000, v001, v011
				}, new float[] {-1.0f, 0.0f, 0.0f}, uvBase, textureSize);
				localBase[0] += baseScale;
				_populateQuad(floats, localBase, new float[][] {
					v111, v101, v100, v110
				}, new float[] {1.0f, 0.0f, 0.0f}, uvBase, textureSize);
			}
		}
		// Y-normal plane.
		for (byte z = 0; z < scale; ++z)
		{
			float zBase = base[2] + (float)z;
			for (byte x = 0; x < scale; ++x)
			{
				float xBase = base[0] + (float)x;
				float[] localBase = new float[] { xBase, base[1], zBase};
				_populateQuad(floats, localBase, new float[][] {
					v001, v000, v100, v101
				}, new float[] {0.0f, -1.0f,0.0f}, uvBase, textureSize);
				localBase[1] += baseScale;
				_populateQuad(floats, localBase, new float[][] {
					v111, v110, v010, v011
				}, new float[] {0.0f, 1.0f, 0.0f}, uvBase, textureSize);
			}
		}
		// Z-normal plane.
		for (byte y = 0; y < scale; ++y)
		{
			float yBase = base[1] + (float)y;
			for (byte x = 0; x < scale; ++x)
			{
				float xBase = base[0] + (float)x;
				float[] localBase = new float[] { xBase, yBase, base[2]};
				_populateQuad(floats, localBase, new float[][] {
					v110, v100, v000, v010
				}, new float[] {0.0f, 0.0f, -1.0f}, uvBase, textureSize);
				localBase[2] += baseScale;
				_populateQuad(floats, localBase, new float[][] {
					v011, v001, v101, v111
				}, new float[] {0.0f, 0.0f, 1.0f}, uvBase, textureSize);
			}
		}
	}

	private static int _uploadNewTexture(GL20 gl, int height, int width, ByteBuffer textureBufferData)
	{
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
}
