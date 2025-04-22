package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;


/**
 * Renders a list of items.  Note that the rect for this still needs to include the entire area of the window since it
 * handles the page actions (logic being that it would handle scrolling, if there were a scroll bar).
 */
public class PaginatedItemView<T> implements IView
{
	public static final float WINDOW_ITEM_SIZE = 0.1f;
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;

	private final GlUi _ui;
	private final Binding<List<ItemTuple<T>>> _binding;
	private final BooleanSupplier _shouldChangePage;

	private final Binding<ItemTuple<T>> _innerBinding;
	private final IView _itemView;
	private int _currentPage;

	public PaginatedItemView(GlUi ui
			, Binding<List<ItemTuple<T>>> binding
			, BooleanSupplier shouldChangePage
			, ComplexItemView.IBindOptions<T> options
	)
	{
		_ui = ui;
		_binding = binding;
		_shouldChangePage = shouldChangePage;
		
		// We use a fake view and binding pair to render the individual items in the list.
		_innerBinding = new Binding<>(null);
		_itemView = new ComplexItemView<>(ui, _innerBinding, options);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		List<ItemTuple<T>> itemList = _binding.get();
		
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
		int totalItems = itemList.size();
		int pageCount = ((totalItems - 1) / itemsPerPage) + 1;
		// Be aware that this may have changed without the caller knowing it.
		int currentPage = Math.min(_currentPage, pageCount - 1);
		
		// Draw our pagination buttons if they make sense.
		if (pageCount > 1)
		{
			IntConsumer pageSelector = (int newPage) -> {
				if (_shouldChangePage.getAsBoolean())
				{
					_currentPage = newPage;
				}
			};
			UiIdioms.drawPageButtons(_ui, pageSelector, location.rightX(), location.topY(), cursor, pageCount, currentPage);
		}
		
		// TODO:  This out-param hack can be removed once all consumers call this directly and we no longer need to preserve the old call shape.
		IAction[] out_action = new IAction[1];
		UiIdioms.ItemRenderer<ItemTuple<T>> renderer = (float left, float bottom, float right, float top, ItemTuple<T> item, boolean isMouseOver) -> {
			_innerBinding.set(item);
			Rect itemRect = new Rect(left, bottom, right, top);
			IAction innerAction = _itemView.render(itemRect, cursor);
			if (null != innerAction)
			{
				out_action[0] = innerAction;
			}
		};
		UiIdioms.drawItemGrid(itemList, renderer, cursor, spacePerElement, WINDOW_ITEM_SIZE, itemsPerRow, itemsPerPage, leftMargin, topMargin, totalItems, currentPage);
		return out_action[0];
	}
}
