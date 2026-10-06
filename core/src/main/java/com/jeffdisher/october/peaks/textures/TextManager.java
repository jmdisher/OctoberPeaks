package com.jeffdisher.october.peaks.textures;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.utils.Assert;


/**
 * We currently store all text as individual textures in graphics memory (these may be combined or done a different way
 * in the future).  This manager tracks those textures and provides facility for rendering new ones.
 * This texture memory is periodically reclaimed if it gets too full.
 */
public class TextManager
{
	/**
	 * We will only try to purge unused text values if there are more than this many.
	 */
	public static final int TEXT_CACHE_TARGET_SIZE = 100;
	/**
	 * This is the largest number of textures we will try to purge in a single purge call (allows buffer reuse since it
	 * is off-heap).
	 */
	public static final int TEXT_CACHE_MAX_PURGE_PER_ATTEMPT = 64;

	private final IGpu _gpu;
	private final Map<String, Element> _textTextures;
	private final Graphics2D _graphics;
	private final Font _font;
	private final java.awt.FontMetrics _fontMetrics;

	// Variables related to texture purging.
	private Set<String> _recentlyUsed;
	private final IntBuffer _purgeBuffer;

	public TextManager(IGpu gpu)
	{
		_gpu = gpu;
		_textTextures = new HashMap<>();
		_purgeBuffer = ByteBuffer.allocateDirect(Integer.BYTES * TEXT_CACHE_MAX_PURGE_PER_ATTEMPT).asIntBuffer();
		
		// We want to get a shared graphics context which we will use for measuring the text size.
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		_graphics = image.createGraphics();
		_font = new Font(Font.SANS_SERIF, Font.BOLD, 64);
		_fontMetrics = _graphics.getFontMetrics(_font);
		// The graphics system either does some lazy loading or heavily depends on JIT since the first call is at least 10x the cost of later ones.
		// Hence, just draw "something" and ignore the result.
		_writtenImage("");
	}

	public Element lazilyLoadStringTexture(String string)
	{
		if (!_textTextures.containsKey(string))
		{
			// Lazily generate the texture and store it in the map.
			BufferedImage image = _writtenImage(string);
			Element element = _convertImageToTexture(image);
			_textTextures.put(string, element);
		}
		if (null != _recentlyUsed)
		{
			// If we are sampling active textures, add this one.
			_recentlyUsed.add(string);
		}
		return _textTextures.get(string);
	}

	public Element lazilyLoadWrappedStringTexture(String string, float aspectRatioLimit)
	{
		if (!_textTextures.containsKey(string))
		{
			// Lazily generate the texture and store it in the map.
			// WARNING:  We are sharing the map with the normal textures, which could cause an issue (not in our use case).
			// Note that we internally divide our output aspect ratio by 2 so multiply by 2 so they sync up.
			BufferedImage image = _writtenWrappedImage(string, aspectRatioLimit * 2.0f);
			Element element = _convertImageToTexture(image);
			_textTextures.put(string, element);
		}
		if (null != _recentlyUsed)
		{
			// If we are sampling active textures, add this one.
			_recentlyUsed.add(string);
		}
		return _textTextures.get(string);
	}

	/**
	 * Called periodically to allow the text manage to purge unused textures.
	 */
	public void allowTexturePurge()
	{
		if (null != _recentlyUsed)
		{
			// Texture sampling is active so purge anything we didn't see and disable sampling.
			int unreferencedCount = _textTextures.size() - _recentlyUsed.size();
			if (unreferencedCount > 0)
			{
				_purgeBuffer.clear();
				int purgeCount = 0;
				
				Iterator<String> iter = _textTextures.keySet().iterator();
				while (iter.hasNext())
				{
					String key = iter.next();
					if (!_recentlyUsed.contains(key))
					{
						// This hasn't been referenced since sampling.
						Element toPurge = _textTextures.get(key);
						Assert.assertTrue(_purgeBuffer.hasRemaining());
						_purgeBuffer.put(toPurge.textureObject);
						purgeCount += 1;
						iter.remove();
					}
					
					// Make sure we still have purge buffer space.
					if (purgeCount >= TEXT_CACHE_MAX_PURGE_PER_ATTEMPT)
					{
						break;
					}
				}
				_gpu.deleteTextureBatch(_purgeBuffer);
			}
			
			_recentlyUsed = null;
		}
		else if (_textTextures.size() > TEXT_CACHE_TARGET_SIZE)
		{
			// We want to try to purge text elements so start sampling what we use.
			_recentlyUsed = new HashSet<>();
		}
	}

	public void shutdown()
	{
		for (Element elt : _textTextures.values())
		{
			_gpu.deleteTexture(elt.textureObject);
		}
		_textTextures.clear();
		_graphics.dispose();
	}


	private Element _convertImageToTexture(BufferedImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		
		int channelsPerPixel = 2;
		ByteBuffer textureBufferData = ByteBuffer.allocateDirect(width * height * channelsPerPixel);
		textureBufferData.order(ByteOrder.nativeOrder());
		// We will load the data from the image into the GL buffer one line at a time, since we need to invert it (BufferedImage starts at top-left while OpenGL starts at bottom-left).
		int[] rawData = new int[width];
		for (int y = height - 1; y >= 0; --y)
		{
			image.getRGB(0, y, width, 1, rawData, 0, width);
			for (int pixel : rawData)
			{
				// This data is pulled out as ARGB but we need to upload it as LA.
				// We draw white so just get any channel and the alpha.
				byte a = (byte)((0xFF000000 & pixel) >> 24);
				byte b = (byte) (0x000000FF & pixel);
				textureBufferData.put(new byte[] { b, a });
			}
		}
		((java.nio.Buffer) textureBufferData).flip();
		
		int texture = _gpu.uploadLuminanceAlpha(width, height, textureBufferData);
		
		// The text is always too wide so we will lie and say it is half this width (it just looks better).
		float aspectRatio = ((float)width / (float)height);
		return new Element(texture, aspectRatio / 2.0f);
	}

	private BufferedImage _writtenImage(String text)
	{
		Rectangle2D rect = _fontMetrics.getStringBounds(text, _graphics);
		float descent = _fontMetrics.getLineMetrics(text, _graphics).getDescent();
		double width = rect.getWidth();
		if (width < 1.0)
		{
			width = 1.0;
		}
		double height = rect.getHeight();
		BufferedImage image = new BufferedImage((int)width, (int)height, BufferedImage.TYPE_INT_ARGB);
		Graphics graphics = image.getGraphics();
		graphics.setFont(_font);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, 0, (int)(height - descent));
		graphics.dispose();
		return image;
	}

	private BufferedImage _writtenWrappedImage(String text, float aspectRatioLimit)
	{
		// Note that we want to wrap this based on the aspect ratio limit so we will loop here (this approach isn't very efficient).
		List<String> lines = new ArrayList<>();
		String checkingText = text;
		String splitRemainder = "";
		boolean didWrapWord = false;
		
		while (null != checkingText)
		{
			Rectangle2D rect = _fontMetrics.getStringBounds(checkingText, _graphics);
			float width = (float)rect.getWidth();
			if (width < 1.0f)
			{
				width = 1.0f;
			}
			float height = (float)rect.getHeight();
			float aspectRatio = width / height;
			if (aspectRatio <= aspectRatioLimit)
			{
				// This line fits.
				lines.add(checkingText);
				if (splitRemainder.isEmpty())
				{
					// We are done.
					checkingText = null;
				}
				else
				{
					checkingText = splitRemainder;
					splitRemainder = "";
				}
			}
			else
			{
				// This line doesn't fit so split the last word or character off.
				int space = checkingText.lastIndexOf(' ');
				String extract;
				if (space >= 0)
				{
					// There is a space so use that.
					extract = checkingText.substring(space + 1);
					checkingText = checkingText.substring(0, space);
				}
				else
				{
					// Just split the last char.
					extract = checkingText.substring(checkingText.length() - 1);
					checkingText = checkingText.substring(0, checkingText.length() - 1);
				}
				if (didWrapWord)
				{
					splitRemainder = extract + ' ' + splitRemainder;
				}
				else
				{
					splitRemainder = extract + splitRemainder;
				}
				didWrapWord = (space >= 0);
			}
		}
		
		// Determine the bounds of the total texture.
		LineMetrics metrics = _fontMetrics.getLineMetrics(text, _graphics);
		float descent = metrics.getDescent();
		float lineHeight = metrics.getHeight();
		int intWidth = (int)(aspectRatioLimit * lineHeight);
		int oneLineHeight = (int)lineHeight;
		int intHeight = lines.size() * oneLineHeight;
		
		BufferedImage image = new BufferedImage(intWidth, intHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics graphics = image.getGraphics();
		graphics.setFont(_font);
		graphics.setColor(Color.WHITE);
		float startY = lineHeight - descent;
		for (String line : lines)
		{
			graphics.drawString(line, 0, (int)startY);
			startY += lineHeight;
		}
		graphics.dispose();
		return image;
	}


	public static record Element(int textureObject, float aspectRatio)
	{}

	public interface IGpu
	{
		int uploadLuminanceAlpha(int width, int height, ByteBuffer textureBufferData);
		void deleteTexture(int texture);
		void deleteTextureBatch(IntBuffer purgeBuffer);
	}
}
