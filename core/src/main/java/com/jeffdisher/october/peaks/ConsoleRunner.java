package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import com.badlogic.gdx.Gdx;
import com.jeffdisher.october.process.ConsoleHandler;
import com.jeffdisher.october.server.MonitoringAgent;
import com.jeffdisher.october.types.WorldConfig;
import com.jeffdisher.october.utils.Assert;


/**
 * Wraps our use of ConsoleHandler since it needs to be run in a background thread and allow a kind of interruptability.
 * Note that this will call "Gdx.app.exit()" on the main thread if the "!stop" command is processed.
 * Note that this was copied directly from OctoberPlains.
 */
public class ConsoleRunner
{
	public static ConsoleRunner runInBackground(InputStream in
			, PrintStream out
			, MonitoringAgent monitoringAgent
			, WorldConfig mutableSharedConfig
	)
	{
		ConsoleRunner runner = new ConsoleRunner(in, out, monitoringAgent, mutableSharedConfig);
		runner._background.start();
		return runner;
	}


	private final Thread _background;

	private ConsoleRunner(InputStream in
			, PrintStream out
			, MonitoringAgent monitoringAgent
			, WorldConfig mutableSharedConfig
	)
	{
		_background = new Thread(() -> {
			try
			{
				ConsoleHandler.readUntilStopInterruptable(in, out, monitoringAgent, mutableSharedConfig);
				Gdx.app.postRunnable(() -> { Gdx.app.exit(); });
			}
			catch (IOException e)
			{
				// This is not expected.
				throw Assert.unexpected(e);
			}
			catch (InterruptedException e)
			{
				// We hit this if we were interrupted so just exit.
			}
		}, "Console Runner");
	}

	public void stop() throws InterruptedException
	{
		_background.interrupt();
		_background.join();
	}
}
