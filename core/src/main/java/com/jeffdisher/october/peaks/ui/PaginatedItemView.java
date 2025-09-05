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
		
		// Now, draw the appropriate number of sub-elements in a grid.
		int startingIndex = currentPage * itemsPerPage;
		int firstIndexBeyondPage = startingIndex + itemsPerPage;
		if (firstIndexBeyondPage > totalItems)
		{
			firstIndexBeyondPage = totalItems;
		}
		
		IAction hoverOver = null;
		int xElement = 0;
		int yElement = 0;
		for (ItemTuple<T> elt : itemList.subList(startingIndex, firstIndexBeyondPage))
		{
			// We want to render these left->right, top->bottom but GL is left->right, bottom->top so we increment X and Y in opposite ways.
			float left = leftMargin + (xElement * spacePerElement);
			float top = topMargin - (yElement * spacePerElement);
			float bottom = top - WINDOW_ITEM_SIZE;
			float right = left + WINDOW_ITEM_SIZE;
			
			_innerBinding.set(elt);
			Rect itemRect = new Rect(left, bottom, right, top);
			IAction innerAction = _itemView.render(itemRect, cursor);
			if (null != innerAction)
			{
				hoverOver = innerAction;
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
}
