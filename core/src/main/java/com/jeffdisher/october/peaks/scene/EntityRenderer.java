package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.wavefront.WavefrontReader;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the entities within the OpenGL scene, but not the world/blocks.
 */
public class EntityRenderer
{
	public static final int BUFFER_SIZE = 1 * 1024 * 1024;
	public static final long DAMAGE_DURATION_MILLIS = 1000L;
	public static class Resources
	{
		private final Program _program;
		private final int _uModelMatrix;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uWorldLightLocation;
		private final int _uTexture0;
		private final int _uDamage;
		private final int _uBrightness;
		private final Map<EntityType, _EntityData> _entityData;
		private final int _highlightTexture;
		
		public Resources(Environment environment, GL20 gl) throws IOException
		{
			// Create the shader program.
			_program = Program.fullyLinkedProgram(gl
					, _readUtf8Asset("entity.vert")
					, _readUtf8Asset("entity.frag")
					, new String[] {
							"aPosition",
							"aNormal",
							"aTexture0",
					}
			);
			_uModelMatrix = _program.getUniformLocation("uModelMatrix");
			_uViewMatrix = _program.getUniformLocation("uViewMatrix");
			_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
			_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
			_uTexture0 = _program.getUniformLocation("uTexture0");
			_uDamage = _program.getUniformLocation("uDamage");
			_uBrightness = _program.getUniformLocation("uBrightness");
			
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer meshBuffer = direct.asFloatBuffer();
			Map<EntityType, _EntityData> entityData = new HashMap<>();
			for (EntityType type : environment.creatures.ENTITY_BY_NUMBER)
			{
				if (null != type)
				{
					_EntityData data = _loadEntityResources(gl, _program, meshBuffer, type);
					entityData.put(type, data);
				}
			}
			_entityData = Collections.unmodifiableMap(entityData);
			_highlightTexture = TextureHelpers.loadSinglePixelImageRGBA(gl, new byte[] {(byte)0xff, (byte)0xff, (byte)0xff, 0x7f});
		}
		
		public void shutdown(GL20 gl)
		{
			_program.delete();
			for (_EntityData data : _entityData.values())
			{
				data.vertices.delete(gl);
				gl.glDeleteTexture(data.texture);
			}
			gl.glDeleteTexture(_highlightTexture);
		}
	}


	private final GL20 _gl;
	private final Binding<Float> _screenBrightness;
	private final Resources _resources;
	private final Map<Integer, PartialEntity> _entities;
	private final Map<Integer, Long> _entityDamageMillis;

	public EntityRenderer(GL20 gl, Binding<Float> screenBrightness, LoadedResources resources)
	{
		_gl = gl;
		_screenBrightness = screenBrightness;
		_resources = resources.entityRenderer();
		
		_entities = new HashMap<>();
		_entityDamageMillis = new HashMap<>();
	}

	public void renderEntities(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_resources._program.useProgram();
		_gl.glUniform3f(_resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _resources._uProjectionMatrix);
		_gl.glUniform1f(_resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We just use the texture for the entity.
		_gl.glUniform1i(_resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		
		// Render any entities.
		long currentMillis = System.currentTimeMillis();
		for (PartialEntity entity : _entities.values())
		{
			EntityType type = entity.type();
			Matrix model = _generateEntityModelMatrix(entity, type);
			model.uploadAsUniform(_gl, _resources._uModelMatrix);
			// In the future, we should change how we do this drawing to avoid so many state changes (either batch by type or combine the types into fewer GL objects).
			_EntityData data = _resources._entityData.get(type);
			_gl.glActiveTexture(GL20.GL_TEXTURE0);
			_gl.glBindTexture(GL20.GL_TEXTURE_2D, data.texture);
			if (_entityDamageMillis.containsKey(entity.id()))
			{
				// We want to set the damage.
				long effectEndMillis = _entityDamageMillis.get(entity.id());
				if (effectEndMillis > currentMillis)
				{
					long millisLeft = effectEndMillis - currentMillis;
					float magnitude = (float)millisLeft / (float)DAMAGE_DURATION_MILLIS;
					_gl.glUniform1f(_resources._uDamage, magnitude);
				}
				else
				{
					_gl.glUniform1f(_resources._uDamage, 0.0f);
					_entityDamageMillis.remove(entity.id());
				}
			}
			else
			{
				_gl.glUniform1f(_resources._uDamage, 0.0f);
			}
			data.vertices.drawAllTriangles(_gl);
		}
	}

	public void renderSelectedEntity(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier, PartialEntity selectedEntity)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_resources._program.useProgram();
		_gl.glUniform3f(_resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _resources._uProjectionMatrix);
		_gl.glUniform1f(_resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We just use the texture for the entity.
		_gl.glUniform1i(_resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._highlightTexture);
		_gl.glDepthFunc(GL20.GL_LEQUAL);
		EntityType type = selectedEntity.type();
		Matrix model = _generateEntityModelMatrix(selectedEntity, type);
		model.uploadAsUniform(_gl, _resources._uModelMatrix);
		_resources._entityData.get(selectedEntity.type()).vertices.drawAllTriangles(_gl);
	}

	public void setEntity(PartialEntity entity)
	{
		_entities.put(entity.id(), entity);
	}

	public void removeEntity(int id)
	{
		_entities.remove(id);
		_entityDamageMillis.remove(id);
	}

	public void entityHurt(int id)
	{
		long endOfEffectMillis = System.currentTimeMillis() + DAMAGE_DURATION_MILLIS;
		_entityDamageMillis.put(id, endOfEffectMillis);
	}


	private static _EntityData _loadEntityResources(GL20 gl, Program program, FloatBuffer meshBuffer, EntityType type) throws IOException
	{
		String name = type.name().toUpperCase();
		FileHandle meshFile = Gdx.files.internal("entity_" + name + ".obj");
		FileHandle textureFile = Gdx.files.internal("entity_" + name + ".png");
		
		// We will require that the entity has a mesh definition and a texture.
		Assert.assertTrue(meshFile.exists());
		Assert.assertTrue(textureFile.exists());
		
		String rawMesh = meshFile.readString();
		BufferBuilder builder = new BufferBuilder(meshBuffer, program.attributes);
		WavefrontReader.readFile((float[] position, float[] texture, float[] normal) -> {
			builder.appendVertex(position
					, normal
					, texture
			);
		}, rawMesh);
		VertexArray buffer = builder.finishOne().flush(gl);
		
		int texture = TextureHelpers.loadHandleRGBA(gl, textureFile);
		_EntityData data = new _EntityData(buffer, texture);
		return data;
	}

	private static Matrix _generateEntityModelMatrix(PartialEntity entity, EntityType type)
	{
		// Note that the model definitions are within the unit cube from [0..1] on all axes.
		// This means that we need to translate by half a block before rotation and then translate back + 0.5.
		// This translation needs to account for the scale, though, since it is being applied twice (and both can't be before scale).
		EntityLocation location = entity.location();
		EntityVolume volume = type.volume();
		float width = volume.width();
		float height = volume.height();
		float halfWidth = width / 2.0f;
		float halfHeight = height / 2.0f;
		Matrix rotatePitch = Matrix.rotateX(OrientationHelpers.getPitchRadians(entity.pitch()));
		Matrix rotateYaw = Matrix.rotateZ(OrientationHelpers.getYawRadians(entity.yaw()));
		Matrix translate = Matrix.translate(location.x() + halfWidth, location.y() + halfWidth, location.z() + halfHeight);
		Matrix scale = Matrix.scale(width, width, height);
		Matrix rotate = Matrix.mutliply(rotateYaw, rotatePitch);
		Matrix centreTranslate = Matrix.translate(-halfWidth, -halfWidth, -halfHeight);
		Matrix model = Matrix.mutliply(translate, Matrix.mutliply(rotate, Matrix.mutliply(centreTranslate, scale)));
		return model;
	}

	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static record _EntityData(VertexArray vertices
			, int texture
	) {}
}
