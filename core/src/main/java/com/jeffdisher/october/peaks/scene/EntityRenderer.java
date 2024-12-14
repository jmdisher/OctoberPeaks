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
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.wavefront.WavefrontReader;
import com.jeffdisher.october.types.EntityConstants;
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

	private final GL20 _gl;
	private final TextureAtlas<SceneMeshHelpers.AuxVariant> _auxBlockTextures;
	private final Program _program;
	private final int _uModelMatrix;
	private final int _uViewMatrix;
	private final int _uProjectionMatrix;
	private final int _uWorldLightLocation;
	private final int _uTexture0;
	private final int _uTexture1;
	private final int _uSkyLight;
	private final Map<Integer, PartialEntity> _entities;
	private final Map<EntityType, _EntityData> _entityData;
	private final int _highlightTexture;

	public EntityRenderer(GL20 gl) throws IOException
	{
		_gl = gl;
		
		// Load the secondary atlas for secondary textures.
		_auxBlockTextures = TextureHelpers.loadAtlasForVariants(_gl
				, "aux_"
				, SceneMeshHelpers.AuxVariant.class
				, "missing_texture.png"
		);
		
		// Create the shader program.
		_program = Program.fullyLinkedProgram(_gl
				, _readUtf8Asset("scene.vert")
				, _readUtf8Asset("scene.frag")
				, new String[] {
						"aPosition",
						"aNormal",
						"aTexture0",
						"aTexture1",
						"aBlockLightMultiplier",
						"aSkyLightMultiplier",
				}
		);
		_uModelMatrix = _program.getUniformLocation("uModelMatrix");
		_uViewMatrix = _program.getUniformLocation("uViewMatrix");
		_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
		_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
		_uTexture0 = _program.getUniformLocation("uTexture0");
		_uTexture1 = _program.getUniformLocation("uTexture1");
		_uSkyLight = _program.getUniformLocation("uSkyLight");
		
		ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
		direct.order(ByteOrder.nativeOrder());
		FloatBuffer meshBuffer = direct.asFloatBuffer();
		_entities = new HashMap<>();
		Map<EntityType, _EntityData> entityData = new HashMap<>();
		for (EntityType type : EntityType.values())
		{
			if (EntityType.ERROR != type)
			{
				_EntityData data = _loadEntityResources(gl, meshBuffer, type);
				entityData.put(type, data);
			}
		}
		_entityData = Collections.unmodifiableMap(entityData);
		_highlightTexture = TextureHelpers.loadSinglePixelImageRGBA(_gl, new byte[] {(byte)0xff, (byte)0xff, (byte)0xff, 0x7f});
	}

	public void renderEntities(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_program.useProgram();
		_gl.glUniform3f(_uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _uProjectionMatrix);
		_gl.glUniform1f(_uSkyLight, skyLightMultiplier);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 2 textures.
		_gl.glUniform1i(_uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glUniform1i(_uTexture1, 1);
		_gl.glActiveTexture(GL20.GL_TEXTURE1);
		
		// We will bind the AUX texture atlas for texture unit 1 in all invocations, but we usually just reference "NONE" where not applicable.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _auxBlockTextures.texture);
		
		// Render any entities.
		for (PartialEntity entity : _entities.values())
		{
			EntityType type = entity.type();
			Matrix model = _generateEntityModelMatrix(entity, type);
			model.uploadAsUniform(_gl, _uModelMatrix);
			// In the future, we should change how we do this drawing to avoid so many state changes (either batch by type or combine the types into fewer GL objects).
			_EntityData data = _entityData.get(type);
			_gl.glActiveTexture(GL20.GL_TEXTURE0);
			_gl.glBindTexture(GL20.GL_TEXTURE_2D, data.texture);
			data.vertices.drawAllTriangles(_gl);
		}
	}

	public void renderSelectedEntity(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier, PartialEntity selectedEntity)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_program.useProgram();
		_gl.glUniform3f(_uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _uProjectionMatrix);
		_gl.glUniform1f(_uSkyLight, skyLightMultiplier);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 2 textures.
		_gl.glUniform1i(_uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glUniform1i(_uTexture1, 1);
		_gl.glActiveTexture(GL20.GL_TEXTURE1);
		
		// We will bind the AUX texture atlas for texture unit 1 in all invocations, but we usually just reference "NONE" where not applicable.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _auxBlockTextures.texture);
		
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _highlightTexture);
		_gl.glDepthFunc(GL20.GL_LEQUAL);
		EntityType type = selectedEntity.type();
		Matrix model = _generateEntityModelMatrix(selectedEntity, type);
		model.uploadAsUniform(_gl, _uModelMatrix);
		_entityData.get(selectedEntity.type()).vertices.drawAllTriangles(_gl);
	}

	public void setEntity(PartialEntity entity)
	{
		_entities.put(entity.id(), entity);
	}

	public void removeEntity(int id)
	{
		_entities.remove(id);
	}

	public void shutdown()
	{
		_auxBlockTextures.shutdown(_gl);
		_program.delete();
	}


	private _EntityData _loadEntityResources(GL20 gl, FloatBuffer meshBuffer, EntityType type) throws IOException
	{
		EntityVolume volume = EntityConstants.getVolume(type);
		String name = type.name();
		FileHandle meshFile = Gdx.files.internal("entity_" + name + ".obj");
		VertexArray buffer;
		float[] ignoredOtherTexture = new float[] { 0.0f, 0.0f };
		float[] blockLightMultiplier = new float[] {1.0f};
		float[] skyLightMultiplier = new float[] {0.0f};
		if (meshFile.exists())
		{
			String rawMesh = meshFile.readString();
			BufferBuilder builder = new BufferBuilder(meshBuffer, _program.attributes);
			WavefrontReader.readFile((float[] position, float[] texture, float[] normal) -> {
				builder.appendVertex(position
						, normal
						, texture
						, ignoredOtherTexture
						, blockLightMultiplier
						, skyLightMultiplier
				);
			}, rawMesh);
			buffer = builder.finishOne().flush(gl);
		}
		else
		{
			Prism prism = Prism.getBoundsAtOrigin(volume.width(), volume.width(), volume.height());
			buffer = SceneMeshHelpers.createOutlinePrism(_gl, _program.attributes, meshBuffer, prism, _auxBlockTextures);
		}
		
		FileHandle textureFile = Gdx.files.internal("entity_" + name + ".png");
		int texture;
		if (textureFile.exists())
		{
			texture = TextureHelpers.loadHandleRGBA(gl, textureFile);
		}
		else
		{
			texture = TextureHelpers.loadInternalRGBA(_gl, "missing_texture.png");
		}
		_EntityData data = new _EntityData(buffer, texture);
		return data;
	}

	private static Matrix _generateEntityModelMatrix(PartialEntity entity, EntityType type)
	{
		// Note that the model definitions are within the unit cube from [0..1] on all axes.
		// This means that we need to translate by half a block before rotation and then translate back + 0.5.
		// This translation needs to account for the scale, though, since it is being applied twice (and both can't be before scale).
		EntityLocation location = entity.location();
		EntityVolume volume = EntityConstants.getVolume(type);
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
