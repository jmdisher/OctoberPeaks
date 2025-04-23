package com.jeffdisher.october.peaks.persistence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.net.Packet_ClientSendDescription;
import com.jeffdisher.october.net.PollingClient;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.utils.Assert;


/**
 * Contains the server list which is used in the UI and is backed on the filesystem.
 * The list Binding is exposed directly but modifications should be made using the public helper here so that the
 * write-back to disk can be correctly orchestrated.
 * Note that this expects the PollingClient responses on a background thread so it internally uses synchronization to
 * lock access to the underlying StateRecord list (although a lock would be more ideal).
 */
public class MutableServerList implements PollingClient.IListener
{
	public static final String SERVER_LIST_FILE_NAME = "server_list.txt";


	private final Thread _mainThread;
	private final File _backingFile;
	private final PollingClient _pollingClient;
	public final Binding<List<ServerRecord>> servers;

	public MutableServerList(File localStorageDirectory)
	{
		_mainThread = Thread.currentThread();
		_backingFile = new File(localStorageDirectory, SERVER_LIST_FILE_NAME);
		_pollingClient = new PollingClient(this);
		
		// Default to a mutable list.
		this.servers = new Binding<>(new ArrayList<>());
		
		// Populate the server list with whatever is on disk.
		if (_backingFile.exists())
		{
			// This is just a text file where each line is a server entry so just load those.
			try(BufferedReader stream = new BufferedReader(new FileReader(_backingFile)))
			{
				String line = stream.readLine();
				while (null != line)
				{
					// We will skip lines which are empty or starting with #.
					if (!line.isEmpty() && !line.startsWith("#"))
					{
						String[] parts = line.split(":");
						Assert.assertTrue(2 == parts.length);
						String hostname = parts[0];
						int port = Integer.parseInt(parts[1]);
						InetSocketAddress object = new InetSocketAddress(hostname, port);
						ServerRecord record = new ServerRecord(object);
						servers.get().add(record);
					}
					line = stream.readLine();
				}
			}
			catch (FileNotFoundException e)
			{
				// We already know this exists.
				throw Assert.unexpected(e);
			}
			catch (IOException e)
			{
				// This would mean a serious issue on the local system.
				throw Assert.unexpected(e);
			}
		}
	}

	public synchronized void pollServers()
	{
		// This call is expected on the main thread.
		Assert.assertTrue(Thread.currentThread() == _mainThread);
		
		for (ServerRecord server : this.servers.get())
		{
			InetSocketAddress address = server.address;
			_pollingClient.pollServer(address);
		}
	}

	public synchronized void addServer(InetSocketAddress newServer)
	{
		// This call is expected on the main thread.
		Assert.assertTrue(Thread.currentThread() == _mainThread);
		
		// Make sure that this isn't already in the list.
		boolean canAdd = (null == _findRecord(newServer));
		if (canAdd)
		{
			ServerRecord server = new ServerRecord(newServer);
			servers.get().add(server);
			_pollingClient.pollServer(newServer);
			_flushToDisk();
		}
	}

	@Override
	public synchronized void networkTimeout(InetSocketAddress serverToPoll)
	{
		// This call is expected on the background thread.
		Assert.assertTrue(Thread.currentThread() != _mainThread);
		
		ServerRecord found = _findRecord(serverToPoll);
		if (null != found)
		{
			found.isGood = false;
			found.humanReadableStatus = "Timeout";
		}
	}

	@Override
	public synchronized void serverReturnedStatus(InetSocketAddress serverToPoll, int version, String serverName, int clientCount, long millisDelay)
	{
		// This call is expected on the background thread.
		Assert.assertTrue(Thread.currentThread() != _mainThread);
		
		ServerRecord found = _findRecord(serverToPoll);
		if (null != found)
		{
			boolean versionDoesMatch = (Packet_ClientSendDescription.NETWORK_PROTOCOL_VERSION == version);
			found.isGood = versionDoesMatch;
			found.humanReadableStatus = versionDoesMatch
					? String.format("Ready(%d ms): %s", millisDelay, serverName)
					: String.format("Wrong version: %d", version)
			;
		}
	}

	public void shutdown()
	{
		_pollingClient.shutdown();
	}


	private void _flushToDisk()
	{
		try (FileOutputStream stream = new FileOutputStream(_backingFile))
		{
			stream.write(String.format("# OctoberPeaks server list file.  See MutableServerList.java for details.%n%n").getBytes(StandardCharsets.UTF_8));
			for (ServerRecord server : this.servers.get())
			{
				InetSocketAddress address = server.address;
				stream.write(String.format("%s:%d%n", address.getHostName(), address.getPort()).getBytes(StandardCharsets.UTF_8));
			}
		}
		catch (FileNotFoundException e)
		{
			// We don't expect the parent to disappear.
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			// This would mean a serious issue on the local system.
			throw Assert.unexpected(e);
		}
	}

	private ServerRecord _findRecord(InetSocketAddress serverToCheck)
	{
		ServerRecord record = null;
		String hostname = serverToCheck.getHostName();
		int port = serverToCheck.getPort();
		for (ServerRecord server : this.servers.get())
		{
			InetSocketAddress address = server.address;
			if (hostname.equals(address.getHostName()) && (port == address.getPort()))
			{
				record = server;
				break;
			}
		}
		return record;
	}


	public static class ServerRecord
	{
		public final InetSocketAddress address;
		public boolean isGood;
		public String humanReadableStatus;
		
		public ServerRecord(InetSocketAddress address)
		{
			this.address = address;
			this.humanReadableStatus = "Checking...";
		}
	}
}
