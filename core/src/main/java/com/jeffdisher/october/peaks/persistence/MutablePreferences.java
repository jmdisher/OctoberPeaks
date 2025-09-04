package com.jeffdisher.october.peaks.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.utils.Assert;


/**
 * Defines the preferences which the user can change in the options view which can be persisted on the filesystem.
 * These are exposed as public Binding objects so that the UI to be bound directly to them, even though it means other
 * consumers will need to Object wrappers access indirect, as opposed to native primitives.
 * Note that the defaults will be replaced with a version from the filesystem, on load, if found.
 * The values are only written back to disk when requested.
 */
public class MutablePreferences
{
	public static final String PREFS_FILE_NAME = "prefs.tablist";
	public static final String KEY_CLIENT_NAME = "CLIENT_NAME";
	public static final String DEFAULT_CLIENT_NAME = "PLACEHOLDER";
	public static final String KEY_SCREEN_BRIGHTNESS = "SCREEN_BRIGHTNESS";
	public static final String KEY_VIEW_DISTANCE = "VIEW_DISTANCE";
	public static final float DEFAULT_SCREEN_BRIGHTNESS = 1.0f;
	// TODO:  Add the other options to the storage once we start applying them on start-up or connection.


	private final File _backingFile;
	public final Binding<Boolean> isFullScreen;
	public final Binding<Integer> preferredViewDistance;
	public final Binding<String> clientName;
	public final Binding<Float> screenBrightness;

	public MutablePreferences(File localStorageDirectory)
	{
		_backingFile = new File(localStorageDirectory, PREFS_FILE_NAME);
		
		// Value defaults.
		this.isFullScreen = new Binding<>(false);
		this.preferredViewDistance = new Binding<>(MiscConstants.DEFAULT_CUBOID_VIEW_DISTANCE);
		this.clientName = new Binding<>(DEFAULT_CLIENT_NAME);
		this.screenBrightness = new Binding<>(DEFAULT_SCREEN_BRIGHTNESS);
		
		// See if there is a version on disk with overrides.
		if (_backingFile.exists())
		{
			try(FileInputStream stream = new FileInputStream(_backingFile))
			{
				FlatTabListCallbacks<String, String> callbacks = new FlatTabListCallbacks<>((String input) -> input, (String input) -> input);
				TabListReader.readEntireFile(callbacks, stream);
				
				if (callbacks.data.containsKey(KEY_VIEW_DISTANCE))
				{
					this.preferredViewDistance.set(Integer.valueOf(callbacks.data.get(KEY_VIEW_DISTANCE)));
				}
				if (callbacks.data.containsKey(KEY_CLIENT_NAME))
				{
					this.clientName.set(callbacks.data.get(KEY_CLIENT_NAME));
				}
				if (callbacks.data.containsKey(KEY_SCREEN_BRIGHTNESS))
				{
					this.screenBrightness.set(Float.valueOf(callbacks.data.get(KEY_SCREEN_BRIGHTNESS)));
				}
				// TODO:  Read the other values here once we start persisting them.
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
			catch (TabListException e)
			{
				// TODO:  Determine how to handle corrupt data.
				throw Assert.unexpected(e);
			}
		}
	}

	public void saveToDisk()
	{
		try (FileOutputStream stream = new FileOutputStream(_backingFile))
		{
			stream.write(String.format("# OctoberPeaks preferences file.  See MutablePreferences.java for details.%n%n").getBytes(StandardCharsets.UTF_8));
			
			stream.write(String.format("%s\t%d%n", KEY_VIEW_DISTANCE, this.preferredViewDistance.get()).getBytes(StandardCharsets.UTF_8));
			stream.write(String.format("%s\t%s%n", KEY_CLIENT_NAME, this.clientName.get()).getBytes(StandardCharsets.UTF_8));
			stream.write(String.format("%s\t%.2f%n", KEY_SCREEN_BRIGHTNESS, this.screenBrightness.get()).getBytes(StandardCharsets.UTF_8));
			// TODO:  Write the other values here once we start persisting them.
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
}
