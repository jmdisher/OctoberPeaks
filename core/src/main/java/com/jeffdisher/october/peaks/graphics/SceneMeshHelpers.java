package com.jeffdisher.october.peaks.graphics;

import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.BlockVariant;
import com.jeffdisher.october.peaks.ItemVariant;
import com.jeffdisher.october.peaks.SparseShortProjection;
import com.jeffdisher.october.peaks.TextureAtlas;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.Items;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


public class SceneMeshHelpers
{
	public static final float DEBRIS_ELEMENT_SIZE = 0.5f;
	public static final float[][] DEBRIS_BASES = new float[][] {
		new float[] { 0.1f, 0.1f, 0.05f }
		, new float[] { 0.4f, 0.4f, 0.1f }
		, new float[] { 0.2f, 0.3f, 0.15f }
	};

	public static SparseShortProjection<SceneMeshHelpers.AuxVariant> buildAuxProjection(Environment env, IReadOnlyCuboidData cuboid)
	{
		SparseShortProjection<SceneMeshHelpers.AuxVariant> variantProjection = SparseShortProjection.fromAspect(cuboid, AspectRegistry.DAMAGE, (short)0, SceneMeshHelpers.AuxVariant.NONE, (BlockAddress blockAddress, Short value) -> {
			short damage = value;
			// We will favour showing cracks at a low damage, so the feedback is obvious
			Block block = new BlockProxy(blockAddress, cuboid).getBlock();
			float damaged = (float) damage / (float)env.damage.getToughness(block);
			
			SceneMeshHelpers.AuxVariant aux;
			if (damaged > 0.6f)
			{
				aux = SceneMeshHelpers.AuxVariant.BREAK_HIGH;
			}
			else if (damaged > 0.3f)
			{
				aux = SceneMeshHelpers.AuxVariant.BREAK_MEDIUM;
			}
			else
			{
				aux = SceneMeshHelpers.AuxVariant.BREAK_LOW;
			}
			return aux;
		});
		return variantProjection;
	}

	public static void populateMeshBufferForCuboid(Environment env
			, BufferBuilder builder
			, TextureAtlas<BlockVariant> blockAtlas
			, SparseShortProjection<AuxVariant> projection
			, TextureAtlas<AuxVariant> auxAtlas
			, short[] blockIndexMapper
			, IReadOnlyCuboidData cuboid
			, IReadOnlyCuboidData otherUp
			, IReadOnlyCuboidData otherDown
			, IReadOnlyCuboidData otherNorth
			, IReadOnlyCuboidData otherSouth
			, IReadOnlyCuboidData otherEast
			, IReadOnlyCuboidData otherWest
			, boolean opaqueVertices
	)
	{
		Predicate<Short> shouldInclude;
		if (opaqueVertices)
		{
			shouldInclude = (Short value) -> {
				short index = blockIndexMapper[value];
				return !blockAtlas.textureHasNonOpaquePixels(index);
			};
		}
		else
		{
			Set<Short> water = _buildWaterNumberSet(env);
			shouldInclude = (Short value) -> {
				short index = blockIndexMapper[value];
				return blockAtlas.textureHasNonOpaquePixels(index)
						&& !water.contains(value)
				;
			};
		}
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
				, shouldInclude
				, otherUp
				, otherDown
				, otherNorth
				, otherSouth
				, otherEast
				, otherWest
		);
		faces.populateMasks(cuboid, shouldInclude);
		faces.buildFaces(cuboid, new _CommonVertexWriter(builder
				, projection
				, blockIndexMapper
				, blockAtlas
				, auxAtlas
				, shouldInclude
				, 1.0f
		));
	}

	public static void populateWaterMeshBufferForCuboid(Environment env
			, BufferBuilder builder
			, TextureAtlas<BlockVariant> blockAtlas
			, SparseShortProjection<AuxVariant> projection
			, TextureAtlas<AuxVariant> auxAtlas
			, short[] blockIndexMapper
			, IReadOnlyCuboidData cuboid
			, IReadOnlyCuboidData otherUp
			, IReadOnlyCuboidData otherDown
			, IReadOnlyCuboidData otherNorth
			, IReadOnlyCuboidData otherSouth
			, IReadOnlyCuboidData otherEast
			, IReadOnlyCuboidData otherWest
	)
	{
		Set<Short> water = _buildWaterNumberSet(env);
		Predicate<Short> shouldInclude = (Short value) -> {
			return water.contains(value);
		};
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
				, shouldInclude
				, otherUp
				, otherDown
				, otherNorth
				, otherSouth
				, otherEast
				, otherWest
		);
		faces.populateMasks(cuboid, shouldInclude);
		faces.buildFaces(cuboid, new _CommonVertexWriter(builder
				, projection
				, blockIndexMapper
				, blockAtlas
				, auxAtlas
				, shouldInclude
				, 0.9f
		));
	}

	public static void populateMeshForDroppedItems(Environment env
			, BufferBuilder builder
			, TextureAtlas<ItemVariant> itemAtlas
			, TextureAtlas<AuxVariant> auxAtlas
			, IReadOnlyCuboidData cuboid
	)
	{
		float textureSize = itemAtlas.coordinateSize;
		
		// Dropped items will never have an aux texture.
		float[] auxUv = auxAtlas.baseOfTexture((short)0, AuxVariant.NONE);
		float auxTextureSize = auxAtlas.coordinateSize;
		
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
						_drawUpFacingSquare(builder, debrisBase, DEBRIS_ELEMENT_SIZE
								, uvBase, textureSize
								, auxUv, auxTextureSize
						);
					}
				}
			}
		}, null);
	}

	public static VertexArray createPrism(GL20 gl
			, Attribute[] attributes
			, FloatBuffer meshBuffer
			, float[] edgeVertices
			, TextureAtlas<AuxVariant> auxAtlas
	)
	{
		// This is currently how we render entities so we can use the hard-coded coordinates.
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float textureSize = 1.0f;
		
		// We will use no AUX texture.
		float[] auxUv = auxAtlas.baseOfTexture((short)0, AuxVariant.NONE);
		float auxTextureSize = auxAtlas.coordinateSize;
		
		BufferBuilder builder = new BufferBuilder(meshBuffer, attributes);
		float[] base = new float[] { 0.0f, 0.0f, 0.0f };
		
		// Note that no matter the scale, the quad vertices are the same magnitudes.
		_PrismVertices v = _PrismVertices.from(base, edgeVertices);
		
		// X-normal plane.
		_populateQuad(builder, base, new float[][] {
				v.v010, v.v000, v.v001, v.v011
			}, new float[] {-1.0f, 0.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
		);
		_populateQuad(builder, base, new float[][] {
				v.v100, v.v110, v.v111, v.v101
			}, new float[] {1.0f, 0.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
		);
		
		// Y-normal plane.
		_populateQuad(builder, base, new float[][] {
				v.v000, v.v100, v.v101, v.v001
			}, new float[] {0.0f, -1.0f,0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
		);
		_populateQuad(builder, base, new float[][] {
				v.v110, v.v010, v.v011, v.v111
			}, new float[] {0.0f, 1.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
		);
		
		// Z-normal plane.
		// Note that the Z-normal creates surfaces parallel to the ground so we will define "up" as "positive y".
		_populateQuad(builder, base, new float[][] {
				v.v100, v.v000, v.v010, v.v110
			}, new float[] {0.0f, 0.0f, -1.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
		);
		_populateQuad(builder, base, new float[][] {
				v.v001, v.v101, v.v111, v.v011
			}, new float[] {0.0f, 0.0f, 1.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
		);
		
		return builder.finishOne().flush(gl);
	}


	private static void _preSeed(FaceBuilder faces
			, Predicate<Short> shouldInclude
			, IReadOnlyCuboidData otherUp
			, IReadOnlyCuboidData otherDown
			, IReadOnlyCuboidData otherNorth
			, IReadOnlyCuboidData otherSouth
			, IReadOnlyCuboidData otherEast
			, IReadOnlyCuboidData otherWest)
	{
		byte omit = -1;
		byte zero = 0;
		byte edge = Encoding.CUBOID_EDGE_SIZE;
		if (null != otherUp)
		{
			faces.preSeedMasks(otherUp, shouldInclude, zero, omit, omit, omit, omit, omit);
		}
		if (null != otherDown)
		{
			faces.preSeedMasks(otherDown, shouldInclude, omit, edge, omit, omit, omit, omit);
		}
		if (null != otherNorth)
		{
			faces.preSeedMasks(otherNorth, shouldInclude, omit, omit, zero, omit, omit, omit);
		}
		if (null != otherSouth)
		{
			faces.preSeedMasks(otherSouth, shouldInclude, omit, omit, omit, edge, omit, omit);
		}
		if (null != otherEast)
		{
			faces.preSeedMasks(otherEast, shouldInclude, omit, omit, omit, omit, zero, omit);
		}
		if (null != otherWest)
		{
			faces.preSeedMasks(otherWest, shouldInclude, omit, omit, omit, omit, omit, edge);
		}
	}

	private static void _drawUpFacingSquare(BufferBuilder builder
			, float[] base
			, float edgeSize
			, float[] uvBase
			, float textureSize
			, float[] otherUvBase
			, float otherTextureSize
	)
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
		
		_populateQuad(builder, base
				, new float[][] {bottomLeft, bottomRight, topRight, topLeft }
				, new float[] { 0.0f, 0.0f, 1.0f }
				, uvBase, textureSize
				, otherUvBase, otherTextureSize
		);
	}

	private static void _populateQuad(BufferBuilder builder
			, float[] base
			, float[][] vertices
			, float[] normal
			, float[] uvBase
			, float textureSize
			, float[] otherUvBase
			, float otherTextureSize
	)
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
		
		float otherU = otherUvBase[0];
		float otherV = otherUvBase[1];
		float otherUEdge = otherU + otherTextureSize;
		float otherVEdge = otherV + otherTextureSize;
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// otherU, otherV
		
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
				, new float[] {otherU, otherV}
		);
		// Right Bottom.
		builder.appendVertex(bottomRight
				, normal
				, new float[] {uEdge, v}
				, new float[] {otherUEdge, otherV}
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
				, new float[] {otherUEdge, otherVEdge}
		);
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
				, new float[] {otherU, otherV}
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
				, new float[] {otherUEdge, otherVEdge}
		);
		// Left Top.
		builder.appendVertex(topLeft
				, normal
				, new float[] {u, vEdge}
				, new float[] {otherU, otherVEdge}
		);
	}

	private static Set<Short> _buildWaterNumberSet(Environment env)
	{
		Set<Short> water = Set.of(env.items.getItemById("op.water_source").number()
				, env.items.getItemById("op.water_strong").number()
				, env.items.getItemById("op.water_weak").number()
		);
		return water;
	}


	public static enum AuxVariant
	{
		NONE,
		BREAK_LOW,
		BREAK_MEDIUM,
		BREAK_HIGH,
	}

	private static record _PrismVertices(float[] v001
			, float[] v101
			, float[] v111
			, float[] v011
			, float[] v000
			, float[] v100
			, float[] v110
			, float[] v010
	)
	{
		public static _PrismVertices from(float[] prismBase, float[] prismEdge)
		{
			float[] v001 = new float[] { prismBase[0], prismBase[1], prismEdge[2] };
			float[] v101 = new float[] { prismEdge[0], prismBase[1], prismEdge[2] };
			float[] v111 = new float[] { prismEdge[0], prismEdge[1], prismEdge[2] };
			float[] v011 = new float[] { prismBase[0], prismEdge[1], prismEdge[2] };
			float[] v000 = new float[] { prismBase[0], prismBase[1], prismBase[2] };
			float[] v100 = new float[] { prismEdge[0], prismBase[1], prismBase[2] };
			float[] v110 = new float[] { prismEdge[0], prismEdge[1], prismBase[2] };
			float[] v010 = new float[] { prismBase[0], prismEdge[1], prismBase[2] };
			return new _PrismVertices(v001, v101, v111, v011, v000, v100, v110, v010);
		}
	}

	private static class _CommonVertexWriter implements FaceBuilder.IWriter
	{
		private final BufferBuilder _builder;
		private final SparseShortProjection<AuxVariant> _projection;
		private final short[] _blockIndexMapper;
		private final TextureAtlas<BlockVariant> _blockAtlas;
		private final TextureAtlas<AuxVariant> _auxAtlas;
		private final Predicate<Short> _shouldInclude;
		private final _PrismVertices _v;
		
		public _CommonVertexWriter(BufferBuilder builder
				, SparseShortProjection<AuxVariant> projection
				, short[] blockIndexMapper
				, TextureAtlas<BlockVariant> blockAtlas
				, TextureAtlas<AuxVariant> auxAtlas
				, Predicate<Short> shouldInclude
				, float blockHeight
		)
		{
			_builder = builder;
			_projection = projection;
			_blockIndexMapper = blockIndexMapper;
			_blockAtlas = blockAtlas;
			_auxAtlas = auxAtlas;
			_shouldInclude = shouldInclude;
			_v = _PrismVertices.from(new float[] { 0.0f, 0.0f, 0.0f }, new float[] { 1.0f, 1.0f, blockHeight });
		}
		@Override
		public boolean shouldInclude(short value)
		{
			return _shouldInclude.test(value);
		}
		@Override
		public void writeXYPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			// Note that the Z-normal creates surfaces parallel to the ground so we will define "up" as "positive y".
			short index = _blockIndexMapper[value];
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			float[] uvBaseTop = _blockAtlas.baseOfTexture(index, BlockVariant.TOP);
			float[] uvBaseBottom = _blockAtlas.baseOfTexture(index, BlockVariant.BOTTOM);
			float[] auxUv = _auxAtlas.baseOfTexture((short)0, _projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				_populateQuad(_builder, localBase, new float[][] {
						_v.v001, _v.v101, _v.v111, _v.v011
					}, new float[] {0.0f, 0.0f, 1.0f}
					, uvBaseTop, _blockAtlas.coordinateSize
					, auxUv, _auxAtlas.coordinateSize
				);
			}
			else
			{
				_populateQuad(_builder, localBase, new float[][] {
						_v.v100, _v.v000, _v.v010, _v.v110
					}, new float[] {0.0f, 0.0f, -1.0f}
					, uvBaseBottom, _blockAtlas.coordinateSize
					, auxUv, _auxAtlas.coordinateSize
				);
			}
		}
		@Override
		public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			short index = _blockIndexMapper[value];
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			float[] uvBaseSide = _blockAtlas.baseOfTexture(index, BlockVariant.SIDE);
			float[] auxUv = _auxAtlas.baseOfTexture((short)0, _projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				_populateQuad(_builder, localBase, new float[][] {
						_v.v110, _v.v010, _v.v011, _v.v111
					}, new float[] {0.0f, 1.0f, 0.0f}
					, uvBaseSide, _blockAtlas.coordinateSize
					, auxUv, _auxAtlas.coordinateSize
				);
			}
			else
			{
				_populateQuad(_builder, localBase, new float[][] {
						_v.v000, _v.v100, _v.v101, _v.v001
					}, new float[] {0.0f, -1.0f,0.0f}
					, uvBaseSide, _blockAtlas.coordinateSize
					, auxUv, _auxAtlas.coordinateSize
				);
			}
		}
		@Override
		public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			short index = _blockIndexMapper[value];
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			float[] uvBaseSide = _blockAtlas.baseOfTexture(index, BlockVariant.SIDE);
			float[] auxUv = _auxAtlas.baseOfTexture((short)0, _projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				_populateQuad(_builder, localBase, new float[][] {
						_v.v100, _v.v110, _v.v111, _v.v101
					}, new float[] {1.0f, 0.0f, 0.0f}
					, uvBaseSide, _blockAtlas.coordinateSize
					, auxUv, _auxAtlas.coordinateSize
				);
			}
			else
			{
				_populateQuad(_builder, localBase, new float[][] {
						_v.v010, _v.v000, _v.v001, _v.v011
					}, new float[] {-1.0f, 0.0f, 0.0f}
					, uvBaseSide, _blockAtlas.coordinateSize
					, auxUv, _auxAtlas.coordinateSize
				);
			}
		}
	}
}
