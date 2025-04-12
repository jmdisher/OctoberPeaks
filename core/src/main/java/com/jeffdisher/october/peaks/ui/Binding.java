package com.jeffdisher.october.peaks.ui;


/**
 * A Binding is essentially an indirect wrapper over a piece of data rendered by an IView implementation.  This allows
 * concrete IView-Binding relationships to built and rooted in Window, while the data value can be updated by reference.
 * This essentially means that the Binding represents a "variable" instead of a "value".
 */
public class Binding<T>
{
	public T data;
}
