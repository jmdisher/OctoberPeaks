package com.jeffdisher.october.peaks;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.PartialEntity;
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
	public static final float GENERAL_TEXT_HEIGHT = 0.1f;
	public static final float SMALL_TEXT_HEIGHT = 0.05f;
	public static final float META_DATA_LABEL_WIDTH = 0.1f;
	public static final float META_DATA_BOX_LEFT = 0.8f;
	public static final float META_DATA_BOX_BOTTOM = -0.95f;
	public static final float SELECTED_BOX_LEFT = 0.05f;
	public static final float SELECTED_BOX_BOTTOM = 0.90f;
	public static final float ARMOUR_SLOT_SCALE = 0.1f;
	public static final float ARMOUR_SLOT_SPACING = 0.05f;
	public static final float ARMOUR_SLOT_RIGHT_EDGE = 0.95f;
	public static final float ARMOUR_SLOT_TOP_EDGE = 0.95f;
	public static final float WINDOW_ITEM_SIZE = 0.1f;
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;
	public static final _WindowDimensions WINDOW_TOP_LEFT = new _WindowDimensions(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final _WindowDimensions WINDOW_TOP_RIGHT = new _WindowDimensions(0.05f, 0.05f, ARMOUR_SLOT_RIGHT_EDGE - ARMOUR_SLOT_SCALE - ARMOUR_SLOT_SPACING, 0.95f);
	public static final _WindowDimensions WINDOW_BOTTOM = new _WindowDimensions(-0.95f, -0.80f, 0.95f, -0.05f);

	private final Environment _env;
	private final GL20 _gl;
	private final TextManager _textManager;
	private final Map<CuboidAddress, IReadOnlyCuboidData> _cuboids;
	private final int _program;
	private final int _uOffset;
	private final int _uScale;
	private final int _uTexture;
	private final int _verticesUnitSquare;
	private final int _pixelLightGrey;
	private final int _pixelDarkGreyAlpha;
	private Entity _projectedEntity;

	public WindowManager(Environment env, GL20 gl)
	{
		_env = env;
		_gl = gl;
		_textManager = new TextManager(_gl);
		_cuboids = new HashMap<>();
		
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

	public void drawActiveWindows(AbsoluteLocation selectedBlock, PartialEntity selectedEntity, boolean isInventoryVisible)
	{
		// We use the orthographic projection and no depth buffer for all overlay windows.
		_gl.glDisable(GL20.GL_DEPTH_TEST);
		_gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
		_gl.glUseProgram(_program);
		_gl.glUniform1i(_uTexture, 0);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _projectedEntity)
		{
			_drawHotbar();
			_drawEntityMetaData();
			Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		}
		
		// If there is anything selected, draw its description at the top of the screen (we always prioritize the block, but at most one of these can be non-null).
		if (isInventoryVisible)
		{
			// We will flesh these out later but for now just draw the empty windows.
			_drawWindow("Inventory", WINDOW_TOP_RIGHT);
			_drawWindow("Crafting", WINDOW_TOP_LEFT);
			_drawWindow("Block Inventory", WINDOW_BOTTOM);
			
			// Also draw the armour slots.
			_drawArmourSlots();
		}
		else if (null != selectedBlock)
		{
			// Draw the block information.
			IReadOnlyCuboidData cuboid = _cuboids.get(selectedBlock.getCuboidAddress());
			if (null != cuboid)
			{
				BlockProxy proxy = new BlockProxy(selectedBlock.getBlockAddress(), cuboid);
				Block blockUnderMouse = proxy.getBlock();
				if (_env.special.AIR != blockUnderMouse)
				{
					Item itemUnderMouse = blockUnderMouse.item();
					_drawTextInFrame(SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM, itemUnderMouse.name());
				}
			}
		}
		else if (null != selectedEntity)
		{
			// Draw the entity information.
			_drawTextInFrame(SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM, selectedEntity.type().name());
		}
	}

	public void setThisEntity(Entity projectedEntity)
	{
		_projectedEntity = projectedEntity;
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		_cuboids.put(cuboid.getCuboidAddress(), cuboid);
	}

	public void removeCuboid(CuboidAddress address)
	{
		IReadOnlyCuboidData removed = _cuboids.remove(address);
		Assert.assertTrue(null != removed);
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
		_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, META_DATA_BOX_LEFT, META_DATA_BOX_BOTTOM, META_DATA_BOX_LEFT + 1.5f * META_DATA_LABEL_WIDTH, META_DATA_BOX_BOTTOM + 3.0f * SMALL_TEXT_HEIGHT);
		
		float valueMargin = META_DATA_BOX_LEFT + META_DATA_LABEL_WIDTH;
		
		// We will use the greater of authoritative and projected for most of these stats.
		// That way, we get the stability of the authoritative numbers but the quick response to eating/breathing actions)
		byte health = _projectedEntity.health();
		float base = META_DATA_BOX_BOTTOM + 2.0f * SMALL_TEXT_HEIGHT;
		float top = base + SMALL_TEXT_HEIGHT;
		_drawLabel(META_DATA_BOX_LEFT, base, top, "Health");
		_drawLabel(valueMargin, base, top, Byte.toString(health));
		
		byte food = _projectedEntity.food();
		base = META_DATA_BOX_BOTTOM + 1.0f * SMALL_TEXT_HEIGHT;
		top = base + SMALL_TEXT_HEIGHT;
		_drawLabel(META_DATA_BOX_LEFT, base, top, "Food");
		_drawLabel(valueMargin, base, top, Byte.toString(food));
		
		int breath = _projectedEntity.breath();
		base = META_DATA_BOX_BOTTOM + 0.0f * SMALL_TEXT_HEIGHT;
		top = base + SMALL_TEXT_HEIGHT;
		_drawLabel(META_DATA_BOX_LEFT, base, top, "Breath");
		_drawLabel(valueMargin, base, top, Integer.toString(breath));
	}

	private void _drawWindow(String title, _WindowDimensions dimensions)
	{
		// Draw the window outline.
		_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, dimensions.leftX, dimensions.bottomY, dimensions.rightX, dimensions.topY);
		
		// Draw the title.
		_drawLabel(dimensions.leftX, dimensions.topY - WINDOW_TITLE_HEIGHT, dimensions.topY, title.toUpperCase());
	}

	private void _drawArmourSlots()
	{
		float nextTopSlot = ARMOUR_SLOT_TOP_EDGE;
		for (int i = 0; i < 4; ++i)
		{
			float left = ARMOUR_SLOT_RIGHT_EDGE - ARMOUR_SLOT_SCALE;
			float bottom = nextTopSlot - ARMOUR_SLOT_SCALE;
			_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, left, bottom, ARMOUR_SLOT_RIGHT_EDGE, nextTopSlot);
			nextTopSlot -= ARMOUR_SLOT_SCALE + ARMOUR_SLOT_SPACING;
		}
	}

	private void _drawTextInFrame(float left, float bottom, String text)
	{
		TextManager.Element element = _textManager.lazilyLoadStringTexture(text.toUpperCase());
		float top = bottom + GENERAL_TEXT_HEIGHT;
		float right = left + element.aspectRatio() * (top - bottom);
		
		_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, left, bottom, right, top);
		_drawTextElement(left, bottom, right, top, element.textureObject());
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


	private static record _WindowDimensions(float leftX
			, float bottomY
			, float rightX
			, float topY
	) {}
}
