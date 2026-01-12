package com.jeffdisher.october.peaks.scene;

import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.utils.Assert;


/**
 * A wrapper over BufferBuilder when used in SceneMeshHelpers since those helpers assume a specific vertex array shape
 * which we don't always want to use.
 */
public class MeshHelperBufferBuilder
{
	public static final String[] ATTRIBUTE_NAME_SUPERSET = new String[] {
		"aPosition",
		"aNormal",
		"aTexture0",
		"aTexture1",
		"aBlockLightMultiplier",
		"aSkyLightMultiplier",
	};
	public static final boolean[] USE_ALL_ATTRIBUTES;
	static {
		boolean[] attributesToUse = new boolean[ATTRIBUTE_NAME_SUPERSET.length];
		for (int i = 0; i < attributesToUse.length; ++i)
		{
			attributesToUse[i] = true;
		}
		USE_ALL_ATTRIBUTES = attributesToUse;
	}

	/**
	 * Given activeAttributes as an in-order subset of ATTRIBUTE_NAME_SUPERSET, returns a boolean array where the flag
	 * is set for every activeAttributes found in ATTRIBUTE_NAME_SUPERSET.
	 * 
	 * @param activeAttributes An in-order subset of the possible attribute names in ATTRIBUTE_NAME_SUPERSET.
	 * @return The in-order list of flags representing which entries in the superset should be included.
	 */
	public static boolean[] useActiveAttributes(Attribute[] activeAttributes)
	{
		boolean[] attributesToUse = new boolean[ATTRIBUTE_NAME_SUPERSET.length];
		int attrIndex = 0;
		for (int i = 0; (i < attributesToUse.length) && (attrIndex < activeAttributes.length); ++i)
		{
			if (activeAttributes[attrIndex].name().equals(ATTRIBUTE_NAME_SUPERSET[i]))
			{
				attributesToUse[i] = true;
				attrIndex += 1;
			}
		}
		// Note:  activeAttributes is a subset so there can't be anything left over.
		Assert.assertTrue(attrIndex == activeAttributes.length);
		return attributesToUse;
	}


	private final BufferBuilder _builder;
	private final boolean[] _attributesToUse;

	public MeshHelperBufferBuilder(BufferBuilder builder, boolean[] attributesToUse)
	{
		_builder = builder;
		_attributesToUse = attributesToUse;
	}

	public void appendVertex(float[]... data)
	{
		Assert.assertTrue(_attributesToUse.length == data.length);
		
		int attribute = 0;
		int sentIndex = 0;
		for (float[] elt : data)
		{
			if (_attributesToUse[attribute])
			{
				_builder.append(sentIndex, elt);
				sentIndex += 1;
			}
			attribute += 1;
		}
	}
}
