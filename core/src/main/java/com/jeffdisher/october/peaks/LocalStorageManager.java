package com.jeffdisher.october.peaks;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.utils.Assert;


/**
 * A container of the high-level idioms related to local storage for single-player games.  This is here since there are
 * a few different UI modes which need these idioms so we want to avoid duplication.
 */
public class LocalStorageManager
{
	public static final String WORLD_DIRECTORY_PREFIX = "world_";

	private final Binding<List<String>> _worldListBinding;
	private final File _localStorageDirectory;

	/**
	 * Creates the storage manager.
	 * 
	 * @param worldListBinding The binding to update when scanning or changing the filesystem.
	 * @param localStorageDirectory The directory where single-player worlds are based.
	 */
	public LocalStorageManager(Binding<List<String>> worldListBinding, File localStorageDirectory)
	{
		_worldListBinding = worldListBinding;
		try
		{
			_localStorageDirectory = localStorageDirectory.getCanonicalFile();
		}
		catch (IOException e)
		{
			// If we fail to make this into a canonical file, there is a serious problem.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Requests that the receiver re-scan the filesystem to rebuild the world list binding.
	 */
	public void rebuildSinglePlayerListBinding()
	{
		_rebuildSinglePlayerListBinding();
	}

	/**
	 * Creates a validated canonical path to the world in directoryName (note that it doesn't create the directory).
	 * 
	 * @param directoryName The name of the directory containing a world.
	 * @return The validated canonical File for this world.
	 */
	public File getWorldDirectory(String directoryName)
	{
		return _getWorldDirectory(directoryName);
	}

	/**
	 * Recursively deletes the world in directoryName and then rebuilds the world list binding.
	 * 
	 * @param directoryName The name of the directory containing a world.
	 */
	public void deleteWorldAndUpdateList(String directoryName)
	{
		// Delete the world directory.
		File localWorldDirectory = _getWorldDirectory(directoryName);
		System.out.println("Deleting local world: " + localWorldDirectory);
		_deleteWorldRecursively(localWorldDirectory);
		
		// We also need to rebuild the list.
		_rebuildSinglePlayerListBinding();
	}


	private File _getWorldDirectory(String directoryName)
	{
		// We do a bunch of checks here to make sure that we aren't ending up somewhere else in the filesystem due to bogus parameters, etc.
		Assert.assertTrue(directoryName.startsWith(WORLD_DIRECTORY_PREFIX));
		Assert.assertTrue(!directoryName.contains(File.pathSeparator));
		Assert.assertTrue(!directoryName.contains(File.separator));
		
		File sub;
		try
		{
			sub = new File(_localStorageDirectory, directoryName).getCanonicalFile();
		}
		catch (IOException e)
		{
			// If we fail to make this into a canonical file, there is a serious problem.
			throw Assert.unexpected(e);
		}
		Assert.assertTrue(sub.getParentFile().equals(_localStorageDirectory));
		return sub;
	}

	private void _rebuildSinglePlayerListBinding()
	{
		List<String> worldNames = List.of(_localStorageDirectory.list((File dir, String name) -> name.startsWith(WORLD_DIRECTORY_PREFIX)));
		_worldListBinding.set(worldNames);
	}

	private static void _deleteWorldRecursively(File directory)
	{
		// We should only see directories this way (unless someone was messing with our on-disk data).
		Assert.assertTrue(directory.isDirectory());
		
		// Walk all the files, recursively deleting directories.
		for (File sub : directory.listFiles())
		{
			if (sub.isDirectory())
			{
				_deleteWorldRecursively(sub);
			}
			else
			{
				sub.delete();
			}
		}
		directory.delete();
	}
}
