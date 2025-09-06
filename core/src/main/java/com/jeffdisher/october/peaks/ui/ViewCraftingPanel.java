package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.types.Items;


/**
 * Renders the panel with a list of possible crafting operations, also maintaining the pagination state in the IView
 * instance.
 */
public class ViewCraftingPanel implements IView
{
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;
	public static final float WINDOW_ITEM_SIZE = 0.1f;

	private final GlUi _ui;
	private final Binding<String> _titleBinding;
	private final Binding<List<CraftDescription>> _binding;

	private final Binding<List<ItemTuple<CraftDescription>>> _internalGridBinding;
	private final IView _itemGrid;

	public ViewCraftingPanel(GlUi ui
			, Binding<String> titleBinding
			, Binding<List<CraftDescription>> binding
			, Consumer<CraftDescription> craftHoverOverItem
			, BooleanSupplier shouldChangePage
	)
	{
		_ui = ui;
		_titleBinding = titleBinding;
		_binding = binding;
		
		Function<CraftDescription, Integer> outlineTextureValueTransformer = (CraftDescription context) -> {
			// We want to set the outline colour based on whether or not all the requirements are satisfied (this could be moved into CraftDescription as an eager calculation).
			boolean isSatisfied = true;
			for (CraftDescription.ItemRequirement items : context.input())
			{
				if (items.available() < items.required())
				{
					isSatisfied = false;
					break;
				}
			}
			return isSatisfied
				? ui.pixelGreen
				: ui.pixelRed
			;
		};
		IStatelessView<ItemTuple<CraftDescription>> hoverRender = (Rect elementBounds, Point cursor, ItemTuple<CraftDescription> data) -> {
			// This hover is pretty complicated since we draw the name an inputs.
			CraftDescription craft = data.context();
			String name = craft.craft().name;
			
			// Calculate the dimensions (we have a title and then a list of input items below this).
			float widthOfTitle = _ui.getLabelWidth(WINDOW_TITLE_HEIGHT, name);
			float widthOfItems = (float)craft.input().length * WINDOW_ITEM_SIZE + (float)(craft.input().length + 1) * WINDOW_MARGIN;
			float widthOfHover = Math.max(widthOfTitle, widthOfItems);
			float heightOfHover = WINDOW_TITLE_HEIGHT + WINDOW_ITEM_SIZE + 3 * WINDOW_MARGIN;
			float glX = cursor.x();
			float glY = cursor.y();
			
			// We can now draw the frame.
			UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, glX, glY - heightOfHover, glX + widthOfHover, glY);
			
			// Draw the title.
			_ui.drawLabel(glX, glY - WINDOW_TITLE_HEIGHT, glY, name);
			
			// Draw the inputs.
			float inputLeft = glX + WINDOW_MARGIN;
			float inputTop = glY - 2 * WINDOW_MARGIN - WINDOW_TITLE_HEIGHT;
			float inputBottom = inputTop - WINDOW_ITEM_SIZE;
			for (CraftDescription.ItemRequirement items : craft.input())
			{
				float noProgress = 0.0f;
				boolean isMouseOver = false;
				int outlineTexture = (items.available() >= items.required())
						? _ui.pixelGreen
						: _ui.pixelRed
				;
				UiIdioms.renderItem(_ui, inputLeft, inputBottom, inputLeft + WINDOW_ITEM_SIZE, inputBottom + WINDOW_ITEM_SIZE, outlineTexture, items.type(), items.required(), noProgress, isMouseOver);
				inputLeft += WINDOW_ITEM_SIZE + WINDOW_MARGIN;
			}
			return null;
		};
		Consumer<ItemTuple<CraftDescription>> actionConsumer = (ItemTuple<CraftDescription> tuple) -> {
			craftHoverOverItem.accept(tuple.context());
		};
		StatelessViewItemTuple<CraftDescription> stateless = new StatelessViewItemTuple<>(_ui
			, outlineTextureValueTransformer
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
		List<CraftDescription> data = _binding.get();
		
		// Draw the window outline.
		UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
		
		// Draw the title.
		_ui.drawLabel(location.leftX(), location.topY() - WINDOW_TITLE_HEIGHT, location.topY(), _titleBinding.get());
		
		// Draw the actual sub-view (which will handle pagination, itself).
		// We need to populate the internal binding since it is based on what we have.
		_internalGridBinding.set(data.stream().map((CraftDescription craft) -> {
			Items output = craft.output();
			return new ItemTuple<>(output.type(), output.count(), craft.progress(), craft);
		}).toList());
		return _itemGrid.render(location, cursor);
	}
}
