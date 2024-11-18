package com.jeffdisher.october.peaks.graphics;

import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Predicate;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.BasicBlockAtlas;
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
	/**
	 * The light value we will see for block light in the case of "total darkness".  Actual block light is added on top
	 * of this.
	 */
	public static final float MINIMUM_LIGHT = 0.1f;
	public static final float SKY_LIGHT_SHADOW = 0.0f;
	public static final float SKY_LIGHT_PARTIAL = 0.5f;
	public static final float SKY_LIGHT_DIRECT = 1.0f;
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
			, BasicBlockAtlas blockAtlas
			, SparseShortProjection<AuxVariant> projection
			, TextureAtlas<AuxVariant> auxAtlas
			, MeshInputData inputData
			, boolean opaqueVertices
	)
	{
		Predicate<Short> shouldInclude;
		if (opaqueVertices)
		{
			shouldInclude = (Short value) -> {
				return !blockAtlas.textureHasNonOpaquePixels(value);
			};
		}
		else
		{
			Set<Short> water = _buildWaterNumberSet(env);
			shouldInclude = (Short value) -> {
				return blockAtlas.textureHasNonOpaquePixels(value)
						&& !water.contains(value)
				;
			};
		}
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
				, shouldInclude
				, inputData
		);
		faces.populateMasks(inputData.cuboid, shouldInclude);
		faces.buildFaces(inputData.cuboid, new _CommonVertexWriter(builder
				, projection
				, blockAtlas
				, auxAtlas
				, shouldInclude
				, inputData
				, 1.0f
		));
	}

	public static void populateWaterMeshBufferForCuboid(Environment env
			, BufferBuilder builder
			, BasicBlockAtlas blockAtlas
			, SparseShortProjection<AuxVariant> projection
			, TextureAtlas<AuxVariant> auxAtlas
			, MeshInputData inputData
	)
	{
		// In this case, we need to configure a WaterSurfaceBuilder since water's surface depends on the strength of flow.
		short sourceNumber = env.items.getItemById("op.water_source").number();
		short strongNumber = env.items.getItemById("op.water_strong").number();
		short weakNumber = env.items.getItemById("op.water_weak").number();
		Set<Short> water = Set.of(sourceNumber
				, strongNumber
				, weakNumber
		);
		Predicate<Short> shouldInclude = (Short value) -> {
			return water.contains(value);
		};
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
				, shouldInclude
				, inputData
		);
		faces.populateMasks(inputData.cuboid, shouldInclude);
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder(shouldInclude, sourceNumber, strongNumber, weakNumber);
		faces.buildFaces(inputData.cuboid, surface);
		
		// For now, just use the same image for all faces.
		float[] uvBase = blockAtlas.baseOfTopTexture(sourceNumber);
		float textureSize = blockAtlas.getCoordinateSize();
		float[] auxUv = auxAtlas.baseOfTexture((short)0, AuxVariant.NONE);
		float auxTextureSize = auxAtlas.coordinateSize;
		
		surface.writeVertices(new WaterSurfaceBuilder.IQuadWriter() {
			float[] _base = new float[] {0.0f, 0.0f, 0.0f};
			@Override
			public void writeQuad(BlockAddress address, BlockAddress externalBlock, float[][] counterClockWiseVertices, float[] normal)
			{
				// We want to check the opacity since we won't draw the internal faces of the water if there is something opaque on the other side.
				if (!_isBlockOpaque(env, inputData, externalBlock))
				{
					float blockLightMultiplier = _mapBlockLight(_getBlockLight(inputData, externalBlock.x(), externalBlock.y(), externalBlock.z()));
					float skyLightMultiplier = _getSkyLightMultiplier(inputData, externalBlock.x(), externalBlock.y(), externalBlock.z(), SKY_LIGHT_DIRECT);
					
					_populateQuad(builder
							, _base
							, counterClockWiseVertices
							, normal
							, uvBase
							, textureSize
							, auxUv
							, auxTextureSize
							, blockLightMultiplier
							, skyLightMultiplier
					);
					
					// We want to draw the quad on the outside and inside of the water (in case you are looking out).
					// We may want a different texture for the "looking out", later.
					float[][] reverseVertices = new float[][] {
						counterClockWiseVertices[3],
						counterClockWiseVertices[2],
						counterClockWiseVertices[1],
						counterClockWiseVertices[0],
					};
					float[] reverseNormal = new float[] {
							-1.0f * normal[0],
							-1.0f * normal[1],
							-1.0f * normal[2],
					};
					_populateQuad(builder
							, _base
							, reverseVertices
							, reverseNormal
							, uvBase
							, textureSize
							, auxUv
							, auxTextureSize
							, blockLightMultiplier
							, skyLightMultiplier
					);
				}
			}
		});
	}

	public static void populateMeshForDroppedItems(Environment env
			, BufferBuilder builder
			, TextureAtlas<ItemVariant> itemAtlas
			, TextureAtlas<AuxVariant> auxAtlas
			, IReadOnlyCuboidData cuboid
			, ColumnHeightMap heightMap
	)
	{
		float textureSize = itemAtlas.coordinateSize;
		
		// Dropped items will never have an aux texture.
		float[] auxUv = auxAtlas.baseOfTexture((short)0, AuxVariant.NONE);
		float auxTextureSize = auxAtlas.coordinateSize;
		
		int cuboidZ = cuboid.getCuboidAddress().getBase().z();
		
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
						byte light = cuboid.getData7(AspectRegistry.LIGHT, base);
						float blockLightMultiplier = _mapBlockLight(light);
						float skyLightMultiplier = ((base.z() + cuboidZ - 1) == heightMap.getHeight(base.x(), base.y())) ? 1.0f : 0.0f;
						_drawUpFacingSquare(builder, debrisBase, DEBRIS_ELEMENT_SIZE
								, uvBase, textureSize
								, auxUv, auxTextureSize
								, blockLightMultiplier
								, skyLightMultiplier
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
		float blockLightMultiplier = 1.0f;
		float skyLightMultiplier = 0.0f;
		
		// X-normal plane.
		_populateQuad(builder, base, new float[][] {
				v.v010, v.v000, v.v001, v.v011
			}, new float[] {-1.0f, 0.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultiplier
			, skyLightMultiplier
		);
		_populateQuad(builder, base, new float[][] {
				v.v100, v.v110, v.v111, v.v101
			}, new float[] {1.0f, 0.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultiplier
			, skyLightMultiplier
		);
		
		// Y-normal plane.
		_populateQuad(builder, base, new float[][] {
				v.v000, v.v100, v.v101, v.v001
			}, new float[] {0.0f, -1.0f,0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultiplier
			, skyLightMultiplier
		);
		_populateQuad(builder, base, new float[][] {
				v.v110, v.v010, v.v011, v.v111
			}, new float[] {0.0f, 1.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultiplier
			, skyLightMultiplier
		);
		
		// Z-normal plane.
		// Note that the Z-normal creates surfaces parallel to the ground so we will define "up" as "positive y".
		_populateQuad(builder, base, new float[][] {
				v.v100, v.v000, v.v010, v.v110
			}, new float[] {0.0f, 0.0f, -1.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultiplier
			, skyLightMultiplier
		);
		_populateQuad(builder, base, new float[][] {
				v.v001, v.v101, v.v111, v.v011
			}, new float[] {0.0f, 0.0f, 1.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultiplier
			, skyLightMultiplier
		);
		
		return builder.finishOne().flush(gl);
	}


	private static void _preSeed(FaceBuilder faces
			, Predicate<Short> shouldInclude
			, MeshInputData inputData
	)
	{
		byte omit = -1;
		byte zero = 0;
		byte edge = Encoding.CUBOID_EDGE_SIZE;
		if (null != inputData.up)
		{
			faces.preSeedMasks(inputData.up, shouldInclude, zero, omit, omit, omit, omit, omit);
		}
		if (null != inputData.down)
		{
			faces.preSeedMasks(inputData.down, shouldInclude, omit, edge, omit, omit, omit, omit);
		}
		if (null != inputData.north)
		{
			faces.preSeedMasks(inputData.north, shouldInclude, omit, omit, zero, omit, omit, omit);
		}
		if (null != inputData.south)
		{
			faces.preSeedMasks(inputData.south, shouldInclude, omit, omit, omit, edge, omit, omit);
		}
		if (null != inputData.east)
		{
			faces.preSeedMasks(inputData.east, shouldInclude, omit, omit, omit, omit, zero, omit);
		}
		if (null != inputData.west)
		{
			faces.preSeedMasks(inputData.west, shouldInclude, omit, omit, omit, omit, omit, edge);
		}
	}

	private static void _drawUpFacingSquare(BufferBuilder builder
			, float[] base
			, float edgeSize
			, float[] uvBase
			, float textureSize
			, float[] otherUvBase
			, float otherTextureSize
			, float blockLightMultiplier
			, float skyLightMultiplier
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
				, blockLightMultiplier
				, skyLightMultiplier
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
			, float blockLightMultiplier
			, float skyLightMultiplier
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
		float[] blockLight = new float[] { blockLightMultiplier };
		float[] skyLight = new float[] { skyLightMultiplier };
		
		// Each element is:
		// vx, vy, vz
		// nx, ny, nz
		// u, v
		// otherU, otherV
		// blockLight
		
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
				, new float[] {otherU, otherV}
				, blockLight
				, skyLight
		);
		// Right Bottom.
		builder.appendVertex(bottomRight
				, normal
				, new float[] {uEdge, v}
				, new float[] {otherUEdge, otherV}
				, blockLight
				, skyLight
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
				, new float[] {otherUEdge, otherVEdge}
				, blockLight
				, skyLight
		);
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
				, new float[] {otherU, otherV}
				, blockLight
				, skyLight
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
				, new float[] {otherUEdge, otherVEdge}
				, blockLight
				, skyLight
		);
		// Left Top.
		builder.appendVertex(topLeft
				, normal
				, new float[] {u, vEdge}
				, new float[] {otherU, otherVEdge}
				, blockLight
				, skyLight
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

	private static float _mapBlockLight(byte inputValue)
	{
		float maxLightFloat = (float)LightAspect.MAX_LIGHT;
		return MINIMUM_LIGHT + (((float)inputValue) / maxLightFloat);
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
		private final BasicBlockAtlas _blockAtlas;
		private final TextureAtlas<AuxVariant> _auxAtlas;
		private final Predicate<Short> _shouldInclude;
		private final _PrismVertices _v;
		private final MeshInputData _inputData;
		
		public _CommonVertexWriter(BufferBuilder builder
				, SparseShortProjection<AuxVariant> projection
				, BasicBlockAtlas blockAtlas
				, TextureAtlas<AuxVariant> auxAtlas
				, Predicate<Short> shouldInclude
				, MeshInputData inputData
				, float blockHeight
		)
		{
			_builder = builder;
			_projection = projection;
			_blockAtlas = blockAtlas;
			_auxAtlas = auxAtlas;
			_shouldInclude = shouldInclude;
			_inputData = inputData;
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
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			float[] uvBaseTop = _blockAtlas.baseOfTopTexture(value);
			float[] uvBaseBottom = _blockAtlas.baseOfBottomTexture(value);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			float[] auxUv = _auxAtlas.baseOfTexture((short)0, _projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				byte z = (byte)(baseZ + 1);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v001, _v.v101, _v.v111, _v.v011
					}, new float[] {0.0f, 0.0f, 1.0f}
					, uvBaseTop, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, _mapBlockLight(_getBlockLight(_inputData, baseX, baseY, z))
					, _getSkyLightMultiplier(_inputData, baseX, baseY, z, SKY_LIGHT_DIRECT)
				);
			}
			else
			{
				byte z = (byte)(baseZ - 1);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v100, _v.v000, _v.v010, _v.v110
					}, new float[] {0.0f, 0.0f, -1.0f}
					, uvBaseBottom, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, _mapBlockLight(_getBlockLight(_inputData, baseX, baseY, z))
					, SKY_LIGHT_SHADOW
				);
			}
		}
		@Override
		public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			float[] uvBaseSide = _blockAtlas.baseOfSideTexture(value);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			float[] auxUv = _auxAtlas.baseOfTexture((short)0, _projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				byte y = (byte)(baseY + 1);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v110, _v.v010, _v.v011, _v.v111
					}, new float[] {0.0f, 1.0f, 0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, _mapBlockLight(_getBlockLight(_inputData, baseX, y, baseZ))
					, _getSkyLightMultiplier(_inputData, baseX, y, baseZ, SKY_LIGHT_PARTIAL)
				);
			}
			else
			{
				byte y = (byte)(baseY - 1);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v000, _v.v100, _v.v101, _v.v001
					}, new float[] {0.0f, -1.0f,0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, _mapBlockLight(_getBlockLight(_inputData, baseX, y, baseZ))
					, _getSkyLightMultiplier(_inputData, baseX, y, baseZ, SKY_LIGHT_PARTIAL)
				);
			}
		}
		@Override
		public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			float[] uvBaseSide = _blockAtlas.baseOfSideTexture(value);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			float[] auxUv = _auxAtlas.baseOfTexture((short)0, _projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				byte x = (byte)(baseX + 1);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v100, _v.v110, _v.v111, _v.v101
					}, new float[] {1.0f, 0.0f, 0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, _mapBlockLight(_getBlockLight(_inputData, x, baseY, baseZ))
					, _getSkyLightMultiplier(_inputData, x, baseY, baseZ, SKY_LIGHT_PARTIAL)
				);
			}
			else
			{
				byte x = (byte)(baseX - 1);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v010, _v.v000, _v.v001, _v.v011
					}, new float[] {-1.0f, 0.0f, 0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, _mapBlockLight(_getBlockLight(_inputData, x, baseY, baseZ))
					, _getSkyLightMultiplier(_inputData, x, baseY, baseZ, SKY_LIGHT_PARTIAL)
				);
			}
		}
	}

	private static byte _getBlockLight(MeshInputData data, byte baseX, byte baseY, byte baseZ)
	{
		byte light;
		if (baseX < 0)
		{
			if (null != data.west)
			{
				light = data.west.getData7(AspectRegistry.LIGHT, new BlockAddress((byte)(baseX + Encoding.CUBOID_EDGE_SIZE), baseY, baseZ));
			}
			else
			{
				light = 0;
			}
		}
		else if (baseX >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.east)
			{
				light = data.east.getData7(AspectRegistry.LIGHT, new BlockAddress((byte)(baseX - Encoding.CUBOID_EDGE_SIZE), baseY, baseZ));
			}
			else
			{
				light = 0;
			}
		}
		else if (baseY < 0)
		{
			if (null != data.south)
			{
				light = data.south.getData7(AspectRegistry.LIGHT, new BlockAddress(baseX, (byte)(baseY + Encoding.CUBOID_EDGE_SIZE), baseZ));
			}
			else
			{
				light = 0;
			}
		}
		else if (baseY >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.north)
			{
				light = data.north.getData7(AspectRegistry.LIGHT, new BlockAddress(baseX, (byte)(baseY - Encoding.CUBOID_EDGE_SIZE), baseZ));
			}
			else
			{
				light = 0;
			}
		}
		else if (baseZ < 0)
		{
			if (null != data.down)
			{
				light = data.down.getData7(AspectRegistry.LIGHT, new BlockAddress(baseX, baseY, (byte)(baseZ + Encoding.CUBOID_EDGE_SIZE)));
			}
			else
			{
				light = 0;
			}
		}
		else if (baseZ >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.up)
			{
				light = data.up.getData7(AspectRegistry.LIGHT, new BlockAddress(baseX, baseY, (byte)(baseZ - Encoding.CUBOID_EDGE_SIZE)));
			}
			else
			{
				light = 0;
			}
		}
		else
		{
			light = data.cuboid.getData7(AspectRegistry.LIGHT, new BlockAddress(baseX, baseY, baseZ));
		}
		return light;
	}

	private static float _getSkyLightMultiplier(MeshInputData data, byte baseX, byte baseY, byte baseZ, float aboveOrMatchLight)
	{
		int realZ = data.cuboid.getCuboidAddress().getBase().z() + baseZ - 1;
		
		boolean isLit;
		if (baseX < 0)
		{
			if (null != data.westHeight)
			{
				isLit = (realZ >= data.westHeight.getHeight(baseX + Encoding.CUBOID_EDGE_SIZE, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseX >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.eastHeight)
			{
				isLit = (realZ >= data.eastHeight.getHeight(baseX - Encoding.CUBOID_EDGE_SIZE, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseY < 0)
		{
			if (null != data.southHeight)
			{
				isLit = (realZ >= data.southHeight.getHeight(baseX, baseY + Encoding.CUBOID_EDGE_SIZE));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseY >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.northHeight)
			{
				isLit = (realZ >= data.northHeight.getHeight(baseX, baseY - Encoding.CUBOID_EDGE_SIZE));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseZ < 0)
		{
			if (null != data.downHeight)
			{
				isLit = (realZ >= data.downHeight.getHeight(baseX, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else if (baseZ >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.upHeight)
			{
				isLit = (realZ >= data.upHeight.getHeight(baseX, baseY));
			}
			else
			{
				isLit = true;
			}
		}
		else
		{
			isLit = (realZ >= data.height.getHeight(baseX, baseY));
		}
		return isLit
				? aboveOrMatchLight
				: SKY_LIGHT_SHADOW
		;
	}

	private static boolean _isBlockOpaque(Environment env, MeshInputData data, BlockAddress address)
	{
		Block blockType;
		if (address.x() < 0)
		{
			if (null != data.west)
			{
				blockType = new BlockProxy(new BlockAddress((byte)(address.x() + Encoding.CUBOID_EDGE_SIZE), address.y(), address.z()), data.west).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.x() >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.east)
			{
				blockType = new BlockProxy(new BlockAddress((byte)(address.x() - Encoding.CUBOID_EDGE_SIZE), address.y(), address.z()), data.east).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.y() < 0)
		{
			if (null != data.south)
			{
				blockType = new BlockProxy(new BlockAddress(address.x(), (byte)(address.y() + Encoding.CUBOID_EDGE_SIZE), address.z()), data.south).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.y() >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.north)
			{
				blockType = new BlockProxy(new BlockAddress(address.x(), (byte)(address.y() - Encoding.CUBOID_EDGE_SIZE), address.z()), data.north).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.z() < 0)
		{
			if (null != data.down)
			{
				blockType = new BlockProxy(new BlockAddress(address.x(), address.y(), (byte)(address.z() + Encoding.CUBOID_EDGE_SIZE)), data.down).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.z() >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.up)
			{
				blockType = new BlockProxy(new BlockAddress(address.x(), address.y(), (byte)(address.z() - Encoding.CUBOID_EDGE_SIZE)), data.up).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else
		{
			blockType = new BlockProxy(address, data.cuboid).getBlock();
		}
		return (null != blockType)
				? (LightAspect.OPAQUE ==  env.lighting.getOpacity(blockType))
				: false
		;
	}

	/**
	 * Packaged-up data passed into the mesh generation helpers.
	 * This record exists to give names to the inputs, instead of just a long parameter list.
	 * Note that any of the fields can be null except for "cuboid".
	 */
	public static record MeshInputData(IReadOnlyCuboidData cuboid
			, ColumnHeightMap height
			, IReadOnlyCuboidData up
			, ColumnHeightMap upHeight
			, IReadOnlyCuboidData down
			, ColumnHeightMap downHeight
			, IReadOnlyCuboidData north
			, ColumnHeightMap northHeight
			, IReadOnlyCuboidData south
			, ColumnHeightMap southHeight
			, IReadOnlyCuboidData east
			, ColumnHeightMap eastHeight
			, IReadOnlyCuboidData west
			, ColumnHeightMap westHeight
	) {}
}
