package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import com.jeffdisher.october.peaks.WindowManager.ItemRenderer;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;


/**
 * Renders the panel with a list of items in an inventory, also maintaining the pagination state in the IView instance.
 */
public class WindowEntityInventory
{
	public static final float WINDOW_ITEM_SIZE = 0.1f;
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;

	public static IView<Inventory> buildRenderer(GlUi ui
			, String title
			, Rect bounds
			, IntConsumer mouseOverKeyConsumer
			, BooleanSupplier shouldChangePage
	)
	{
		ComplexItemView.IBindOptions<Integer> options = new ComplexItemView.IBindOptions<>()
		{
			@Override
			public int getOutlineTexture(ItemTuple<Integer> context)
			{
				// The user's inventory is always drawn with light grey.
				return ui.pixelLightGrey;
			}
			@Override
			public void hoverRender(Point cursor, ItemTuple<Integer> context)
			{
				// We just render the name of the item.
				Item type = context.getItemType();
				String name = type.name();
				UiIdioms.drawTextRootedAtTop(ui, cursor.x(), cursor.y(), name);
			}
			@Override
			public void hoverAction(ItemTuple<Integer> context)
			{
				mouseOverKeyConsumer.accept(context.context());
			}
		};
		IView<ItemTuple<Integer>> itemView = ComplexItemView.buildRenderer(ui, options, false);
		Binding<ItemTuple<Integer>> innerBinding = new Binding<>();
		
		return new IView<>()
		{
			private int _currentPage;
			@Override
			public IAction render(Rect location, Binding<Inventory> binding, Point cursor)
			{
				Inventory data = binding.get();
				
				// Draw the window outline.
				UiIdioms.drawOverlayFrame(ui, ui.pixelDarkGreyAlpha, ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
				
				// Draw the title.
				float labelRight = ui.drawLabel(location.leftX(), location.topY() - WINDOW_TITLE_HEIGHT, location.topY(), title.toUpperCase());
				
				// Draw the capacity.
				String extraTitle = String.format("(%d/%d)", data.currentEncumbrance, data.maxEncumbrance);
				float bottomY = location.topY() - WINDOW_TITLE_HEIGHT;
				ui.drawLabel(labelRight + WINDOW_MARGIN, bottomY, bottomY + WINDOW_TITLE_HEIGHT, extraTitle.toUpperCase());
				
				// We want to draw these in a grid, in rows.  Leave space for the right margin since we count the left margin in the element sizing.
				float xSpace = location.rightX() - location.leftX() - WINDOW_MARGIN;
				float ySpace = location.topY() - location.bottomY() - WINDOW_MARGIN;
				// The size of each item is the margin before the element and the element itself.
				float spacePerElement = WINDOW_ITEM_SIZE + WINDOW_MARGIN;
				int itemsPerRow = (int) Math.round(Math.floor(xSpace / spacePerElement));
				int rowsPerPage = (int) Math.round(Math.floor(ySpace / spacePerElement));
				int itemsPerPage = itemsPerRow * rowsPerPage;
				
				float leftMargin = location.leftX() + WINDOW_MARGIN;
				// Leave space for top margin and title.
				float topMargin = location.topY() - WINDOW_TITLE_HEIGHT - WINDOW_MARGIN;
				List<Integer> sortedKeys = data.sortedKeys();
				int totalItems = sortedKeys.size();
				int pageCount = ((totalItems - 1) / itemsPerPage) + 1;
				// Be aware that this may have changed without the caller knowing it.
				int currentPage = Math.min(_currentPage, pageCount - 1);
				
				// Draw our pagination buttons if they make sense.
				if (pageCount > 1)
				{
					IntConsumer pageSelector = (int newPage) -> {
						if (shouldChangePage.getAsBoolean())
						{
							_currentPage = newPage;
						}
					};
					UiIdioms.drawPageButtons(ui, pageSelector, location.rightX(), location.topY(), cursor, pageCount, currentPage);
				}
				
				// TODO:  This out-param hack can be removed once all consumers call this directly and we no longer need to preserve the old call shape.
				IAction[] out_action = new IAction[1];
				ItemRenderer<Integer> renderer = (float left, float bottom, float right, float top, Integer item, boolean isMouseOver) -> {
					int itemKey = item;
					innerBinding.set(new ItemTuple<>(data.getStackForKey(itemKey), data.getNonStackableForKey(itemKey), item));
					Rect itemRect = new Rect(left, bottom, right, top);
					IAction innerAction = itemView.render(itemRect, innerBinding, cursor);
					if (null != innerAction)
					{
						out_action[0] = innerAction;
					}
				};
				UiIdioms.drawItemGrid(sortedKeys, renderer, cursor, spacePerElement, WINDOW_ITEM_SIZE, itemsPerRow, itemsPerPage, leftMargin, topMargin, totalItems, currentPage);
				return out_action[0];
			}
		};
	}
}
