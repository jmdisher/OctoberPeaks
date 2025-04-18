package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.CraftDescription;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.IView;
import com.jeffdisher.october.peaks.ui.ItemTuple;
import com.jeffdisher.october.peaks.ui.Point;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.ui.ViewArmour;
import com.jeffdisher.october.peaks.ui.ViewHotbar;
import com.jeffdisher.october.peaks.ui.ViewMetaData;
import com.jeffdisher.october.peaks.ui.ViewSelection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BodyPart;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.NonStackableItem;
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
	public static final Rect WINDOW_TOP_LEFT = new Rect(-0.95f, 0.05f, -0.05f, 0.95f);
	public static final Rect WINDOW_TOP_RIGHT = new Rect(0.05f, 0.05f, ViewArmour.ARMOUR_SLOT_RIGHT_EDGE - ViewArmour.ARMOUR_SLOT_SCALE - ViewArmour.ARMOUR_SLOT_SPACING, 0.95f);
	public static final Rect WINDOW_BOTTOM = new Rect(-0.95f, -0.80f, 0.95f, -0.05f);
	public static final float RETICLE_SIZE = 0.05f;

	private final GlUi _ui;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final Map<Integer, String> _otherPlayersById;
	private final Set<Block> _waterBlockTypes;
	private final Set<Block> _lavaBlockTypes;
	private AbsoluteLocation _eyeBlockLocation;

	// Data bindings populated by updates and read when rendering windows in the mode.
	private final Binding<Entity> _entityBinding;

	// The window definitions to be used when rendering specific modes.
	private final Window<Entity> _metaDataWindow;
	private final Window<Entity> _hotbarWindow;
	private final Window<NonStackableItem[]> _armourWindow;
	private final Window<WorldSelection> _selectionWindow;

	public WindowManager(Environment env
			, GlUi ui
			, Function<AbsoluteLocation, BlockProxy> blockLookup
			, Consumer<BodyPart> eventHoverArmourBodyPart
			, Binding<Entity> entityBinding
			, Binding<WorldSelection> selectionBinding
			, Binding<NonStackableItem[]> armourBinding
	)
	{
		_ui = ui;
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
		
		// Define the data bindings used by the window system.
		_entityBinding = entityBinding;
		
		// Define the windows for different UI modes.
		_metaDataWindow = new Window<>(ViewMetaData.LOCATION, new ViewMetaData(_ui, _entityBinding));
		_hotbarWindow = new Window<>(ViewHotbar.LOCATION, new ViewHotbar(_ui, _entityBinding));
		_armourWindow = new Window<>(ViewArmour.LOCATION, new ViewArmour(_ui, armourBinding, eventHoverArmourBodyPart));
		_selectionWindow = new Window<>(ViewSelection.LOCATION, new ViewSelection(_ui, env, selectionBinding, _blockLookup, _otherPlayersById));
	}

	public IAction drawPlayMode(Point cursor)
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
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
		}
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _selectionWindow.doRender(cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
		
		return null;
	}

	public IAction drawMenuMode(Point cursor)
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
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
		}
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _selectionWindow.doRender(cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		// Draw the overlay to dim the window.
		_ui.drawWholeTextureRect(_ui.pixelDarkGreyAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
		
		// Draw the paused text.
		_ui.drawLabel(-0.2f, -0.0f, 0.1f, "Paused");
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
		
		return null;
	}

	public IAction drawInventoryMode(IView<List<ItemTuple<CraftDescription>>> craftingPanelView
			, IView<Inventory> thisEntityInventoryView
			, IView<Inventory> bottomPaneInventoryView
			, Point cursor
	)
	{
		Assert.assertTrue(null != thisEntityInventoryView);
		Assert.assertTrue(null != bottomPaneInventoryView);
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
		if (null != _entityBinding.get())
		{
			IAction noAction = _hotbarWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
			noAction = _metaDataWindow.doRender(cursor);
			Assert.assertTrue(null == noAction);
		}
		
		IAction action = null;
		if (null != craftingPanelView)
		{
			IAction hover = craftingPanelView.render(WINDOW_TOP_LEFT, cursor);
			if (null != hover)
			{
				action = hover;
			}
		}
		IAction hover = thisEntityInventoryView.render(WINDOW_TOP_RIGHT, cursor);
		if (null != hover)
		{
			action = hover;
		}
		hover = bottomPaneInventoryView.render(WINDOW_BOTTOM, cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// We are in windowed mode so also draw the armour slots.
		hover = _armourWindow.doRender(cursor);
		if (null != hover)
		{
			action = hover;
		}
		
		// If we should be rendering a hover, do it here.
		if (null != action)
		{
			action.renderHover(cursor);
		}
		
		// Allow any periodic cleanup.
		_ui.textManager.allowTexturePurge();
		
		// Return any action so that the caller can run the action now that rendering is finished.
		return action;
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

	public void shutdown()
	{
		_ui.shutdown();
	}
}
