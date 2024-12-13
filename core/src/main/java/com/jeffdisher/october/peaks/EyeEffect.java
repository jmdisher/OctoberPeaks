package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.utils.Assert;


/**
 * The "eye effect" is an orthographic layer drawn on top of the screen to show something happening.  The most obvious
 * example of this is the red layer when the player takes damage.
 */
public class EyeEffect
{
	public static final long EYE_EFFECT_DURATION_MILLIS = 1000L;

	private final GL20 _gl;
	private final Program _program;
	private final VertexArray _screenSquare;
	private final int _effectTexture;
	private final int _uTexture;
	private final int _uColour;
	private long _effectEndMillis;

	public EyeEffect(GL20 gl)
	{
		_gl = gl;
		
		_program = Program.fullyLinkedProgram(_gl
				, _readUtf8Asset("eye_effect.vert")
				, _readUtf8Asset("eye_effect.frag")
				, new String[] {
						"aPosition",
						"aTexture",
				}
		);
		
		// Create the scratch buffer we will use for out graphics data (short-lived).
		int floatsPerVertex = Arrays.stream(_program.attributes)
				.map((Attribute attribute) -> attribute.floats())
				.collect(Collectors.summingInt((Integer i) -> i))
		;
		int vertexCount = 6;
		ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * floatsPerVertex * Float.BYTES);
		buffer.order(ByteOrder.nativeOrder());
		FloatBuffer meshBuffer = buffer.asFloatBuffer();
		
		_screenSquare = _defineEyeEffectVertices(_gl, _program, meshBuffer);
		try
		{
			_effectTexture = TextureHelpers.loadInternalRGBA(gl, "eye_effect.jpeg");
		}
		catch (IOException e)
		{
			throw Assert.unexpected(e);
		}
		_uTexture = _program.getUniformLocation("uTexture");
		Assert.assertTrue(_uTexture >= 0);
		_uColour = _program.getUniformLocation("uColour");
		Assert.assertTrue(_uColour >= 0);
	}

	public void drawEyeEffect()
	{
		long currentTimeMillis = System.currentTimeMillis();
		if (currentTimeMillis < _effectEndMillis)
		{
			long millisLeft = _effectEndMillis - currentTimeMillis;
			float alpha = (float)millisLeft / (float)EYE_EFFECT_DURATION_MILLIS;
			_program.useProgram();
			_gl.glActiveTexture(GL20.GL_TEXTURE1);
			_gl.glBindTexture(GL20.GL_TEXTURE_2D, _effectTexture);
			_gl.glUniform1i(_uTexture, 1);
			_gl.glUniform4f(_uColour, 1.0f, 0.0f, 0.0f, alpha);
			_screenSquare.drawAllTriangles(_gl);
		}
	}

	public void thisEntityHurt()
	{
		_effectEndMillis = System.currentTimeMillis() + EYE_EFFECT_DURATION_MILLIS;
	}


	private static VertexArray _defineEyeEffectVertices(GL20 gl, Program program, FloatBuffer meshBuffer)
	{
		float height = 1.0f;
		float width = 1.0f;
		float textureBaseU = 0.0f;
		float textureBaseV = 0.0f;
		float textureSize = 1.0f;
		BufferBuilder builder = new BufferBuilder(meshBuffer, program.attributes);
		builder.appendVertex(new float[] {-width, -height}
				, new float[] {textureBaseU, textureBaseV}
		);
		builder.appendVertex(new float[] {width, height}
				, new float[] {textureBaseU + textureSize, textureBaseV + textureSize}
		);
		builder.appendVertex(new float[] {-width, height}
				, new float[] {textureBaseU, textureBaseV + textureSize}
		);
		builder.appendVertex(new float[] {-width, -height}
				, new float[] {textureBaseU, textureBaseV}
		);
		builder.appendVertex(new float[] {width, -height}
				, new float[] {textureBaseU + textureSize, textureBaseV}
		);
		builder.appendVertex(new float[] {width, height}
				, new float[] {textureBaseU + textureSize, textureBaseV + textureSize}
		);
		return builder.finishOne().flush(gl);
	}

	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}
}
