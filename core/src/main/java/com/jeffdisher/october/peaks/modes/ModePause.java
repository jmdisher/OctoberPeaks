package com.jeffdisher.october.peaks.modes;


/**
 * The mode where play is effectively "paused".  The cursor is released and buttons to change game setup will be
 * presented.
 * Note that we call the state "paused" even though servers are never "paused" (the title will reflect this
 * difference).
 */
public class ModePause implements IGameMode
{
}
