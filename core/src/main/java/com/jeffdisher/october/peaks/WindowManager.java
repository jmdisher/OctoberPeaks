package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.IView;
import com.jeffdisher.october.peaks.ui.Point;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.UiIdioms;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.ui.WindowArmour;
import com.jeffdisher.october.peaks.ui.WindowEntityInventory;
import com.jeffdisher.october.peaks.ui.WindowHotbar;
import com.jeffdisher.october.peaks.ui.WindowMetaData;
import com.jeffdisher.october.peaks.ui.WindowSelection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Craft;
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
	public static final float WINDOW_ITEM_SIZE = 0.1f;
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;
	public static final _WindowDimensions WINDOW_TOP_LEFT = new _WindowDimensions(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final Rect WINDOW_TOP_RIGHT = new Rect(0.05f, 0.05f, WindowArmour.ARMOUR_SLOT_RIGHT_EDGE - WindowArmour.ARMOUR_SLOT_SCALE - WindowArmour.ARMOUR_SLOT_SPACING, 0.95f);
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
	private final Binding<WindowSelection.Selection> _selectionBinding;

	// The window definitions to be used when rendering specific modes.
	private final Window<Entity> _metaDataWindow;
	private final Window<Entity> _hotbarWindow;
	private final Window<Entity> _armourWindow;
	private final Window<WindowSelection.Selection> _selectionWindow;

	public WindowManager(Environment env
			, GL20 gl
			, TextureAtlas<ItemVariant> itemAtlas
			, Function<AbsoluteLocation, BlockProxy> blockLookup
			, Consumer<BodyPart> eventHoverArmourBodyPart
	)
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
			UiIdioms.renderStackableItem(_ui, left, bottom, right, top, _ui.pixelLightGrey, item, isMouseOver);
		};
		this.renderNonStackable = (float left, float bottom, float right, float top, NonStackableItem item, boolean isMouseOver) -> {
			UiIdioms.renderNonStackableItem(_ui, left, bottom, right, top, _ui.pixelLightGrey, item, isMouseOver);
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
			UiIdioms.renderItem(_ui, left, bottom, right, top, outlineTexture, item.output.type(), item.output.count(), item.progress, item.canBeSelected ? isMouseOver : false);
		};
		this.hoverItem = (Point cursor, Item item) -> {
			// Just draw the name.
			String name = item.name();
			UiIdioms.drawTextRootedAtTop(_ui, cursor.x(), cursor.y(), name);
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
			UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, glX, glY - heightOfHover, glX + widthOfHover, glY);
			
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
				UiIdioms.renderItem(_ui, inputLeft, inputBottom, inputLeft + WINDOW_ITEM_SIZE, inputBottom + WINDOW_ITEM_SIZE, outlineTexture, items.type, items.required, noProgress, isMouseOver);
				inputLeft += WINDOW_ITEM_SIZE + WINDOW_MARGIN;
			}
		};
		
		// Define the data bindings used by the window system.
		_entityBinding = new Binding<>();
		_selectionBinding = new Binding<>();
		_selectionBinding.data = new WindowSelection.Selection(null, null);
		
		// Define the windows for different UI modes.
		_metaDataWindow = new Window<>(WindowMetaData.LOCATION, WindowMetaData.buildRenderer(_ui), _entityBinding);
		_hotbarWindow = new Window<>(WindowHotbar.LOCATION, WindowHotbar.buildRenderer(_ui), _entityBinding);
		_armourWindow = new Window<>(WindowArmour.LOCATION, WindowArmour.buildRenderer(_ui, eventHoverArmourBodyPart), _entityBinding);
		_selectionWindow = new Window<>(WindowSelection.LOCATION, WindowSelection.buildRenderer(_ui, _env, _blockLookup, _otherPlayersById), _selectionBinding);
	}

	public <A, B, C> IAction drawActiveWindows(AbsoluteLocation selectedBlock
			, PartialEntity selectedEntity
			, WindowData<A> topLeft
			, IView<Inventory> thisEntityInventoryView
			, Binding<Inventory> thisEntityInventoryBinding
			, WindowData<C> bottom
			, NonStackableItem[] armourSlots
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
			IAction noAction = _hotbarWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
		}
		
		IAction action = null;
		// We will disable the handling of any selection if we draw any overlay windows (they should be null in that case, anyway).
		boolean didDrawWindows = false;
		if (null != topLeft)
		{
			IAction hover = _drawWindow(topLeft, WINDOW_TOP_LEFT, cursor);
			if (null != hover)
			{
				action = hover;
			}
			didDrawWindows = true;
		}
		if (null != thisEntityInventoryView)
		{
			IAction hover = thisEntityInventoryView.render(WINDOW_TOP_RIGHT, thisEntityInventoryBinding, cursor);
			if (null != hover)
			{
				action = hover;
			}
			didDrawWindows = true;
		}
		if (null != bottom)
		{
			IAction hover = _drawWindow(bottom, WINDOW_BOTTOM, cursor);
			if (null != hover)
			{
				action = hover;
			}
			didDrawWindows = true;
		}
		
		if (didDrawWindows)
		{
			// We are in windowed mode so also draw the armour slots.
			IAction hover = _armourWindow.doRender(cursor);
			if (null != hover)
			{
				action = hover;
			}
		}
		else
		{
			// We are not in windowed mode so draw the selection (if any) and crosshairs.
			_selectionBinding.data = new WindowSelection.Selection(selectedBlock, selectedEntity);
			IAction noAction = _selectionWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
			
			_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action)
		{
			action.renderHover(cursor);
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
		
		// Return any action so that the caller can run the action now that rendering is finished.
		return action;
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

	public IView<Inventory> buildTopRightView(String title, IntConsumer mouseOverKeyConsumer, BooleanSupplier shouldChangePage)
	{
		return WindowEntityInventory.buildRenderer(_ui, title, WINDOW_TOP_RIGHT, mouseOverKeyConsumer, shouldChangePage);
	}

	public void shutdown()
	{
		_ui.shutdown();
	}


	private <T> IAction _drawWindow(WindowData<T> data, _WindowDimensions dimensions, Point cursor)
	{
		// Draw the window outline.
		UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, dimensions.leftX, dimensions.bottomY, dimensions.rightX, dimensions.topY);
		
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
			UiIdioms.renderItem(_ui, right, bottom, right + WINDOW_ITEM_SIZE, bottom + WINDOW_ITEM_SIZE, _ui.pixelGreen, optionalFuel.fuel, 0, optionalFuel.remainingFraction, false);
		}
		
		// We want to draw these in a grid, in rows.  Leave space for the right margin since we count the left margin in the element sizing.
		float xSpace = dimensions.rightX - dimensions.leftX - WINDOW_MARGIN;
		float ySpace = dimensions.topY - dimensions.bottomY - WINDOW_MARGIN;
		// The size of each item is the margin before the element and the element itself.
		float spacePerElement = WINDOW_ITEM_SIZE + WINDOW_MARGIN;
		int itemsPerRow = (int) Math.round(Math.floor(xSpace / spacePerElement));
		int rowsPerPage = (int) Math.round(Math.floor(ySpace / spacePerElement));
		int itemsPerPage = itemsPerRow * rowsPerPage;
		
		float leftMargin = dimensions.leftX + WINDOW_MARGIN;
		// Leave space for top margin and title.
		float topMargin = dimensions.topY - WINDOW_TITLE_HEIGHT - WINDOW_MARGIN;
		int totalItems = data.items.size();
		int pageCount = ((totalItems - 1) / itemsPerPage) + 1;
		// Be aware that this may have changed without the caller knowing it.
		int currentPage = Math.min(data.currentPage, pageCount - 1);
		
		// Draw our pagination buttons if they make sense.
		if (pageCount > 1)
		{
			UiIdioms.drawPageButtons(_ui, data.eventHoverChangePage, dimensions.rightX, dimensions.topY, cursor, pageCount, currentPage);
		}
		
		T hoverOver = UiIdioms.drawItemGrid(data.items, data.renderItem, cursor, spacePerElement, WINDOW_ITEM_SIZE, itemsPerRow, itemsPerPage, leftMargin, topMargin, totalItems, currentPage);
		
		IAction action = null;
		if (null != hoverOver)
		{
			// There is something to hover over so determine which action implementation to use.
			final T finalHoverOver = hoverOver;
			if (null != data.eventHoverOverItem())
			{
				action = new IAction() {
					@Override
					public void renderHover(Point cursor)
					{
						data.renderHover.drawHoverAtPoint(cursor, finalHoverOver);
					}
					@Override
					public void takeAction()
					{
						data.eventHoverOverItem.accept(finalHoverOver);
					}
				};
			}
			else
			{
				action = new IAction() {
					@Override
					public void renderHover(Point cursor)
					{
						data.renderHover.drawHoverAtPoint(cursor, finalHoverOver);
					}
					@Override
					public void takeAction()
					{
						// No action in this case.
					}
				};
			}
		}
		return action;
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
