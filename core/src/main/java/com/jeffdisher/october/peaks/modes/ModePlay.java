package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.MouseState;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.ViewHotbar;
import com.jeffdisher.october.peaks.ui.ViewMetaData;
import com.jeffdisher.october.peaks.ui.ViewSelection;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.peaks.utils.GeometryHelpers;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * The mode where play is normal.  Cursor is captured and there is no open window.
 */
public class ModePlay implements IGameMode
{
	public static final float RETICLE_SIZE = 0.05f;
	public static final float CHARGE_BAR_WIDTH_MAX = 0.4f;
	public static final float CHARGE_BAR_LEFT = -0.2f;
	public static final float CHARGE_BAR_BOTTOM = -0.25f;
	public static final float CHARGE_BAR_TOP = -0.2f;

	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final MouseState _mouseState;
	private final Binding<WorldSelection> _selectionBinding;
	private final Binding<Entity> _entityBinding;
	public final Window selectionWindow;
	private final Window _metaDataWindow;
	private final Window _hotbarWindow;

	public GameSession currentGameSession;
	public PartialEntity selectedEntity;
	public AbsoluteLocation selectedBlock;
	public Block selectedBlockType;
	public FacingDirection selectedBlockOrientation;
	public AbsoluteLocation preSelectedBlock;

	public ModePlay(ModeContainer modeContainer
		, GlUi ui
		, MouseState mouseState
		, Binding<Entity> entityBinding
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_mouseState = mouseState;
		_selectionBinding = new Binding<>(null);
		_entityBinding = entityBinding;
		this.selectionWindow = new Window(ViewSelection.LOCATION, new ViewSelection(ui, Environment.getShared(), _selectionBinding, (AbsoluteLocation location) -> {
			return _modeContainer.play.currentGameSession.blockLookup.readBlock(location);
		}, (Integer id) -> {
			String name = null;
			if (null != ModePlay.this.currentGameSession)
			{
				name = ModePlay.this.currentGameSession.otherPlayerNamesById.get(id);
			}
			return name;
		}));
		_metaDataWindow = new Window(ViewMetaData.LOCATION, new ViewMetaData(_ui, _entityBinding));
		_hotbarWindow = new Window(ViewHotbar.LOCATION, new ViewHotbar(_ui, _entityBinding));
	}

	public ModePlay becomeActive(GameSession currentGameSession)
	{
		this.currentGameSession = currentGameSession;
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
		_selectionBinding.set(null);
		this.currentGameSession = null;
		this.selectedEntity = null;
		this.selectedBlock = null;
		this.selectedBlockType = null;
		this.selectedBlockOrientation = null;
		this.preSelectedBlock = null;
	}

	@Override
	public void handleEscape()
	{
		this.currentGameSession.client.pauseGame();
		_modeContainer.setActive(_modeContainer.pause.becomeActive(this.currentGameSession));
		_mouseState.captureState.shouldCaptureMouse(false);
	}

	public void updateSelection()
	{
		// Capture whatever is selected.
		// Note that these are currently stored as public variables since external consumers still need to reference them.
		this.selectedEntity = null;
		this.selectedBlock = null;
		this.selectedBlockType = null;
		this.selectedBlockOrientation = null;
		this.preSelectedBlock = null;
		
		WorldSelection selection = _modeContainer.play.currentGameSession.selectionManager.findSelection();
		if (null != selection)
		{
			this.selectedEntity = selection.entity();
			this.selectedBlock = selection.stopBlock();
			BlockProxy proxy = (null != this.selectedBlock)
				? _modeContainer.play.currentGameSession.blockLookup.readBlock(this.selectedBlock)
				: null
			;
			if (null != proxy)
			{
				this.selectedBlockType = proxy.getBlock();
				this.selectedBlockOrientation = proxy.getOrientation();
			}
			else
			{
				// Note that the stopBlock can also point at the first not loaded block (since it is a "stop point"), but there is no point in drawing that.
				this.selectedBlock = null;
			}
			this.preSelectedBlock = selection.preStopBlock();
		}
		_selectionBinding.set(selection);
	}

	public void drawCommonPlayOverlay(GameSession gameSession, Block waterBlock, Block lavaBlock)
	{
		// We will assume that we are already in the UI rendering mode.
		// If our eye is under a liquid, draw the liquid over the screen (we do this here since it is part of the orthographic plane and not logically part of the scene).
		Vector eye = gameSession.movement.computeEye();
		if (null != eye)
		{
			AbsoluteLocation eyeBlockLocation = GeometryHelpers.locationFromVector(eye);
			BlockProxy eyeProxy = gameSession.blockLookup.readBlock(eyeBlockLocation);
			if (null != eyeProxy)
			{
				Block blockType = eyeProxy.getBlock();
				if (waterBlock == blockType)
				{
					_ui.drawWholeTextureRect(_ui.pixelBlueAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
				}
				else if (lavaBlock == blockType)
				{
					_ui.drawWholeTextureRect(_ui.pixelOrangeLava, -1.0f, -1.0f, 1.0f, 1.0f);
				}
			}
		}
		
		// We are not in windowed mode so draw the selection (if any) and crosshairs.
		IAction noAction = _modeContainer.play.selectionWindow.doRender(_mouseState.cursor);
		Assert.assertTrue(null == noAction);
		
		_ui.drawReticle(RETICLE_SIZE, RETICLE_SIZE);
		
		// Once we have loaded the entity, we can draw the hotbar and meta-data.
		Entity entity = _entityBinding.get();
		if (null != entity)
		{
			_drawPassiveOverlayWindows();
			
			int chargeMillis = entity.ephemeralShared().chargeMillis();
			if (chargeMillis > 0)
			{
				// We want to show the weapon charge as a horizontal progress bar, from left to right.
				int key = entity.hotbarItems()[entity.hotbarIndex()];
				// If we have nothing selected, we should have cleared the charge.
				Assert.assertTrue(0 != key);
				int maxCharge = Environment.getShared().tools.getChargeMillis(MiscPeaksHelpers.getInventory(entity).getSlotForKey(key).getType());
				// If we have a charge, we must be charging something.
				Assert.assertTrue(maxCharge > 0);
				float progress = (float)chargeMillis / (float)maxCharge;
				float left = CHARGE_BAR_LEFT;
				float bottom = CHARGE_BAR_BOTTOM;
				float right = progress * CHARGE_BAR_WIDTH_MAX + CHARGE_BAR_LEFT;
				float top = CHARGE_BAR_TOP;
				_ui.drawWholeTextureRect(_ui.pixelGreenAlpha, left, bottom, right, top);
			}
		}
	}

	public void drawPassiveOverlayWindows()
	{
		Entity entity = _entityBinding.get();
		if (null != entity)
		{
			_drawPassiveOverlayWindows();
		}
	}


	private void _drawPassiveOverlayWindows()
	{
		IAction noAction = _hotbarWindow.doRender(_mouseState.cursor);
		Assert.assertTrue(null == noAction);
		noAction = _metaDataWindow.doRender(_mouseState.cursor);
		Assert.assertTrue(null == noAction);
	}
}
