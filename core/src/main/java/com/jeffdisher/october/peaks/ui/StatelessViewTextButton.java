package com.jeffdisher.october.peaks.ui;

import java.util.function.Function;


public class StatelessViewTextButton implements IStatelessView<String>
{
	private final GlUi _ui;
	private final Function<String, String> _valueTransformer;

	public StatelessViewTextButton(GlUi ui, Function<String, String> valueTransformer)
	{
		_ui = ui;
		_valueTransformer = valueTransformer;
	}

	@Override
	public void render(Rect bounds, boolean shouldHighlight, String data)
	{
		UiIdioms.drawOutline(_ui, bounds, shouldHighlight);
		String transformed = _valueTransformer.apply(data);
		UiIdioms.drawTextCentred(_ui, bounds, transformed);
	}
}
