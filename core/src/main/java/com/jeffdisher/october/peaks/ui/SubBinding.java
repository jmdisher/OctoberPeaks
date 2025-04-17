package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;


/**
 * A binding to a data element within another binding structure.  When the parent is updated, so is this instance.
 */
public class SubBinding<T, P> extends Binding<T>
{
	private final Binding<P> _parent;
	private final Function<P, T> _loader;

	public SubBinding(Binding<P> parent, Function<P, T> loader)
	{
		_parent = parent;
		_loader = loader;
		
		_parent.addObserver(this);
	}

	@Override
	protected void rebuild()
	{
		this.set(_loader.apply(_parent._data));
	}
}
