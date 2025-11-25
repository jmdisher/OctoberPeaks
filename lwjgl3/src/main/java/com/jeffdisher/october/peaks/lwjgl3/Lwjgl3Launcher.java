package com.jeffdisher.october.peaks.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import com.jeffdisher.october.peaks.OctoberPeaks;
import com.jeffdisher.october.peaks.Options;
import com.jeffdisher.october.peaks.WindowListener;


/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher
{
	public static void main(String[] args)
	{
		if (StartupHelper.startNewJvmIfRequired())
			return; // This handles macOS support and helps on Windows.
		createApplication(Options.fromCommandLine(args));
	}

	private static Lwjgl3Application createApplication(Options options)
	{
		WindowListener windowListener = new WindowListener();
		return new Lwjgl3Application(new OctoberPeaks(options, windowListener), getDefaultConfiguration(windowListener));
	}

	private static Lwjgl3ApplicationConfiguration getDefaultConfiguration(WindowListener windowListener)
	{
		Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
		configuration.setTitle("OctoberPeaks");
		//// Vsync limits the frames per second to what your hardware can display, and
		//// helps eliminate
		//// screen tearing. This setting doesn't always work on Linux, so the line
		//// after is a safeguard.
		configuration.useVsync(true);
		//// Limits FPS to the refresh rate of the currently active monitor, plus 1 to
		//// try to match fractional
		//// refresh rates. The Vsync setting above should limit the actual FPS to match
		//// the monitor.
		configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
		//// If you remove the above line and set Vsync to false, you can get unlimited
		//// FPS, which can be
		//// useful for testing performance, but can also be very stressful to some
		//// hardware.
		//// You may also need to configure GPU drivers to fully disable Vsync; this can
		//// cause screen tearing.
		configuration.setWindowedMode(1280, 960);
		configuration.setWindowIcon("op_logo128.png", "op_logo64.png", "op_logo32.png", "op_logo16.png");
		configuration.setWindowListener(new Lwjgl3WindowListener() {
			@Override
			public void created(Lwjgl3Window window)
			{
			}
			@Override
			public void iconified(boolean isIconified)
			{
			}
			@Override
			public void maximized(boolean isMaximized)
			{
			}
			@Override
			public void focusLost()
			{
				windowListener.focusLost();
			}
			@Override
			public void focusGained()
			{
				windowListener.focusGained();
			}
			@Override
			public boolean closeRequested()
			{
				return true;
			}
			@Override
			public void filesDropped(String[] files)
			{
			}
			@Override
			public void refreshRequested()
			{
			}
		});
		return configuration;
	}
}
