package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.types.Item;


/**
 * A stateless view for an ItemTuple.  Most of the customization for this is handled by passed in transformers,
 * sub-views, or consumers.
 */
public class StatelessViewItemTuple<T> implements IStatelessView<T>
{
	private final GlUi _ui;
	private final ToIntFunction<T> _outlineTextureValueTransformer;
	private final Function<T, Item> _typeValueTransformer;
	private final ToIntFunction<T> _numberLabelValueTransformer;
	private final ToFloatFunction<T> _progressBarValueTransformer;
	private final IStatelessView<T> _hoverRenderer;
	private final Consumer<T> _actionConsumer;

	public StatelessViewItemTuple(GlUi ui
		, ToIntFunction<T> outlineTextureValueTransformer
		, Function<T, Item> typeValueTransformer
		, ToIntFunction<T> numberLabelValueTransformer
		, ToFloatFunction<T> progressBarValueTransformer
		, IStatelessView<T> hoverRenderer
		, Consumer<T> actionConsumer
	)
	{
		_ui = ui;
		_outlineTextureValueTransformer = outlineTextureValueTransformer;
		_typeValueTransformer = typeValueTransformer;
		_numberLabelValueTransformer = numberLabelValueTransformer;
		_progressBarValueTransformer = progressBarValueTransformer;
		_hoverRenderer = hoverRenderer;
		_actionConsumer = actionConsumer;
	}

	@Override
	public IAction render(Rect bounds, Point cursor, T data)
	{
		IAction action;
		if (null != data)
		{
			boolean isMouseOver = bounds.containsPoint(cursor);
			action = _render(bounds, data, isMouseOver);
		}
		else
		{
			// Nothing here.
			action = null;
		}
		return action;
	}


	private IAction _render(Rect location, T data, boolean isMouseOver)
	{
		float left = location.leftX();
		float bottom = location.bottomY();
		float right = location.rightX();
		float top = location.topY();
		Item type = _typeValueTransformer.apply(data);
		int outlineTexture = _outlineTextureValueTransformer.applyAsInt(data);
		
		if (null != type)
		{
			// There is an item here so draw it.
			int count = (null != _numberLabelValueTransformer)
				? _numberLabelValueTransformer.applyAsInt(data)
				: 0
			;
			float progress = (null != _progressBarValueTransformer)
				? _progressBarValueTransformer.applyAsFloat(data)
				: 0.0f
			;
			UiIdioms.renderItem(_ui, left, bottom, right, top, outlineTexture, type, count, progress, isMouseOver);
		}
		else
		{
			int backgroundTexture = isMouseOver
					? _ui.pixelLightGrey
					: _ui.pixelDarkGreyAlpha
			;
			UiIdioms.drawOverlayFrame(_ui, backgroundTexture, outlineTexture, left, bottom, right, top);
		}
		
		return isMouseOver
			? new IAction() {
				@Override
				public void renderHover(Point cursor)
				{
					if (null != _hoverRenderer)
					{
						_hoverRenderer.render(location, cursor, data);
					}
				}
				@Override
				public void takeAction()
				{
					if (null != _actionConsumer)
					{
						_actionConsumer.accept(data);
					}
				}
			}
			: null
		;
	}


	public static interface ToFloatFunction<T>
	{
		public float applyAsFloat(T value);
	}
}
