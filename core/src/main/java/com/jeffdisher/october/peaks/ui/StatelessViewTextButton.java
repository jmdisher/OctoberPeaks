package com.jeffdisher.october.peaks.ui;


public class StatelessViewTextButton implements IStatelessView<String>
{
	private final GlUi _ui;

	public StatelessViewTextButton(GlUi ui)
	{
		_ui = ui;
	}

	@Override
	public void render(Rect bounds, boolean shouldHighlight, String data)
	{
		// TODO:  Remove this once the underlying idioms are refactored.
		Point fakeCursor = shouldHighlight
				? new Point(bounds.leftX(), bounds.bottomY())
				: null
		;
		UiIdioms.drawFixedButton(_ui, bounds, data, fakeCursor);
	}
}
