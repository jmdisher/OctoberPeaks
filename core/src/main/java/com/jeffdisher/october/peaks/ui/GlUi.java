package com.jeffdisher.october.peaks.ui;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.TextManager;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Support for drawing the UI in OpenGL ES2.
 * Currently, this is just a container of the GL-specific data but it will evolve into an encapsulation of UI routines
 * related specifically to GL resource interaction (programs, textures, vertex arrays, etc).
 * The initial plan is to use this to support the migration to the new window management system.
 */
public class GlUi
{
	public static class Resources
	{
		private final TextureAtlas<ItemVariant> _itemAtlas;
		private final Program _program;
		private final int _uOffset;
		private final int _uScale;
		private final int _uTexture;
		private final int _uTextureBaseOffset;
		private final VertexArray _verticesUnitSquare;
		private final VertexArray _verticesItemSquare;
		private final VertexArray _verticesReticleLines;
		public final TextManager textManager;
		public final int pixelLightGrey;
		public final int pixelDarkGreyAlpha;
		public final int pixelRed;
		public final int pixelGreen;
		public final int pixelGreenAlpha;
		public final int pixelBlueAlpha;
		public final int pixelOrangeLava;
		
		public Resources(GL20 gl, TextureAtlas<ItemVariant> itemAtlas)
		{
			_itemAtlas = itemAtlas;
			
			// Create the program we will use for the window overlays.
			// The overlays are all rectangular tiles representing windows, graphic tiles, or text tiles.
			// This means that the only mesh we will use is a unit square and we will apply a scaling factor and offset
			// location to place and size it correctly.
			// In order to simplify the usage, we will assume that all colour data originates in textures (but some of the
			// textures may just be single-pixel colour data).
			_program = Program.fullyLinkedProgram(gl
					, _readUtf8Asset("windows.vert")
					, _readUtf8Asset("windows.frag")
					, new String[] {
							"aPosition",
							"aTexture",
					}
			);
			_uOffset = _program.getUniformLocation("uOffset");
			_uScale = _program.getUniformLocation("uScale");
			_uTexture = _program.getUniformLocation("uTexture");
			_uTextureBaseOffset = _program.getUniformLocation("uTextureBaseOffset");
			
			// Create the scratch buffer we will use for out graphics data (short-lived).
			int floatsPerVertex = Arrays.stream(_program.attributes)
					.map((Attribute attribute) -> attribute.floats())
					.collect(Collectors.summingInt((Integer i) -> i))
			;
			int vertexCount = 6;
			ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * floatsPerVertex * Float.BYTES);
			buffer.order(ByteOrder.nativeOrder());
			FloatBuffer meshBuffer = buffer.asFloatBuffer();
			// Create the unit square we will use for common vertices.
			_verticesUnitSquare = _defineCommonVertices(gl, _program, meshBuffer, 1.0f);
			// Create the unit square we can configure for item drawing
			_verticesItemSquare = _defineCommonVertices(gl, _program, meshBuffer, _itemAtlas.coordinateSize);
			_verticesReticleLines = _defineReticleVertices(gl, _program, meshBuffer);
			
			// The text manager is still public since some callers need to issue specific queries to it to mouse-over handling.
			this.textManager = new TextManager(gl);
			
			// Build the initial pixel textures.
			ByteBuffer textureBufferData = ByteBuffer.allocateDirect(4);
			textureBufferData.order(ByteOrder.nativeOrder());
			
			// We use a light grey pixel for a window "frame".
			this.pixelLightGrey = _makePixelRgba(gl, textureBufferData, (byte)180, (byte)180, (byte)180, (byte)255);
			
			// We use a dark grey pixel, with partial alpha, for a window "background".
			this.pixelDarkGreyAlpha = _makePixelRgba(gl, textureBufferData, (byte)32, (byte)32, (byte)32, (byte)196);
			
			// We use the Red/Green pixels for outlines of frames in a few cases.
			this.pixelRed = _makePixelRgba(gl, textureBufferData, (byte)255, (byte)0, (byte)0, (byte)255);
			this.pixelGreen = _makePixelRgba(gl, textureBufferData, (byte)0, (byte)255, (byte)0, (byte)255);
			
			// We use the semi-transparent green for "progress" overlays.
			this.pixelGreenAlpha = _makePixelRgba(gl, textureBufferData, (byte)0, (byte)255, (byte)0, (byte)100);
			
			// We use the semi-transparent blue for "under water" overlay layer.
			this.pixelBlueAlpha = _makePixelRgba(gl, textureBufferData, (byte)0, (byte)0, (byte)255, (byte)100);
			
			// We use a mostly-opaque orange for "under lava" overlay layer.
			this.pixelOrangeLava = _makePixelRgba(gl, textureBufferData, (byte)255, (byte)69, (byte)0, (byte)220);
			
			Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
		}
		
		public void shutdown(GL20 gl)
		{
			// We don't own _itemAtlas.
			this.textManager.shutdown();
			_program.delete();
			_verticesUnitSquare.delete(gl);
			_verticesItemSquare.delete(gl);
			_verticesReticleLines.delete(gl);
			gl.glDeleteTexture(this.pixelLightGrey);
			gl.glDeleteTexture(this.pixelDarkGreyAlpha);
			gl.glDeleteTexture(this.pixelRed);
			gl.glDeleteTexture(this.pixelGreen);
			gl.glDeleteTexture(this.pixelGreenAlpha);
			gl.glDeleteTexture(this.pixelBlueAlpha);
			gl.glDeleteTexture(this.pixelOrangeLava);
		}
	}


	private final GL20 _gl;
	private final Resources _resources;
	public final TextManager textManager;
	public final int pixelLightGrey;
	public final int pixelDarkGreyAlpha;
	public final int pixelRed;
	public final int pixelGreen;
	public final int pixelGreenAlpha;
	public final int pixelBlueAlpha;
	public final int pixelOrangeLava;

	public GlUi(GL20 gl, LoadedResources resources)
	{
		_gl = gl;
		_resources = resources.glui();
		
		// The text manager is still public since some callers need to issue specific queries to it to mouse-over handling.
		this.textManager = _resources.textManager;
		
		// We use a light grey pixel for a window "frame".
		this.pixelLightGrey = _resources.pixelLightGrey;
		
		// We use a dark grey pixel, with partial alpha, for a window "background".
		this.pixelDarkGreyAlpha = _resources.pixelDarkGreyAlpha;
		
		// We use the Red/Green pixels for outlines of frames in a few cases.
		this.pixelRed = _resources.pixelRed;
		this.pixelGreen = _resources.pixelGreen;
		
		// We use the semi-transparent green for "progress" overlays.
		this.pixelGreenAlpha = _resources.pixelGreenAlpha;
		
		// We use the semi-transparent blue for "under water" overlay layer.
		this.pixelBlueAlpha = _resources.pixelBlueAlpha;
		
		// We use a mostly-opaque orange for "under lava" overlay layer.
		this.pixelOrangeLava = _resources.pixelOrangeLava;
	}

	public void enterUiRenderMode()
	{
		// We use the orthographic projection and no depth buffer for all overlay windows.
		_gl.glDisable(GL20.GL_DEPTH_TEST);
		_gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
		_resources._program.useProgram();
		_gl.glUniform1i(_resources._uTexture, 0);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	public void drawWholeTextureRect(int texture, float left, float bottom, float right, float top)
	{
		_drawWholeTextureRect(texture, left, bottom, right, top);
	}

	public void drawReticle(float xScale, float yScale)
	{
		// We will use the highlight texture for the reticle.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, this.pixelLightGrey);
		_gl.glUniform2f(_resources._uOffset, 0.0f, 0.0f);
		_gl.glUniform2f(_resources._uScale, xScale, yScale);
		_gl.glUniform2f(_resources._uTextureBaseOffset, 0.0f, 0.0f);
		_resources._verticesReticleLines.drawAllLines(_gl);
	}

	public void drawItemTextureRect(Item item, float left, float bottom, float right, float top)
	{
		float[] itemTextureBase = _resources._itemAtlas.baseOfTexture(item.number(), ItemVariant.NONE);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._itemAtlas.texture);
		float xScale = (right - left);
		float yScale = (top - bottom);
		_gl.glUniform2f(_resources._uOffset, left, bottom);
		_gl.glUniform2f(_resources._uScale, xScale, yScale);
		_gl.glUniform2f(_resources._uTextureBaseOffset, itemTextureBase[0], itemTextureBase[1]);
		_resources._verticesItemSquare.drawAllTriangles(_gl);
	}

	public float getLabelWidth(float height, String label)
	{
		TextManager.Element element = this.textManager.lazilyLoadStringTexture(label);
		float textureAspect = element.aspectRatio();
		return textureAspect * height;
	}

	public float drawLabel(float left, float bottom, float top, String label)
	{
		TextManager.Element element = this.textManager.lazilyLoadStringTexture(label);
		float textureAspect = element.aspectRatio();
		float right = left + textureAspect * (top - bottom);
		_drawWholeTextureRect(element.textureObject(), left, bottom, right, top);
		return right;
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

	private void _drawWholeTextureRect(int texture, float left, float bottom, float right, float top)
	{
		// This helper assumes that we are drawing the full texture (used for pixels and text, not item atlasses, etc).
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
		float xScale = (right - left);
		float yScale = (top - bottom);
		_gl.glUniform2f(_resources._uOffset, left, bottom);
		_gl.glUniform2f(_resources._uScale, xScale, yScale);
		_gl.glUniform2f(_resources._uTextureBaseOffset, 0.0f, 0.0f);
		_resources._verticesUnitSquare.drawAllTriangles(_gl);
	}
}
