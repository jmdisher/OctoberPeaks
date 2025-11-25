package com.jeffdisher.october.peaks.utils;

import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.Gdx;


/**
 * Miscellaneous helper functions specific to OctoberPeaks.
 */
public class MiscPeaksHelpers
{
	public static String readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}
}
