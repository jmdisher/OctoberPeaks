package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.IntConsumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.peaks.WindowManager.ItemRenderer;
import com.jeffdisher.october.peaks.textures.TextManager;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * A collection of helper constants and routines which are common to many different parts of the UI.
 */
public class UiIdioms
{
	/**
	 * The outline thickness around a pane in the UI.
	 */
	public static final float OUTLINE_SIZE = 0.01f;
	public static final float GENERAL_TEXT_HEIGHT = 0.1f;
	public static final float WINDOW_PAGE_BUTTON_HEIGHT = 0.05f;

	public static void drawOverlayFrame(GlUi gl, int backgroundTexture, int outlineTexture, float left, float bottom, float right, float top)
	{
		_drawOverlayFrame(gl, backgroundTexture, outlineTexture, left, bottom, right, top);
	}

	public static void renderStackableItem(GlUi ui, float left, float bottom, float right, float top, int outlineTexture, Items item, boolean isMouseOver)
	{
		float noProgress = 0.0f;
		_renderItem(ui, left, bottom, right, top, outlineTexture, item.type(), item.count(), noProgress, isMouseOver);
	}

	public static void renderNonStackableItem(GlUi ui, float left, float bottom, float right, float top, int outlineTexture, NonStackableItem item, boolean isMouseOver)
	{
		Environment env = Environment.getShared();
		Item type = item.type();
		int maxDurability = env.durability.getDurability(type);
		int count = 0;
		float progress = (maxDurability > 0)
				? (float)item.durability() / (float)maxDurability
				: 0.0f
		;
		_renderItem(ui, left, bottom, right, top, outlineTexture, type, count, progress, isMouseOver);
	}

	public static void renderItem(GlUi ui, float left, float bottom, float right, float top, int outlineTexture, Item item, int count, float progress, boolean isMouseOver)
	{
		_renderItem(ui, left, bottom, right, top, outlineTexture, item, count, progress, isMouseOver);
	}

	public static void drawTextInFrame(GlUi ui, float left, float bottom, String text)
	{
		_drawTextInFrameWithHoverCheck(ui, left, bottom, text, null);
	}

	public static void drawTextRootedAtTop(GlUi ui, float left, float top, String text)
	{
		_drawTextInFrameWithHoverCheck(ui, left, top - GENERAL_TEXT_HEIGHT, text, null);
	}

	public static boolean drawTextInFrameWithHoverCheck(GlUi ui, float left, float bottom, String text, Point cursor)
	{
		return _drawTextInFrameWithHoverCheck(ui, left, bottom, text, cursor);
	}

	public static <T> void drawPageButtons(GlUi ui, IntConsumer eventHoverChangePage, float rightX, float topY, Point cursor, int pageCount, int currentPage)
	{
		boolean canPageBack = (currentPage > 0);
		boolean canPageForward = (currentPage < (pageCount - 1));
		float buttonTop = topY - WINDOW_PAGE_BUTTON_HEIGHT;
		float buttonBase = buttonTop - WINDOW_PAGE_BUTTON_HEIGHT;
		if (canPageBack)
		{
			float left = rightX - 0.25f;
			boolean isMouseOver = _drawTextInFrameWithHoverCheck(ui, left, buttonBase, "<", cursor);
			if (isMouseOver)
			{
				eventHoverChangePage.accept(currentPage - 1);
			}
		}
		String label = (currentPage + 1) + " / " + pageCount;
		ui.drawLabel(rightX - 0.2f, buttonBase, buttonTop, label);
		if (canPageForward)
		{
			float left = rightX - 0.1f;
			boolean isMouseOver = _drawTextInFrameWithHoverCheck(ui, left, buttonBase, ">", cursor);
			if (isMouseOver)
			{
				eventHoverChangePage.accept(currentPage + 1);
			}
		}
	}

	public static <T> T drawItemGrid(List<T> data
			, ItemRenderer<T> renderer
			, Point cursor
			, float spacePerElement
			, float sizePerElement
			, int itemsPerRow
			, int itemsPerPage
			, float leftMargin
			, float topMargin
			, int totalItems
			, int currentPage
	)
	{
		int startingIndex = currentPage * itemsPerPage;
		int firstIndexBeyondPage = startingIndex + itemsPerPage;
		if (firstIndexBeyondPage > totalItems)
		{
			firstIndexBeyondPage = totalItems;
		}
		
		T hoverOver = null;
		int xElement = 0;
		int yElement = 0;
		for (T elt : data.subList(startingIndex, firstIndexBeyondPage))
		{
			// We want to render these left->right, top->bottom but GL is left->right, bottom->top so we increment X and Y in opposite ways.
			float left = leftMargin + (xElement * spacePerElement);
			float top = topMargin - (yElement * spacePerElement);
			float bottom = top - sizePerElement;
			float right = left + sizePerElement;
			// We only handle the mouse-over if there is a handler we will notify.
			boolean isMouseOver = new Rect(left, bottom, right, top).containsPoint(cursor);
			renderer.drawItem(left, bottom, right, top, elt, isMouseOver);
			if (isMouseOver)
			{
				hoverOver = elt;
			}
			
			// On to the next item.
			xElement += 1;
			if (xElement >= itemsPerRow)
			{
				xElement = 0;
				yElement += 1;
			}
		}
		return hoverOver;
	}


	private static void _drawOverlayFrame(GlUi gl, int backgroundTexture, int outlineTexture, float left, float bottom, float right, float top)
	{
		// We want draw the frame and then the space on top of that.
		gl.drawWholeTextureRect(outlineTexture, left - OUTLINE_SIZE, bottom - OUTLINE_SIZE, right + OUTLINE_SIZE, top + OUTLINE_SIZE);
		gl.drawWholeTextureRect(backgroundTexture, left, bottom, right, top);
	}

	private static void _renderItem(GlUi ui, float left, float bottom, float right, float top, int outlineTexture, Item item, int count, float progress, boolean isMouseOver)
	{
		// Draw the background.
		int backgroundTexture = isMouseOver
				? ui.pixelLightGrey
				: ui.pixelDarkGreyAlpha
		;
		_drawOverlayFrame(ui, backgroundTexture, outlineTexture, left, bottom, right, top);
		
		// Draw the item.
		ui.drawItemTextureRect(item, left, bottom, right, top);
		
		// Draw the number in the corner (only if it is non-zero).
		if (count > 0)
		{
			TextManager.Element element = ui.textManager.lazilyLoadStringTexture(Integer.toString(count));
			// We want to draw the text in the bottom-left of the box, at half-height.
			float vDelta = (top - bottom) / 2.0f;
			float hDelta = vDelta * element.aspectRatio();
			ui.drawWholeTextureRect(element.textureObject(), left, bottom, left + hDelta, bottom + vDelta);
		}
		
		// If there is a progress bar, draw it on top.
		if (progress > 0.0f)
		{
			float progressTop = bottom + (top - bottom) * progress;
			ui.drawWholeTextureRect(ui.pixelGreenAlpha, left, bottom, right, progressTop);
		}
	}

	private static boolean _drawTextInFrameWithHoverCheck(GlUi ui, float left, float bottom, String text, Point cursor)
	{
		TextManager.Element element = ui.textManager.lazilyLoadStringTexture(text.toUpperCase());
		float top = bottom + GENERAL_TEXT_HEIGHT;
		float right = left + element.aspectRatio() * (top - bottom);
		
		boolean isMouseOver = new Rect(left, bottom, right, top).containsPoint(cursor);
		int backgroundTexture = isMouseOver
				? ui.pixelLightGrey
				: ui.pixelDarkGreyAlpha
		;
		
		_drawOverlayFrame(ui, backgroundTexture, ui.pixelLightGrey, left, bottom, right, top);
		ui.drawWholeTextureRect(element.textureObject(), left, bottom, right, top);
		return isMouseOver;
	}
}
