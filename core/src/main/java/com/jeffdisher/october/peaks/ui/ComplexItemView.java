package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * An implementation of IView which can render Items or NonStackableItem with either the number in the stack or a
 * display of the durability.
 */
public class ComplexItemView
{
	public static <T> IView<ItemTuple<T>> buildRenderer(GlUi ui
			, IBindOptions<T> options
			, boolean showHoverText
	)
	{
		return (Rect location, Binding<ItemTuple<T>> binding, Point cursor) -> {
			// There are essentially 4 modes:
			// 1) No item and NOT highlighted
			// 2) No item and IS highlighted
			// 3) Stackable (needs number)
			// 4) Non-stackable (needs durability)
			
			boolean isMouseOver = location.containsPoint(cursor);
			float left = location.leftX();
			float bottom = location.bottomY();
			float right = location.rightX();
			float top = location.topY();
			ItemTuple<T> tuple = binding.data;
			Items stack = tuple.stackable();
			NonStackableItem nonStack = tuple.nonStackable();
			int outlineTexture = options.getOutlineTexture(tuple);
			
			if (null != stack)
			{
				// There can be at most one of these set.
				Assert.assertTrue(null == nonStack);
				
				UiIdioms.renderStackableItem(ui, left, bottom, right, top, outlineTexture, stack, isMouseOver);
			}
			else if (null != nonStack)
			{
				UiIdioms.renderNonStackableItem(ui, left, bottom, right, top, outlineTexture, nonStack, isMouseOver);
			}
			else
			{
				int backgroundTexture = isMouseOver
						? ui.pixelLightGrey
						: ui.pixelDarkGreyAlpha
				;
				UiIdioms.drawOverlayFrame(ui, backgroundTexture, outlineTexture, left, bottom, right, top);
			}
			
			return isMouseOver
					? new IAction() {
						@Override
						public void renderHover(Point cursor)
						{
							options.hoverRender(cursor, tuple);
						}
						@Override
						public void takeAction()
						{
							options.hoverAction(tuple);
						}
					}
					: null
			;
		};
	}


	public static interface IBindOptions<T>
	{
		public int getOutlineTexture(ItemTuple<T> context);
		public void hoverRender(Point cursor, ItemTuple<T> context);
		public void hoverAction(ItemTuple<T> context);
	}
}
