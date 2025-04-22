package com.jeffdisher.october.peaks.ui;


/**
 * Similar to the IView but intended for cases where the view is reused during rendering and can therefore not have any
 * state.
 * It can be considered the "behaviour-only" logic underneath IView.
 */
public interface IStatelessView<T>
{
	public void render(Rect bounds, boolean shouldHighlight, T data);
}
