package com.jeffdisher.october.peaks.modes;


/**
 * A container for all the UI game modes and the container of the current mode.
 */
public class ModeContainer
{
	public IGameMode currentMode;
	public ModeStart start;
	public ModeListSinglePlayer listSinglePlayer;
	public ModeConfirmDeleteSinglePlayer confirmDeleteSinglePlayer;
	public ModeNewSinglePlayer newSinglePlayer;
	public ModeListMultiPlayer listMultiPlayer;
	public ModeNewMultiPlayer newMultiPlayer;
	public ModeListForProfile listForProfile;
	public ModeOptions options;
	public ModeKeyBindings keyBindings;
	public ModeConnecting connecting;
	public ModePlay play;
	public ModeInventory inventory;
	public ModePause pause;
	public ModeProfile profile;
	public ModeTrading trading;
	public ModeError error;

	public void setActive(IGameMode active)
	{
		this.currentMode.didBecomeInactive();
		this.currentMode = active;
	}
}
