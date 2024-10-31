package com.jeffdisher.october.peaks.wavefront;

import org.junit.Assert;
import org.junit.Test;


public class TestWavefrontReader
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
		float[][] expectedPositions = new float[][] {
			new float[] {0.828934f, -1.42158f, 1.994554f},
			new float[] {0.828934f, -0.12469f, 0.697664f},
			new float[] {0.828934f, -1.42158f, 0.697664f},
		};
		float[][] expectedTextures = new float[][] {
			new float[] {0.656723f, 0.76597f},
			new float[] {0.791192f, 0.6313f},
			new float[] {0.791192f, 0.76597f},
		};
		float[][] expectedNormals = new float[][] {
			new float[] {-1.0f, -0.0f, -0.0f},
			new float[] {-1.0f, -0.0f, -0.0f},
			new float[] {-1.0f, -0.0f, -0.0f},
		};
		int[] nextVertex = new int[] { 0 };
		WavefrontReader.readFile((float[] position, float[] texture, float[] normal) -> {
			Assert.assertArrayEquals(expectedPositions[nextVertex[0]], position, 0.01f);
			Assert.assertArrayEquals(expectedTextures[nextVertex[0]], texture, 0.01f);
			Assert.assertArrayEquals(expectedNormals[nextVertex[0]], normal, 0.01f);
			nextVertex[0] += 1;
		}, string);
	}
}
