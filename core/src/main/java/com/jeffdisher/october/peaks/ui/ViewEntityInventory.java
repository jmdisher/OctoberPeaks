package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Renders the panel with a list of items in an inventory, also maintaining the pagination state in the IView instance.
 */
public class ViewEntityInventory implements IView
{
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;
	public static final float WINDOW_ITEM_SIZE = 0.1f;

	private final GlUi _ui;
	private final Binding<String> _titleBinding;
	private final Binding<Inventory> _binding;
	private final ComplexItemView<Void> _optionalProgress;

	private final Binding<List<ItemTuple<Integer>>> _internalGridBinding;
	private final IView _itemGrid;

	public ViewEntityInventory(GlUi ui
			, Binding<String> titleBinding
			, Binding<Inventory> binding
			, ComplexItemView<Void> optionalProgress
			, IntConsumer mouseOverKeyConsumer
			, BooleanSupplier shouldChangePage
	)
	{
		_ui = ui;
		_titleBinding = titleBinding;
		_binding = binding;
		_optionalProgress = optionalProgress;
		
		ComplexItemView.IBindOptions<Integer> options = new ComplexItemView.IBindOptions<>()
		{
			@Override
			public int getOutlineTexture(ItemTuple<Integer> context)
			{
				// The user's inventory is always drawn with light grey.
				return ui.pixelLightGrey;
			}
			@Override
			public void hoverRender(Point cursor, ItemTuple<Integer> context)
			{
				// We just render the name of the item.
				Item type = context.type();
				String name = type.name();
				float width = UiIdioms.getTextWidth(ui, name, UiIdioms.GENERAL_TEXT_HEIGHT);
				Rect bounds = new Rect(cursor.x(), cursor.y() - UiIdioms.GENERAL_TEXT_HEIGHT, cursor.x() + width + (2.0f * UiIdioms.OUTLINE_SIZE), cursor.y());
				UiIdioms.drawOutline(_ui, bounds, false);
				UiIdioms.drawTextCentred(_ui, bounds, name);
			}
			@Override
			public void hoverAction(ItemTuple<Integer> context)
			{
				mouseOverKeyConsumer.accept(context.context());
			}
		};
		
		// We use a fake view and binding pair to render the paginated view within the window.
		_internalGridBinding = new Binding<>(null);
		_itemGrid = new PaginatedItemView<>(ui
				, _internalGridBinding
				, shouldChangePage
				, options
		);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		Inventory data = _binding.get();
		
		// Draw the window outline.
		UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
		
		// Draw the title.
		String upperCaseTitle = _titleBinding.get().toUpperCase();
		float labelRight = _ui.drawLabel(location.leftX(), location.topY() - WINDOW_TITLE_HEIGHT, location.topY(), upperCaseTitle);
		
		// Draw the capacity.
		String extraTitle = String.format("(%d/%d)", data.currentEncumbrance, data.maxEncumbrance);
		float bottomY = location.topY() - WINDOW_TITLE_HEIGHT;
		float rightEdgeOfTitle = _ui.drawLabel(labelRight + WINDOW_MARGIN, bottomY, bottomY + WINDOW_TITLE_HEIGHT, extraTitle);
		
		// Draw any additional information we need:
		if (null != _optionalProgress)
		{
			float left = rightEdgeOfTitle + WINDOW_MARGIN;
			float bottom = location.topY() - WINDOW_TITLE_HEIGHT;
			Rect progressLocation = new Rect(left, bottom, left + WINDOW_ITEM_SIZE, bottom + WINDOW_ITEM_SIZE);
			_optionalProgress.render(progressLocation, cursor);
		}
		
		// Draw the actual sub-view (which will handle pagination, itself).
		// We need to populate the internal binding since it is based on what we have.
		Environment env = Environment.getShared();
		_internalGridBinding.set(data.sortedKeys().stream().map((Integer key) -> {
			Items stack = data.getStackForKey(key);
			NonStackableItem nonStack = data.getNonStackableForKey(key);
			return ItemTuple.commonFromItems(env, stack, nonStack, key);
		}).toList());
		return _itemGrid.render(location, cursor);
	}
}
