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
		UiIdioms.drawOutline(_ui, bounds, shouldHighlight);
		UiIdioms.drawTextCentred(_ui, bounds, data);
	}
}
