package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.wavefront.WavefrontReader;
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
	private final TextureAtlas<ItemVariant> _itemAtlas;
	private final TextureAtlas<BlockVariant> _blockTextures;
	private final short[] _itemToBlockIndexMapper;
	private final Program _program;
	private final int _uModelMatrix;
	private final int _uViewMatrix;
	private final int _uProjectionMatrix;
	private final int _uWorldLightLocation;
	private final int _uTexture0;
	private final Map<CuboidAddress, _CuboidData> _cuboids;
	private final FloatBuffer _meshBuffer;
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
		
		// Extract the items which are blocks and create the index mapping function so we can pack the block atlas.
		short[] itemToBlockMap = new short[_environment.items.ITEMS_BY_TYPE.length];
		short nextIndex = 0;
		for (int i = 0; i < _environment.items.ITEMS_BY_TYPE.length; ++ i)
		{
			Item item = _environment.items.ITEMS_BY_TYPE[i];
			if (null != _environment.blocks.fromItem(item))
			{
				itemToBlockMap[i] = nextIndex;
				nextIndex += 1;
			}
			else
			{
				itemToBlockMap[i] = -1;
			}
		}
		_itemToBlockIndexMapper = itemToBlockMap;
		
		Block[] blocks = Arrays.stream(_environment.items.ITEMS_BY_TYPE)
				.map((Item item) -> _environment.blocks.fromItem(item))
				.filter((Block block) -> null != block)
				.toArray((int size) -> new Block[size])
		;
		_blockTextures = TextureHelpers.loadAtlasForBlocks(_gl
				, blocks
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
				}
		);
		_uModelMatrix = _program.getUniformLocation("uModelMatrix");
		_uViewMatrix = _program.getUniformLocation("uViewMatrix");
		_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
		_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
		_uTexture0 = _program.getUniformLocation("uTexture0");
		
		_cuboids = new HashMap<>();
		ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
		direct.order(ByteOrder.nativeOrder());
		_meshBuffer = direct.asFloatBuffer();
		_entities = new HashMap<>();
		Map<EntityType, _EntityData> entityData = new HashMap<>();
		for (EntityType type : EntityType.values())
		{
			if (EntityType.ERROR != type)
			{
				_EntityData data = _loadEntityResources(gl, type);
				entityData.put(type, data);
			}
		}
		_entityData = Collections.unmodifiableMap(entityData);
		_highlightTexture = TextureHelpers.loadSinglePixelImageRGBA(_gl, new byte[] {(byte)0xff, (byte)0xff, (byte)0xff, 0x7f});
		_highlightCube = _createPrism(_gl, _program.attributes, _meshBuffer, new float[] {1.0f, 1.0f, 1.0f});
		
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
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _blockTextures.texture);
		for (Map.Entry<CuboidAddress, _CuboidData> elt : _cuboids.entrySet())
		{
			CuboidAddress key = elt.getKey();
			_CuboidData value = elt.getValue();
			if (null != value.opaqueArray)
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _uModelMatrix);
				value.opaqueArray.drawAllTriangles(_gl);
			}
		}
		
		// Render any dropped items.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _itemAtlas.texture);
		for (Map.Entry<CuboidAddress, _CuboidData> elt : _cuboids.entrySet())
		{
			CuboidAddress key = elt.getKey();
			_CuboidData value = elt.getValue();
			if (null != value.itemsOnGroundArray)
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _uModelMatrix);
				value.itemsOnGroundArray.drawAllTriangles(_gl);
			}
		}
		
		// Render any entities.
		for (PartialEntity entity : _entities.values())
		{
			EntityLocation location = entity.location();
			Matrix model = Matrix.translate(location.x(), location.y(), location.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			// In the future, we should change how we do this drawing to avoid so many state changes (either batch by type or combine the types into fewer GL objects).
			_EntityData data = _entityData.get(entity.type());
			_gl.glBindTexture(GL20.GL_TEXTURE_2D, data.texture);
			data.vertices.drawAllTriangles(_gl);
		}
		
		// Render the transparent cuboid vertices.
		// Note that we may want to consider rendering this with _gl.glDepthMask(false) but there doesn't seem to be an
		// ideal blending function to make this look right.  Leaving it read-write seems to produce better results, for
		// now.  In the future, more of the non-opaque blocks will be replaced by complex models.
		// Most likely, we will need to slice every cuboid by which of the 6 faces they include, and sort that way, but
		// this may not work for complex models.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _blockTextures.texture);
		for (Map.Entry<CuboidAddress, _CuboidData> elt : _cuboids.entrySet())
		{
			CuboidAddress key = elt.getKey();
			_CuboidData value = elt.getValue();
			if (null != value.transparentArray)
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _uModelMatrix);
				value.transparentArray.drawAllTriangles(_gl);
			}
		}
		
		// Highlight the selected entity or block - prioritize the block since the entity will restrict the block check distance.
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
			Matrix model = Matrix.translate(location.x(), location.y(), location.z());
			model.uploadAsUniform(_gl, _uModelMatrix);
			_entityData.get(selectedEntity.type()).vertices.drawAllTriangles(_gl);
		}
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		// Delete any previous.
		CuboidAddress address = cuboid.getCuboidAddress();
		_CuboidData previous = _cuboids.remove(address);
		if (null != previous)
		{
			if (null != previous.opaqueArray)
			{
				previous.opaqueArray.delete(_gl);
			}
			if (null != previous.itemsOnGroundArray)
			{
				previous.itemsOnGroundArray.delete(_gl);
			}
			if (null != previous.transparentArray)
			{
				previous.transparentArray.delete(_gl);
			}
		}
		
		// Create the opaque cuboid vertices.
		VertexArray opaqueData = _buildVertexArray(cuboid, true);
		
		// Create the vertex array for any items dropped on the ground.
		VertexArray itemsOnGroundArray = _buildDroppedItemVertexArray(cuboid);
		
		// Create the transparent cuboid vertices.
		VertexArray transparentData = _buildVertexArray(cuboid, false);
		
		if ((null != opaqueData) || (null != itemsOnGroundArray) || (null != transparentData))
		{
			_cuboids.put(address, new _CuboidData(opaqueData, itemsOnGroundArray, transparentData));
		}
	}

	public void removeCuboid(CuboidAddress address)
	{
		_CuboidData removed = _cuboids.remove(address);
		// Note that this will be null if the cuboid was empty.
		if (null != removed)
		{
			if (null != removed.opaqueArray)
			{
				removed.opaqueArray.delete(_gl);
			}
			if (null != removed.itemsOnGroundArray)
			{
				removed.itemsOnGroundArray.delete(_gl);
			}
			if (null != removed.transparentArray)
			{
				removed.transparentArray.delete(_gl);
			}
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


	private _EntityData _loadEntityResources(GL20 gl, EntityType type) throws IOException
	{
		EntityVolume volume = EntityConstants.getVolume(type);
		String name = type.name();
		FileHandle meshFile = Gdx.files.internal("entity_" + name + ".obj");
		VertexArray buffer;
		if (meshFile.exists())
		{
			String rawMesh = meshFile.readString();
			BufferBuilder builder = new BufferBuilder(_meshBuffer, _program.attributes);
			WavefrontReader.readFile((float[] position, float[] texture, float[] normal) -> {
				builder.appendVertex(position
						, normal
						, texture
				);
			}, rawMesh);
			buffer = builder.flush(gl);
		}
		else
		{
			buffer = _createPrism(_gl, _program.attributes, _meshBuffer, new float[] {volume.width(), volume.width(), volume.height()});
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

	private VertexArray _buildVertexArray(IReadOnlyCuboidData cuboid, boolean renderOpaque)
	{
		BufferBuilder builder = new BufferBuilder(_meshBuffer, _program.attributes);
		_populateMeshBufferForCuboid(_environment, builder, _blockTextures, _itemToBlockIndexMapper, cuboid, renderOpaque);
		return builder.flush(_gl);
	}

	private VertexArray _buildDroppedItemVertexArray(IReadOnlyCuboidData cuboid)
	{
		BufferBuilder builder = new BufferBuilder(_meshBuffer, _program.attributes);
		_populateMeshForDroppedItems(_environment, builder, _itemAtlas, cuboid);
		return builder.flush(_gl);
	}

	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}

	private static VertexArray _createPrism(GL20 gl, Attribute[] attributes, FloatBuffer meshBuffer, float[] edgeVertices)
	{
		// This is currently how we render entities so we can use the hard-coded coordinates.
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float textureSize = 1.0f;
		
		BufferBuilder builder = new BufferBuilder(meshBuffer, attributes);
		_drawRectangularPrism(builder
				, new float[] { 0.0f, 0.0f, 0.0f }
				, (byte)1
				, new float[] { 0.0f, 0.0f, 0.0f }
				, edgeVertices
				, new float[][] { uvBase, uvBase, uvBase }
				, textureSize
		);
		return builder.flush(gl);
	}

	private static void _populateMeshBufferForCuboid(Environment env
			, BufferBuilder builder
			, TextureAtlas<BlockVariant> blockAtlas
			, short[] blockIndexMapper
			, IReadOnlyCuboidData cuboid
			, boolean opaqueVertices
	)
	{
		float textureSize = blockAtlas.coordinateSize;
		cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<Short>() {
			@Override
			public void visit(BlockAddress base, byte size, Short value)
			{
				short index = blockIndexMapper[value];
				Assert.assertTrue(index >= 0);
				if (opaqueVertices != blockAtlas.textureHasNonOpaquePixels(index))
				{
					float[] uvBaseTop = blockAtlas.baseOfTexture(index, BlockVariant.TOP);
					float[] uvBaseBottom = blockAtlas.baseOfTexture(index, BlockVariant.BOTTOM);
					float[] uvBaseSide = blockAtlas.baseOfTexture(index, BlockVariant.SIDE);
					_drawCube(builder
							, new float[] { (float)base.x(), (float)base.y(), (float)base.z()}
							, size
							, new float[][] { uvBaseTop, uvBaseBottom, uvBaseSide }
							, textureSize
					);
				}
			}
		}, (short)0);
	}

	private static void _populateMeshForDroppedItems(Environment env
			, BufferBuilder builder
			, TextureAtlas<ItemVariant> itemAtlas
			, IReadOnlyCuboidData cuboid
	)
	{
		float textureSize = itemAtlas.coordinateSize;
		
		// See if there are any inventories in empty blocks in this cuboid.
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
						
						float[] uvBase = itemAtlas.baseOfTexture(type.number(), ItemVariant.NONE);
						float[] offset = DEBRIS_BASES[i];
						float[] debrisBase = new float[] { blockBase[0] + offset[0], blockBase[1] + offset[1], blockBase[2] + offset[2] };
						_drawUpFacingSquare(builder, debrisBase, DEBRIS_ELEMENT_SIZE, uvBase, textureSize);
					}
				}
			}
		}, null);
	}

	private static void _drawUpFacingSquare(BufferBuilder builder, float[] base, float edgeSize, float[] uvBase, float textureSize)
	{
		float[] bottomLeft = new float[] {
				0.0f,
				0.0f,
				0.0f,
		};
		float[] bottomRight = new float[] {
				edgeSize,
				0.0f,
				0.0f,
		};
		float[] topRight = new float[] {
				edgeSize,
				edgeSize,
				0.0f,
		};
		float[] topLeft = new float[] {
				0.0f,
				edgeSize,
				0.0f,
		};
		
		_populateQuad(builder, base, new float[][] {bottomLeft, bottomRight, topRight, topLeft }, new float[] { 0.0f, 0.0f, 1.0f }, uvBase, textureSize);
	}

	// uvBase is 3 pairs:  top, bottom, side.
	private static void _drawCube(BufferBuilder builder, float[] base, byte scale, float[][] uvBase, float textureSize)
	{
		// Note that no matter the scale, the quad vertices are the same magnitudes.
		_drawRectangularPrism(builder
				, base
				, scale
				, new float[] { 0.0f, 0.0f, 0.0f }
				, new float[] { 1.0f, 1.0f, 1.0f }
				, uvBase
				, textureSize
		);
	}

	private static void _drawRectangularPrism(BufferBuilder builder
			, float[] base
			, byte scale
			, float[] prismBase
			, float[] prismEdge
			// There are 3 texture base pairs:  top, bottom, side.
			, float[][] uvBase
			, float textureSize
	)
	{
		// Note that no matter the scale, the quad vertices are the same magnitudes.
		float[] v001 = new float[] { prismBase[0], prismBase[1], prismEdge[2] };
		float[] v101 = new float[] { prismEdge[0], prismBase[1], prismEdge[2] };
		float[] v111 = new float[] { prismEdge[0], prismEdge[1], prismEdge[2] };
		float[] v011 = new float[] { prismBase[0], prismEdge[1], prismEdge[2] };
		float[] v000 = new float[] { prismBase[0], prismBase[1], prismBase[2] };
		float[] v100 = new float[] { prismEdge[0], prismBase[1], prismBase[2] };
		float[] v110 = new float[] { prismEdge[0], prismEdge[1], prismBase[2] };
		float[] v010 = new float[] { prismBase[0], prismEdge[1], prismBase[2] };
		
		// We will fill in each quad by multiple instances, offset by different bases, by tiling along each plane up to scale.
		// We subtract one from the base scale since we would double-count the top "1.0f".
		float baseScale = (float)scale - 1.0f;
		
		// X-normal plane.
		for (byte z = 0; z < scale; ++z)
		{
			float zBase = base[2] + (float)z;
			for (byte y = 0; y < scale; ++y)
			{
				float yBase = base[1] + (float)y;
				float[] localBase = new float[] { base[0], yBase, zBase};
				_populateQuad(builder, localBase, new float[][] {
					v010, v000, v001, v011
				}, new float[] {-1.0f, 0.0f, 0.0f}, uvBase[2], textureSize);
				localBase[0] += baseScale;
				_populateQuad(builder, localBase, new float[][] {
					v111, v101, v100, v110
				}, new float[] {1.0f, 0.0f, 0.0f}, uvBase[2], textureSize);
			}
		}
		// Y-normal plane.
		for (byte z = 0; z < scale; ++z)
		{
			float zBase = base[2] + (float)z;
			for (byte x = 0; x < scale; ++x)
			{
				float xBase = base[0] + (float)x;
				float[] localBase = new float[] { xBase, base[1], zBase};
				_populateQuad(builder, localBase, new float[][] {
					v001, v000, v100, v101
				}, new float[] {0.0f, -1.0f,0.0f}, uvBase[2], textureSize);
				localBase[1] += baseScale;
				_populateQuad(builder, localBase, new float[][] {
					v111, v110, v010, v011
				}, new float[] {0.0f, 1.0f, 0.0f}, uvBase[2], textureSize);
			}
		}
		// Z-normal plane.
		for (byte y = 0; y < scale; ++y)
		{
			float yBase = base[1] + (float)y;
			for (byte x = 0; x < scale; ++x)
			{
				float xBase = base[0] + (float)x;
				float[] localBase = new float[] { xBase, yBase, base[2]};
				_populateQuad(builder, localBase, new float[][] {
					v110, v100, v000, v010
				}, new float[] {0.0f, 0.0f, -1.0f}, uvBase[1], textureSize);
				localBase[2] += baseScale;
				_populateQuad(builder, localBase, new float[][] {
					v011, v001, v101, v111
				}, new float[] {0.0f, 0.0f, 1.0f}, uvBase[0], textureSize);
			}
		}
	}

	private static void _populateQuad(BufferBuilder builder, float[] base, float[][] vertices, float[] normal, float[] uvBase, float textureSize)
	{
		float[] bottomLeft = new float[] {
				base[0] + vertices[0][0],
				base[1] + vertices[0][1],
				base[2] + vertices[0][2],
		};
		float[] bottomRight = new float[] {
				base[0] + vertices[1][0],
				base[1] + vertices[1][1],
				base[2] + vertices[1][2],
		};
		float[] topRight = new float[] {
				base[0] + vertices[2][0],
				base[1] + vertices[2][1],
				base[2] + vertices[2][2],
		};
		float[] topLeft = new float[] {
				base[0] + vertices[3][0],
				base[1] + vertices[3][1],
				base[2] + vertices[3][2],
		};
		float u = uvBase[0];
		float v = uvBase[1];
		float uEdge = u + textureSize;
		float vEdge = v + textureSize;
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
		);
		// Right Bottom.
		builder.appendVertex(bottomRight
				, normal
				, new float[] {uEdge, v}
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
		);
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
		);
		// Left Top.
		builder.appendVertex(topLeft
				, normal
				, new float[] {u, vEdge}
		);
	}

	private static record _CuboidData(VertexArray opaqueArray
			, VertexArray itemsOnGroundArray
			, VertexArray transparentArray
	) {}

	private static record _EntityData(VertexArray vertices
			, int texture
	) {}
}
