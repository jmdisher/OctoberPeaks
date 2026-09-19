package com.jeffdisher.october.peaks;

import java.io.File;
import java.util.List;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.october.peaks.ui.Binding;


public class TestLocalStorageManager
{
	@ClassRule
	public static TemporaryFolder DIRECTORY = new TemporaryFolder();

	@Test
	public void basicUsage() throws Throwable
	{
		Binding<List<String>> worldListBinding = new Binding<>(List.of());
		File topDirectory = DIRECTORY.newFolder();
		LocalStorageManager manager = new LocalStorageManager(worldListBinding, topDirectory);
		
		String subName = LocalStorageManager.WORLD_DIRECTORY_PREFIX + "test";
		File test = manager.getWorldDirectory(subName);
		test.mkdir();
		Assert.assertTrue(test.getParentFile().equals(topDirectory));
		Assert.assertTrue(test.getAbsolutePath().startsWith(topDirectory.getAbsolutePath()));
		
		manager.rebuildSinglePlayerListBinding();
		Assert.assertEquals(1, worldListBinding.get().size());
		Assert.assertEquals(subName, worldListBinding.get().get(0));
		
		manager.deleteWorldAndUpdateList(subName);
		Assert.assertEquals(0, worldListBinding.get().size());
	}

	@Test
	public void invalidName() throws Throwable
	{
		Binding<List<String>> worldListBinding = new Binding<>(List.of());
		File topDirectory = DIRECTORY.newFolder();
		LocalStorageManager manager = new LocalStorageManager(worldListBinding, topDirectory);
		
		String subName = LocalStorageManager.WORLD_DIRECTORY_PREFIX + "/../../test";
		boolean failed = false;
		try
		{
			manager.getWorldDirectory(subName);
		}
		catch (AssertionError e)
		{
			failed = true;
		}
		Assert.assertTrue(failed);
		
		manager.rebuildSinglePlayerListBinding();
		Assert.assertEquals(0, worldListBinding.get().size());
		
		failed = false;
		try
		{
			manager.deleteWorldAndUpdateList(subName);
		}
		catch (AssertionError e)
		{
			failed = true;
		}
		Assert.assertTrue(failed);
		Assert.assertEquals(0, worldListBinding.get().size());
	}
}
