package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.CuboidMeshManager;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.SceneMeshHelpers;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.wavefront.WavefrontReader;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the OpenGL scene of the world (does not include UI elements, windows, etc).
 */
public class SceneRenderer
{
	public static final int BUFFER_SIZE = 1 * 1024 * 1024;

	private final Environment _environment;
	private final GL20 _gl;
	private final TextureAtlas<ItemVariant> _itemAtlas;
	private final TextureAtlas<BlockVariant> _blockTextures;
	private final TextureAtlas<SceneMeshHelpers.AuxVariant> _auxBlockTextures;
	private final Program _program;
	private final int _uModelMatrix;
	private final int _uViewMatrix;
	private final int _uProjectionMatrix;
	private final int _uWorldLightLocation;
	private final int _uTexture0;
	private final int _uTexture1;
	private final CuboidMeshManager _cuboidMeshes;
	private final Map<Integer, PartialEntity> _entities;
	private final Map<EntityType, _EntityData> _entityData;
	private final int _highlightTexture;
	private final VertexArray _highlightCube;

	private Matrix _viewMatrix;
	private final Matrix _projectionMatrix;
	private Vector _eye;

	public SceneRenderer(Environment environment, GL20 gl, TextureAtlas<ItemVariant> itemAtlas) throws IOException
	{
		_environment = environment;
		_gl = gl;
		_itemAtlas = itemAtlas;
		
		Block[] blocks = Arrays.stream(_environment.items.ITEMS_BY_TYPE)
				.map((Item item) -> _environment.blocks.fromItem(item))
				.filter((Block block) -> null != block)
				.toArray((int size) -> new Block[size])
		;
		_blockTextures = TextureHelpers.loadAtlasForBlocks(_gl
				, blocks
				, "missing_texture.png"
		);
		
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
				}
		);
		_uModelMatrix = _program.getUniformLocation("uModelMatrix");
		_uViewMatrix = _program.getUniformLocation("uViewMatrix");
		_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
		_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
		_uTexture0 = _program.getUniformLocation("uTexture0");
		_uTexture1 = _program.getUniformLocation("uTexture1");
		
		_cuboidMeshes = new CuboidMeshManager(_environment, _gl, _program, itemAtlas, _blockTextures, _auxBlockTextures);
		
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
		_highlightCube = SceneMeshHelpers.createPrism(_gl, _program.attributes, meshBuffer, new float[] {1.0f, 1.0f, 1.0f}, _auxBlockTextures);
		
		_viewMatrix = Matrix.identity();
		_projectionMatrix = Matrix.perspective(90.0f, 1.0f, 0.1f, 100.0f);
		_eye = new Vector(0.0f, 0.0f, 0.0f);
	}

	public void updatePosition(Vector eye, Vector target)
	{
		_eye = eye;
		_viewMatrix = Matrix.lookAt(eye, target, new Vector(0.0f, 0.0f, 1.0f));
	}

	public void render(PartialEntity selectedEntity, AbsoluteLocation selectedBlock)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_program.useProgram();
		_gl.glUniform3f(_uWorldLightLocation, _eye.x(), _eye.y(), _eye.z());
		_viewMatrix.uploadAsUniform(_gl, _uViewMatrix);
		_projectionMatrix.uploadAsUniform(_gl, _uProjectionMatrix);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 2 textures.
		_gl.glUniform1i(_uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glUniform1i(_uTexture1, 1);
		_gl.glActiveTexture(GL20.GL_TEXTURE1);
		
		// We will bind the AUX texture atlas for texture unit 1 in all invocations, but we usually just reference "NONE" where not applicable.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _auxBlockTextures.texture);
		
		// Render the opaque cuboid vertices.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _blockTextures.texture);
		for (Map.Entry<CuboidAddress, CuboidMeshManager.CuboidData> elt : _cuboidMeshes.viewCuboids().entrySet())
		{
			CuboidAddress key = elt.getKey();
			CuboidMeshManager.CuboidData value = elt.getValue();
			if (null != value.opaqueArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _uModelMatrix);
				value.opaqueArray().drawAllTriangles(_gl);
			}
		}
		
		// Render any dropped items.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _itemAtlas.texture);
		for (Map.Entry<CuboidAddress, CuboidMeshManager.CuboidData> elt : _cuboidMeshes.viewCuboids().entrySet())
		{
			CuboidAddress key = elt.getKey();
			CuboidMeshManager.CuboidData value = elt.getValue();
			if (null != value.itemsOnGroundArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _uModelMatrix);
				value.itemsOnGroundArray().drawAllTriangles(_gl);
			}
		}
		
		// Render any entities.
		for (PartialEntity entity : _entities.values())
		{
			EntityLocation location = entity.location();
			EntityType type = entity.type();
			EntityVolume volume = EntityConstants.getVolume(type);
			Matrix translate = Matrix.translate(location.x(), location.y(), location.z());
			Matrix scale = Matrix.scale(volume.width(), volume.width(), volume.height());
			Matrix model = Matrix.mutliply(translate, scale);
			model.uploadAsUniform(_gl, _uModelMatrix);
			// In the future, we should change how we do this drawing to avoid so many state changes (either batch by type or combine the types into fewer GL objects).
			_EntityData data = _entityData.get(type);
			_gl.glActiveTexture(GL20.GL_TEXTURE0);
			_gl.glBindTexture(GL20.GL_TEXTURE_2D, data.texture);
			data.vertices.drawAllTriangles(_gl);
		}
		
		// Render the transparent cuboid vertices.
		// Note that we may want to consider rendering this with _gl.glDepthMask(false) but there doesn't seem to be an
		// ideal blending function to make this look right.  Leaving it read-write seems to produce better results, for
		// now.  In the future, more of the non-opaque blocks will be replaced by complex models.
		// Most likely, we will need to slice every cuboid by which of the 6 faces they include, and sort that way, but
		// this may not work for complex models.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _blockTextures.texture);
		for (Map.Entry<CuboidAddress, CuboidMeshManager.CuboidData> elt : _cuboidMeshes.viewCuboids().entrySet())
		{
			CuboidAddress key = elt.getKey();
			CuboidMeshManager.CuboidData value = elt.getValue();
			if (null != value.transparentArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _uModelMatrix);
				value.transparentArray().drawAllTriangles(_gl);
			}
		}
		
		// Highlight the selected entity or block - prioritize the block since the entity will restrict the block check distance.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _highlightTexture);
		_gl.glDepthFunc(GL20.GL_LEQUAL);
		if (null != selectedBlock)
		{
			Matrix model = Matrix.translate(selectedBlock.x(), selectedBlock.y(), selectedBlock.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			_highlightCube.drawAllTriangles(_gl);
		}
		else if (null != selectedEntity)
		{
			EntityLocation location = selectedEntity.location();
			EntityType type = selectedEntity.type();
			EntityVolume volume = EntityConstants.getVolume(type);
			Matrix translate = Matrix.translate(location.x(), location.y(), location.z());
			Matrix scale = Matrix.scale(volume.width(), volume.width(), volume.height());
			Matrix model = Matrix.mutliply(translate, scale);
			model.uploadAsUniform(_gl, _uModelMatrix);
			_entityData.get(selectedEntity.type()).vertices.drawAllTriangles(_gl);
		}
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		_cuboidMeshes.setCuboid(cuboid);
	}

	public void removeCuboid(CuboidAddress address)
	{
		_cuboidMeshes.removeCuboid(address);
	}

	public void setEntity(PartialEntity entity)
	{
		_entities.put(entity.id(), entity);
	}

	public void removeEntity(int id)
	{
		_entities.remove(id);
	}


	private _EntityData _loadEntityResources(GL20 gl, FloatBuffer meshBuffer, EntityType type) throws IOException
	{
		EntityVolume volume = EntityConstants.getVolume(type);
		String name = type.name();
		FileHandle meshFile = Gdx.files.internal("entity_" + name + ".obj");
		VertexArray buffer;
		float[] ignoredOtherTexture = new float[] { 0.0f, 0.0f };
		if (meshFile.exists())
		{
			String rawMesh = meshFile.readString();
			BufferBuilder builder = new BufferBuilder(meshBuffer, _program.attributes);
			WavefrontReader.readFile((float[] position, float[] texture, float[] normal) -> {
				builder.appendVertex(position
						, normal
						, texture
						, ignoredOtherTexture
				);
			}, rawMesh);
			buffer = builder.finishOne().flush(gl);
		}
		else
		{
			buffer = SceneMeshHelpers.createPrism(_gl, _program.attributes, meshBuffer, new float[] {volume.width(), volume.width(), volume.height()}, _auxBlockTextures);
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

	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static record _EntityData(VertexArray vertices
			, int texture
	) {}
}
