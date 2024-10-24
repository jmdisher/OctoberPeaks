package com.jeffdisher.october.peaks;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
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
	public static final float WINDOW_PAGE_BUTTON_HEIGHT = 0.05f;
	public static final _WindowDimensions WINDOW_TOP_LEFT = new _WindowDimensions(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final _WindowDimensions WINDOW_TOP_RIGHT = new _WindowDimensions(0.05f, 0.05f, ARMOUR_SLOT_RIGHT_EDGE - ARMOUR_SLOT_SCALE - ARMOUR_SLOT_SPACING, 0.95f);
	public static final _WindowDimensions WINDOW_BOTTOM = new _WindowDimensions(-0.95f, -0.80f, 0.95f, -0.05f);

	private final Environment _env;
	private final GL20 _gl;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final TextManager _textManager;
	private final int _program;
	private final int _uOffset;
	private final int _uScale;
	private final int _uTexture;
	private final int _verticesUnitSquare;
	private final int _pixelLightGrey;
	private final int _pixelDarkGreyAlpha;
	private final int _pixelRed;
	private final int _pixelGreen;
	private final int _pixelGreenAlpha;
	private Entity _projectedEntity;

	// We define these public rendering helpers in order to avoid adding special public interfaces or spreading rendering logic around.
	public final ItemRenderer<Items> renderItemStack;
	public final ItemRenderer<NonStackableItem> renderNonStackable;
	public final ItemRenderer<CraftDescription> renderCraftOperation;
	public final HoverRenderer<Item> hoverItem;
	public final HoverRenderer<CraftDescription> hoverCraftOperation;

	public WindowManager(Environment env, GL20 gl, Function<AbsoluteLocation, BlockProxy> blockLookup)
	{
		_env = env;
		_gl = gl;
		_blockLookup = blockLookup;
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
		_pixelLightGrey = _makePixelRgba(_gl, textureBufferData, (byte)180, (byte)180, (byte)180, (byte)255);
		
		// We use a dark grey pixel, with partial alpha, for a window "background".
		_pixelDarkGreyAlpha = _makePixelRgba(_gl, textureBufferData, (byte)32, (byte)32, (byte)32, (byte)196);
		
		// We use the Red/Green pixels for outlines of frames in a few cases.
		_pixelRed = _makePixelRgba(_gl, textureBufferData, (byte)255, (byte)0, (byte)0, (byte)255);
		_pixelGreen = _makePixelRgba(_gl, textureBufferData, (byte)0, (byte)255, (byte)0, (byte)255);
		
		// We use the semi-transparent green for "progress" overlays.
		_pixelGreenAlpha = _makePixelRgba(_gl, textureBufferData, (byte)0, (byte)255, (byte)0, (byte)100);
		
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// Set up our public rendering helpers.
		this.renderItemStack = (float left, float bottom, float right, float top, Items item, boolean isMouseOver) -> {
			float noProgress = 0.0f;
			_renderItem(left, bottom, right, top, _pixelLightGrey, item.type(), item.count(), noProgress, isMouseOver);
		};
		this.renderNonStackable = (float left, float bottom, float right, float top, NonStackableItem item, boolean isMouseOver) -> {
			Item type = item.type();
			int maxDurability = _env.durability.getDurability(type);
			int count = 0;
			float progress = (maxDurability > 0)
					? (float)item.durability() / (float)maxDurability
					: 0.0f
			;
			_renderItem(left, bottom, right, top, _pixelLightGrey, type, count, progress, isMouseOver);
		};
		this.renderCraftOperation = (float left, float bottom, float right, float top, CraftDescription item, boolean isMouseOver) -> {
			// Note that this is often used to render non-operations, just as a generic craft rendering helper.
			// NOTE:  We are assuming only a single output type.
			boolean isValid = true;
			for (ItemRequirement input : item.input)
			{
				if (input.available < input.required)
				{
					isValid = false;
					break;
				}
			}
			int outlineTexture = isValid
					? _pixelGreen
					: _pixelRed
			;
			_renderItem(left, bottom, right, top, outlineTexture, item.output.type(), item.output.count(), item.progress, isMouseOver);
		};
		this.hoverItem = (float glX, float glY, Item item) -> {
			// Just draw the name.
			String name = item.name();
			_drawTextInFrame(glX, glY - GENERAL_TEXT_HEIGHT, name);
		};
		this.hoverCraftOperation = (float glX, float glY, CraftDescription item) -> {
			String name = item.name;
			
			// Calculate the dimensions (we have a title and then a list of input items below this).
			float widthOfTitle = _getLabelWidth(WINDOW_TITLE_HEIGHT, name);
			float widthOfItems = (float)item.input.length * WINDOW_ITEM_SIZE + (float)(item.input.length + 1) * WINDOW_MARGIN;
			float widthOfHover = Math.max(widthOfTitle, widthOfItems);
			float heightOfHover = WINDOW_TITLE_HEIGHT + WINDOW_ITEM_SIZE + 3 * WINDOW_MARGIN;
			
			// We can now draw the frame.
			_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, glX, glY - heightOfHover, glX + widthOfHover, glY);
			
			// Draw the title.
			_drawLabel(glX, glY - WINDOW_TITLE_HEIGHT, glY, name);
			
			// Draw the inputs.
			float inputLeft = glX + WINDOW_MARGIN;
			float inputTop = glY - 2 * WINDOW_MARGIN - WINDOW_TITLE_HEIGHT;
			float inputBottom = inputTop - WINDOW_ITEM_SIZE;
			for (ItemRequirement items : item.input)
			{
				float noProgress = 0.0f;
				boolean isMouseOver = false;
				int outlineTexture = (items.available >= items.required)
						? _pixelGreen
						: _pixelRed
				;
				_renderItem(inputLeft, inputBottom, inputLeft + WINDOW_ITEM_SIZE, inputBottom + WINDOW_ITEM_SIZE, outlineTexture, items.type, items.required, noProgress, isMouseOver);
				inputLeft += WINDOW_ITEM_SIZE + WINDOW_MARGIN;
			}
		};
	}

	public <A, B, C> void drawActiveWindows(AbsoluteLocation selectedBlock, PartialEntity selectedEntity, WindowData<A> topLeft, WindowData<B> topRight, WindowData<C> bottom, float glX, float glY)
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
		
		// We will disable the handling of any selection if we draw any overlay windows (they should be null in that case, anyway).
		boolean didDrawWindows = false;
		if (null != topLeft)
		{
			_drawWindow(topLeft, WINDOW_TOP_LEFT, glX, glY);
			didDrawWindows = true;
		}
		if (null != topRight)
		{
			_drawWindow(topRight, WINDOW_TOP_RIGHT, glX, glY);
			didDrawWindows = true;
		}
		if (null != bottom)
		{
			_drawWindow(bottom, WINDOW_BOTTOM, glX, glY);
			didDrawWindows = true;
		}
		
		// If there is anything selected, draw its description at the top of the screen (we always prioritize the block, but at most one of these can be non-null).
		if (didDrawWindows)
		{
			// Also draw the armour slots.
			_drawArmourSlots();
		}
		else if (null != selectedBlock)
		{
			// Draw the block information.
			BlockProxy proxy = _blockLookup.apply(selectedBlock);
			if (null != proxy)
			{
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

	private <T> void _drawWindow(WindowData<T> data, _WindowDimensions dimensions, float glX, float glY)
	{
		// Draw the window outline.
		_drawOverlayFrame(_pixelDarkGreyAlpha, _pixelLightGrey, dimensions.leftX, dimensions.bottomY, dimensions.rightX, dimensions.topY);
		
		// Draw the title.
		float labelRight = _drawLabel(dimensions.leftX, dimensions.topY - WINDOW_TITLE_HEIGHT, dimensions.topY, data.name.toUpperCase());
		if (data.maxSize > 0)
		{
			String extraTitle = String.format("(%d/%d)", data.usedSize, data.maxSize);
			float bottom = dimensions.topY - WINDOW_TITLE_HEIGHT;
			_drawLabel(labelRight + WINDOW_MARGIN, bottom, bottom + WINDOW_TITLE_HEIGHT, extraTitle.toUpperCase());
		}
		
		// We want to draw these in a grid, in rows.  Leave space for the right margin since we count the left margin in the element sizing.
		float xSpace = dimensions.rightX - dimensions.leftX - WINDOW_MARGIN;
		float ySpace = dimensions.topY - dimensions.bottomY - WINDOW_MARGIN;
		// The size of each item is the margin before the element and the element itself.
		float spacePerElement = WINDOW_ITEM_SIZE + WINDOW_MARGIN;
		int itemsPerRow = (int) Math.round(Math.floor(xSpace / spacePerElement));
		int rowsPerPage = (int) Math.round(Math.floor(ySpace / spacePerElement));
		int itemsPerPage = itemsPerRow * rowsPerPage;
		int xElement = 0;
		int yElement = 0;
		
		float leftMargin = dimensions.leftX + WINDOW_MARGIN;
		// Leave space for top margin and title.
		float topMargin = dimensions.topY - WINDOW_TITLE_HEIGHT - WINDOW_MARGIN;
		int totalItems = data.items.size();
		int pageCount = ((totalItems - 1) / itemsPerPage) + 1;
		// Be aware that this may have changed without the caller knowing it.
		int currentPage = Math.min(data.currentPage, pageCount);
		int startingIndex = currentPage * itemsPerPage;
		int firstIndexBeyondPage = startingIndex + itemsPerPage;
		if (firstIndexBeyondPage > totalItems)
		{
			firstIndexBeyondPage = totalItems;
		}
		
		T hoverOver = null;
		for (T elt : data.items.subList(startingIndex, firstIndexBeyondPage))
		{
			// We want to render these left->right, top->bottom but GL is left->right, bottom->top so we increment X and Y in opposite ways.
			float left = leftMargin + (xElement * spacePerElement);
			float top = topMargin - (yElement * spacePerElement);
			float bottom = top - WINDOW_ITEM_SIZE;
			float right = left + WINDOW_ITEM_SIZE;
			// We only handle the mouse-over if there is a handler we will notify.
			boolean isMouseOver = _isMouseOver(left, bottom, right, top, glX, glY);
			data.renderItem.drawItem(left, bottom, right, top, elt, isMouseOver);
			if (isMouseOver)
			{
				hoverOver = elt;
			}
			
			// We also want to call the associated handler.
			if (isMouseOver && (null != data.eventHoverOverItem))
			{
				data.eventHoverOverItem.accept(elt);
			}
			
			// On to the next item.
			xElement += 1;
			if (xElement >= itemsPerRow)
			{
				xElement = 0;
				yElement += 1;
			}
		}
		
		// Draw our pagination buttons if they make sense.
		if (pageCount > 1)
		{
			boolean canPageBack = (currentPage > 0);
			boolean canPageForward = (currentPage < (pageCount - 1));
			float buttonTop = dimensions.topY - WINDOW_PAGE_BUTTON_HEIGHT;
			float buttonBase = buttonTop - WINDOW_PAGE_BUTTON_HEIGHT;
			if (canPageBack)
			{
				float left = dimensions.rightX - 0.25f;
				boolean isMouseOver = _drawTextInFrameWithHoverCheck(left, buttonBase, "<", glX, glY);
				if (isMouseOver)
				{
					data.eventHoverChangePage.accept(currentPage - 1);
				}
			}
			String label = (currentPage + 1) + " / " + pageCount;
			_drawLabel(dimensions.rightX - 0.2f, buttonBase, buttonTop, label);
			if (canPageForward)
			{
				float left = dimensions.rightX - 0.1f;
				boolean isMouseOver = _drawTextInFrameWithHoverCheck(left, buttonBase, ">", glX, glY);
				if (isMouseOver)
				{
					data.eventHoverChangePage.accept(currentPage + 1);
				}
			}
		}
		
		// Draw hover-over details, if applicable.
		if (null != hoverOver)
		{
			data.renderHover.drawHoverAtPoint(glX, glY, hoverOver);
		}
	}

	private void _renderItem(float left, float bottom, float right, float top, int outlineTexture, Item item, int count, float progress, boolean isMouseOver)
	{
		// TODO:  Actually draw the item once we have the textures available.
		TextManager.Element element = _textManager.lazilyLoadStringTexture("X");
		
		int backgroundTexture = isMouseOver
				? _pixelLightGrey
				: _pixelDarkGreyAlpha
		;
		
		_drawOverlayFrame(backgroundTexture, outlineTexture, left, bottom, right, top);
		_drawTextElement(left, bottom, right, top, element.textureObject());
		
		// If there is a progress bar, draw it on top.
		if (progress > 0.0f)
		{
			_gl.glBindTexture(GL20.GL_TEXTURE_2D, _pixelGreenAlpha);
			float progressTop = bottom + (top - bottom) * progress;
			_drawRect(left, bottom, right, progressTop);
		}
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
		float outOfRange = -2.0f;
		_drawTextInFrameWithHoverCheck(left, bottom, text, outOfRange, outOfRange);
	}

	private boolean _drawTextInFrameWithHoverCheck(float left, float bottom, String text, float glX, float glY)
	{
		TextManager.Element element = _textManager.lazilyLoadStringTexture(text.toUpperCase());
		float top = bottom + GENERAL_TEXT_HEIGHT;
		float right = left + element.aspectRatio() * (top - bottom);
		
		boolean isMouseOver = _isMouseOver(left, bottom, right, top, glX, glY);
		int backgroundTexture = isMouseOver
				? _pixelLightGrey
				: _pixelDarkGreyAlpha
		;
		
		_drawOverlayFrame(backgroundTexture, _pixelLightGrey, left, bottom, right, top);
		_drawTextElement(left, bottom, right, top, element.textureObject());
		return isMouseOver;
	}

	private float _drawLabel(float left, float bottom, float top, String label)
	{
		TextManager.Element element = _textManager.lazilyLoadStringTexture(label);
		float textureAspect = element.aspectRatio();
		float right = left + textureAspect * (top - bottom);
		_drawTextElement(left, bottom, right, top, element.textureObject());
		return right;
	}

	private float _getLabelWidth(float height, String label)
	{
		TextManager.Element element = _textManager.lazilyLoadStringTexture(label);
		float textureAspect = element.aspectRatio();
		return textureAspect * height;
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

	private static boolean _isMouseOver(float left, float bottom, float right, float top, float glX, float glY)
	{
		return ((left <= glX) && (glX <= right) && (bottom <= glY) && (glY <= top));
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

	public static interface ItemRenderer<T>
	{
		void drawItem(float left, float bottom, float right, float top, T item, boolean isMouseOver);
	}

	public static interface HoverRenderer<T>
	{
		void drawHoverAtPoint(float glX, float glY, T item);
	}

	public static record WindowData<T>(String name
			, int usedSize
			, int maxSize
			, int currentPage
			, IntConsumer eventHoverChangePage
			, List<T> items
			, ItemRenderer<T> renderItem
			, HoverRenderer<T> renderHover
			, Consumer<T> eventHoverOverItem
	) {}

	public static record CraftDescription(String name
			, Items output
			, ItemRequirement[] input
			, float progress
	) {}

	public static record ItemRequirement(Item type
			, int required
			, int available
	) {}

	private static record _WindowDimensions(float leftX
			, float bottomY
			, float rightX
			, float topY
	) {}
}
