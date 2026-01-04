package com.jeffdisher.october.peaks.ui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.types.Item;


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
	private final BooleanSupplier _didLeftClick;
	private final IView _itemGrid;
	private final ViewToggleButton _isValidToggle;
	private final Binding<Boolean> _showValidBinding;

	public ViewCraftingPanel(GlUi ui
			, Binding<String> titleBinding
			, Binding<List<CraftDescription>> binding
			, Consumer<CraftDescription> actionConsumer
			, BooleanSupplier didLeftClick
	)
	{
		_ui = ui;
		_titleBinding = titleBinding;
		_binding = binding;
		_didLeftClick = didLeftClick;
		
		Predicate<CraftDescription> isValid = (CraftDescription context) -> {
			boolean isSatisfied = true;
			for (CraftDescription.ItemRequirement items : context.input())
			{
				if (items.available() < items.required())
				{
					isSatisfied = false;
					break;
				}
			}
			return isSatisfied;
		};
		ToIntFunction<CraftDescription> outlineTextureValueTransformer = (CraftDescription context) -> {
			// We want to set the outline colour based on whether or not all the requirements are satisfied (this could be moved into CraftDescription as an eager calculation).
			return isValid.test(context)
				? ui.pixelGreen
				: ui.pixelRed
			;
		};
		Function<CraftDescription, Item> typeValueTransformer = (CraftDescription desc) -> desc.output().type();
		ToIntFunction<CraftDescription> numberLabelValueTransformer = (CraftDescription desc) -> desc.output().count();
		StatelessViewItemTuple.ToFloatFunction<CraftDescription> progressBarValueTransformer = (CraftDescription desc) -> desc.progress();
		IStatelessView<CraftDescription> hoverRender = (Rect elementBounds, Point cursor, CraftDescription craft) -> {
			// This hover is pretty complicated since we draw the name an inputs.
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
		StatelessViewItemTuple<CraftDescription> stateless = new StatelessViewItemTuple<>(_ui
			, outlineTextureValueTransformer
			, typeValueTransformer
			, numberLabelValueTransformer
			, progressBarValueTransformer
			, hoverRender
			, actionConsumer
		);
		
		_showValidBinding = new Binding<>(false);
		Function<List<CraftDescription>, List<CraftDescription>> filter = (List<CraftDescription> input) -> {
			return _showValidBinding.get()
				? input.stream().filter(isValid).toList()
				: input
			;
		};
		_itemGrid = new PaginatedItemView<>(ui
				, _binding
				, filter
				, _didLeftClick
				, stateless
		);
		_isValidToggle = new ViewToggleButton(ui, new Binding<>("Valid"), _showValidBinding, _didLeftClick);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// Draw the window outline.
		UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
		float buttonBottom = location.topY() - WINDOW_TITLE_HEIGHT;
		
		// Draw the title.
		float rightOfTitle = _ui.drawLabel(location.leftX(), buttonBottom, location.topY(), _titleBinding.get());
		
		// We need to draw our toggle button.
		float leftToggle = rightOfTitle + WINDOW_MARGIN;
		Rect toggleLocation = new Rect(leftToggle, buttonBottom, location.rightX(), location.topY());
		IAction validToggleAction = _isValidToggle.render(toggleLocation, cursor);
		
		// Draw the actual sub-view (which will handle pagination, itself).
		IAction gridAction = _itemGrid.render(location, cursor);
		
		return (null != gridAction)
			? gridAction
			: validToggleAction
		;
	}
}
