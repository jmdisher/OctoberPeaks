package com.jeffdisher.october.peaks.animation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.files.FileHandle;
import com.jeffdisher.october.config.TabListReader;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * The data describing the rigging components of an entity and the logic required to load this data from a rigging
 * tablist file.
 */
public class Rigging
{
	public static List<LimbRig> loadFromTablistFile(FileHandle riggingFile) throws IOException
	{
		// We assume that this exists if we were called.
		Assert.assertTrue(riggingFile.exists());
		
		_ReaderCallbacks callbacks = new _ReaderCallbacks();
		try (InputStream stream = riggingFile.read())
		{
			try
			{
				TabListReader.readEntireFile(callbacks, stream);
			}
			catch (TabListReader.TabListException e)
			{
				// We will consider this a fatal installation failure.
				throw Assert.unexpected(e);
			}
		}
		return callbacks.allLimbs;
	}

	private Rigging()
	{
		// No external instantiation.
	}


	public static record LimbRig(String name
		, ComponentType type
		, EntityLocation base
	) {}

	public static enum ComponentType
	{
		BODY,
		PITCH,
		POSITIVE,
		NEGATIVE,
		;
	}

	private static class _ReaderCallbacks implements TabListReader.IParseCallbacks
	{
		public static final String KEY_BASE = "base";
		
		public List<LimbRig> allLimbs = new ArrayList<>();
		
		public String recordName;
		public ComponentType type;
		public EntityLocation base;
		
		@Override
		public void startNewRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			if (1 != parameters.length)
			{
				throw new TabListReader.TabListException("Expected one parameter for \"" + name + "\"");
			}
			
			String typeName = parameters[0];
			ComponentType type;
			try
			{
				type = ComponentType.valueOf(typeName);
			}
			catch(IllegalArgumentException e)
			{
				throw new TabListReader.TabListException("Not a type under \"" + name + "\": \"" + typeName + "\"");
			}
			
			this.recordName = name;
			this.type = type;
		}
		@Override
		public void endRecord() throws TabListReader.TabListException
		{
			// Make sure that we have all the components.
			Assert.assertTrue(null != this.recordName);
			Assert.assertTrue(null != this.type);
			Assert.assertTrue(null != this.base);
			
			LimbRig limb = new LimbRig(this.recordName
				, this.type
				, this.base
			);
			this.allLimbs.add(limb);
			
			this.recordName = null;
			this.type = null;
			this.base = null;
		}
		@Override
		public void processSubRecord(String name, String[] parameters) throws TabListReader.TabListException
		{
			if (!KEY_BASE.equals(name))
			{
				throw new TabListReader.TabListException("Unknown sub-record type \"" + name + "\"");
			}
			if (3 != parameters.length)
			{
				throw new TabListReader.TabListException("Expected 3 parameters for \"" + name + "\"");
			}
			
			EntityLocation base;
			try
			{
				base = new EntityLocation(Float.parseFloat(parameters[0])
					, Float.parseFloat(parameters[1])
					, Float.parseFloat(parameters[2])
				);
			}
			catch (NumberFormatException e)
			{
				throw new TabListReader.TabListException("Non-float base parameter under \"" + this.recordName + "\"");
			}
			this.base = base;
		}
	}
}
