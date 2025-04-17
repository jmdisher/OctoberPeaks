package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;


/**
 * Renders the panel with a list of items in an inventory, also maintaining the pagination state in the IView instance.
 */
public class ViewEntityInventory implements IView<Inventory>
{
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;

	private final GlUi _ui;
	private final String _upperCaseTitle;
	private final Binding<Inventory> _binding;

	private final Binding<List<ItemTuple<Integer>>> _internalGridBinding;
	private final IView<List<ItemTuple<Integer>>> _itemGrid;

	public ViewEntityInventory(GlUi ui
			, String title
			, Binding<Inventory> binding
			, IntConsumer mouseOverKeyConsumer
			, BooleanSupplier shouldChangePage
	)
	{
		_ui = ui;
		_upperCaseTitle = title.toUpperCase();
		_binding = binding;
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
				Item type = context.getItemType();
				String name = type.name();
				UiIdioms.drawTextRootedAtTop(ui, cursor.x(), cursor.y(), name);
			}
			@Override
			public void hoverAction(ItemTuple<Integer> context)
			{
				mouseOverKeyConsumer.accept(context.context());
			}
		};
		
		// We use a fake view and binding pair to render the paginated view within the window.
		_internalGridBinding = new Binding<>();
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
		float labelRight = _ui.drawLabel(location.leftX(), location.topY() - WINDOW_TITLE_HEIGHT, location.topY(), _upperCaseTitle);
		
		// Draw the capacity.
		String extraTitle = String.format("(%d/%d)", data.currentEncumbrance, data.maxEncumbrance);
		float bottomY = location.topY() - WINDOW_TITLE_HEIGHT;
		_ui.drawLabel(labelRight + WINDOW_MARGIN, bottomY, bottomY + WINDOW_TITLE_HEIGHT, extraTitle.toUpperCase());
		
		// Draw the actual sub-view (which will handle pagination, itself).
		// We need to populate the internal binding since it is based on what we have.
		_internalGridBinding.set(data.sortedKeys().stream().map((Integer key) -> new ItemTuple<>(data.getStackForKey(key), data.getNonStackableForKey(key), key)).toList());
		return _itemGrid.render(location, cursor);
	}
}
