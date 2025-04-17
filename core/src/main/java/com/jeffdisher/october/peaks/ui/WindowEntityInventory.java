package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;


/**
 * Renders the panel with a list of items in an inventory, also maintaining the pagination state in the IView instance.
 */
public class WindowEntityInventory
{
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;

	public static IView<Inventory> buildRenderer(GlUi ui
			, String title
			, Rect bounds
			, IntConsumer mouseOverKeyConsumer
			, BooleanSupplier shouldChangePage
	)
	{
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
		IView<List<ItemTuple<Integer>>> itemGrid = PaginatedItemView.buildRenderer(ui
				, bounds
				, (Integer val) -> mouseOverKeyConsumer.accept(val)
				, shouldChangePage
				, options
		);
		Binding<List<ItemTuple<Integer>>> innerBinding = new Binding<>();
		
		return new IView<>()
		{
			@Override
			public IAction render(Rect location, Binding<Inventory> binding, Point cursor)
			{
				Inventory data = binding.get();
				
				// Draw the window outline.
				UiIdioms.drawOverlayFrame(ui, ui.pixelDarkGreyAlpha, ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
				
				// Draw the title.
				float labelRight = ui.drawLabel(location.leftX(), location.topY() - WINDOW_TITLE_HEIGHT, location.topY(), title.toUpperCase());
				
				// Draw the capacity.
				String extraTitle = String.format("(%d/%d)", data.currentEncumbrance, data.maxEncumbrance);
				float bottomY = location.topY() - WINDOW_TITLE_HEIGHT;
				ui.drawLabel(labelRight + WINDOW_MARGIN, bottomY, bottomY + WINDOW_TITLE_HEIGHT, extraTitle.toUpperCase());
				
				// Draw the actual sub-view (which will handle pagination, itself).
				innerBinding.set(data.sortedKeys().stream().map((Integer key) -> new ItemTuple<>(data.getStackForKey(key), data.getNonStackableForKey(key), key)).toList());
				return itemGrid.render(location, innerBinding, cursor);
			}
		};
	}
}
