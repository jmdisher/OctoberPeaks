package com.jeffdisher.october.peaks.modes;


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
}
