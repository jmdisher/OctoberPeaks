package com.jeffdisher.october.peaks.wavefront;


/**
 * Caches information regarding a Wavefront model so that it can be fetched later for application to a mesh.
 */
public class ModelBuffer
{
	public static ModelBuffer buildFromWavefront(String fileText)
	{
		int vertexCount = WavefrontReader.getVertexCount(fileText);
		_Builder builder = new _Builder(vertexCount);
		WavefrontReader.readFile(builder, fileText);
		return new ModelBuffer(vertexCount
				, builder.positionValues
				, builder.textureValues
				, builder.normalValues
		);
	}


	public final int vertexCount;
	public final float[] positionValues;
	public final float[] textureValues;
	public final float[] normalValues;

	private ModelBuffer(int vertexCount
			, float[] positionValues
			, float[] textureValues
			, float[] normalValues
	)
	{
		this.vertexCount = vertexCount;
		this.positionValues = positionValues;
		this.textureValues = textureValues;
		this.normalValues = normalValues;
	}


	private static class _Builder implements WavefrontReader.VertexConsumer
	{
		public final float[] positionValues;
		public final float[] textureValues;
		public final float[] normalValues;
		private int _index;
		
		public _Builder(int vertexCount)
		{
			this.positionValues = new float[3 * vertexCount];
			this.textureValues = new float[2 * vertexCount];
			this.normalValues = new float[3 * vertexCount];
		}
		@Override
		public void consume(float[] position, float[] texture, float[] normal)
		{
			System.arraycopy(position, 0, this.positionValues, 3 * _index, 3);
			System.arraycopy(texture, 0, this.textureValues, 2 * _index, 2);
			System.arraycopy(normal, 0, this.normalValues, 3 * _index, 3);
			_index += 1;
		}
	}
}
