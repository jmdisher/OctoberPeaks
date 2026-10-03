package com.jeffdisher.october.peaks.modes;

import com.jeffdisher.october.peaks.ui.IAction;


/**
 * The interface implemented by UI modes, called by the InputManager.
 */
public interface IGameMode
{
	void didBecomeInactive();

	/**
	 * Called when the "escape" key is pressed.  This is generally just so that a state can implement a "go back"
	 * transition so the name may be made more generic if the escape key is no longer the only caller.
	 */
	void handleEscape();

	/**
	 * Directly renders the relevant windows for this mode, in its current state.
	 * 
	 * @return The action under the cursor (usually null).
	 */
	IAction drawRelevantWindows();
}
