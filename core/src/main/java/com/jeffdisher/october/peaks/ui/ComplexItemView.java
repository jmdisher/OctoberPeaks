package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Item;


/**
 * An implementation of IView which can render Items or NonStackableItem with either the number in the stack or a
 * display of the durability.
 */
public class ComplexItemView<T> implements IView
{
	private final GlUi _ui;
	private final Binding<ItemTuple<T>> _binding;
	private final IBindOptions<T> _options;

	public ComplexItemView(GlUi ui
			, Binding<ItemTuple<T>> binding
			, IBindOptions<T> options
	)
	{
		_ui = ui;
		_binding = binding;
		_options = options;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// We first want to get the binding since it being null means that this item slot shouldn't even be rendered.
		ItemTuple<T> tuple =_binding.get();
		IAction action;
		if (null != tuple)
		{
			boolean isMouseOver = location.containsPoint(cursor);
			action = _render(location, tuple, isMouseOver);
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
		int outlineTexture = _options.getOutlineTexture(tuple);
		
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
						_options.hoverRender(cursor, tuple);
					}
					@Override
					public void takeAction()
					{
						_options.hoverAction(tuple);
					}
				}
				: null
		;
	}


	public static interface IBindOptions<T>
	{
		public int getOutlineTexture(ItemTuple<T> context);
		public void hoverRender(Point cursor, ItemTuple<T> context);
		public void hoverAction(ItemTuple<T> context);
	}
}
