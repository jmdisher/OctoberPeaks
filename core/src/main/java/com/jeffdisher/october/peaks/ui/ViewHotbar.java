package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * Rendering of the hotbar "window".
 */
public class ViewHotbar implements IView
{
	public static final float HOTBAR_ITEM_SCALE = 0.1f;
	public static final float HOTBAR_ITEM_SPACING = 0.05f;
	public static final float HOTBAR_BOTTOM_Y = -0.95f;
	public static final float HOTBAR_WIDTH = ((float)Entity.HOTBAR_SIZE * HOTBAR_ITEM_SCALE) + ((float)(Entity.HOTBAR_SIZE - 1) * HOTBAR_ITEM_SPACING);

	public static final Rect LOCATION = new Rect(- HOTBAR_WIDTH / 2.0f, HOTBAR_BOTTOM_Y, HOTBAR_WIDTH / 2.0f, HOTBAR_BOTTOM_Y + HOTBAR_ITEM_SCALE);

	private final Binding<Entity> _binding;
	private final Binding<ItemTuple<Boolean>> _innerBinding;
	private final IView _itemView;

	public ViewHotbar(GlUi ui
			, Binding<Entity> binding
	)
	{
		_binding = binding;
		
		// We only care about whether or not this is selected so we will pass in a boolean.
		ComplexItemView.IBindOptions<Boolean> options = new ComplexItemView.IBindOptions<Boolean>()
		{
			@Override
			public int getOutlineTexture(ItemTuple<Boolean> context)
			{
				return context.context()
						? ui.pixelGreen
						: ui.pixelLightGrey
				;
			}
			@Override
			public void hoverRender(Point cursor, ItemTuple<Boolean> context)
			{
				// Nothing.
			}
			@Override
			public void hoverAction(ItemTuple<Boolean> context)
			{
				// No action.
			}
		};
		
		// Create the fake binding for the inner view.
		_innerBinding = new Binding<>(null);
		_itemView = new ComplexItemView<>(ui, _innerBinding, options);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		Environment env = Environment.getShared();
		float nextLeftButton = location.leftX();
		Entity entity = _binding.get();
		Inventory entityInventory = _getEntityInventory(entity);
		int[] hotbarKeys = entity.hotbarItems();
		int activeIndex = entity.hotbarIndex();
		for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
		{
			boolean isActive = (activeIndex == i);
			int thisKey = hotbarKeys[i];
			if (0 == thisKey)
			{
				// No item so just draw the frame.
				_innerBinding.set(new ItemTuple<>(null, 0, 0.0f, isActive));
			}
			else
			{
				// There is something here so render it.
				Items stack = entityInventory.getStackForKey(thisKey);
				NonStackableItem nonStack = entityInventory.getNonStackableForKey(thisKey);
				_innerBinding.set(ItemTuple.commonFromItems(env, stack, nonStack, isActive));
			}
			
			// Use the composed item - we ignore the response since it doesn't do anything.
			_itemView.render(new Rect(nextLeftButton, location.bottomY(), nextLeftButton + HOTBAR_ITEM_SCALE, location.topY()), cursor);
			
			nextLeftButton += HOTBAR_ITEM_SCALE + HOTBAR_ITEM_SPACING;
		}
		
		// No hover or action.
		return null;
	}


	private static Inventory _getEntityInventory(Entity entity)
	{
		Inventory inventory = entity.isCreativeMode()
				? CreativeInventory.fakeInventory()
				: entity.inventory()
		;
		return inventory;
	}
}
