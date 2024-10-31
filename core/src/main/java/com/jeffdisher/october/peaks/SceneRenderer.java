package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityConstants;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.EntityVolume;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the OpenGL scene of the world (does not include UI elements, windows, etc).
 */
public class SceneRenderer
{
	public static final int BUFFER_SIZE = 64 * 1024 * 1024;
	public static final float DEBRIS_ELEMENT_SIZE = 0.5f;
	public static final float[][] DEBRIS_BASES = new float[][] {
		new float[] { 0.1f, 0.1f, 0.05f }
		, new float[] { 0.4f, 0.4f, 0.1f }
		, new float[] { 0.2f, 0.3f, 0.15f }
	};

	private final Environment _environment;
	private final GL20 _gl;
	private final TextureAtlas _itemAtlas;
	private final Program _program;
	private final int _uModelMatrix;
	private final int _uViewMatrix;
	private final int _uProjectionMatrix;
	private final int _uWorldLightLocation;
	private final int _uTexture0;
	private final int _entityTexture;
	private final Map<CuboidAddress, _CuboidData> _cuboids;
	private final FloatBuffer _meshBuffer;
	private final Map<Integer, PartialEntity> _entities;
	private final Map<EntityType, Integer> _entityMeshes;
	private final int _highlightTexture;
	private final int _highlightCube;

	private Matrix _viewMatrix;
	private final Matrix _projectionMatrix;
	private Vector _eye;

	public SceneRenderer(Environment environment, GL20 gl, TextureAtlas itemAtlas) throws IOException
	{
		_environment = environment;
		_gl = gl;
		_itemAtlas = itemAtlas;
		
		// Create the shader program.
		_program = Program.fullyLinkedProgram(_gl
				, _readUtf8Asset("scene.vert")
				, _readUtf8Asset("scene.frag")
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
		
		_entityTexture = GraphicsHelpers.loadInternalRGBA(_gl, "missing_texture.png");
		_cuboids = new HashMap<>();
		ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
		direct.order(ByteOrder.nativeOrder());
		_meshBuffer = direct.asFloatBuffer();
		_entities = new HashMap<>();
		Map<EntityType, Integer> entityMeshes = new HashMap<>();
		for (EntityType type : EntityType.values())
		{
			if (EntityType.ERROR != type)
			{
				EntityVolume volume = EntityConstants.getVolume(type);
				int buffer = _createPrism(_gl, _meshBuffer, new float[] {volume.width(), volume.width(), volume.height()});
				entityMeshes.put(type, buffer);
			}
		}
		_entityMeshes = Collections.unmodifiableMap(entityMeshes);
		_highlightTexture = GraphicsHelpers.loadSinglePixelImageRGBA(_gl, new byte[] {(byte)0xff, (byte)0xff, (byte)0xff, 0x7f});
		_highlightCube = _createPrism(_gl, _meshBuffer, new float[] {1.0f, 1.0f, 1.0f});
		
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
		_gl.glUniform1i(_uTexture0, 0);
		_gl.glUniform3f(_uWorldLightLocation, _eye.x(), _eye.y(), _eye.z());
		_viewMatrix.uploadAsUniform(_gl, _uViewMatrix);
		_projectionMatrix.uploadAsUniform(_gl, _uProjectionMatrix);
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We currently only use texture0.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		
		// Render the opaque cuboid vertices.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _itemAtlas.texture);
		for (Map.Entry<CuboidAddress, _CuboidData> elt : _cuboids.entrySet())
		{
			CuboidAddress key = elt.getKey();
			_CuboidData value = elt.getValue();
			Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			GraphicsHelpers.renderStandardArray(_gl, value.opaqueArray, value.opaqueVertexCount);
		}
		
		// Render any entities.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _entityTexture);
		for (PartialEntity entity : _entities.values())
		{
			EntityLocation location = entity.location();
			Matrix model = Matrix.translate(location.x(), location.y(), location.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			GraphicsHelpers.renderStandardArray(_gl, _entityMeshes.get(entity.type()), GraphicsHelpers.RECTANGULAR_PRISM_VERTEX_COUNT);
		}
		
		// Render the transparent cuboid vertices.
		// Note that we may want to consider rendering this with _gl.glDepthMask(false) but there doesn't seem to be an
		// ideal blending function to make this look right.  Leaving it read-write seems to produce better results, for
		// now.  In the future, more of the non-opaque blocks will be replaced by complex models.
		// Most likely, we will need to slice every cuboid by which of the 6 faces they include, and sort that way, but
		// this may not work for complex models.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _itemAtlas.texture);
		for (Map.Entry<CuboidAddress, _CuboidData> elt : _cuboids.entrySet())
		{
			CuboidAddress key = elt.getKey();
			_CuboidData value = elt.getValue();
			Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			GraphicsHelpers.renderStandardArray(_gl, value.transparentArray, value.transparentVertexCount);
		}
		
		// Highlight the selected entity or block - prioritize the block since the entity will restrict the block check distance.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _highlightTexture);
		_gl.glDepthFunc(GL20.GL_LEQUAL);
		if (null != selectedBlock)
		{
			Matrix model = Matrix.translate(selectedBlock.x(), selectedBlock.y(), selectedBlock.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			GraphicsHelpers.renderStandardArray(_gl, _highlightCube, GraphicsHelpers.RECTANGULAR_PRISM_VERTEX_COUNT);
		}
		else if (null != selectedEntity)
		{
			EntityLocation location = selectedEntity.location();
			Matrix model = Matrix.translate(location.x(), location.y(), location.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			GraphicsHelpers.renderStandardArray(_gl, _entityMeshes.get(selectedEntity.type()), GraphicsHelpers.RECTANGULAR_PRISM_VERTEX_COUNT);
		}
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		// Delete any previous.
		CuboidAddress address = cuboid.getCuboidAddress();
		_CuboidData previous = _cuboids.remove(address);
		if (null != previous)
		{
			_gl.glDeleteBuffer(previous.opaqueArray);
			_gl.glDeleteBuffer(previous.transparentArray);
		}
		
		// Create the opaque cuboid vertices.
		int[] opaqueData = _buildVertexArray(cuboid, true);
		
		// Create the transparent cuboid vertices.
		int[] transparentData = _buildVertexArray(cuboid, false);
		
		if ((opaqueData[0] > 0) || (transparentData[0] > 0))
		{
			_cuboids.put(address, new _CuboidData(opaqueData[0], opaqueData[1], transparentData[0], transparentData[1]));
		}
	}

	public void removeCuboid(CuboidAddress address)
	{
		_CuboidData removed = _cuboids.remove(address);
		// Note that this will be null if the cuboid was empty.
		if (null != removed)
		{
			_gl.glDeleteBuffer(removed.opaqueArray);
			_gl.glDeleteBuffer(removed.transparentArray);
			Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		}
	}

	public void setEntity(PartialEntity entity)
	{
		_entities.put(entity.id(), entity);
	}

	public void removeEntity(int id)
	{
		_entities.remove(id);
	}


	private int[] _buildVertexArray(IReadOnlyCuboidData cuboid, boolean renderOpaque)
	{
		_meshBuffer.clear();
		int vertexCount = _populateMeshBufferForCuboid(_environment, _meshBuffer, _itemAtlas, cuboid, renderOpaque);
		_meshBuffer.flip();
		// We only bother building a buffer is it will have some contents.
		int vertices = 0;
		if (_meshBuffer.hasRemaining())
		{
			vertices = _gl.glGenBuffer();
			Assert.assertTrue(vertices > 0);
			_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vertices);
			// WARNING:  Since we are using a FloatBuffer in glBufferData, the size is ignored and only remaining is considered.
			_gl.glBufferData(GL20.GL_ARRAY_BUFFER, 0, _meshBuffer, GL20.GL_STATIC_DRAW);
		}
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		return new int[] { vertices, vertexCount };
	}

	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static int _createPrism(GL20 gl, FloatBuffer meshBuffer, float[] edgeVertices)
	{
		// This is currently how we render entities so we can use the hard-coded coordinates.
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float textureSize = 1.0f;
		
		int buffer = gl.glGenBuffer();
		Assert.assertTrue(buffer > 0);
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
		meshBuffer.clear();
		GraphicsHelpers.drawRectangularPrism(meshBuffer, edgeVertices, uvBase, textureSize);
		meshBuffer.flip();
		// WARNING:  Since we are using a FloatBuffer in glBufferData, the size is ignored and only remaining is considered.
		gl.glBufferData(GL20.GL_ARRAY_BUFFER, 0, meshBuffer, GL20.GL_STATIC_DRAW);
		Assert.assertTrue(GL20.GL_NO_ERROR == gl.glGetError());
		return buffer;
	}

	private static int _populateMeshBufferForCuboid(Environment env
			, FloatBuffer meshBuffer
			, TextureAtlas blockAtlas
			, IReadOnlyCuboidData cuboid
			, boolean opaqueVertices
	)
	{
		float textureSize = blockAtlas.coordinateSize;
		cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<Short>() {
			@Override
			public void visit(BlockAddress base, byte size, Short value)
			{
				if (opaqueVertices != blockAtlas.textureHasNonOpaquePixels(value))
				{
					float[] uvBase = blockAtlas.baseOfTexture(value);
					GraphicsHelpers.drawCube(meshBuffer
							, new float[] { (float)base.x(), (float)base.y(), (float)base.z()}
							, size
							, uvBase
							, textureSize
					);
				}
			}
		}, (short)0);
		
		// See if there are any inventories in empty blocks in this cuboid.
		if (opaqueVertices)
		{
			cuboid.walkData(AspectRegistry.INVENTORY, new IOctree.IWalkerCallback<Inventory>() {
				@Override
				public void visit(BlockAddress base, byte size, Inventory blockInventory)
				{
					Assert.assertTrue((byte)1 == size);
					BlockProxy proxy = new BlockProxy(base, cuboid);
					Block blockType = proxy.getBlock();
					boolean blockPermitsEntityMovement = !env.blocks.isSolid(blockType);
					if (blockPermitsEntityMovement)
					{
						float[] blockBase = new float[] { (float) base.x(), (float) base.y(), (float) base.z() };
						Iterator<Integer> sortedKeys = blockInventory.sortedKeys().iterator();
						for (int i = 0; (i < DEBRIS_BASES.length) && sortedKeys.hasNext(); ++i)
						{
							int key = sortedKeys.next();
							Items stack = blockInventory.getStackForKey(key);
							Item type = (null != stack)
									? stack.type()
									: blockInventory.getNonStackableForKey(key).type()
							;
							
							float[] uvBase = blockAtlas.baseOfTexture(type.number());
							float[] offset = DEBRIS_BASES[i];
							float[] debrisBase = new float[] { blockBase[0] + offset[0], blockBase[1] + offset[1], blockBase[2] + offset[2] };
							GraphicsHelpers.drawUpFacingSquare(meshBuffer, debrisBase, DEBRIS_ELEMENT_SIZE, uvBase, textureSize);
						}
					}
				}
			}, null);
		}
		
		// Note that the position() in a FloatBuffer is in units of floats.
		int vertexCount = meshBuffer.position() / GraphicsHelpers.FLOATS_PER_VERTEX;
		return vertexCount;
	}

	private static record _CuboidData(int opaqueArray
			, int opaqueVertexCount
			, int transparentArray
			, int transparentVertexCount
	) {}
}
