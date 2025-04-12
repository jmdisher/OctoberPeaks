package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.textures.TextManager;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IView;
import com.jeffdisher.october.peaks.ui.Point;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * This class manages the overlays in the system which are drawn into a 2D orthographic plane on top of the existing
 * world.
 */
public class WindowManager
{
	public static final float OUTLINE_SIZE = 0.01f;
	public static final float HOTBAR_ITEM_SCALE = 0.1f;
	public static final float HOTBAR_ITEM_SPACING = 0.05f;
	public static final float HOTBAR_BOTTOM_Y = -0.95f;
	public static final float GENERAL_TEXT_HEIGHT = 0.1f;
	public static final float SMALL_TEXT_HEIGHT = 0.05f;
	public static final float META_DATA_LABEL_WIDTH = 0.1f;
	public static final float META_DATA_BOX_LEFT = 0.8f;
	public static final float META_DATA_BOX_BOTTOM = -0.95f;
	public static final float SELECTED_BOX_LEFT = 0.05f;
	public static final float SELECTED_BOX_BOTTOM = 0.90f;
	public static final float ARMOUR_SLOT_SCALE = 0.1f;
	public static final float ARMOUR_SLOT_SPACING = 0.05f;
	public static final float ARMOUR_SLOT_RIGHT_EDGE = 0.95f;
	public static final float ARMOUR_SLOT_TOP_EDGE = 0.95f;
	public static final float WINDOW_ITEM_SIZE = 0.1f;
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;
	public static final float WINDOW_PAGE_BUTTON_HEIGHT = 0.05f;
	public static final _WindowDimensions WINDOW_TOP_LEFT = new _WindowDimensions(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final _WindowDimensions WINDOW_TOP_RIGHT = new _WindowDimensions(0.05f, 0.05f, ARMOUR_SLOT_RIGHT_EDGE - ARMOUR_SLOT_SCALE - ARMOUR_SLOT_SPACING, 0.95f);
	public static final _WindowDimensions WINDOW_BOTTOM = new _WindowDimensions(-0.95f, -0.80f, 0.95f, -0.05f);
	public static final float RETICLE_SIZE = 0.05f;

	private final Environment _env;
	private final GlUi _ui;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final Map<Integer, String> _otherPlayersById;
	private final Set<Block> _waterBlockTypes;
	private final Set<Block> _lavaBlockTypes;
	private AbsoluteLocation _eyeBlockLocation;
	private boolean _isPaused;

	// We define these public rendering helpers in order to avoid adding special public interfaces or spreading rendering logic around.
	public final ItemRenderer<Items> renderItemStack;
	public final ItemRenderer<NonStackableItem> renderNonStackable;
	public final ItemRenderer<CraftDescription> renderCraftOperation;
	public final HoverRenderer<Item> hoverItem;
	public final HoverRenderer<CraftDescription> hoverCraftOperation;

	// Data bindings populated by updates and read when rendering windows in the mode.
	private final Binding<Entity> _entityBinding;

	// The window definitions to be used when rendering specific modes.
	private final Window<Entity> _metaDataWindow;

	public WindowManager(Environment env, GL20 gl, TextureAtlas<ItemVariant> itemAtlas, Function<AbsoluteLocation, BlockProxy> blockLookup)
	{
		_env = env;
		_ui = new GlUi(gl, itemAtlas);
		_blockLookup = blockLookup;
		_otherPlayersById = new HashMap<>();
		
		// Find the set of water block types we will use to determine when to draw the water overlay.
		_waterBlockTypes = Set.of(env.blocks.fromItem(env.items.getItemById("op.water_source"))
				, env.blocks.fromItem(env.items.getItemById("op.water_strong"))
				, env.blocks.fromItem(env.items.getItemById("op.water_weak"))
		);
		
		// Same for lava block types.
		_lavaBlockTypes = Set.of(env.blocks.fromItem(env.items.getItemById("op.lava_source"))
				, env.blocks.fromItem(env.items.getItemById("op.lava_strong"))
				, env.blocks.fromItem(env.items.getItemById("op.lava_weak"))
		);
		
		// Set up our public rendering helpers.
		this.renderItemStack = (float left, float bottom, float right, float top, Items item, boolean isMouseOver) -> {
			_renderStackableItem(left, bottom, right, top, _ui.pixelLightGrey, item, isMouseOver);
		};
		this.renderNonStackable = (float left, float bottom, float right, float top, NonStackableItem item, boolean isMouseOver) -> {
			_renderNonStackableItem(left, bottom, right, top, _ui.pixelLightGrey, item, isMouseOver);
		};
		this.renderCraftOperation = (float left, float bottom, float right, float top, CraftDescription item, boolean isMouseOver) -> {
			// Note that this is often used to render non-operations, just as a generic craft rendering helper.
			// NOTE:  We are assuming only a single output type.
			boolean isValid = true;
			for (ItemRequirement input : item.input)
			{
				if (input.available < input.required)
				{
					isValid = false;
					break;
				}
			}
			int outlineTexture = isValid
					? _ui.pixelGreen
					: _ui.pixelRed
			;
			_renderItem(left, bottom, right, top, outlineTexture, item.output.type(), item.output.count(), item.progress, item.canBeSelected ? isMouseOver : false);
		};
		this.hoverItem = (Point cursor, Item item) -> {
			// Just draw the name.
			String name = item.name();
			_drawTextInFrame(cursor.x(), cursor.y() - GENERAL_TEXT_HEIGHT, name);
		};
		this.hoverCraftOperation = (Point cursor, CraftDescription item) -> {
			String name = item.craft.name;
			
			// Calculate the dimensions (we have a title and then a list of input items below this).
			float widthOfTitle = _ui.getLabelWidth(WINDOW_TITLE_HEIGHT, name);
			float widthOfItems = (float)item.input.length * WINDOW_ITEM_SIZE + (float)(item.input.length + 1) * WINDOW_MARGIN;
			float widthOfHover = Math.max(widthOfTitle, widthOfItems);
			float heightOfHover = WINDOW_TITLE_HEIGHT + WINDOW_ITEM_SIZE + 3 * WINDOW_MARGIN;
			float glX = cursor.x();
			float glY = cursor.y();
			
			// We can now draw the frame.
			_drawOverlayFrame(_ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, glX, glY - heightOfHover, glX + widthOfHover, glY);
			
			// Draw the title.
			_ui.drawLabel(glX, glY - WINDOW_TITLE_HEIGHT, glY, name);
			
			// Draw the inputs.
			float inputLeft = glX + WINDOW_MARGIN;
			float inputTop = glY - 2 * WINDOW_MARGIN - WINDOW_TITLE_HEIGHT;
			float inputBottom = inputTop - WINDOW_ITEM_SIZE;
			for (ItemRequirement items : item.input)
			{
				float noProgress = 0.0f;
				boolean isMouseOver = false;
				int outlineTexture = (items.available >= items.required)
						? _ui.pixelGreen
						: _ui.pixelRed
				;
				_renderItem(inputLeft, inputBottom, inputLeft + WINDOW_ITEM_SIZE, inputBottom + WINDOW_ITEM_SIZE, outlineTexture, items.type, items.required, noProgress, isMouseOver);
				inputLeft += WINDOW_ITEM_SIZE + WINDOW_MARGIN;
			}
		};
		
		// Define the data bindings used by the window system.
		_entityBinding = new Binding<>();
		
		// Define the windows for different UI modes.
		Rect metaDataLocation = new Rect(META_DATA_BOX_LEFT, META_DATA_BOX_BOTTOM, META_DATA_BOX_LEFT + 1.5f * META_DATA_LABEL_WIDTH, META_DATA_BOX_BOTTOM + 3.0f * SMALL_TEXT_HEIGHT);
		IView<Entity> metaDataRender = (Rect location, Binding<Entity> binding) -> {
			_drawEntityMetaData(location, binding);
		};
		_metaDataWindow = new Window<>(metaDataLocation, metaDataRender, _entityBinding);
	}

	public <A, B, C> void drawActiveWindows(AbsoluteLocation selectedBlock
			, PartialEntity selectedEntity
			, WindowData<A> topLeft
			, WindowData<B> topRight
			, WindowData<C> bottom
			, NonStackableItem[] armourSlots
			, Consumer<BodyPart> eventHoverBodyPart
			, Point cursor
	)
	{
		_ui.enterUiRenderMode();
		
		// If our eye is under a liquid, draw the liquid over the screen (we do this here since it is part of the orthographic plane and not logically part of the scene).
		if (null != _eyeBlockLocation)
		{
			BlockProxy eyeProxy = _blockLookup.apply(_eyeBlockLocation);
			if (null != eyeProxy)
			{
				Block blockType = eyeProxy.getBlock();
				if (_waterBlockTypes.contains(blockType))
				{
					_ui.drawWholeTextureRect(_ui.pixelBlueAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
				}
				else if (_lavaBlockTypes.contains(blockType))
				{
					_ui.drawWholeTextureRect(_ui.pixelOrangeLava, -1.0f, -1.0f, 1.0f, 1.0f);
				}
			}
		}
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		if (null != _entityBinding.data)
		{
			_drawHotbar();
			_metaDataWindow.view().render(_metaDataWindow.location(), _metaDataWindow.binding());
		}
		
		// We need to draw the hover last so we track the Runnable to do that (avoids needing to re-associate with the correct type by leaving the action opaque).
		Runnable hoverRunnable = null;
		// We will disable the handling of any selection if we draw any overlay windows (they should be null in that case, anyway).
		boolean didDrawWindows = false;
		if (null != topLeft)
		{
			Runnable hover = _drawWindow(topLeft, WINDOW_TOP_LEFT, cursor);
			if (null != hover)
			{
				hoverRunnable = hover;
			}
			didDrawWindows = true;
		}
		if (null != topRight)
		{
			Runnable hover = _drawWindow(topRight, WINDOW_TOP_RIGHT, cursor);
			if (null != hover)
			{
				hoverRunnable = hover;
			}
			didDrawWindows = true;
		}
		if (null != bottom)
		{
			Runnable hover = _drawWindow(bottom, WINDOW_BOTTOM, cursor);
			if (null != hover)
			{
				hoverRunnable = hover;
			}
			didDrawWindows = true;
		}
		
		if (didDrawWindows)
		{
			// We are in windowed mode so also draw the armour slots.
			_drawArmourSlots(armourSlots, eventHoverBodyPart, cursor);
		}
		else
		{
			// We are not in windowed mode so draw the selection (if any) and crosshairs.
			// If there is anything selected, draw its description at the top of the screen (we always prioritize the block, but at most one of these can be non-null).
			if (null != selectedBlock)
			{
				// Draw the block information.
				BlockProxy proxy = _blockLookup.apply(selectedBlock);
				if (null != proxy)
				{
					Block blockUnderMouse = proxy.getBlock();
					if (_env.special.AIR != blockUnderMouse)
					{
						Item itemUnderMouse = blockUnderMouse.item();
						_drawTextInFrame(SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM, itemUnderMouse.name());
					}
				}
			}
			else if (null != selectedEntity)
			{
				// Draw the entity information.
				// If this matches a player, show the name instead of the type name.
				String textToShow = _otherPlayersById.get(selectedEntity.id());
				if (null == textToShow)
				{
					textToShow = selectedEntity.type().name();
				}
				_drawTextInFrame(SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM, textToShow);
			}
			
			_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		}
		
		// If we should be rendering a hover, do it here.
		if (null != hoverRunnable)
		{
			hoverRunnable.run();
		}
		
		// If we are paused, show the pause overlay.
		if (_isPaused)
		{
			// Draw the overlay to dim the window.
			_ui.drawWholeTextureRect(_ui.pixelDarkGreyAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
			
			// Draw the paused text.
			_ui.drawLabel(-0.2f, -0.0f, 0.1f, "Paused");
		}
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
	}

	public void setThisEntity(Entity projectedEntity)
	{
		_entityBinding.data = projectedEntity;
	}

	public void otherPlayerJoined(int clientId, String name)
	{
		Object old = _otherPlayersById.put(clientId, name);
		Assert.assertTrue(null == old);
	}

	public void otherPlayerLeft(int clientId)
	{
		Object old = _otherPlayersById.remove(clientId);
		Assert.assertTrue(null != old);
	}

	public void updateEyeBlock(AbsoluteLocation eyeBlockLocation)
	{
		_eyeBlockLocation = eyeBlockLocation;
	}

	public void setPaused(boolean isPaused)
	{
		_isPaused = isPaused;
	}

	public void shutdown()
	{
		_ui.shutdown();
	}


	private void _drawHotbar()
	{
		float hotbarWidth = ((float)Entity.HOTBAR_SIZE * HOTBAR_ITEM_SCALE) + ((float)(Entity.HOTBAR_SIZE - 1) * HOTBAR_ITEM_SPACING);
		float nextLeftButton = - hotbarWidth / 2.0f;
		Inventory entityInventory = _getEntityInventory();
		int[] hotbarKeys = _entityBinding.data.hotbarItems();
		int activeIndex = _entityBinding.data.hotbarIndex();
		for (int i = 0; i < Entity.HOTBAR_SIZE; ++i)
		{
			int outline = (activeIndex == i)
					? _ui.pixelGreen
					: _ui.pixelLightGrey
			;
			int thisKey = hotbarKeys[i];
			if (0 == thisKey)
			{
				// No item so just draw the frame.
				_drawOverlayFrame(_ui.pixelDarkGreyAlpha, outline, nextLeftButton, HOTBAR_BOTTOM_Y, nextLeftButton + HOTBAR_ITEM_SCALE, HOTBAR_BOTTOM_Y + HOTBAR_ITEM_SCALE);
			}
			else
			{
				// There is something here so render it.
				Items stack = entityInventory.getStackForKey(thisKey);
				if (null != stack)
				{
					_renderStackableItem(nextLeftButton, HOTBAR_BOTTOM_Y, nextLeftButton + HOTBAR_ITEM_SCALE, HOTBAR_BOTTOM_Y + HOTBAR_ITEM_SCALE, outline, stack, false);
				}
				else
				{
					NonStackableItem nonStack = entityInventory.getNonStackableForKey(thisKey);
					_renderNonStackableItem(nextLeftButton, HOTBAR_BOTTOM_Y, nextLeftButton + HOTBAR_ITEM_SCALE, HOTBAR_BOTTOM_Y + HOTBAR_ITEM_SCALE, outline, nonStack, false);
				}
			}
			nextLeftButton += HOTBAR_ITEM_SCALE + HOTBAR_ITEM_SPACING;
		}
	}

	private void _drawEntityMetaData(Rect location, Binding<Entity> binding)
	{
		_drawOverlayFrame(_ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
		
		float valueMargin = location.leftX() + META_DATA_LABEL_WIDTH;
		
		// We will use the greater of authoritative and projected for most of these stats.
		// That way, we get the stability of the authoritative numbers but the quick response to eating/breathing actions)
		byte health = binding.data.health();
		float base = location.bottomY() + 2.0f * SMALL_TEXT_HEIGHT;
		float top = base + SMALL_TEXT_HEIGHT;
		_ui.drawLabel(location.leftX(), base, top, "Health");
		_ui.drawLabel(valueMargin, base, top, Byte.toString(health));
		
		byte food = binding.data.food();
		base = location.bottomY() + 1.0f * SMALL_TEXT_HEIGHT;
		top = base + SMALL_TEXT_HEIGHT;
		_ui.drawLabel(location.leftX(), base, top, "Food");
		_ui.drawLabel(valueMargin, base, top, Byte.toString(food));
		
		int breath = binding.data.breath();
		base = location.bottomY() + 0.0f * SMALL_TEXT_HEIGHT;
		top = base + SMALL_TEXT_HEIGHT;
		_ui.drawLabel(location.leftX(), base, top, "Breath");
		_ui.drawLabel(valueMargin, base, top, Integer.toString(breath));
	}

	// Returns a Runnable to draw the hover, if it was detected here.
	private <T> Runnable _drawWindow(WindowData<T> data, _WindowDimensions dimensions, Point cursor)
	{
		// Draw the window outline.
		_drawOverlayFrame(_ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, dimensions.leftX, dimensions.bottomY, dimensions.rightX, dimensions.topY);
		
		// Draw the title.
		float labelRight = _ui.drawLabel(dimensions.leftX, dimensions.topY - WINDOW_TITLE_HEIGHT, dimensions.topY, data.name.toUpperCase());
		float rightEdgeOfTitle = labelRight;
		if (data.maxSize > 0)
		{
			String extraTitle = String.format("(%d/%d)", data.usedSize, data.maxSize);
			float bottom = dimensions.topY - WINDOW_TITLE_HEIGHT;
			rightEdgeOfTitle = _ui.drawLabel(labelRight + WINDOW_MARGIN, bottom, bottom + WINDOW_TITLE_HEIGHT, extraTitle.toUpperCase());
		}
		
		// If there is fuel, draw that item to the right of the title.
		FuelSlot optionalFuel = data.optionalFuel;
		if (null != optionalFuel)
		{
			float right = rightEdgeOfTitle + WINDOW_MARGIN;
			float bottom = dimensions.topY - WINDOW_TITLE_HEIGHT;
			_renderItem(right, bottom, right + WINDOW_ITEM_SIZE, bottom + WINDOW_ITEM_SIZE, _ui.pixelGreen, optionalFuel.fuel, 0, optionalFuel.remainingFraction, false);
		}
		
		// We want to draw these in a grid, in rows.  Leave space for the right margin since we count the left margin in the element sizing.
		float xSpace = dimensions.rightX - dimensions.leftX - WINDOW_MARGIN;
		float ySpace = dimensions.topY - dimensions.bottomY - WINDOW_MARGIN;
		// The size of each item is the margin before the element and the element itself.
		float spacePerElement = WINDOW_ITEM_SIZE + WINDOW_MARGIN;
		int itemsPerRow = (int) Math.round(Math.floor(xSpace / spacePerElement));
		int rowsPerPage = (int) Math.round(Math.floor(ySpace / spacePerElement));
		int itemsPerPage = itemsPerRow * rowsPerPage;
		int xElement = 0;
		int yElement = 0;
		
		float leftMargin = dimensions.leftX + WINDOW_MARGIN;
		// Leave space for top margin and title.
		float topMargin = dimensions.topY - WINDOW_TITLE_HEIGHT - WINDOW_MARGIN;
		int totalItems = data.items.size();
		int pageCount = ((totalItems - 1) / itemsPerPage) + 1;
		// Be aware that this may have changed without the caller knowing it.
		int currentPage = Math.min(data.currentPage, pageCount - 1);
		int startingIndex = currentPage * itemsPerPage;
		int firstIndexBeyondPage = startingIndex + itemsPerPage;
		if (firstIndexBeyondPage > totalItems)
		{
			firstIndexBeyondPage = totalItems;
		}
		
		T hoverOver = null;
		for (T elt : data.items.subList(startingIndex, firstIndexBeyondPage))
		{
			// We want to render these left->right, top->bottom but GL is left->right, bottom->top so we increment X and Y in opposite ways.
			float left = leftMargin + (xElement * spacePerElement);
			float top = topMargin - (yElement * spacePerElement);
			float bottom = top - WINDOW_ITEM_SIZE;
			float right = left + WINDOW_ITEM_SIZE;
			// We only handle the mouse-over if there is a handler we will notify.
			boolean isMouseOver = _isMouseOver(left, bottom, right, top, cursor);
			data.renderItem.drawItem(left, bottom, right, top, elt, isMouseOver);
			if (isMouseOver)
			{
				hoverOver = elt;
			}
			
			// We also want to call the associated handler.
			if (isMouseOver && (null != data.eventHoverOverItem))
			{
				data.eventHoverOverItem.accept(elt);
			}
			
			// On to the next item.
			xElement += 1;
			if (xElement >= itemsPerRow)
			{
				xElement = 0;
				yElement += 1;
			}
		}
		
		// Draw our pagination buttons if they make sense.
		if (pageCount > 1)
		{
			boolean canPageBack = (currentPage > 0);
			boolean canPageForward = (currentPage < (pageCount - 1));
			float buttonTop = dimensions.topY - WINDOW_PAGE_BUTTON_HEIGHT;
			float buttonBase = buttonTop - WINDOW_PAGE_BUTTON_HEIGHT;
			if (canPageBack)
			{
				float left = dimensions.rightX - 0.25f;
				boolean isMouseOver = _drawTextInFrameWithHoverCheck(left, buttonBase, "<", cursor);
				if (isMouseOver)
				{
					data.eventHoverChangePage.accept(currentPage - 1);
				}
			}
			String label = (currentPage + 1) + " / " + pageCount;
			_ui.drawLabel(dimensions.rightX - 0.2f, buttonBase, buttonTop, label);
			if (canPageForward)
			{
				float left = dimensions.rightX - 0.1f;
				boolean isMouseOver = _drawTextInFrameWithHoverCheck(left, buttonBase, ">", cursor);
				if (isMouseOver)
				{
					data.eventHoverChangePage.accept(currentPage + 1);
				}
			}
		}
		
		// Capture the hover render operation, if applicable.
		final T finalHoverOver = hoverOver;
		return (null != finalHoverOver)
				? () -> data.renderHover.drawHoverAtPoint(cursor, finalHoverOver)
				: null
		;
	}

	private void _renderStackableItem(float left, float bottom, float right, float top, int outlineTexture, Items item, boolean isMouseOver)
	{
		float noProgress = 0.0f;
		_renderItem(left, bottom, right, top, outlineTexture, item.type(), item.count(), noProgress, isMouseOver);
	}

	private void _renderNonStackableItem(float left, float bottom, float right, float top, int outlineTexture, NonStackableItem item, boolean isMouseOver)
	{
		Item type = item.type();
		int maxDurability = _env.durability.getDurability(type);
		int count = 0;
		float progress = (maxDurability > 0)
				? (float)item.durability() / (float)maxDurability
				: 0.0f
		;
		_renderItem(left, bottom, right, top, outlineTexture, type, count, progress, isMouseOver);
	}

	private void _renderItem(float left, float bottom, float right, float top, int outlineTexture, Item item, int count, float progress, boolean isMouseOver)
	{
		// Draw the background.
		int backgroundTexture = isMouseOver
				? _ui.pixelLightGrey
				: _ui.pixelDarkGreyAlpha
		;
		_drawOverlayFrame(backgroundTexture, outlineTexture, left, bottom, right, top);
		
		// Draw the item.
		_ui.drawItemTextureRect(item, left, bottom, right, top);
		
		// Draw the number in the corner (only if it is non-zero).
		if (count > 0)
		{
			TextManager.Element element = _ui.textManager.lazilyLoadStringTexture(Integer.toString(count));
			// We want to draw the text in the bottom-left of the box, at half-height.
			float vDelta = (top - bottom) / 2.0f;
			float hDelta = vDelta * element.aspectRatio();
			_ui.drawWholeTextureRect(element.textureObject(), left, bottom, left + hDelta, bottom + vDelta);
		}
		
		// If there is a progress bar, draw it on top.
		if (progress > 0.0f)
		{
			float progressTop = bottom + (top - bottom) * progress;
			_ui.drawWholeTextureRect(_ui.pixelGreenAlpha, left, bottom, right, progressTop);
		}
	}

	private void _drawArmourSlots(NonStackableItem[] armourSlots, Consumer<BodyPart> eventHoverBodyPart, Point cursor)
	{
		float nextTopSlot = ARMOUR_SLOT_TOP_EDGE;
		for (int i = 0; i < 4; ++i)
		{
			float left = ARMOUR_SLOT_RIGHT_EDGE - ARMOUR_SLOT_SCALE;
			float bottom = nextTopSlot - ARMOUR_SLOT_SCALE;
			float right = ARMOUR_SLOT_RIGHT_EDGE;
			float top = nextTopSlot;
			boolean isMouseOver = _isMouseOver(left, bottom, right, top, cursor);
			
			// See if there is an item for this slot.
			NonStackableItem armour = armourSlots[i];
			
			if (null != armour)
			{
				// Draw this item.
				_renderNonStackableItem(left, bottom, right, top, _ui.pixelLightGrey, armour, isMouseOver);
			}
			else
			{
				// Just draw the background.
				int backgroundTexture = isMouseOver
						? _ui.pixelLightGrey
						: _ui.pixelDarkGreyAlpha
				;
				
				_drawOverlayFrame(backgroundTexture, _ui.pixelLightGrey, left, bottom, right, top);
			}
			if (isMouseOver)
			{
				eventHoverBodyPart.accept(BodyPart.values()[i]);
			}
			
			nextTopSlot -= ARMOUR_SLOT_SCALE + ARMOUR_SLOT_SPACING;
		}
	}

	private void _drawTextInFrame(float left, float bottom, String text)
	{
		_drawTextInFrameWithHoverCheck(left, bottom, text, null);
	}

	private boolean _drawTextInFrameWithHoverCheck(float left, float bottom, String text, Point cursor)
	{
		TextManager.Element element = _ui.textManager.lazilyLoadStringTexture(text.toUpperCase());
		float top = bottom + GENERAL_TEXT_HEIGHT;
		float right = left + element.aspectRatio() * (top - bottom);
		
		boolean isMouseOver = _isMouseOver(left, bottom, right, top, cursor);
		int backgroundTexture = isMouseOver
				? _ui.pixelLightGrey
				: _ui.pixelDarkGreyAlpha
		;
		
		_drawOverlayFrame(backgroundTexture, _ui.pixelLightGrey, left, bottom, right, top);
		_ui.drawWholeTextureRect(element.textureObject(), left, bottom, right, top);
		return isMouseOver;
	}

	private void _drawOverlayFrame(int backgroundTexture, int outlineTexture, float left, float bottom, float right, float top)
	{
		// We want draw the frame and then the space on top of that.
		_ui.drawWholeTextureRect(outlineTexture, left - OUTLINE_SIZE, bottom - OUTLINE_SIZE, right + OUTLINE_SIZE, top + OUTLINE_SIZE);
		_ui.drawWholeTextureRect(backgroundTexture, left, bottom, right, top);
	}

	private static boolean _isMouseOver(float left, float bottom, float right, float top, Point cursor)
	{
		boolean isOver;
		if (null != cursor)
		{
			float glX = cursor.x();
			float glY = cursor.y();
			isOver = ((left <= glX) && (glX <= right) && (bottom <= glY) && (glY <= top));
		}
		else
		{
			isOver = false;
		}
		return isOver;
	}

	private Inventory _getEntityInventory()
	{
		Inventory inventory = _entityBinding.data.isCreativeMode()
				? CreativeInventory.fakeInventory()
				: _entityBinding.data.inventory()
		;
		return inventory;
	}


	public static interface ItemRenderer<T>
	{
		void drawItem(float left, float bottom, float right, float top, T item, boolean isMouseOver);
	}

	public static interface HoverRenderer<T>
	{
		void drawHoverAtPoint(Point cursor, T item);
	}

	public static record WindowData<T>(String name
			, int usedSize
			, int maxSize
			, int currentPage
			, IntConsumer eventHoverChangePage
			, List<T> items
			, ItemRenderer<T> renderItem
			, HoverRenderer<T> renderHover
			, Consumer<T> eventHoverOverItem
			, FuelSlot optionalFuel
	) {}

	public static record CraftDescription(Craft craft
			, Items output
			, ItemRequirement[] input
			, float progress
			, boolean canBeSelected
	) {}

	public static record ItemRequirement(Item type
			, int required
			, int available
	) {}

	public static record FuelSlot(Item fuel
			, float remainingFraction
	) {}

	private static record _WindowDimensions(float leftX
			, float bottomY
			, float rightX
			, float topY
	) {}
}
