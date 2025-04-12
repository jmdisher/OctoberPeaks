package com.jeffdisher.october.peaks.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.TextManager;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.utils.Assert;


/**
 * Support for drawing the UI in OpenGL ES2.
 * Currently, this is just a container of the GL-specific data but it will evolve into an encapsulation of UI routines
 * related specifically to GL resource interaction (programs, textures, vertex arrays, etc).
 * The initial plan is to use this to support the migration to the new window management system.
 */
public class GlUi
{
	public final GL20 gl;
	public final TextureAtlas<ItemVariant> itemAtlas;
	public final TextManager textManager;
	public final Program program;
	public final int uOffset;
	public final int uScale;
	public final int uTexture;
	public final int uTextureBaseOffset;
	public final VertexArray verticesUnitSquare;
	public final VertexArray verticesItemSquare;
	public final VertexArray verticesReticleLines;
	public final int pixelLightGrey;
	public final int pixelDarkGreyAlpha;
	public final int pixelRed;
	public final int pixelGreen;
	public final int pixelGreenAlpha;
	public final int pixelBlueAlpha;
	public final int pixelOrangeLava;

	public GlUi(GL20 gl, TextureAtlas<ItemVariant> itemAtlas)
	{
		this.gl = gl;
		this.itemAtlas = itemAtlas;
		this.textManager = new TextManager(this.gl);
		
		// Create the program we will use for the window overlays.
		// The overlays are all rectangular tiles representing windows, graphic tiles, or text tiles.
		// This means that the only mesh we will use is a unit square and we will apply a scaling factor and offset
		// location to place and size it correctly.
		// In order to simplify the usage, we will assume that all colour data originates in textures (but some of the
		// textures may just be single-pixel colour data).
		this.program = Program.fullyLinkedProgram(this.gl
				, _readUtf8Asset("windows.vert")
				, _readUtf8Asset("windows.frag")
				, new String[] {
						"aPosition",
						"aTexture",
				}
		);
		this.uOffset = this.program.getUniformLocation("uOffset");
		this.uScale = this.program.getUniformLocation("uScale");
		this.uTexture = this.program.getUniformLocation("uTexture");
		this.uTextureBaseOffset = this.program.getUniformLocation("uTextureBaseOffset");
		
		// Create the scratch buffer we will use for out graphics data (short-lived).
		int floatsPerVertex = Arrays.stream(this.program.attributes)
				.map((Attribute attribute) -> attribute.floats())
				.collect(Collectors.summingInt((Integer i) -> i))
		;
		int vertexCount = 6;
		ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * floatsPerVertex * Float.BYTES);
		buffer.order(ByteOrder.nativeOrder());
		FloatBuffer meshBuffer = buffer.asFloatBuffer();
		// Create the unit square we will use for common vertices.
		this.verticesUnitSquare = _defineCommonVertices(this.gl, this.program, meshBuffer, 1.0f);
		// Create the unit square we can configure for item drawing
		this.verticesItemSquare = _defineCommonVertices(this.gl, this.program, meshBuffer, this.itemAtlas.coordinateSize);
		this.verticesReticleLines = _defineReticleVertices(this.gl, this.program, meshBuffer);
		
		// Build the initial pixel textures.
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(4);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		// We use a light grey pixel for a window "frame".
		this.pixelLightGrey = _makePixelRgba(this.gl, textureBufferData, (byte)180, (byte)180, (byte)180, (byte)255);
		
		// We use a dark grey pixel, with partial alpha, for a window "background".
		this.pixelDarkGreyAlpha = _makePixelRgba(this.gl, textureBufferData, (byte)32, (byte)32, (byte)32, (byte)196);
		
		// We use the Red/Green pixels for outlines of frames in a few cases.
		this.pixelRed = _makePixelRgba(this.gl, textureBufferData, (byte)255, (byte)0, (byte)0, (byte)255);
		this.pixelGreen = _makePixelRgba(this.gl, textureBufferData, (byte)0, (byte)255, (byte)0, (byte)255);
		
		// We use the semi-transparent green for "progress" overlays.
		this.pixelGreenAlpha = _makePixelRgba(this.gl, textureBufferData, (byte)0, (byte)255, (byte)0, (byte)100);
		
		// We use the semi-transparent blue for "under water" overlay layer.
		this.pixelBlueAlpha = _makePixelRgba(this.gl, textureBufferData, (byte)0, (byte)0, (byte)255, (byte)100);
		
		// We use a mostly-opaque orange for "under lava" overlay layer.
		this.pixelOrangeLava = _makePixelRgba(this.gl, textureBufferData, (byte)255, (byte)69, (byte)0, (byte)220);
		
		Assert.assertTrue(GL20.GL_NO_ERROR == this.gl.glGetError());
	}

	public void shutdown()
	{
		// We don't own _itemAtlas.
		this.textManager.shutdown();
		this.program.delete();
		this.verticesUnitSquare.delete(this.gl);
		this.verticesItemSquare.delete(this.gl);
		this.verticesReticleLines.delete(this.gl);
	}


	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static VertexArray _defineCommonVertices(GL20 gl, Program program, FloatBuffer meshBuffer, float textureSize)
	{
		float height = 1.0f;
		float width = 1.0f;
		float textureBaseU = 0.0f;
		float textureBaseV = 0.0f;
		BufferBuilder builder = new BufferBuilder(meshBuffer, program.attributes);
		builder.appendVertex(new float[] {0.0f, 0.0f}
				, new float[] {textureBaseU, textureBaseV}
		);
		builder.appendVertex(new float[] {width, height}
				, new float[] {textureBaseU + textureSize, textureBaseV + textureSize}
		);
		builder.appendVertex(new float[] {0.0f, height}
				, new float[] {textureBaseU, textureBaseV + textureSize}
		);
		builder.appendVertex(new float[] {0.0f, 0.0f}
				, new float[] {textureBaseU, textureBaseV}
		);
		builder.appendVertex(new float[] {width, 0.0f}
				, new float[] {textureBaseU + textureSize, textureBaseV}
		);
		builder.appendVertex(new float[] {width, height}
				, new float[] {textureBaseU + textureSize, textureBaseV + textureSize}
		);
		return builder.finishOne().flush(gl);
	}

	private static VertexArray _defineReticleVertices(GL20 gl, Program program, FloatBuffer meshBuffer)
	{
		// We always draw the reticle at the full size of the screen and scale it in the shader.
		float origin = 0.0f;
		float sizeFromOrigin = 1.0f;
		float textureBaseU = 0.0f;
		float textureBaseV = 0.0f;
		float textureSize = 1.0f;
		BufferBuilder builder = new BufferBuilder(meshBuffer, program.attributes);
		builder.appendVertex(new float[] {origin, -sizeFromOrigin}
				, new float[] {textureBaseU, textureBaseV}
		);
		builder.appendVertex(new float[] {origin, sizeFromOrigin}
				, new float[] {textureBaseU + textureSize, textureBaseV + textureSize}
		);
		builder.appendVertex(new float[] {-sizeFromOrigin, origin}
				, new float[] {textureBaseU, textureBaseV}
		);
		builder.appendVertex(new float[] {sizeFromOrigin, origin}
				, new float[] {textureBaseU + textureSize, textureBaseV + textureSize}
		);
		return builder.finishOne().flush(gl);
	}

	private static int _makePixelRgba(GL20 gl, ByteBuffer textureBufferData, byte r, byte g, byte b, byte a)
	{
		int texture = gl.glGenTexture();
		textureBufferData.clear();
		textureBufferData.put(new byte[] { r, g, b, a });
		textureBufferData.flip();
		gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, 1, 1, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
		gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
		return texture;
	}
}
