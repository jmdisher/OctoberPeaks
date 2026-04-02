package com.jeffdisher.october.peaks.ui;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.utils.Assert;


/**
 * Contains a collection of IView objects and their associated bounds in the scene so that they can easily be rendered,
 * together.
 */
public class FixedWindow
{
	private final IView[] _views;
	private final Rect[] _bounds;

	private FixedWindow(IView[] views, Rect[] bounds)
	{
		Assert.assertTrue(views.length == bounds.length);
		_views = views;
		_bounds = bounds;
	}

	public IAction render(Point cursor)
	{
		IAction action = null;
		for (int i = 0; i < _views.length; ++i)
		{
			action = _renderViewChainAction(_views[i], _bounds[i], cursor, action);
		}
		return action;
	}


	private IAction _renderViewChainAction(IView view, Rect location, Point cursor, IAction existingAction)
	{
		IAction tempAction = view.render(location, cursor);
		return (null != tempAction)
			? tempAction
			: existingAction
		;
	}


	public static class Builder
	{
		private final List<IView> _views = new ArrayList<>();
		private final List<Rect> _bounds = new ArrayList<>();
		
		public Builder add(IView view, Rect bounds)
		{
			_views.add(view);
			_bounds.add(bounds);
			return this;
		}
		public FixedWindow finish()
		{
			IView[] views = _views.toArray((int size) -> new IView[size]);
			Rect[] bounds = _bounds.toArray((int size) -> new Rect[size]);
			return new FixedWindow(views, bounds);
		}
	}
}
