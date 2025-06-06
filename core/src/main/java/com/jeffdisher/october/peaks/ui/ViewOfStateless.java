package com.jeffdisher.october.peaks.ui;


/**
 * An implementation of IView which contains and instance of IStatelessView.
 * The purpose of this is for binding data to an existing IStatelessView implementation when it is for a single data
 * element (as IStatelessView is mostly used in list containers, etc).
 */
public class ViewOfStateless<T> implements IView
{
	private final IStatelessView<T> _internal;
	private final Binding<T> _binding;

	public ViewOfStateless(IStatelessView<T> internal
			, Binding<T> binding
	)
	{
		_internal = internal;
		_binding = binding;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// We only draw this if there is something here.
		T data = _binding.get();
		IAction action = null;
		if (null != data)
		{
			action = _internal.render(location, cursor, data);
		}
		return action;
	}
}
