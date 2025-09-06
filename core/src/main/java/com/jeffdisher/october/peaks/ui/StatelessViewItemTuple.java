package com.jeffdisher.october.peaks.ui;

import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.types.Item;


/**
 * A stateless view for an ItemTuple.  Most of the customization for this is handled by passed in transformers,
 * sub-views, or consumers.
 */
public class StatelessViewItemTuple<T> implements IStatelessView<ItemTuple<T>>
{
	private final GlUi _ui;
	private final Function<T, Integer> _outlineTextureValueTransformer;
	private final IStatelessView<ItemTuple<T>> _hoverRenderer;
	private final Consumer<ItemTuple<T>> _actionConsumer;

	public StatelessViewItemTuple(GlUi ui
		, Function<T, Integer> outlineTextureValueTransformer
		, IStatelessView<ItemTuple<T>> hoverRenderer
		, Consumer<ItemTuple<T>> actionConsumer
	)
	{
		_ui = ui;
		_outlineTextureValueTransformer = outlineTextureValueTransformer;
		_hoverRenderer = hoverRenderer;
		_actionConsumer = actionConsumer;
	}

	@Override
	public IAction render(Rect bounds, Point cursor, ItemTuple<T> data)
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


	private IAction _render(Rect location, ItemTuple<T> tuple, boolean isMouseOver)
	{
		float left = location.leftX();
		float bottom = location.bottomY();
		float right = location.rightX();
		float top = location.topY();
		Item type = tuple.type();
		T data = tuple.context();
		int outlineTexture = _outlineTextureValueTransformer.apply(data);
		
		if (null != type)
		{
			// There is an item here so draw it.
			UiIdioms.renderItem(_ui, left, bottom, right, top, outlineTexture, type, tuple.count(), tuple.durability(), isMouseOver);
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
						_hoverRenderer.render(location, cursor, tuple);
					}
				}
				@Override
				public void takeAction()
				{
					if (null != _actionConsumer)
					{
						_actionConsumer.accept(tuple);
					}
				}
			}
			: null
		;
	}
}
