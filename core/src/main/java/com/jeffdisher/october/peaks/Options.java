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
				options = new Options("Local", null);
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
			throw _usageError();
		}
		return options;
	}


	private static RuntimeException _usageError()
	{
		System.err.println("Args:  (--single)|(--multi user_name host port)");
		System.exit(1);
		return null;
	}
}
