package com.jeffdisher.october.peaks.ui;


/**
 * IView implementations are for rendering specific data structures to the screen.
 * They are assumed to access their data via implementation-specific state.
 */
public interface IView
{
	public IAction render(Rect location, Point cursor);
}