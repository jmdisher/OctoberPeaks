package com.jeffdisher.october.peaks.wavefront;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.october.utils.Assert;


/**
 * Reads a Wavefront (.obj) file in from assets and converts it into a VertexArray for later rendering.
 * Note that our support for Wavefront is minimal, as to only include the use-cases relevant here:
 * -vertices
 * -normals
 * -UV coordinates
 * -only triangular faces, assuming a counter-clockwise order, in self-contained triplets
 */
public class WavefrontReader
{
	public static final String VERTEX = "v";
	public static final String NORMAL = "vn";
	public static final String TEXTURE = "vt";
	public static final String FACE = "f";

	public static int getVertexCount(String fileText)
	{
		long faceCount = fileText.lines()
			.filter((String line) -> line.startsWith(FACE))
			.count()
		;
		return 3 * (int)faceCount;
	}

	public static void readFile(VertexConsumer consumer, String fileText)
	{
		List<float[]> vertices = new ArrayList<>();
		List<float[]> normals = new ArrayList<>();
		List<float[]> textures = new ArrayList<>();
		
		fileText.lines().forEachOrdered((String line) -> {
			if (!line.isEmpty())
			{
				String[] tokens = line.split(" ");
				String head = tokens[0];
				
				if (head.equals(VERTEX))
				{
					// Read 3 floats and store them in vertices.
					Assert.assertTrue(4 == tokens.length);
					float x = Float.parseFloat(tokens[1]);
					float y = Float.parseFloat(tokens[2]);
					float z = Float.parseFloat(tokens[3]);
					vertices.add(new float[] { x, y, z });
				}
				else if (head.equals(NORMAL))
				{
					// Read 3 floats and store them in normals.
					Assert.assertTrue(4 == tokens.length);
					float x = Float.parseFloat(tokens[1]);
					float y = Float.parseFloat(tokens[2]);
					float z = Float.parseFloat(tokens[3]);
					normals.add(new float[] { x, y, z });
				}
				else if (head.equals(TEXTURE))
				{
					// Read 2 floats and store them in textures.
					Assert.assertTrue(3 == tokens.length);
					float u = Float.parseFloat(tokens[1]);
					float v = Float.parseFloat(tokens[2]);
					textures.add(new float[] { u, v });
				}
				else if (head.equals(FACE))
				{
					// Read 3 integer triples referencing the other arrays and describe them to the consumer.
					Assert.assertTrue(4 == tokens.length);
					for (int i = 0; i < 3; ++i)
					{
						String[] parts = tokens[i + 1].split("/");
						// Remember that Wavefront vertex references are 1-index.
						int vert = Integer.parseInt(parts[0]);
						int texture = Integer.parseInt(parts[1]);
						int normal = Integer.parseInt(parts[2]);
						consumer.consume(vertices.get(vert - 1), textures.get(texture - 1), normals.get(normal - 1));
					}
				}
				else
				{
					// This is a comment or an unsupported field so just ignore this.
				}
			}
		});
	}


	/**
	 * The interface called for every vertex parsed, in the order they are read.
	 */
	public static interface VertexConsumer
	{
		void consume(float[] position, float[] texture, float[] normal);
	}
}
