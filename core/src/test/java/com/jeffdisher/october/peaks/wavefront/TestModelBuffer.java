package com.jeffdisher.october.peaks.wavefront;

import org.junit.Assert;
import org.junit.Test;


public class TestModelBuffer
{
	@Test
	public void basics() throws Throwable
	{
		String string = "v 0.828934 -1.421580 0.697664\n"
				+ "v 0.828934 -1.421580 1.994554\n"
				+ "v 0.828934 -0.124690 0.697664\n"
				+ "vn -1.0000 -0.0000 -0.0000\n"
				+ "vt 0.656723 0.765970\n"
				+ "vt 0.791192 0.631300\n"
				+ "vt 0.791192 0.765970\n"
				+ "f 2/1/1 3/2/1 1/3/1\n"
		;
		float[] expectedPositions = new float[] {
			0.828934f, -1.42158f, 1.994554f,
			0.828934f, -0.12469f, 0.697664f,
			0.828934f, -1.42158f, 0.697664f,
		};
		float[] expectedTextures = new float[] {
			0.656723f, 0.76597f,
			0.791192f, 0.6313f,
			0.791192f, 0.76597f,
		};
		float[] expectedNormals = new float[] {
			-1.0f, -0.0f, -0.0f,
			-1.0f, -0.0f, -0.0f,
			-1.0f, -0.0f, -0.0f,
		};
		ModelBuffer model = ModelBuffer.buildFromWavefront(string);
		Assert.assertEquals(3, model.vertexCount);
		Assert.assertArrayEquals(expectedPositions, model.positionValues, 0.01f);
		Assert.assertArrayEquals(expectedTextures, model.textureValues, 0.01f);
		Assert.assertArrayEquals(expectedNormals, model.normalValues, 0.01f);
	}
}
