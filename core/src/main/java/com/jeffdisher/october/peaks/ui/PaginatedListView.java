package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.jeffdisher.october.utils.Assert;


/**
 * An implementation of the IView which renders a list of data items.
 * It internally determines how to manage pagination (or scrolling) to show only what is required.
 */
public class PaginatedListView<T> implements IView
{
	public static final float HEADER_HEIGHT = 0.1f;

	private final GlUi _ui;
	private final Binding<List<T>> _binding;
	private final BooleanSupplier _shouldChangePage;
	private final IStatelessView<T> _innerView;
	private final float _innerViewHeight;
	private final Consumer<T> _actionConsumer;
	private int _currentListIndex;

	public PaginatedListView(GlUi ui
			, Binding<List<T>> binding
			, BooleanSupplier shouldChangePage
			, IStatelessView<T> innerView
			, float innerViewHeight
			, Consumer<T> actionConsumer
	)
	{
		_ui = ui;
		_binding = binding;
		_shouldChangePage = shouldChangePage;
		_innerView = innerView;
		_innerViewHeight = innerViewHeight;
		_actionConsumer = actionConsumer;
		_currentListIndex = 0;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// We must be able to fit at least one item and the header in the bounds.
		float ourHeight = location.getHeight();
		Assert.assertTrue((_innerViewHeight + HEADER_HEIGHT) <= ourHeight);
		
		// Get the data, find out how many elements we can draw, and rationalize the index.
		// (for now, we will only draw this as a vertical list, even though it could be tiled)
		List<T> list = _binding.get();
		int listSize = list.size();
		int itemsPerPage = (int)((ourHeight - HEADER_HEIGHT) / _innerViewHeight);
		int totalPages = ((listSize + itemsPerPage - 1) / itemsPerPage);
		if ((totalPages > 0) && (_currentListIndex >= listSize))
		{
			_currentListIndex = (totalPages - 1) * itemsPerPage;
		}
		
		// Draw our pagination buttons if they make sense.
		if (totalPages > 1)
		{
			IntConsumer pageSelector = (int newPage) -> {
				if (_shouldChangePage.getAsBoolean())
				{
					_currentListIndex = newPage * itemsPerPage;
				}
			};
			int currentPageIndex = _currentListIndex / itemsPerPage;
			UiIdioms.drawPageButtons(_ui, pageSelector, location.rightX(), location.topY(), cursor, totalPages, currentPageIndex);
		}
		
		// Now, draw the appropriate number of sub-elements.
		float nextItemTop = location.topY() - HEADER_HEIGHT;
		int nextIndex = _currentListIndex;
		T targetData = null;
		for (int i = 0; (i < itemsPerPage) && (nextIndex < listSize); ++i)
		{
			float itemBottom = nextItemTop - _innerViewHeight;
			Rect innerBounds = new Rect(location.leftX(), itemBottom, location.rightX(), nextItemTop);
			T data = list.get(nextIndex);
			boolean shouldHighlight = innerBounds.containsPoint(cursor);
			_innerView.render(innerBounds, shouldHighlight, data);
			if (shouldHighlight)
			{
				targetData = data;
			}
			
			// Prepare for next iteration.
			nextItemTop = itemBottom;
			nextIndex += 1;
		}
		
		return (null != targetData)
				? new _Action(targetData)
				: null
		;
	}


	private class _Action implements IAction
	{
		private final T _data;
		public _Action(T data)
		{
			_data = data;
		}
		@Override
		public void renderHover(Point cursor)
		{
			// Do nothing.
		}
		@Override
		public void takeAction()
		{
			_actionConsumer.accept(_data);
		}
	}
}
