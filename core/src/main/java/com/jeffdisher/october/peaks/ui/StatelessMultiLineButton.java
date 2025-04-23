package com.jeffdisher.october.peaks.ui;


public class StatelessMultiLineButton<T> implements IStatelessView<T>
{
	private final GlUi _ui;
	private final ITransformer<T> _transformer;
	private final int _lineCount;

	public StatelessMultiLineButton(GlUi ui
			, ITransformer<T> transformer
			, int lineCount
	)
	{
		_ui = ui;
		_transformer = transformer;
		_lineCount = lineCount;
	}

	@Override
	public void render(Rect bounds, boolean shouldHighlight, T data)
	{
		int outlineTexture = _transformer.getOutline(data);
		UiIdioms.drawOutlineColour(_ui, bounds, outlineTexture, shouldHighlight);
		float leftX = bounds.leftX() + UiIdioms.OUTLINE_SIZE;
		float rightX = bounds.rightX() - UiIdioms.OUTLINE_SIZE;
		float lineHeight = bounds.getHeight() / (float)_lineCount;
		float nextTop = bounds.topY();
		for (int i = 0; i < _lineCount; ++i)
		{
			float bottom = nextTop - lineHeight;
			Rect lineRect = new Rect(leftX, bottom, rightX, nextTop);
			String line = _transformer.getLine(data, i);
			UiIdioms.drawTextLeft(_ui, lineRect, line);
			nextTop = bottom;
		}
	}


	public static interface ITransformer<T>
	{
		public int getOutline(T data);
		public String getLine(T data, int line);
	}
}
