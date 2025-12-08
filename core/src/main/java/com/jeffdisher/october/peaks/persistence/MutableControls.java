package com.jeffdisher.october.peaks.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Input.Keys;
import com.jeffdisher.october.config.FlatTabListCallbacks;
import com.jeffdisher.october.config.IValueTransformer;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.config.TabListReader.TabListException;
import com.jeffdisher.october.utils.Assert;


/**
 * Defines the shared state of what key codes the InputManager uses to implement the shared controls in the system.
 * This allows the key binding UI to directly share the state with the InputManager.
 * Note that the defaults will be replaced with a version from the filesystem, on load, if found.  Also, the bindings
 * are written back to disk, inline, whenever any of them are changed.
 */
public class MutableControls
{
	public static final String KEY_BINDING_FILE_NAME = "key_bindings.tablist";

	public static enum Control
	{
		MOVE_FORWARD(Keys.W, false, "Move Forward"),
		MOVE_RIGHT(Keys.D, false, "Move Right"),
		MOVE_LEFT(Keys.A, false, "Move Left"),
		MOVE_BACKWARD(Keys.S, false, "Move Backward"),
		MOVE_JUMP(Keys.SPACE, false, "Jump/Swim"),
		TOGGLE_INVENTORY(Keys.I, true, "Open/Close Inventory"),
		TOGGLE_FUEL(Keys.F, true, "Toggle Fuel Inventory"),
		MOVE_SPRINT(Keys.CONTROL_LEFT, false, "Sprint"),
		MOVE_SNEAK(Keys.SHIFT_LEFT, false, "Sneak"),
		DROP_ITEM(Keys.Q, true, "Drop Item"),
		;
		
		private int keyCode;
		// "Click only" means that the event is only sent on the key up, not continuously sent while key is down.
		public final boolean isClickOnly;
		public final String description;
		private Control(int defaultCode, boolean isClickOnly, String description)
		{
			// The key code will be modified as we run but starts as default.
			this.keyCode = defaultCode;
			this.isClickOnly = isClickOnly;
			this.description = description;
		}
	}


	private final File _backingFile;
	private final Map<Integer, Control> _keyCodeToCommand;

	public MutableControls(File localStorageDirectory)
	{
		_backingFile = new File(localStorageDirectory, KEY_BINDING_FILE_NAME);
		_keyCodeToCommand = new HashMap<>();
		
		// Populate this with defaults.
		for (Control control : Control.values())
		{
			_keyCodeToCommand.put(control.keyCode, control);
		}
		
		// See if there is a version on disk with overrides.
		if (_backingFile.exists())
		{
			try(FileInputStream stream = new FileInputStream(_backingFile))
			{
				FlatTabListCallbacks<String, Integer> callbacks = new FlatTabListCallbacks<>((String input) -> input, new IValueTransformer.IntegerTransformer("keycode"));
				TabListReader.readEntireFile(callbacks, stream);
				for (Map.Entry<String, Integer> entry : callbacks.data.entrySet())
				{
					try
					{
						Control control = Control.valueOf(entry.getKey());
						// This will fail with an exception and not return null.
						Assert.assertTrue(null != control);
						_setKeyForControl(control, entry.getValue());
					}
					catch (IllegalArgumentException ignored)
					{
						// We might see stale data from older version so we just want to ignore those.
					}
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
			catch (TabListException e)
			{
				// TODO:  Determine how to handle corrupt data.
				throw Assert.unexpected(e);
			}
		}
	}

	public Control getCodeForKey(int keyCode)
	{
		return _keyCodeToCommand.get(keyCode);
	}

	public boolean setKeyForControl(Control control, int keyCode)
	{
		// Update the data.
		boolean didSet = _setKeyForControl(control, keyCode);
		
		if (didSet)
		{
			// These updates are rare so just write this now, to keep the UI simpler.
			_flushToDisk();
		}
		return didSet;
	}

	public int getKeyCode(Control control)
	{
		return control.keyCode;
	}


	private boolean _setKeyForControl(Control control, int keyCode)
	{
		boolean didSet = false;
		if (!_keyCodeToCommand.containsKey(keyCode))
		{
			_keyCodeToCommand.remove(control.keyCode);
			control.keyCode = keyCode;
			_keyCodeToCommand.put(control.keyCode, control);
			didSet = true;
		}
		return didSet;
	}

	private void _flushToDisk()
	{
		try (FileOutputStream stream = new FileOutputStream(_backingFile))
		{
			stream.write(String.format("# OctoberPeaks key bindings file.  See MutableControls.java for details.%n%n").getBytes(StandardCharsets.UTF_8));
			for (Map.Entry<Integer, Control> elt : _keyCodeToCommand.entrySet())
			{
				String key = elt.getValue().name();
				int value = elt.getKey();
				String line = String.format("%s\t%d%n", key, value);
				stream.write(line.getBytes(StandardCharsets.UTF_8));
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
}
