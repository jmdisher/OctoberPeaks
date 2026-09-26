package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.GameSession;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.ViewSelection;
import com.jeffdisher.october.peaks.ui.Window;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.types.PartialEntity;


/**
 * The mode where play is normal.  Cursor is captured and there is no open window.
 */
public class ModePlay implements IGameMode
{
	private final ModeContainer _modeContainer;
	private final IMouseCapture _mouseCapture;
	private final Binding<WorldSelection> _selectionBinding;
	public final Window selectionWindow;

	public GameSession currentGameSession;
	public PartialEntity selectedEntity;
	public AbsoluteLocation selectedBlock;
	public Block selectedBlockType;
	public FacingDirection selectedBlockOrientation;
	public AbsoluteLocation preSelectedBlock;

	public ModePlay(ModeContainer modeContainer
		, GlUi ui
		, IMouseCapture mouseCapture
	)
	{
		_modeContainer = modeContainer;
		_mouseCapture = mouseCapture;
		_selectionBinding = new Binding<>(null);
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
		_mouseCapture.disableMouseCapture();
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


	public static interface IMouseCapture
	{
		void disableMouseCapture();
	}
}
