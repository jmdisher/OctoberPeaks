package com.jeffdisher.october.peaks.utils;

import org.junit.Assert;
import org.junit.Test;


public class TestRingBufferLogic
{
	@Test
	public void empty() throws Throwable
	{
		RingBufferLogic ringLogic = new RingBufferLogic(10);
		Assert.assertEquals(0, ringLogic.getFirstIndexFree());
		Assert.assertEquals(-1, ringLogic.getFirstIndexUsed());
	}

	@Test
	public void basicUse() throws Throwable
	{
		RingBufferLogic ringLogic = new RingBufferLogic(10);
		int allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(0, allocated);
		allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(1, allocated);
		Assert.assertEquals(2, ringLogic.getFirstIndexFree());
		Assert.assertEquals(0, ringLogic.getFirstIndexUsed());
		int nextUsed = ringLogic.freeFirstUsedAndGetNext();
		Assert.assertEquals(1, nextUsed);
		nextUsed = ringLogic.freeFirstUsedAndGetNext();
		Assert.assertEquals(-1, nextUsed);
		allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(2, allocated);
		Assert.assertEquals(3, ringLogic.getFirstIndexFree());
		Assert.assertEquals(2, ringLogic.getFirstIndexUsed());
		nextUsed = ringLogic.freeFirstUsedAndGetNext();
		Assert.assertEquals(-1, nextUsed);
		Assert.assertEquals(3, ringLogic.getFirstIndexFree());
		Assert.assertEquals(-1, ringLogic.getFirstIndexUsed());
	}

	@Test
	public void fillDrain() throws Throwable
	{
		RingBufferLogic ringLogic = new RingBufferLogic(3);
		int allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(0, allocated);
		allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(1, allocated);
		allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(2, allocated);
		allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(-1, allocated);
		Assert.assertEquals(-1, ringLogic.getFirstIndexFree());
		Assert.assertEquals(0, ringLogic.getFirstIndexUsed());
		
		int nextUsed = ringLogic.freeFirstUsedAndGetNext();
		Assert.assertEquals(1, nextUsed);
		nextUsed = ringLogic.freeFirstUsedAndGetNext();
		Assert.assertEquals(2, nextUsed);
		nextUsed = ringLogic.freeFirstUsedAndGetNext();
		Assert.assertEquals(-1, nextUsed);
		Assert.assertEquals(0, ringLogic.getFirstIndexFree());
		Assert.assertEquals(-1, ringLogic.getFirstIndexUsed());
		
		allocated = ringLogic.incrementFreeAndReturnPrevious();
		Assert.assertEquals(0, allocated);
		Assert.assertEquals(1, ringLogic.getFirstIndexFree());
		Assert.assertEquals(0, ringLogic.getFirstIndexUsed());
		nextUsed = ringLogic.freeFirstUsedAndGetNext();
		Assert.assertEquals(-1, nextUsed);
		Assert.assertEquals(1, ringLogic.getFirstIndexFree());
		Assert.assertEquals(-1, ringLogic.getFirstIndexUsed());
	}
}
