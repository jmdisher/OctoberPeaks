package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.IntConsumer;

import com.jeffdisher.october.peaks.textures.TextManager;
import com.jeffdisher.october.types.Item;


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

	public static void renderItem(GlUi ui, float left, float bottom, float right, float top, int outlineTexture, Item item, int count, float progress, boolean isMouseOver)
	{
		_renderItem(ui, left, bottom, right, top, outlineTexture, item, count, progress, isMouseOver);
	}

	public static void drawRawTextCentredAtTop(GlUi ui, float centreX, float top, String text)
	{
		TextManager.Element element = ui.textManager.lazilyLoadStringTexture(text);
		float bottom = top - GENERAL_TEXT_HEIGHT;
		float width = element.aspectRatio() * GENERAL_TEXT_HEIGHT;
		float left = centreX - (width / 2.0f);
		float right = centreX + (width / 2.0f);
		ui.drawWholeTextureRect(element.textureObject(), left, bottom, right, top);
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

	/**
	 * Fills the given bounds with the default UI background and draws a minimal outline within the bounds.  If
	 * shouldHighlight is set, then the background will be highlighted.
	 * 
	 * @param gl The UI helpers.
	 * @param bounds The bounds to fill.
	 * @param shouldHighlight True if the background should be highlighted.
	 */
	public static void drawOutline(GlUi gl, Rect bounds, boolean shouldHighlight)
	{
		int backgroundTexture = shouldHighlight
				? gl.pixelLightGrey
				: gl.pixelDarkGreyAlpha
		;
		gl.drawWholeTextureRect(gl.pixelLightGrey, bounds.leftX(), bounds.bottomY(), bounds.rightX(), bounds.topY());
		gl.drawWholeTextureRect(backgroundTexture, bounds.leftX() + OUTLINE_SIZE, bounds.bottomY() + OUTLINE_SIZE, bounds.rightX() - OUTLINE_SIZE, bounds.topY() - OUTLINE_SIZE);
	}

	/**
	 * Draws the given text centred inside the given bounds, sized to the vertical height of the bounds.
	 * 
	 * @param gl The UI helpers.
	 * @param bounds The bounds of the text.
	 * @param text The text to write.
	 */
	public static void drawTextCentred(GlUi ui, Rect bounds, String text)
	{
		float centreX = bounds.getCentreX();
		float centreY = bounds.getCentreY();
		float heightY = bounds.getHeight();
		TextManager.Element element = ui.textManager.lazilyLoadStringTexture(text);
		float halfTextHeight = GENERAL_TEXT_HEIGHT / 2.0f;
		float halfTextWidth = (element.aspectRatio() * heightY) / 2.0f;
		float left = centreX - halfTextWidth;
		float right = centreX + halfTextWidth;
		float bottom = centreY - halfTextHeight;
		float top = centreY + halfTextHeight;
		ui.drawWholeTextureRect(element.textureObject(), left, bottom, right, top);
	}

	/**
	 * Returns the width of the given string if drawn with the given height.
	 * 
	 * @param gl The UI helpers.
	 * @param text The text.
	 * @param height The assumed height of the text.
	 * @return The width of the string.
	 */
	public static float getTextWidth(GlUi ui, String text, float height)
	{
		TextManager.Element element = ui.textManager.lazilyLoadStringTexture(text);
		return element.aspectRatio() * height;
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
		TextManager.Element element = ui.textManager.lazilyLoadStringTexture(text);
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


	public static interface ItemRenderer<T>
	{
		void drawItem(float left, float bottom, float right, float top, T item, boolean isMouseOver);
	}
}
