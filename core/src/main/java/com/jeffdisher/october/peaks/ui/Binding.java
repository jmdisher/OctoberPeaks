package com.jeffdisher.october.peaks.ui;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.utils.Assert;


/**
 * A Binding is essentially an indirect wrapper over a piece of data rendered by an IView implementation.  This allows
 * concrete IView-Binding relationships to built and rooted in Window, while the data value can be updated by reference.
 * This essentially means that the Binding represents a "variable" instead of a "value".
 */
public class Binding<T>
{
	protected T _data;
	private List<Binding<?>> _observers = new ArrayList<>();

	public Binding(T start)
	{
		_data = start;
	}

	public T get()
	{
		return _data;
	}

	public void set(T data)
	{
		if (_data != data)
		{
			_data = data;
			for (Binding<?> observer : _observers)
			{
				observer.rebuild();
			}
		}
	}

	public void addObserver(Binding<?> observer)
	{
		_observers.add(observer);
	}

	protected void rebuild()
	{
		// Root implementation does nothing - sub-classes must override this.
		// This can't be abstract since concrete instances of this class can be created but the root implementation should never be called.
		throw Assert.unreachable();
	}
}
