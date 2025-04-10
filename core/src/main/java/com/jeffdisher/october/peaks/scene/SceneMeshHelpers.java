package com.jeffdisher.october.peaks.scene;

import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.aspects.OrientationAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.FaceBuilder;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.wavefront.ModelBuffer;
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
		// First, we will use the short callbacks based on damage.
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
		
		// Then, we will do a pass on the flags to override anything burning.
		cuboid.walkData(AspectRegistry.FLAGS, (BlockAddress base, byte size, Byte value) -> {
			if (FlagsAspect.isSet(value, FlagsAspect.FLAG_BURNING))
			{
				variantProjection.set(base, SceneMeshHelpers.AuxVariant.BURNING);
			}
		}, (byte) 0);
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
			Set<Short> lava = _buildLavaNumberSet(env);
			shouldInclude = (Short value) -> {
				return blockAtlas.isInBasicAtlas(value)
						&& !blockAtlas.textureHasNonOpaquePixels(value)
						&& !lava.contains(value)
				;
			};
		}
		else
		{
			Set<Short> water = _buildWaterNumberSet(env);
			shouldInclude = (Short value) -> {
				return blockAtlas.isInBasicAtlas(value)
						&& blockAtlas.textureHasNonOpaquePixels(value)
						&& !water.contains(value)
				;
			};
		}
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
				, shouldInclude
				, null
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

	public static void populateBufferWithComplexModels(Environment env
			, BufferBuilder builder
			, BlockModelsAndAtlas blockModels
			, SparseShortProjection<AuxVariant> projection
			, TextureAtlas<AuxVariant> auxAtlas
			, MeshInputData inputData
	)
	{
		Map<Short, Block> included = blockModels.getBlockSet().stream().collect(Collectors.toMap((Block block) -> block.item().number(), (Block block) -> block));
		float uvCoordinateSize = blockModels.getCoordinateSize();
		float auxCoordinateSize = auxAtlas.coordinateSize;
		inputData.cuboid.walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<Short>() {
			@Override
			public void visit(BlockAddress base, byte size, Short object)
			{
				Block includedBlock = included.get(object);
				if (null != includedBlock)
				{
					float[] uv = blockModels.baseOfModelTexture(includedBlock);
					ModelBuffer bufferForType = blockModels.getModelForBlock(includedBlock);
					int blockHeight = env.blocks.isMultiBlock(includedBlock)
							? env.multiBlocks.getDefaultVolume(includedBlock).z()
							: 1
					;
					for (byte z = 0; z < size; ++z)
					{
						for (byte y = 0; y < size; ++y)
						{
							for (byte x = 0; x < size; ++x)
							{
								byte baseX = (byte)(base.x() + x);
								byte baseY = (byte)(base.y() + y);
								byte baseZ = (byte)(base.z() + z);
								// Multi-blocks with complex models should only render at the root.
								BlockAddress thisAddress = new BlockAddress(baseX, baseY, baseZ);
								if (null == inputData.cuboid.getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, thisAddress))
								{
									OrientationAspect.Direction multiBlockDirection = OrientationAspect.byteToDirection(inputData.cuboid.getData7(AspectRegistry.ORIENTATION, thisAddress));
									_renderModel(builder
											, projection
											, auxAtlas
											, inputData
											, uvCoordinateSize
											, auxCoordinateSize
											, uv
											, bufferForType
											, baseX
											, baseY
											, baseZ
											, multiBlockDirection
											, blockHeight
									);
								}
							}
						}
					}
				}
			}
		}, (short)0);
	}

	public static void populateWaterMeshBufferForCuboid(Environment env
			, BufferBuilder builder
			, BasicBlockAtlas blockAtlas
			, SparseShortProjection<AuxVariant> projection
			, TextureAtlas<AuxVariant> auxAtlas
			, MeshInputData inputData
			, short sourceNumber
			, short strongNumber
			, short weakNumber
			, boolean drawInternalSurfaces
	)
	{
		// In this case, we need to configure a WaterSurfaceBuilder since water's surface depends on the strength of flow.
		Set<Short> water = Set.of(sourceNumber
				, strongNumber
				, weakNumber
		);
		Predicate<Short> shouldInclude = (Short value) -> {
			return water.contains(value);
		};
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder(shouldInclude, sourceNumber, strongNumber, weakNumber);
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
				, shouldInclude
				, new FaceBuilder.IEdgeWriter()
				{
					@Override
					public void writeEdgeValue(byte baseX, byte baseY, byte baseZ, short value)
					{
						surface.setEdgeValue(baseX, baseY, baseZ, value);
					}
				}
				, inputData
		);
		faces.populateMasks(inputData.cuboid, shouldInclude);
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
				// The exception to this rule is that we want to draw the top face of a liquid block.
				if ((1.0f == normal[2]) || !_isBlockOpaque(env, inputData, externalBlock))
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
					
					if (drawInternalSurfaces)
					{
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

	public static VertexArray createOutlinePrism(GL20 gl
			, Attribute[] attributes
			, FloatBuffer meshBuffer
			, Prism prism
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
		
		// This is an outline so we want to increase the space around the prism before making the vertices.
		float outlineDistance = 0.01f;
		Prism outline = new Prism(prism.west() - outlineDistance
				, prism.south() - outlineDistance
				, prism.bottom() - outlineDistance
				, prism.east() + outlineDistance
				, prism.north() + outlineDistance
				, prism.top() + outlineDistance
		);
		
		// Note that no matter the scale, the quad vertices are the same magnitudes.
		_PrismVertices v = _PrismVertices.from(outline);
		float blockLightMultiplier = 1.0f;
		float skyLightMultiplier = 0.0f;
		float[] base = new float[] { 0.0f, 0.0f, 0.0f };
		
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
			, FaceBuilder.IEdgeWriter edgeWriter
			, MeshInputData inputData
	)
	{
		byte omit = -1;
		byte zero = 0;
		byte edge = Encoding.CUBOID_EDGE_SIZE;
		if (null != inputData.up)
		{
			faces.preSeedMasks(inputData.up, shouldInclude, edgeWriter, zero, omit, omit, omit, omit, omit);
		}
		if (null != inputData.down)
		{
			faces.preSeedMasks(inputData.down, shouldInclude, edgeWriter, omit, edge, omit, omit, omit, omit);
		}
		if (null != inputData.north)
		{
			faces.preSeedMasks(inputData.north, shouldInclude, edgeWriter, omit, omit, zero, omit, omit, omit);
		}
		if (null != inputData.south)
		{
			faces.preSeedMasks(inputData.south, shouldInclude, edgeWriter, omit, omit, omit, edge, omit, omit);
		}
		if (null != inputData.east)
		{
			faces.preSeedMasks(inputData.east, shouldInclude, edgeWriter, omit, omit, omit, omit, zero, omit);
		}
		if (null != inputData.west)
		{
			faces.preSeedMasks(inputData.west, shouldInclude, edgeWriter, omit, omit, omit, omit, omit, edge);
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

	private static Set<Short> _buildLavaNumberSet(Environment env)
	{
		Set<Short> water = Set.of(env.items.getItemById("op.lava_source").number()
				, env.items.getItemById("op.lava_strong").number()
				, env.items.getItemById("op.lava_weak").number()
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
		BURNING,
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
		public static _PrismVertices from(Prism prism)
		{
			float[] v001 = new float[] { prism.west(), prism.south(), prism.top() };
			float[] v101 = new float[] { prism.east(), prism.south(), prism.top() };
			float[] v111 = new float[] { prism.east(), prism.north(), prism.top() };
			float[] v011 = new float[] { prism.west(), prism.north(), prism.top() };
			float[] v000 = new float[] { prism.west(), prism.south(), prism.bottom() };
			float[] v100 = new float[] { prism.east(), prism.south(), prism.bottom() };
			float[] v110 = new float[] { prism.east(), prism.north(), prism.bottom() };
			float[] v010 = new float[] { prism.west(), prism.north(), prism.bottom() };
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
			_v = _PrismVertices.from(Prism.getBoundsAtOrigin(1.0f, 1.0f, blockHeight));
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

	private static byte _getMaxAreaLight(MeshInputData data, byte baseX, byte baseY, byte baseZ)
	{
		// Check this block and the adjacent ones, returning the maximum light value.
		byte centre = _getBlockLight(data, baseX, baseY, baseZ);
		byte xm = _getBlockLight(data, (byte)(baseX - 1), baseY, baseZ);
		byte xp = _getBlockLight(data, (byte)(baseX + 1), baseY, baseZ);
		byte ym = _getBlockLight(data, baseX, (byte)(baseY - 1), baseZ);
		byte yp = _getBlockLight(data, baseX, (byte)(baseY + 1), baseZ);
		byte zm = _getBlockLight(data, baseX, baseY, (byte)(baseZ - 1));
		byte zp = _getBlockLight(data, baseX, baseY, (byte)(baseZ + 1));
		
		return (byte) Math.max(
				Math.max(
						centre
						, Math.max(xm, xp)
				)
				, Math.max(
						Math.max(ym, yp)
						, Math.max(zm, zp)
				)
		);
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

	private static void _renderModel(BufferBuilder builder
			, SparseShortProjection<AuxVariant> projection
			, TextureAtlas<AuxVariant> auxAtlas
			, MeshInputData inputData
			, float uvCoordinateSize
			, float auxCoordinateSize
			, float[] uv
			, ModelBuffer bufferForType
			, byte baseX
			, byte baseY
			, byte baseZ
			, OrientationAspect.Direction multiBlockDirection
			, int blockHeight
	)
	{
		float[] auxUv = auxAtlas.baseOfTexture((short)0, projection.get(new BlockAddress(baseX, baseY, baseZ)));
		// We interpret the max of the adjacent blocks as the light value of a model (since it has interior surfaces on all sides).
		float[] blockLight = new float[] { _mapBlockLight(_getMaxAreaLight(inputData, baseX, baseY, baseZ)) };
		// Sky light never falls in this block but we still want to account for it so check the block above with partial lighting.
		float[] skyLight = new float[] { _getSkyLightMultiplier(inputData, baseX, baseY, (byte)(baseZ + blockHeight), SKY_LIGHT_PARTIAL) };
		float offsetX = baseX;
		float offsetY = baseY;
		float offsetZ = baseZ;
		// The models are based in the 0-1 unit cube but we want to rotate around the centre so translate by X/Y.
		float centreX = 0.5f;
		float centreY = 0.5f;
		for (int i = 0; i < bufferForType.vertexCount; ++i)
		{
			float x = bufferForType.positionValues[3 * i + 0];
			float y = bufferForType.positionValues[3 * i + 1];
			float z = bufferForType.positionValues[3 * i + 2];
			if (OrientationAspect.Direction.NORTH != multiBlockDirection)
			{
				float[] out = multiBlockDirection.rotateXYTupleAboutZ(new float[] { x - centreX, y - centreY });
				x = out[0] + centreY;
				y = out[1] + centreY;
			}
			
			// Each element is:
			// vx, vy, vz
			// nx, ny, nz
			// u, v
			// otherU, otherV
			// blockLight
			float[] positions = new float[] {
					offsetX + x,
					offsetY + y,
					offsetZ + z,
			};
			float[] normals = new float[] {
					bufferForType.normalValues[3 * i + 0],
					bufferForType.normalValues[3 * i + 1],
					bufferForType.normalValues[3 * i + 2],
			};
			float[] textures = new float[] {
					uv[0] + (uvCoordinateSize * bufferForType.textureValues[2 * i + 0]),
					uv[1] + (uvCoordinateSize * bufferForType.textureValues[2 * i + 1]),
			};
			float[] otherTextures = new float[] {
					auxUv[0] + (auxCoordinateSize * bufferForType.textureValues[2 * i + 0]),
					auxUv[1] + (auxCoordinateSize * bufferForType.textureValues[2 * i + 1]),
			};
			
			builder.appendVertex(positions
					, normals
					, textures
					, otherTextures
					, blockLight
					, skyLight
			);
		}
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
