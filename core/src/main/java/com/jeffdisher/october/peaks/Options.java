package com.jeffdisher.october.peaks;

import java.net.InetSocketAddress;


/**
 * A high-level representation of the command-line options.
 */
public record Options(String clientName
		, InetSocketAddress serverAddress
)
{
	public static Options fromCommandLine(String[] commandLineArgs)
	{
		Options options;
		if (commandLineArgs.length >= 1)
		{
			if ("--single".equals(commandLineArgs[0]))
			{
				// When running single-player, we don't have any options but we can't return null as we are explicitly
				// single-player and should start into that immediately.
				options = new Options(null, null);
			}
			else if ("--multi".equals(commandLineArgs[0]))
			{
				if (4 == commandLineArgs.length)
				{
					String clientName = commandLineArgs[1];
					String host = commandLineArgs[2];
					int port = Integer.parseInt(commandLineArgs[3]);
					System.out.println("Resolving host: " + host);
					options = new Options(clientName, new InetSocketAddress(host, port));
				}
				else
				{
					throw _usageError();
				}
			}
			else
			{
				throw _usageError();
			}
		}
		else
		{
			// Providing no options is what we expect from normal "double-click" invocation so we will return null as
			// options so that we will start up in a state where the UI will present these options.
			// In fact, this is the expected case, starting with version 1.3, as it is more user-friendly and allows for
			// direct world management.
			options = null;
		}
		return options;
	}


	private static RuntimeException _usageError()
	{
		System.err.println("Args:  [(--single)|(--multi user_name host port)]");
		System.exit(1);
		return null;
	}
}
