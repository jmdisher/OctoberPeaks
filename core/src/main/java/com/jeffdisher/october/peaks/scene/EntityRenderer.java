package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.logic.OrientationHelpers;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.animation.AnimationManager;
import com.jeffdisher.october.peaks.animation.GhostManager;
import com.jeffdisher.october.peaks.animation.Rigging;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.peaks.utils.WorldCache;
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
		private final int _uOpacity;
		private final Map<EntityType, _EntityData> _entityData;
		private final int _highlightTexture;
		
		public Resources(Environment environment, GL20 gl) throws IOException
		{
			// Create the shader program.
			_program = Program.fullyLinkedProgram(gl
					, MiscPeaksHelpers.readUtf8Asset("entity.vert")
					, MiscPeaksHelpers.readUtf8Asset("entity.frag")
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
			_uOpacity = _program.getUniformLocation("uOpacity");
			
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
	private final WorldCache _worldCache;
	private final AnimationManager _animationManager;
	private final GhostManager _ghostManager;

	public EntityRenderer(GL20 gl
		, Binding<Float> screenBrightness
		, LoadedResources resources
		, WorldCache worldCache
		, AnimationManager animationManager
		, GhostManager ghostManager
	)
	{
		_gl = gl;
		_screenBrightness = screenBrightness;
		_resources = resources.entityRenderer();
		
		_worldCache = worldCache;
		_animationManager = animationManager;
		_ghostManager = ghostManager;
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
		_gl.glUniform1f(_resources._uOpacity, 1.0f);
		for (PartialEntity entity : _worldCache.getOtherEntities())
		{
			_drawPartialEntity(currentMillis, entity);
		}
		
		// Walk any ghosts.
		// Note that blending is normally disabled for entities, since they are all opaque, but ghosts have partial opacity.
		_gl.glEnable(GL20.GL_BLEND);
		for (GhostManager.GhostSnapshot<PartialEntity> snapshot : _ghostManager.pruneAndSnapshotEntities(currentMillis))
		{
			float opacity = 1.0f - snapshot.animationCompleteFraction();
			_gl.glUniform1f(_resources._uOpacity, opacity);
			PartialEntity corpse = snapshot.corpse();
			PartialEntity entity = new PartialEntity(corpse.id()
				, corpse.type()
				, snapshot.location()
				, corpse.yaw()
				, corpse.pitch()
				, corpse.health()
				, corpse.extendedData()
			);
			_drawPartialEntity(currentMillis, entity);
		}
		_gl.glDisable(GL20.GL_BLEND);
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
		EntityLocation entityLocation = selectedEntity.location();
		Matrix model = _generateEntityBodyModelMatrix(type, entityLocation, selectedEntity.yaw());
		model.uploadAsUniform(_gl, _resources._uModelMatrix);
		_resources._entityData.get(selectedEntity.type()).vertices.drawAllTriangles(_gl);
	}


	private static _EntityData _loadEntityResources(GL20 gl, Program program, FloatBuffer meshBuffer, EntityType type) throws IOException
	{
		String name = type.name().toUpperCase();
		EntityVolume volume = type.volume();
		float width = volume.width();
		float height = volume.height();
		
		// We always require the texture.
		FileHandle textureFile = Gdx.files.internal("entity_" + name + ".png");
		Assert.assertTrue(textureFile.exists());
		
		// We either need a file for the entire entity mesh, or one split out into body parts.
		VertexArray bodyBuffer;
		_RiggingData headRig;
		_RiggingData[] limbRigs;
		
		FileHandle riggingFile = Gdx.files.internal("entity_" + name + "_rigging.tablist");
		if (riggingFile.exists())
		{
			// There is a rigging definition so load that and then process referenced files.
			List<Rigging.LimbRig> limbs = Rigging.loadFromTablistFile(riggingFile);
			
			BufferBuilder builder = new BufferBuilder(meshBuffer, program.attributes);
			bodyBuffer = null;
			headRig = null;
			List<_RiggingData> otherRigs = new ArrayList<>();
			for (Rigging.LimbRig limb : limbs)
			{
				FileHandle meshFile = Gdx.files.internal("entity_" + name + "_" + limb.name() + ".obj");
				Assert.assertTrue(meshFile.exists());
				
				switch (limb.type())
				{
					case BODY:
					{
						Assert.assertTrue(null == bodyBuffer);
						WavefrontReader.readFile(new _AdaptingVertexLoader(builder, null, width, height), meshFile.readString());
						bodyBuffer = builder.finishOne().flush(gl);
						break;
					}
					case PITCH:
					{
						Assert.assertTrue(null == headRig);
						headRig = _loadRig(gl, builder, _RigType.PITCH, limb.base(), meshFile, width, height);
						break;
					}
					case POSITIVE:
					{
						_RiggingData other = _loadRig(gl, builder, _RigType.POSITIVE, limb.base(), meshFile, width, height);
						otherRigs.add(other);
						break;
					}
					case NEGATIVE:
					{
						_RiggingData other = _loadRig(gl, builder, _RigType.NEGATIVE, limb.base(), meshFile, width, height);
						otherRigs.add(other);
						break;
					}
				}
			}
			
			limbRigs = otherRigs.toArray((int size) -> new _RiggingData[size]);
		}
		else
		{
			FileHandle meshFile = Gdx.files.internal("entity_" + name + ".obj");
			String rawMesh = meshFile.readString();
			BufferBuilder builder = new BufferBuilder(meshBuffer, program.attributes);
			WavefrontReader.readFile(new _AdaptingVertexLoader(builder, null, width, height), rawMesh);
			bodyBuffer = builder.finishOne().flush(gl);
			headRig = null;
			limbRigs = new _RiggingData[0];
		}
		
		int texture = TextureHelpers.loadHandleRGBA(gl, textureFile);
		_EntityData data = new _EntityData(bodyBuffer, texture, headRig, limbRigs);
		return data;
	}

	private static Matrix _generateEntityBodyModelMatrix(EntityType type, EntityLocation location, byte yaw)
	{
		// Note that model definitions are moved to be centred on 0,0,0 during load, and scaled.
		// This means that we need to add half the width and height to the base location when positioning them but that
		// rotation can be done first, without changing anything.
		EntityVolume volume = type.volume();
		float width = volume.width();
		float height = volume.height();
		float halfWidth = width / 2.0f;
		float halfHeight = height / 2.0f;
		Matrix translate = Matrix.translate(location.x() + halfWidth, location.y() + halfWidth, location.z() + halfHeight);
		Matrix rotate = Matrix.rotateZ(OrientationHelpers.getYawRadians(yaw));
		Matrix model = Matrix.multiply(translate, rotate);
		return model;
	}

	private static Matrix _generateEntityPartModelMatrix(EntityType type, EntityLocation base, EntityLocation offset, byte yaw, byte pitch)
	{
		// Note that model part definitions are moved to be centred on 0,0,0 during load, and scaled.
		
		// We want to rotate by pitch before anything else, since that just orients the limb, not where it is in space.
		Matrix rotatePitch = Matrix.rotateX(OrientationHelpers.getPitchRadians(pitch));
		
		// Then, we will translate to where in the entity it should be.
		EntityVolume volume = type.volume();
		float width = volume.width();
		float height = volume.height();
		float halfWidth = width / 2.0f;
		float halfHeight = height / 2.0f;
		Matrix translateToEntity = Matrix.translate(offset.x() - halfWidth, offset.y() - halfWidth, offset.z() - halfHeight);
		
		// Then, we will rotate by yaw so that it moves in sync with the rest of the model.
		Matrix rotateYaw = Matrix.rotateZ(OrientationHelpers.getYawRadians(yaw));
		
		// Finally, translate to where the entity would be drawn in the world.
		Matrix translateToWorld = Matrix.translate(base.x() + halfWidth, base.y() + halfWidth, base.z() + halfHeight);
		
		return Matrix.multiply(translateToWorld, Matrix.multiply(rotateYaw, Matrix.multiply(translateToEntity, rotatePitch)));
	}

	private void _drawPartialEntity(long currentMillis, PartialEntity entity)
	{
		EntityType type = entity.type();
		// In the future, we should change how we do this drawing to avoid so many state changes (either batch by type or combine the types into fewer GL objects).
		_EntityData data = _resources._entityData.get(type);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, data.texture);
		
		// Ask the animation manager if this entity recently took damage (as we discolour it in reponse for a short time).
		float fraction = _animationManager.getDamageFreshnessFraction(currentMillis, entity.id());
		_gl.glUniform1f(_resources._uDamage, fraction);
		
		EntityLocation entityLocation = entity.location();
		Matrix bodyModel = _generateEntityBodyModelMatrix(type, entityLocation, entity.yaw());
		bodyModel.uploadAsUniform(_gl, _resources._uModelMatrix);
		data.vertices.drawAllTriangles(_gl);
		
		// Draw rigged limbs.
		_RiggingData headRig = data.headRig;
		if (null != headRig)
		{
			Matrix headModel = _generateEntityPartModelMatrix(type, entityLocation, headRig.offsetWorldCoords, entity.yaw(), entity.pitch());
			headModel.uploadAsUniform(_gl, _resources._uModelMatrix);
			headRig.vertices.drawAllTriangles(_gl);
		}
		byte animationFrame = _animationManager.getWalkingAnimationFrame(entity);
		for (_RiggingData limb : data.limbRigs)
		{
			byte animation = (_RigType.POSITIVE == limb.type) ? (byte)animationFrame : (byte)-animationFrame;
			Matrix model = _generateEntityPartModelMatrix(type, entityLocation, limb.offsetWorldCoords, entity.yaw(), animation);
			model.uploadAsUniform(_gl, _resources._uModelMatrix);
			limb.vertices.drawAllTriangles(_gl);
		}
	}

	private static _RiggingData _loadRig(GL20 gl
		, BufferBuilder builder
		, _RigType type
		, EntityLocation offsetFileCoords
		, FileHandle meshFile
		, float width
		, float height
	)
	{
		_RiggingData rig;
		if (meshFile.exists())
		{
			WavefrontReader.readFile(new _AdaptingVertexLoader(builder, offsetFileCoords, width, height), meshFile.readString());
			VertexArray buffer = builder.finishOne().flush(gl);
			EntityLocation offsetWorldCoords = new EntityLocation(offsetFileCoords.x() * width
				, offsetFileCoords.y() * width
				, offsetFileCoords.z() * height
			);
			rig = new _RiggingData(type, offsetWorldCoords, buffer);
		}
		else
		{
			rig = null;
		}
		return rig;
	}


	private static record _EntityData(VertexArray vertices
		, int texture
		, _RiggingData headRig
		, _RiggingData[] limbRigs
	) {}

	private static record _RiggingData(_RigType type
		, EntityLocation offsetWorldCoords
		, VertexArray vertices
	) {}

	private static enum _RigType
	{
		PITCH,
		POSITIVE,
		NEGATIVE,
		;
	}

	private static class _AdaptingVertexLoader implements WavefrontReader.VertexConsumer
	{
		private final BufferBuilder _builder;
		private final Matrix _transform;
		
		public _AdaptingVertexLoader(BufferBuilder builder, EntityLocation relativeBase, float width, float height)
		{
			_builder = builder;
			
			Matrix scale = Matrix.scale(width, width, height);
			
			// We will translate from file coordinates to the origin before scaling.
			Matrix fileCoordsToOrigin;
			if (null != relativeBase)
			{
				// This is for a rigged extension of the body (head, for example), so we want to map the rigging point to the origin.
				fileCoordsToOrigin = Matrix.translate(-relativeBase.x(), -relativeBase.y(), -relativeBase.z());
			}
			else
			{
				// This must be for the body, itself, so the origin is always in the middle of the unit cube.
				fileCoordsToOrigin = Matrix.translate(-0.5f, -0.5f, -0.5f);
			}
			_transform = Matrix.multiply(scale, fileCoordsToOrigin);
		}
		@Override
		public void consume(float[] position, float[] texture, float[] normal)
		{
			float x = position[0];
			float y = position[1];
			float z = position[2];
			float w = 1.0f;
			
			float[] temp = _transform.multiplyVectorComponents(x, y, z, w);
			float[] shiftedPosition = new float[] { temp[0], temp[1], temp[2] };
			_builder.appendVertex(shiftedPosition
				, normal
				, texture
			);
		}
	}
}
