package com.jeffdisher.october.peaks.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.PropertyHelpers;
import com.jeffdisher.october.properties.PropertyRegistry;
import com.jeffdisher.october.properties.PropertyType;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
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

	private final Binding<List<_ItemTuple>> _internalGridBinding;
	private final PaginatedItemView<_ItemTuple> _itemGrid;

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
		
		Function<_ItemTuple, Item> typeValueTransformer = (_ItemTuple desc) -> desc.getType();
		ToIntFunction<_ItemTuple> numberLabelValueTransformer = (_ItemTuple desc) -> desc.getCount();
		StatelessViewItemTuple.ToFloatFunction<_ItemTuple> progressBarValueTransformer = (_ItemTuple desc) -> desc.getDurability();
		_Hover hoverRender = new _Hover();
		Consumer<_ItemTuple> actionConsumer = (_ItemTuple tuple) -> mouseOverKeyConsumer.accept(tuple.key);
		StatelessViewItemTuple<_ItemTuple> stateless = new StatelessViewItemTuple<>(_ui
			// The standard inventory is always drawn with light grey.
			, (_ItemTuple ignored) -> ui.pixelLightGrey
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
				, (List<_ItemTuple> in) -> in
				, shouldChangePage
				, stateless
		);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		Inventory data = _binding.get();
		
		// Note that this binding may be null if there is no bottom inventory, for example.
		IAction action = null;
		if (null != data)
		{
			action = _render(location, cursor, data);
		}
		return action;
	}


	private IAction _render(Rect location, Point cursor, Inventory data)
	{
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
		_internalGridBinding.set(data.sortedKeys().stream().map((Integer key) -> new _ItemTuple(data.getSlotForKey(key), key)).toList());
		return _itemGrid.render(location, cursor);
	}

	private static record _ItemTuple(ItemSlot slot, int key)
	{
		public Item getType()
		{
			return (null != slot.stack) ? slot.stack.type() : slot.nonStackable.type();
		}
		public int getCount()
		{
			return (null != slot.stack) ? slot.stack.count() : 0;
		}
		public float getDurability()
		{
			float durability = 0.0f;
			if (null != slot.nonStackable)
			{
				Environment env = Environment.getShared();
				Item type = slot.nonStackable.type();
				durability = (float)PropertyHelpers.getDurability(slot.nonStackable) / (float)env.durability.getDurability(type);
			}
			return durability;
		}
	}

	private class _Hover implements IStatelessView<_ItemTuple>
	{
		@Override
		public IAction render(Rect elementBounds, Point cursor, _ItemTuple data)
		{
			// For the hover, we want to show more details if this item has special properties.
			if (null != data.slot.stack)
			{
				// We just render the name of the item.
				Item type = data.getType();
				String name = type.name();
				float width = UiIdioms.getTextWidth(_ui, name, UiIdioms.GENERAL_TEXT_HEIGHT);
				Rect bounds = new Rect(cursor.x(), cursor.y() - UiIdioms.GENERAL_TEXT_HEIGHT, cursor.x() + width + (2.0f * UiIdioms.OUTLINE_SIZE), cursor.y());
				UiIdioms.drawOutline(_ui, bounds, false);
				UiIdioms.drawTextCentred(_ui, bounds, name);
			}
			else
			{
				// Check if this has a special name or other properties.
				NonStackableItem nonStack = data.slot.nonStackable;
				Map<PropertyType<?>, Object> properties = nonStack.properties();
				
				String name = PropertyHelpers.getName(nonStack);
				byte durability = _getValue(properties, PropertyRegistry.ENCHANT_DURABILITY);
				byte weaponMelee = _getValue(properties, PropertyRegistry.ENCHANT_WEAPON_MELEE);
				byte toolEfficiency = _getValue(properties, PropertyRegistry.ENCHANT_TOOL_EFFICIENCY);
				
				List<String> strings = new ArrayList<>();
				float width = UiIdioms.getTextWidth(_ui, name, UiIdioms.GENERAL_TEXT_HEIGHT);
				float height = UiIdioms.GENERAL_TEXT_HEIGHT;
				strings.add(name);
				if (durability > 0)
				{
					String enchant = "+" + durability + " durability";
					float thisWidth = UiIdioms.getTextWidth(_ui, enchant, UiIdioms.GENERAL_TEXT_HEIGHT);
					width = Math.max(width, thisWidth);
					height += UiIdioms.GENERAL_TEXT_HEIGHT;
					strings.add(enchant);
				}
				if (weaponMelee > 0)
				{
					String enchant = "+" + weaponMelee + " melee damage";
					float thisWidth = UiIdioms.getTextWidth(_ui, enchant, UiIdioms.GENERAL_TEXT_HEIGHT);
					width = Math.max(width, thisWidth);
					height += UiIdioms.GENERAL_TEXT_HEIGHT;
					strings.add(enchant);
				}
				if (toolEfficiency > 0)
				{
					String enchant = "+" + toolEfficiency + " efficiency";
					float thisWidth = UiIdioms.getTextWidth(_ui, enchant, UiIdioms.GENERAL_TEXT_HEIGHT);
					width = Math.max(width, thisWidth);
					height += UiIdioms.GENERAL_TEXT_HEIGHT;
					strings.add(enchant);
				}
				
				Rect bounds = new Rect(cursor.x(), cursor.y() - height, cursor.x() + width + (2.0f * UiIdioms.OUTLINE_SIZE), cursor.y());
				UiIdioms.drawOutline(_ui, bounds, false);
				
				float textTop = cursor.y();
				for (String string : strings)
				{
					Rect rect = new Rect(cursor.x() + UiIdioms.OUTLINE_SIZE, textTop - UiIdioms.GENERAL_TEXT_HEIGHT, cursor.x() + width + (2.0f * UiIdioms.OUTLINE_SIZE), textTop);
					UiIdioms.drawTextLeft(_ui, rect, string);
					textTop -= UiIdioms.GENERAL_TEXT_HEIGHT;
				}
			}
			return null;
		}
		private static byte _getValue(Map<PropertyType<?>, Object> properties, PropertyType<Byte> type)
		{
			byte value = 0;
			if (properties.containsKey(type))
			{
				Object raw = properties.get(type);
				value = type.type().cast(raw);
			}
			return value;
		}
	}
}
