package com.jeffdisher.october.peaks.textures;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.junit.Assert;
import org.junit.Test;


public class TestTextManager
{
	@Test
	public void basic() throws Throwable
	{
		_Gpu gpu = new _Gpu(1);
		TextManager manager = new TextManager(gpu);
		TextManager.Element element = manager.lazilyLoadStringTexture("testing");
		
		Assert.assertEquals(1, element.textureObject());
		manager.allowTexturePurge();
		
		element = manager.lazilyLoadStringTexture("testing");
		Assert.assertEquals(1, element.textureObject());
		Assert.assertEquals(229, gpu.knownTextures[element.textureObject()].width);
		Assert.assertEquals(87, gpu.knownTextures[element.textureObject()].height);
		
		manager.shutdown();
		Assert.assertNull(gpu.knownTextures[element.textureObject()]);
	}

	@Test
	public void fill() throws Throwable
	{
		_Gpu gpu = new _Gpu(400);
		TextManager manager = new TextManager(gpu);
		for (int i = 1; i <= 200; ++i)
		{
			TextManager.Element element = manager.lazilyLoadStringTexture(String.format("Test %d", i));
			Assert.assertEquals(i, element.textureObject());
		}
		
		int purgeCount = 0;
		for (int i = 201; i <= 400; ++i)
		{
			manager.allowTexturePurge();
			if (gpu.lastPurgeSize > 0)
			{
				// We end up purging multiple times, but always in blocks of 64.
				Assert.assertEquals(64, gpu.lastPurgeSize);
				purgeCount += 1;
				gpu.lastPurgeSize = 0;
			}
			
			TextManager.Element element = manager.lazilyLoadStringTexture(String.format("Test %d", i));
			Assert.assertEquals(i, element.textureObject());
		}
		
		// Check the final state before shutdown.
		Assert.assertEquals(0, gpu.lastPurgeSize);
		Assert.assertEquals(5, purgeCount);
		int nonNull = 0;
		for (int i = 1; i <= 400; ++i)
		{
			if (null != gpu.knownTextures[i])
			{
				nonNull += 1;
			}
		}
		Assert.assertEquals(80, nonNull);
		
		manager.shutdown();
		for (int i = 1; i <= 400; ++i)
		{
			Assert.assertNull(gpu.knownTextures[i]);
		}
	}

	@Test
	public void wrap() throws Throwable
	{
		_Gpu gpu = new _Gpu(2);
		TextManager manager = new TextManager(gpu);
		TextManager.Element oneLine = manager.lazilyLoadWrappedStringTexture("testing", 2.0f);
		TextManager.Element twoLine = manager.lazilyLoadWrappedStringTexture("testing is long", 2.0f);
		
		Assert.assertEquals(1, oneLine.textureObject());
		Assert.assertEquals(2.0f, oneLine.aspectRatio(), 0.01f);
		Assert.assertEquals(348, gpu.knownTextures[oneLine.textureObject()].width);
		Assert.assertEquals(87, gpu.knownTextures[oneLine.textureObject()].height);
		Assert.assertEquals(2, twoLine.textureObject());
		Assert.assertEquals(1.0f, twoLine.aspectRatio(), 0.01f);
		Assert.assertEquals(348, gpu.knownTextures[twoLine.textureObject()].width);
		Assert.assertEquals(174, gpu.knownTextures[twoLine.textureObject()].height);
		
		manager.shutdown();
	}


	private static class _Gpu implements TextManager.IGpu
	{
		public final _Elt[] knownTextures;
		public int nextTexture = 1;
		public int lastPurgeSize = 0;
		
		public _Gpu(int textureLimit)
		{
			this.knownTextures = new _Elt[textureLimit + 1];
		}
		@Override
		public int uploadLuminanceAlpha(int width, int height, ByteBuffer textureBufferData)
		{
			int texture = this.nextTexture;
			this.nextTexture += 1;
			this.knownTextures[texture] = new _Elt(width, height);
			return texture;
		}
		@Override
		public void deleteTexture(int texture)
		{
			Assert.assertNotNull(this.knownTextures[texture]);
			this.knownTextures[texture] = null;
		}
		@Override
		public void deleteTextureBatch(IntBuffer purgeBuffer)
		{
			this.lastPurgeSize = purgeBuffer.position();
			purgeBuffer.flip();
			while (purgeBuffer.hasRemaining())
			{
				int texture = purgeBuffer.get();
				Assert.assertNotNull(this.knownTextures[texture]);
				this.knownTextures[texture] = null;
			}
			purgeBuffer.flip();
		}
	}

	private static record _Elt(int width
		, int height
	) {}
}
