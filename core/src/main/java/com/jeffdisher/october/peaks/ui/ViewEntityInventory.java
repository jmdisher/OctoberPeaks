package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

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
	private final ViewFuelSlot _optionalProgress;

	private final Binding<List<ItemTuple<Integer>>> _internalGridBinding;
	private final PaginatedItemView<ItemTuple<Integer>> _itemGrid;

	public ViewEntityInventory(GlUi ui
			, Binding<String> titleBinding
			, Binding<Inventory> binding
			, ViewFuelSlot optionalProgress
			, IntConsumer mouseOverKeyConsumer
			, BooleanSupplier shouldChangePage
	)
	{
		_ui = ui;
		_titleBinding = titleBinding;
		_binding = binding;
		_optionalProgress = optionalProgress;
		
		Function<ItemTuple<Integer>, Item> typeValueTransformer = (ItemTuple<Integer> desc) -> desc.type();
		ToIntFunction<ItemTuple<Integer>> numberLabelValueTransformer = (ItemTuple<Integer> desc) -> desc.count();
		StatelessViewItemTuple.ToFloatFunction<ItemTuple<Integer>> progressBarValueTransformer = (ItemTuple<Integer> desc) -> desc.durability();
		IStatelessView<ItemTuple<Integer>> hoverRender = new IStatelessView<>() {
			@Override
			public IAction render(Rect elementBounds, Point cursor, ItemTuple<Integer> data)
			{
				// We just render the name of the item.
				Item type = data.type();
				String name = type.name();
				float width = UiIdioms.getTextWidth(ui, name, UiIdioms.GENERAL_TEXT_HEIGHT);
				Rect bounds = new Rect(cursor.x(), cursor.y() - UiIdioms.GENERAL_TEXT_HEIGHT, cursor.x() + width + (2.0f * UiIdioms.OUTLINE_SIZE), cursor.y());
				UiIdioms.drawOutline(_ui, bounds, false);
				UiIdioms.drawTextCentred(_ui, bounds, name);
				return null;
			}
		};
		Consumer<ItemTuple<Integer>> actionConsumer = (ItemTuple<Integer> tuple) -> mouseOverKeyConsumer.accept(tuple.context());
		StatelessViewItemTuple<ItemTuple<Integer>> stateless = new StatelessViewItemTuple<>(_ui
			// The standard inventory is always drawn with light grey.
			, (ItemTuple<Integer> ignored) -> ui.pixelLightGrey
			, typeValueTransformer
			, numberLabelValueTransformer
			, progressBarValueTransformer
			, hoverRender
			, actionConsumer
		);
		
		// We use a fake view and binding pair to render the paginated view within the window.
		_internalGridBinding = new Binding<>(null);
		_itemGrid = new PaginatedItemView<>(ui
				, _internalGridBinding
				, shouldChangePage
				, stateless
		);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		Inventory data = _binding.get();
		
		// Draw the window outline.
		UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
		
		// Draw the title.
		float labelRight = _ui.drawLabel(location.leftX(), location.topY() - WINDOW_TITLE_HEIGHT, location.topY(), _titleBinding.get());
		
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
