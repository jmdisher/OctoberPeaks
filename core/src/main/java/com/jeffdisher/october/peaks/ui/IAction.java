package com.jeffdisher.october.peaks.ui;


/**
 * Implementations of this are created when the cursor is hovering over something which has either a hover render and/or
 * a mouse click action.
 * This allows the top-level to the do the hover render after everything else is rendered (forcing it on top) but also
 * allows the action to be invoked at the top-level, if there is one.
 */
public interface IAction
{
	void renderHover(Point cursor);
	void takeAction();
}
