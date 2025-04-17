package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.utils.Assert;


/**
 * An implementation of IView which can render Items or NonStackableItem with either the number in the stack or a
 * display of the durability.
 */
public class ComplexItemView<T> implements IView<ItemTuple<T>>
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
		// There are essentially 4 modes:
		// 1) No item and NOT highlighted
		// 2) No item and IS highlighted
		// 3) Stackable (needs number)
		// 4) Non-stackable (needs durability)
		
		boolean isMouseOver = location.containsPoint(cursor);
		float left = location.leftX();
		float bottom = location.bottomY();
		float right = location.rightX();
		float top = location.topY();
		ItemTuple<T> tuple =_binding.get();
		Items stack = tuple.stackable();
		NonStackableItem nonStack = tuple.nonStackable();
		int outlineTexture = _options.getOutlineTexture(tuple);
		
		if (null != stack)
		{
			// There can be at most one of these set.
			Assert.assertTrue(null == nonStack);
			
			UiIdioms.renderStackableItem(_ui, left, bottom, right, top, outlineTexture, stack, isMouseOver);
		}
		else if (null != nonStack)
		{
			UiIdioms.renderNonStackableItem(_ui, left, bottom, right, top, outlineTexture, nonStack, isMouseOver);
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
