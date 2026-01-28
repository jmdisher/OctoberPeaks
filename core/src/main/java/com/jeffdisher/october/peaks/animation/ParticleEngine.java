package com.jeffdisher.october.peaks.animation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.peaks.utils.RingBufferLogic;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the particle effects within the OpenGL scene.
 */
public class ParticleEngine
{
	// Note that we use GL_POINTS to render the particles, so there is only 1 vertex per particle.
	public static final int MAX_PARTICLE_COUNT = 1024;
	// animationOffset, startLocation, endLocation, colour(RGB).
	public static final int FOUR_BYTE_SLOT_PER_VERTEX = 1 + 3 + 3 + 3;
	public static final int BYTES_PER_PARTICLE = FOUR_BYTE_SLOT_PER_VERTEX * Float.BYTES;
	public static final int BUFFER_SIZE_IN_BYTES = MAX_PARTICLE_COUNT * BYTES_PER_PARTICLE;
	public static final int MILLIS_TO_LIVE = 2_000;
	public static final float PARTICLE_HALF_EDGE_SIZE = 0.05f;

	public static class Resources
	{
		private final Program _program;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uTexture0;
		private final int _uBrightness;
		private final int _uAnimationFraction;
		private final int _particleTexture;
		
		// We will keep a native buffer we can use to transfer a single vertex.
		private final ByteBuffer _singleTransferBuffer;
		private final int _gpuDataBuffer;
		
		public Resources(GL20 gl) throws IOException
		{
			// Create the shader program.
			_program = Program.fullyLinkedProgram(gl
				, MiscPeaksHelpers.readUtf8Asset("particles.vert")
				, MiscPeaksHelpers.readUtf8Asset("particles.frag")
				, new String[] {
					"aAnimationOffset",
					"aStartPosition",
					"aEndPosition",
					"aColour",
				}
			);
			_uViewMatrix = _program.getUniformLocation("uViewMatrix");
			_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
			_uTexture0 = _program.getUniformLocation("uTexture0");
			_uBrightness = _program.getUniformLocation("uBrightness");
			_uAnimationFraction = _program.getUniformLocation("uAnimationFraction");
			
			// In the future, if we want multiple ParticleEngine instances with different textures, we will need to
			// store these in a different way, in Resources.  For now, we just hard-code the one name.
			FileHandle textureFile = Gdx.files.internal("particle.png");
			Assert.assertTrue(textureFile.exists());
			_particleTexture = TextureHelpers.loadHandleRGBA(gl, textureFile);
			
			_singleTransferBuffer = ByteBuffer.allocateDirect(BYTES_PER_PARTICLE);
			_singleTransferBuffer.order(ByteOrder.nativeOrder());
			_gpuDataBuffer = gl.glGenBuffer();
			Assert.assertTrue(_gpuDataBuffer > 0);
			
			// Note that this data starts empty so we don't upload our new _systemDataBuffer but we need it to be dynamic, since we will update it.
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _gpuDataBuffer);
			// WARNING:  Since we are using a FloatBuffer in glBufferData, the size is ignored and only remaining is considered.
			gl.glBufferData(GL20.GL_ARRAY_BUFFER, BUFFER_SIZE_IN_BYTES, null, GL20.GL_DYNAMIC_DRAW);
			Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
		}
		
		public void shutdown(GL20 gl)
		{
			_program.delete();
			gl.glDeleteTexture(_particleTexture);
			gl.glDeleteBuffer(_gpuDataBuffer);
		}
	}


	private final GL20 _gl;
	private final Binding<Float> _screenBrightness;
	private final Resources _resources;
	private final long _startMillis;

	// Our use of the buffer is circular since every element is added in-order and has the same lifetime.
	private RingBufferLogic _ringLogic;
	private final int[] _millisExpiries;

	public ParticleEngine(GL20 gl, Binding<Float> screenBrightness, LoadedResources resources, long currentTimeMillis)
	{
		_gl = gl;
		_screenBrightness = screenBrightness;
		_resources = resources.particleResources();
		_startMillis = currentTimeMillis;
		
		_ringLogic = new RingBufferLogic(MAX_PARTICLE_COUNT);
		_millisExpiries = new int[MAX_PARTICLE_COUNT];
	}

	public boolean addNewParticle(EntityLocation start, EntityLocation end, float r, float g, float b, long currentTimeMillis)
	{
		int allocatedIndex = _ringLogic.incrementFreeAndReturnPrevious();
		
		if (-1 != allocatedIndex)
		{
			// This gives us millions of seconds, which should be sufficient.
			int millisOffset = (int) (currentTimeMillis - _startMillis);
			int byteOffset = allocatedIndex * BYTES_PER_PARTICLE;
			ByteBuffer buffer = _resources._singleTransferBuffer.clear();
			int millisExpiry = millisOffset + MILLIS_TO_LIVE;
			_millisExpiries[allocatedIndex] = millisExpiry;
			_writeParticle(buffer, start, end, r, g, b, millisOffset);
			
			// Do we benefit from batching these into 1-2 calls per frame?
			_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _resources._gpuDataBuffer);
			ByteBuffer uploadBuffer = _resources._singleTransferBuffer.flip();
			_gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, byteOffset, BYTES_PER_PARTICLE, uploadBuffer);
		}
		return (-1 != allocatedIndex);
	}

	public void freeDeadParticles(long currentTimeMillis)
	{
		// We want to walk the ring buffer from the first used, marking anything free which has expired.
		int millisOffset = (int) (currentTimeMillis - _startMillis);
		int usedIndex = _ringLogic.getFirstIndexUsed();
		while (-1 != usedIndex)
		{
			// Read this index and see if it has expired.
			int millisExpiry = _millisExpiries[usedIndex];
			if (millisExpiry <= millisOffset)
			{
				// This is expired so free it.
				usedIndex = _ringLogic.freeFirstUsedAndGetNext();
			}
			else
			{
				// This is still valid so break since everything else is still valid.
				break;
			}
		}
	}

	public void renderAllParticles(Matrix commonViewMatrix, Matrix projectionMatrix, long currentTimeMillis)
	{
		// We want to rotate the model so that they always face the camera.
		Matrix viewMatrix = commonViewMatrix;
		
		// For now we will just use the standard depth approach but we may want to make this read-only or change the comparison, in the future.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_gl.glEnable(GL20.GL_VERTEX_PROGRAM_POINT_SIZE);
		// NOTE:  We need to manually enable GL_POINT_SPRITE, although this should be enabled in this version (otherwise, gl_PointCoord is always vec2(0,0)).
		_gl.glEnable(0x8861);
		
		_resources._program.useProgram();
		viewMatrix.uploadAsUniform(_gl, _resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _resources._uProjectionMatrix);
		_gl.glUniform1f(_resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 1 texture.
		_gl.glUniform1i(_resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._particleTexture);
		
		int millisOffset = (int) (currentTimeMillis - _startMillis);
		float animationOffset = (float)(millisOffset % MILLIS_TO_LIVE) / (float)MILLIS_TO_LIVE;
		_gl.glUniform1f(_resources._uAnimationFraction, animationOffset);
		
		// Setup the buffer attributes.
		_setupBuffer();
		
		// We will draw everything in the used portion of the buffer, which may take 2 calls.
		int firstIndexFree = _ringLogic.getFirstIndexFree();
		int firstIndexUsed = _ringLogic.getFirstIndexUsed();
		if (-1 == firstIndexUsed)
		{
			// Nothing here so do nothing.
		}
		else if (-1 == firstIndexFree)
		{
			// This is full so just draw everything.
			_drawExtent(0, MAX_PARTICLE_COUNT);
		}
		else
		{
			// This is part of the buffer so it might wrap.
			if (firstIndexUsed < firstIndexFree)
			{
				// This is just one segment.
				int particlesToDraw = firstIndexFree - firstIndexUsed;
				_drawExtent(firstIndexUsed, particlesToDraw);
			}
			else
			{
				// This is split into 2 segments.
				int endParticlesToDraw = MAX_PARTICLE_COUNT - firstIndexUsed;
				_drawExtent(firstIndexUsed, endParticlesToDraw);
				_drawExtent(0, firstIndexFree);
			}
		}
	}


	private static void _writeParticle(ByteBuffer buffer, EntityLocation start, EntityLocation end, float r, float g, float b, int millisOffset)
	{
		float animationOffset = (float)(millisOffset % MILLIS_TO_LIVE) / (float)MILLIS_TO_LIVE;
		
		// We use GL_POINTS in this rendering so we only write a single vertex.
		// animation offset used by the shader to determine the animation step.
		buffer.putFloat(animationOffset);
		
		// start
		buffer.putFloat(start.x());
		buffer.putFloat(start.y());
		buffer.putFloat(start.z());
		
		// end
		buffer.putFloat(end.x());
		buffer.putFloat(end.y());
		buffer.putFloat(end.z());
		
		// colour
		buffer.putFloat(r);
		buffer.putFloat(g);
		buffer.putFloat(b);
	}

	private void _drawExtent(int particleStartIndex, int particlesToDraw)
	{
		_gl.glDrawArrays(GL20.GL_POINTS, particleStartIndex, particlesToDraw);
	}

	private void _setupBuffer()
	{
		_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _resources._gpuDataBuffer);
		int floatOffset = 0;
		for (int i = 0; i < _resources._program.attributes.length; ++i)
		{
			Attribute attribute = _resources._program.attributes[i];
			_gl.glEnableVertexAttribArray(i);
			_gl.glVertexAttribPointer(i, attribute.floats(), GL20.GL_FLOAT, false, FOUR_BYTE_SLOT_PER_VERTEX * Float.BYTES, floatOffset * Float.BYTES);
			floatOffset += attribute.floats();
		}
	}
}
