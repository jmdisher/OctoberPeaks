package com.jeffdisher.october.peaks.utils;

import com.jeffdisher.october.utils.Assert;


/**
 * Contains basic logic for managing a ring buffer (extracted from ParticleEngine to make testing easier).
 * This logic is just used for managing the indices of a buffer but the actual storage and invalidation mechanism is
 * handled by the caller.
 */
public class RingBufferLogic
{
	private final int _bufferSize;
	private int _firstIndexUsed;
	private int _firstIndexFree;

	/**
	 * Creates the buffer logic, assuming the actual buffer starts empty.
	 * 
	 * @param bufferSize The number of elements in the buffer (must be > 1).
	 */
	public RingBufferLogic(int bufferSize)
	{
		// We require that the buffer size be greater than 1 to avoid some corner-cases in small buffers (which we never use).
		Assert.assertTrue(bufferSize > 1);
		_bufferSize = bufferSize;
		
		// If nothing is used, we set this to -1, if nothing is free, we set the free to -1.
		_firstIndexUsed = -1;
		_firstIndexFree = 0;
	}

	/**
	 * @return The index of the first used element (-1 if empty).
	 */
	public int getFirstIndexUsed()
	{
		return _firstIndexUsed;
	}

	/**
	 * @return The index of the first free element (-1 if full).
	 */
	public int getFirstIndexFree()
	{
		return _firstIndexFree;
	}

	/**
	 * Allocates an index, incrementing the internal free index and returning the previous (now allocated) value.
	 * 
	 * @return The allocated index for use (-1 if full and allocation failed).
	 */
	public int incrementFreeAndReturnPrevious()
	{
		int freeIndexToReturn = _firstIndexFree;
		
		// See if we have a valid index.
		if (-1 != _firstIndexFree)
		{
			int index = _firstIndexFree;
			_firstIndexFree = (_firstIndexFree + 1) % _bufferSize;
			if (_firstIndexFree == _firstIndexUsed)
			{
				// We are fully allocated after this one.
				_firstIndexFree = -1;
			}
			if (-1 == _firstIndexUsed)
			{
				// Nothing is used so we want to mark this one used.
				_firstIndexUsed = index;
			}
		}
		return freeIndexToReturn;
	}

	/**
	 * Frees the first used index, returning the next used index after this free.
	 * NOTE:  This cannot be called on an empty index.
	 * 
	 * @return The next used index after freeing the previous (-1 if the buffer is now empty).
	 */
	public int freeFirstUsedAndGetNext()
	{
		// This can only be called if we have something used which we can free.
		Assert.assertTrue(-1 != _firstIndexUsed);
		
		int index = _firstIndexUsed;
		_firstIndexUsed = (_firstIndexUsed + 1) % _bufferSize;
		if (_firstIndexUsed == _firstIndexFree)
		{
			_firstIndexUsed = -1;
		}
		if (-1 == _firstIndexFree)
		{
			_firstIndexFree = index;
		}
		return _firstIndexUsed;
	}
}
