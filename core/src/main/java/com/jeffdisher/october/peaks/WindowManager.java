package com.jeffdisher.october.peaks;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.utils.Assert;


/**
 * This class manages the overlays in the system which are drawn into a 2D orthographic plane on top of the existing
 * world.
 */
public class WindowManager
{
	public static final float OUTLINE_SIZE = 0.01f;
	public static final float HOTBAR_ITEM_SCALE = 0.1f;
	public static final float HOTBAR_ITEM_SPACING = 0.05f;
	public static final float HOTBAR_BOTTOM_Y = -0.95f;
	public static final float META_DATA_LABEL_WIDTH = 0.1f;
	public static final float META_DATA_ROW_HEIGHT = 0.05f;
	public static final float META_DATA_BOX_LEFT = 0.8f;
	public static final float META_DATA_BOX_BOTTOM = -0.95f;

	private final GL20 _gl;
	private final TextManager _textManager;
	private final int _program;
	private final int _uOffset;
	private final int _uScale;
	private final int _uTexture;
	private final int _verticesUnitSquare;
	private final int _pixelLightGrey;
	private final int _pixelDarkGreyAlpha;
	private Entity _projectedEntity;

	public WindowManager(GL20 gl)
	{
		_gl = gl;
		_textManager = new TextManager(_gl);
		
		// Create the program we will use for the window overlays.
		// The overlays are all rectangular tiles representing windows, graphic tiles, or text tiles.
		// This means that the only mesh we will use is a unit square and we will apply a scaling factor and offset
		// location to place and size it correctly.
		// In order to simplify the usage, we will assume that all colour data originates in textures (but some of the
		// textures may just be single-pixel colour data).
		_program = GraphicsHelpers.fullyLinkedProgram(_gl
				, _readUtf8Asset("windows.vert")
				, _readUtf8Asset("windows.frag")
				, new String[] {
						"aPosition",
						"aTexture",
				}
		);
		_uOffset = _gl.glGetUniformLocation(_program, "uOffset");
		_uScale = _gl.glGetUniformLocation(_program, "uScale");
		_uTexture = _gl.glGetUniformLocation(_program, "uTexture");
		
		// Create the unit square we will use for vertices.
		_verticesUnitSquare = _defineCommonVertices(_gl);
		
		// Build the initial pixel textures.
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(4);
		textureBufferData.order(ByteOrder.nativeOrder());
		
		// We use a light grey pixel for a window "frame".
		_pixelLightGrey = _gl.glGenTexture();
		textureBufferData.put(new byte[] { (byte) 180, (byte)255 });
		textureBufferData.flip();
		gl.glBindTexture(GL20.GL_TEXTURE_2D, _pixelLightGrey);
		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_LUMINANCE_ALPHA, 1, 1, 0, GL20.GL_LUMINANCE_ALPHA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
		gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
		textureBufferData.clear();
		
		// We use a dark grey pixel, with partial alpha, for a window "background".
		_pixelDarkGreyAlpha = _gl.glGenTexture();
		textureBufferData.put(new byte[] { 32, (byte)196 });
		textureBufferData.flip();
		gl.glBindTexture(GL20.GL_TEXTURE_2D, _pixelDarkGreyAlpha);
		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_LUMINANCE_ALPHA, 1, 1, 0, GL20.GL_LUMINANCE_ALPHA, GL20.GL_UNSIGNED_BYTE, textureBufferData);
		gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
		textureBufferData.clear();
		
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
	}

	public void drawCommonOverlays()
	{
		// We use the orthographic projection and no depth buffer for all overlay windows.
		_gl.glDisable(GL20.GL_DEPTH_TEST);
		_gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
		_gl.glUseProgram(_program);
		_gl.glUniform1i(_uTexture, 0);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		if (null != _projectedEntity)
		{
			_drawHotbar();
			_drawEntityMetaData();
			Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		}
	}

	public void setThisEntity(Entity projectedEntity)
	{
		_projectedEntity = projectedEntity;
	}


	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static int _defineCommonVertices(GL20 gl)
	{
		float height = 1.0f;
		float width = 1.0f;
		float textureBaseU = 0.0f;
		float textureBaseV = 0.0f;
		float textureSize = 1.0f;
		float[] vertices = new float[] {
				0.0f, 0.0f, textureBaseU, textureBaseV + textureSize,
				width, height, textureBaseU + textureSize, textureBaseV,
				0.0f, height, textureBaseU, textureBaseV,
				
				0.0f, 0.0f, textureBaseU, textureBaseV + textureSize,
				width, 0.0f, textureBaseU + textureSize, textureBaseV + textureSize,
				width, height, textureBaseU + textureSize, textureBaseV,
		};
		ByteBuffer direct = ByteBuffer.allocateDirect(vertices.length * Float.BYTES);
		direct.order(ByteOrder.nativeOrder());
		for (float f : vertices)
		{
			direct.putFloat(f);
		}
		direct.flip();
		
		int entityBuffer = gl.glGenBuffer();
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, entityBuffer);
		gl.glBufferData(GL20.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, direct.asFloatBuffer(), GL20.GL_STATIC_DRAW);
		return entityBuffer;
	}

	private void _drawHotbar()
	{
		float hotbarWidth = ((float)Entity.HOTBAR_SIZE * HOTBAR_ITEM_SCALE) + ((float)(Entity.HOTBAR_SIZE - 1) * HOTBAR_ITEM_SPACING);
		float nextLeftButton = - hotbarWidth / 2.0f;
		for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
		{
			_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, nextLeftButton, HOTBAR_BOTTOM_Y, nextLeftButton + HOTBAR_ITEM_SCALE, HOTBAR_BOTTOM_Y + HOTBAR_ITEM_SCALE);
			nextLeftButton += HOTBAR_ITEM_SCALE + HOTBAR_ITEM_SPACING;
		}
	}

	private void _drawEntityMetaData()
	{
		_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, META_DATA_BOX_LEFT, META_DATA_BOX_BOTTOM, META_DATA_BOX_LEFT + 1.5f * META_DATA_LABEL_WIDTH, META_DATA_BOX_BOTTOM + 3.0f * META_DATA_ROW_HEIGHT);
		
		float valueMargin = META_DATA_BOX_LEFT + META_DATA_LABEL_WIDTH;
		
		// We will use the greater of authoritative and projected for most of these stats.
		// That way, we get the stability of the authoritative numbers but the quick response to eating/breathing actions)
		byte health = _projectedEntity.health();
		float base = META_DATA_BOX_BOTTOM + 2.0f * META_DATA_ROW_HEIGHT;
		float top = base + META_DATA_ROW_HEIGHT;
		_drawLabel(META_DATA_BOX_LEFT, base, top, "Health");
		_drawLabel(valueMargin, base, top, Byte.toString(health));
		
		byte food = _projectedEntity.food();
		base = META_DATA_BOX_BOTTOM + 1.0f * META_DATA_ROW_HEIGHT;
		top = base + META_DATA_ROW_HEIGHT;
		_drawLabel(META_DATA_BOX_LEFT, base, top, "Food");
		_drawLabel(valueMargin, base, top, Byte.toString(food));
		
		int breath = _projectedEntity.breath();
		base = META_DATA_BOX_BOTTOM + 0.0f * META_DATA_ROW_HEIGHT;
		top = base + META_DATA_ROW_HEIGHT;
		_drawLabel(META_DATA_BOX_LEFT, base, top, "Breath");
		_drawLabel(valueMargin, base, top, Integer.toString(breath));
	}

	private float _drawLabel(float left, float bottom, float top, String label)
	{
		TextManager.Element element = _textManager.lazilyLoadStringTexture(label);
		float textureAspect = element.aspectRatio();
		float right = left + textureAspect * (top - bottom);
		_drawTextElement(left, bottom, right, top, element.textureObject());
		return right;
	}

	private void _drawTextElement(float left, float bottom, float right, float top, int labelTexture)
	{
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, labelTexture);
		_drawRect(left, bottom, right, top);
	}

	private void _drawOverlayFrame(int backgroundTexture, int outlineTexture, float left, float bottom, float right, float top)
	{
		// We want draw the frame and then the space on top of that.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, outlineTexture);
		_drawRect(left - OUTLINE_SIZE, bottom - OUTLINE_SIZE, right + OUTLINE_SIZE, top + OUTLINE_SIZE);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, backgroundTexture);
		_drawRect(left, bottom, right, top);
	}

	private void _drawRect(float left, float bottom, float right, float top)
	{
		// NOTE:  This assumes that texture unit 0 is already bound to the appropriate texture.
		// The unit vertex buffer has 0.0 - 1.0 on both axes so scale within that.
		float xScale = (right - left);
		float yScale = (top - bottom);
		_gl.glUniform2f(_uOffset, left, bottom);
		_gl.glUniform2f(_uScale, xScale, yScale);
		_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _verticesUnitSquare);
		_gl.glEnableVertexAttribArray(0);
		_gl.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 0);
		_gl.glEnableVertexAttribArray(1);
		_gl.glVertexAttribPointer(1, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
		_gl.glDrawArrays(GL20.GL_TRIANGLES, 0, 6);
	}
}
