package com.jeffdisher.october.peaks.ui;


/**
 * A window represents a logical root of view-binding coupling which can be selectively rendered into the screen.
 * It is operated upon, from the outside, when rendering the forest of windows in a UI mode.
 */
public record Window<T>(Rect location
		, IView<T> view
		, Binding<T> binding
)
{
	/**
	 * Since the fields of the record are all used together, this helper turns that into one simple call.
	 * 
	 * @param cursor The current location of the mouse on screen (can be null).
	 */
	public void doRender(Point cursor)
	{
		this.view().render(this.location(), this.binding(), cursor);
	}
}
