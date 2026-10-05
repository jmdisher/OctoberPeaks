package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.client.RelativeDirection;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.logic.SpatialHelpers;
import com.jeffdisher.october.logic.ViscosityReader;
import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.persistence.MutableControls;
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
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
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
	private final InputCapture _inputCapture;
	private final Binding<WorldSelection> _selectionBinding;
	private final Binding<Entity> _entityBinding;
	private final Binding<Integer> _currentTradingPartnerIdBinding;
	public final Window selectionWindow;
	private final Window _metaDataWindow;
	private final Window _hotbarWindow;

	public GameSession currentGameSession;
	public PartialEntity selectedEntity;
	public AbsoluteLocation selectedBlock;
	public Block selectedBlockType;
	public FacingDirection selectedBlockOrientation;
	public AbsoluteLocation preSelectedBlock;

	// User input state specific to this mode.
	public boolean isWaitingForRightClickRelease;

	// Information related to world data.
	private final Block _waterBlock;
	private final Block _lavaBlock;
	private final EntityVolume _playerVolume;
	private final EntityType _villagerEntityType;

	// Tracking related to orientation change updates and walking.
	private boolean _orientationNeedsFlush;
	private float _yawRadians;
	private float _pitchRadians;
	private _AudibleMotion _audibleMotionInFrame;

	public ModePlay(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, Binding<Entity> entityBinding
		, Binding<Integer> currentTradingPartnerIdBinding
	)
	{
		Environment env = Environment.getShared();
		
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		_selectionBinding = new Binding<>(null);
		_entityBinding = entityBinding;
		_currentTradingPartnerIdBinding = currentTradingPartnerIdBinding;
		this.selectionWindow = new Window(ViewSelection.LOCATION, new ViewSelection(ui, env, _selectionBinding, (AbsoluteLocation location) -> {
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
		
		// Look up the environment data we require.
		_waterBlock = env.blocks.fromItem(env.items.getItemById("op.water_source"));
		_lavaBlock = env.blocks.fromItem(env.items.getItemById("op.lava_source"));
		_playerVolume = env.creatures.PLAYER.volume();
		_villagerEntityType = env.creatures.getTypeById("op.villager");
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
		_inputCapture.captureState.shouldCaptureMouse(false);
	}

	@Override
	public IAction drawRelevantWindows()
	{
		this.currentGameSession.scene.renderCommon();
		PartialEntity selectedEntity = this.selectedEntity;
		AbsoluteLocation selectedBlock = this.selectedBlock;
		Block stopBlockType = this.selectedBlockType;
		FacingDirection stopBlockOrientation = this.selectedBlockOrientation;
		this.currentGameSession.scene.renderSelection(selectedEntity, selectedBlock, stopBlockType, stopBlockOrientation);
		this.currentGameSession.eyeEffect.drawEyeEffect();
		
		// Now, draw the overlays.
		_ui.enterUiRenderMode();
		this.drawCommonPlayOverlay(this.currentGameSession, _waterBlock, _lavaBlock);
		return null;
	}

	@Override
	public void handleUserEvents()
	{
		// We area always capturing mouse movement in this mode.
		Assert.assertTrue(_inputCapture.shouldCaptureMouseMovements);
		
		// When we are capturing, the cursor is invisible and this is essentially a "yoke".
		if ((_inputCapture.mouseX != _inputCapture.lastReportedMouseX) || (_inputCapture.mouseY != _inputCapture.lastReportedMouseY))
		{
			int deltaX = _inputCapture.mouseX - _inputCapture.lastReportedMouseX;
			int deltaY = _inputCapture.mouseY - _inputCapture.lastReportedMouseY;
			_inputCapture.lastReportedMouseX = _inputCapture.mouseX;
			_inputCapture.lastReportedMouseY = _inputCapture.mouseY;
			
			// Something has to change for us to get this call.
			Assert.assertTrue((0 != deltaX) || (0 != deltaY));
			_yawRadians = this.currentGameSession.movement.rotateYaw(deltaX);
			_pitchRadians = this.currentGameSession.movement.rotatePitch(deltaY);
			_orientationNeedsFlush = true;
		}
		
		// Check out movement controls.
		RelativeDirection relativeMove = _getCurrentMove();
		if (null != relativeMove)
		{
			if (_inputCapture.controlHeld[MutableControls.Control.MOVE_SNEAK.ordinal()])
			{
				this.currentGameSession.client.sneak(relativeMove);
				_inputCapture.didAccountForTimeInFrame = true;
				
				// We will say that sneaking is silent.
				_audibleMotionInFrame = null;
			}
			else if (_inputCapture.controlHeld[MutableControls.Control.MOVE_SPRINT.ordinal()])
			{
				boolean runningSpeed = true;
				this.currentGameSession.client.accelerateHorizontal(relativeMove, runningSpeed);
				_inputCapture.didAccountForTimeInFrame = true;
				_audibleMotionInFrame = _AudibleMotion.RUN;
			}
			else
			{
				boolean runningSpeed = false;
				this.currentGameSession.client.accelerateHorizontal(relativeMove, runningSpeed);
				_inputCapture.didAccountForTimeInFrame = true;
				_audibleMotionInFrame = _AudibleMotion.WALK;
			}
		}
		
		// See if we want to jump or try descending a ladder.
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_JUMP.ordinal()])
		{
			this.currentGameSession.client.ascendOrJumpOrSwim();
		}
		else if (_inputCapture.controlHeld[MutableControls.Control.MOVE_SNEAK.ordinal()])
		{
			this.currentGameSession.client.tryDescend();
		}
		if (_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FLIGHT.ordinal()])
		{
			this.currentGameSession.client.toggleCreativeFlight();
			_inputCapture.controlReleased[MutableControls.Control.TOGGLE_FLIGHT.ordinal()] = false;
		}
		
		// We also update the selection in event handling (as this is a "scene-specific event", of sorts).
		// Capture whatever is selected.
		// Note that these are currently stored as public variables since external consumers still need to reference them.
		this.selectedEntity = null;
		this.selectedBlock = null;
		this.selectedBlockType = null;
		this.selectedBlockOrientation = null;
		this.preSelectedBlock = null;
		
		WorldSelection selection = this.currentGameSession.selectionManager.findSelection();
		if (null != selection)
		{
			this.selectedEntity = selection.entity();
			this.selectedBlock = selection.stopBlock();
			BlockProxy proxy = (null != this.selectedBlock)
				? this.currentGameSession.blockLookup.readBlock(this.selectedBlock)
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
		
		if (-1 != _inputCapture.lastPressedNumber)
		{
			int hotbarIndex = _inputCapture.lastPressedNumber - 1;
			this.currentGameSession.client.changeHotbarIndex(hotbarIndex);
			_inputCapture.lastPressedNumber = -1;
		}
		
		// Finalizing frame events can change the game session so capture that, now.
		GameSession currentGameSession = this.currentGameSession;
		
		// This is the most common mode where events matter since it is where most of them start and passive events still need to be applied, in the background.
		// Finalize the event processing with this selection and accounting for inter-frame time.
		// Note that this must be last since we deliver some events while drawing windows, etc, when we discover click locations, etc.
		_finalizeFrameEvents();
		_passTimeWhileRunning(currentGameSession);
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
		IAction noAction = this.selectionWindow.doRender(_inputCapture.glCursorLocation);
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

	public void drawPassiveOverlayWindows(GameSession currentGameSession)
	{
		Entity entity = _entityBinding.get();
		if (null != entity)
		{
			_handleEyeFilter(currentGameSession);
			_drawPassiveOverlayWindows();
		}
	}
	/**
	 * Continues any active operations and completes accounting for time in a frame where the game is active and not
	 * paused.
	 */
	public void commonIdleWhileRunning(GameSession currentGameSession)
	{
		_passTimeWhileRunning(currentGameSession);
	}


	private void _drawPassiveOverlayWindows()
	{
		IAction noAction = _hotbarWindow.doRender(_inputCapture.glCursorLocation);
		Assert.assertTrue(null == noAction);
		noAction = _metaDataWindow.doRender(_inputCapture.glCursorLocation);
		Assert.assertTrue(null == noAction);
	}

	private void _handleEyeFilter(GameSession currentGameSession)
	{
		// If our eye is under a liquid, draw the liquid over the screen (we do this here since it is part of the orthographic plane and not logically part of the scene).
		Vector eye = currentGameSession.movement.computeEye();
		if (null != eye)
		{
			AbsoluteLocation eyeBlockLocation = GeometryHelpers.locationFromVector(eye);
			BlockProxy eyeProxy = currentGameSession.blockLookup.readBlock(eyeBlockLocation);
			if (null != eyeProxy)
			{
				Block blockType = eyeProxy.getBlock();
				if (_waterBlock == blockType)
				{
					_ui.drawWholeTextureRect(_ui.pixelBlueAlpha, -1.0f, -1.0f, 1.0f, 1.0f);
				}
				else if (_lavaBlock == blockType)
				{
					_ui.drawWholeTextureRect(_ui.pixelOrangeLava, -1.0f, -1.0f, 1.0f, 1.0f);
				}
			}
		}
	}

	private RelativeDirection _getCurrentMove()
	{
		// Given that we can mix directions (forward + right, for example), this function handles that combination logic.
		int forward = 0;
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_FORWARD.ordinal()])
		{
			forward += 1;
		}
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_BACKWARD.ordinal()])
		{
			forward -= 1;
		}
		int right = 0;
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_RIGHT.ordinal()])
		{
			right += 1;
		}
		if (_inputCapture.controlHeld[MutableControls.Control.MOVE_LEFT.ordinal()])
		{
			right -= 1;
		}
		
		RelativeDirection relative;
		if (forward > 0)
		{
			if (right > 0)
			{
				relative = RelativeDirection.FORWARD_RIGHT;
			}
			else if (right < 0)
			{
				relative = RelativeDirection.FORWARD_LEFT;
			}
			else
			{
				relative = RelativeDirection.FORWARD;
			}
		}
		else if (forward < 0)
		{
			if (right > 0)
			{
				relative = RelativeDirection.BACKWARD_RIGHT;
			}
			else if (right < 0)
			{
				relative = RelativeDirection.BACKWARD_LEFT;
			}
			else
			{
				relative = RelativeDirection.BACKWARD;
			}
		}
		else
		{
			if (right > 0)
			{
				relative = RelativeDirection.RIGHT;
			}
			else if (right < 0)
			{
				relative = RelativeDirection.LEFT;
			}
			else
			{
				relative = null;
			}
		}
		return relative;
	}

	private void _finalizeFrameEvents()
	{
		PartialEntity entity = this.selectedEntity;
		AbsoluteLocation stopBlock = this.selectedBlock;
		AbsoluteLocation preStopBlock = this.preSelectedBlock;
		
		// See if we need to update our orientation.
		if (_orientationNeedsFlush)
		{
			this.currentGameSession.client.setOrientation(_yawRadians, _pitchRadians);
			_orientationNeedsFlush = false;
		}
		
		// See if the click refers to anything selected.
		boolean didAct = false;
		if (_inputCapture.mouseHeld0)
		{
			if (null != stopBlock)
			{
				didAct = this.currentGameSession.client.hitBlock(stopBlock);
			}
			else if (null != entity)
			{
				if (_inputCapture.mouseClicked0)
				{
					this.currentGameSession.client.hitEntity(entity);
					didAct = true;
				}
			}
		}
		else if (_inputCapture.mouseHeld1)
		{
			// We want to treat things like a bow as the highest priority, so we will handle that first, whether or not
			// the mouse button is held or clicked (although these priorities may be reconsidered).
			didAct = this.currentGameSession.client.holdRightClickOnSelf();
			if (didAct)
			{
				this.isWaitingForRightClickRelease = true;
			}
			
			if (null != stopBlock)
			{
				// First, see if we need to change the UI state if this is a station we just clicked on.
				if (!didAct && _inputCapture.mouseClicked1)
				{
					didAct = _didOpenStationInventory(stopBlock);
				}
			}
			else if (null != entity)
			{
				if (!didAct && _inputCapture.mouseClicked1)
				{
					// Check if this is a villager and then switch into the trading UI mode.
					if ((entity.type() == _villagerEntityType) && (null != ((ExtensionVillager.Data)entity.extendedData()).profession()))
					{
						// This is a villager with a profession so switch to our trading UI mode.
						_modeContainer.setActive(_modeContainer.trading.becomeActive(this.currentGameSession));
						_inputCapture.captureState.shouldCaptureMouse(false);
						_currentTradingPartnerIdBinding.set(entity.id());
					}
					else
					{
						// Otherwise, try to apply the current item to the entity.
						this.currentGameSession.client.applyToEntity(entity);
					}
					// As long as we attempted either of these, we consider the action complete.
					didAct = true;
				}
			}
			
			// If we still didn't do anything, try clicks on the block or self.
			if (!didAct && _inputCapture.mouseClicked1 && (null != stopBlock))
			{
				didAct = this.currentGameSession.client.runRightClickOnBlock(stopBlock, preStopBlock);
			}
			if (!didAct && _inputCapture.mouseClicked1)
			{
				didAct = this.currentGameSession.client.runRightClickOnSelf();
			}
			if (!didAct && (null != stopBlock) && (null != preStopBlock))
			{
				// In this case, we either want to place a block or repair a block.
				didAct = this.currentGameSession.client.runPlaceBlock(stopBlock, preStopBlock);
				if (!didAct)
				{
					didAct = this.currentGameSession.client.runRepairBlock(stopBlock);
				}
			}
		}
		else if (this.isWaitingForRightClickRelease)
		{
			didAct = this.currentGameSession.client.releasedRightClickOnSelf();
			if (didAct)
			{
				// If we failed to send the release, just wait for our next frame (usually means that there is still a "charge" in the current accumulation).
				this.isWaitingForRightClickRelease = false;
			}
		}
		
		// If we were in the normal play mode, we still want to be able to drop from the hotbar.
		if (!didAct && _inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()])
		{
			// If we are holding ctrl, drop the entire stack.
			Entity thisEntity = _entityBinding.get();
			int selectedKey = thisEntity.hotbarItems()[thisEntity.hotbarIndex()];
			if (Entity.NO_SELECTION != selectedKey)
			{
				this.currentGameSession.client.dropItemSlot(selectedKey, _inputCapture.leftCtrlHeld);
				didAct = true;
			}
			_inputCapture.controlReleased[MutableControls.Control.DROP_ITEM.ordinal()] = false;
		}
		if (!didAct && _inputCapture.controlReleased[MutableControls.Control.TOGGLE_INVENTORY.ordinal()])
		{
			_modeContainer.setActive(_modeContainer.inventory.becomeActive(this.currentGameSession, null));
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_inputCapture.captureState.shouldCaptureMouse(false);
			_inputCapture.controlReleased[MutableControls.Control.TOGGLE_INVENTORY.ordinal()] = false;
			didAct = true;
		}
		
		// We may have changed mode above so check that.
		GameSession currentGameSession = this.currentGameSession;
		if (didAct)
		{
			if (_modeContainer.inventory == _modeContainer.currentMode)
			{
				currentGameSession = _modeContainer.inventory.currentGameSession;
			}
			else if (_modeContainer.trading == _modeContainer.currentMode)
			{
				currentGameSession = _modeContainer.trading.currentGameSession;
			}
		}
		
		ViscosityReader reader = new ViscosityReader(Environment.getShared(), currentGameSession.blockLookup);
		if ((null != _audibleMotionInFrame) && SpatialHelpers.isStandingOnGround(reader, _entityBinding.get().location(), _playerVolume))
		{
			switch (_audibleMotionInFrame)
			{
			case WALK:
				currentGameSession.audioManager.setWalking();
				break;
			case RUN:
				currentGameSession.audioManager.setRunning();
				break;
			}
		}
		else
		{
			currentGameSession.audioManager.setStanding();
		}
		
		// If we took any action, clear the input capture since we don't want the event to redundantly apply to UI elements.
		if (didAct)
		{
			_inputCapture.clearReleaseState();
		}
	}

	private boolean _didOpenStationInventory(AbsoluteLocation blockLocation)
	{
		// See if there is an inventory we can open at the given block location.
		// NOTE:  We don't use this mechanism to talk about air blocks (or other empty blocks with ad-hoc inventories), only actual blocks.
		BlockProxy proxy = this.currentGameSession.blockLookup.readBlock(blockLocation);
		boolean didOpen = false;
		Block block = proxy.getBlock();
		if (Environment.getShared().stations.getNormalInventorySize(block) > 0)
		{
			// We are at least some kind of station with an inventory.
			_modeContainer.setActive(_modeContainer.inventory.becomeActive(this.currentGameSession, blockLocation));
			// TODO:  Should we find a way to reset the page in _thisEntityInventoryView, _bottomInventoryView, and _craftingPanelView?
			_inputCapture.captureState.shouldCaptureMouse(false);
			didOpen = true;
		}
		return didOpen;
	}

	private void _passTimeWhileRunning(GameSession currentGameSession)
	{
		// If we took no action, just tell the client to pass time.
		if (!_inputCapture.didAccountForTimeInFrame)
		{
			currentGameSession.client.passTimeWhileRunning(null, null, null);
			_inputCapture.didAccountForTimeInFrame = true;
		}
		
		_audibleMotionInFrame = null;
	}


	private static enum _AudibleMotion
	{
		WALK,
		RUN,
	}
}
