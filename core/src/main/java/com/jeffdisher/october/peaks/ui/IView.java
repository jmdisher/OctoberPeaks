package com.jeffdisher.october.peaks.ui;


/**
 * IView implementations are for rendering specific data structures to the screen.  They are stateless as they only
 * represent behaviour.  The data they render is passed in as a Binding instance at runtime.
 */
public interface IView<T>
{
	public void render(Rect location, Binding<T> binding, Point cursor);
}
