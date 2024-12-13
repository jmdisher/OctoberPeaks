package com.jeffdisher.october.peaks.scene;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.jeffdisher.october.aspects.Aspect;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.data.OctreeShort;
import com.jeffdisher.october.types.BlockAddress;


/**
 * Used to convert sparse data elements into a high-level look-up based on cuboid BlockAddress location.
 */
public class SparseShortProjection<T>
{
	public static <T> SparseShortProjection<T> fromAspect(IReadOnlyCuboidData cuboid, Aspect<Short, OctreeShort> aspect, Short ignore, T defaultValue, BiFunction<BlockAddress, Short, T> mapper)
	{
		Map<BlockAddress, T> values = new HashMap<>();
		cuboid.walkData(aspect, new IOctree.IWalkerCallback<Short>() {
			@Override
			public void visit(BlockAddress base, byte size, Short value)
			{
				for (byte x = 0; x < size; ++x)
				{
					for (byte y = 0; y < size; ++y)
					{
						for (byte z = 0; z < size; ++z)
						{
							BlockAddress address = new BlockAddress((byte)(base.x() + x), (byte)(base.y() + y), (byte)(base.z() + z));
							T mapped = mapper.apply(address, value);
							values.put(address, mapped);
						}
					}
				}
			}
		}, ignore);
		return new SparseShortProjection<T>(defaultValue, values);
	}

	private final T _defaultValue;
	private final Map<BlockAddress, T> _values;

	public SparseShortProjection(T defaultValue, Map<BlockAddress, T> values)
	{
		_defaultValue = defaultValue;
		_values = values;
	}

	public T get(BlockAddress address)
	{
		return _values.getOrDefault(address, _defaultValue);
	}
}
