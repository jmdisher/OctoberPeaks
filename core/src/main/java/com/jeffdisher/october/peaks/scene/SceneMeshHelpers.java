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
import com.jeffdisher.october.peaks.textures.AuxilliaryTextureAtlas;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.wavefront.ModelBuffer;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
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

	public static SparseShortProjection<AuxilliaryTextureAtlas.Variant> buildAuxProjection(Environment env, IReadOnlyCuboidData cuboid)
	{
		// First, we will use the short callbacks based on damage.
		SparseShortProjection<AuxilliaryTextureAtlas.Variant> variantProjection = SparseShortProjection.fromAspect(cuboid, AspectRegistry.DAMAGE, (short)0, AuxilliaryTextureAtlas.Variant.NONE, (BlockAddress blockAddress, Short value) -> {
			short damage = value;
			// We will favour showing cracks at a low damage, so the feedback is obvious
			Block block = new BlockProxy(blockAddress, cuboid).getBlock();
			float damaged = (float) damage / (float)env.damage.getToughness(block);
			
			AuxilliaryTextureAtlas.Variant aux;
			if (damaged > 0.6f)
			{
				aux = AuxilliaryTextureAtlas.Variant.BREAK_HIGH;
			}
			else if (damaged > 0.3f)
			{
				aux = AuxilliaryTextureAtlas.Variant.BREAK_MEDIUM;
			}
			else
			{
				aux = AuxilliaryTextureAtlas.Variant.BREAK_LOW;
			}
			return aux;
		});
		
		// Then, we will do a pass on the flags to override anything burning.
		cuboid.walkData(AspectRegistry.FLAGS, (BlockAddress base, byte size, Byte value) -> {
			if (FlagsAspect.isSet(value, FlagsAspect.FLAG_BURNING))
			{
				variantProjection.set(base, AuxilliaryTextureAtlas.Variant.BURNING);
			}
		}, (byte) 0);
		return variantProjection;
	}

	public static void populateMeshBufferForCuboid(Environment env
			, BufferBuilder builder
			, BasicBlockAtlas blockAtlas
			, SparseShortProjection<AuxilliaryTextureAtlas.Variant> projection
			, AuxilliaryTextureAtlas auxAtlas
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
		faces.buildFaces(inputData.cuboid, new _CommonVertexWriter(env
				, builder
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
			, SparseShortProjection<AuxilliaryTextureAtlas.Variant> projection
			, AuxilliaryTextureAtlas auxAtlas
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
					short value = object.shortValue();
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
									// We need to see if this block has an active variant, since that is required to select the appropriate model.
									boolean hasActiveVariant = env.blocks.hasActiveVariant(env.blocks.fromItem(env.items.ITEMS_BY_TYPE[value]));
									boolean isActive = hasActiveVariant
											? FlagsAspect.isSet(inputData.cuboid.getData7(AspectRegistry.FLAGS, new BlockAddress(baseX, baseY, baseZ)), FlagsAspect.FLAG_ACTIVE)
											: false
									;
									OrientationAspect.Direction multiBlockDirection = OrientationAspect.byteToDirection(inputData.cuboid.getData7(AspectRegistry.ORIENTATION, thisAddress));
									boolean isDown = (OrientationAspect.Direction.DOWN == multiBlockDirection);
									if (isDown)
									{
										// If this is facing down, we just use a north rotation.
										multiBlockDirection = OrientationAspect.Direction.NORTH;
									}
									float[] uv = blockModels.baseOfModelTexture(includedBlock, isActive, isDown);
									ModelBuffer bufferForType = blockModels.getModelForBlock(includedBlock, isActive, isDown);
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
			, SparseShortProjection<AuxilliaryTextureAtlas.Variant> projection
			, AuxilliaryTextureAtlas auxAtlas
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
		// (we assume liquids are never "active").
		boolean isActive = false;
		float[] uvBase = blockAtlas.baseOfTopTexture(isActive, sourceNumber);
		float textureSize = blockAtlas.getCoordinateSize();
		float[] auxUv = auxAtlas.baseOfTexture(AuxilliaryTextureAtlas.Variant.NONE);
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
					// Liquids may be translucent or light emitters so we want to take the maximum of the external face light and the internal light.
					// (this avoids cases where lava is dark just because there is a partial block next to it).
					byte externalLight = _getBlockLight(inputData, externalBlock.x(), externalBlock.y(), externalBlock.z());
					byte internalLight = _getBlockLight(inputData, address.x(), address.y(), address.z());
					float blockLightMultiplier = _mapBlockLight((byte)Math.max(externalLight, internalLight));
					float skyLightMultiplier = _getSkyLightMultiplier(inputData, externalBlock.x(), externalBlock.y(), externalBlock.z(), SKY_LIGHT_DIRECT);
					// For now, at least, we will leave the liquid surfaces without blending.
					float[] blockLightMultipliers = new float[] {blockLightMultiplier, blockLightMultiplier, blockLightMultiplier, blockLightMultiplier};
					float[] skyLightMultipliers = new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier};
					
					_populateQuad(builder
							, _base
							, counterClockWiseVertices
							, normal
							, uvBase
							, textureSize
							, auxUv
							, auxTextureSize
							, blockLightMultipliers
							, skyLightMultipliers
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
								, blockLightMultipliers
								, skyLightMultipliers
						);
					}
				}
			}
		});
	}

	public static void populateMeshForItemsInWorld(Environment env
			, BufferBuilder builder
			, ItemTextureAtlas itemAtlas
			, AuxilliaryTextureAtlas auxAtlas
			, IReadOnlyCuboidData cuboid
			, ColumnHeightMap heightMap
			, Block pedestal
	)
	{
		float textureSize = itemAtlas.coordinateSize;
		
		// Dropped items will never have an aux texture.
		float[] auxUv = auxAtlas.baseOfTexture(AuxilliaryTextureAtlas.Variant.NONE);
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
				boolean isActive = FlagsAspect.isSet(proxy.getFlags(), FlagsAspect.FLAG_ACTIVE);
				boolean blockPermitsEntityMovement = !env.blocks.isSolid(blockType, isActive);
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
						
						float[] uvBase = itemAtlas.baseOfTexture(type.number());
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
		
		// See if there are any special slots in pedestals in this cuboid (we may generalize this later).
		cuboid.walkData(AspectRegistry.SPECIAL_ITEM_SLOT, new IOctree.IWalkerCallback<ItemSlot>() {
			@Override
			public void visit(BlockAddress base, byte size, ItemSlot specialSlot)
			{
				Assert.assertTrue((byte)1 == size);
				BlockProxy proxy = new BlockProxy(base, cuboid);
				Block blockType = proxy.getBlock();
				if (pedestal == blockType)
				{
					Item type = specialSlot.getType();
					float[] uvBase = itemAtlas.baseOfTexture(type.number());
					float[] blockBase = new float[] { (float) base.x(), (float) base.y(), (float) base.z() };
					float pedestalTopHeight = 0.7f;
					float itemEdge = 0.4f;
					float itemInset = 0.3f;
					
					// We don't want to offset in X since that is the direction we currently draw into.
					float[] itemBase = new float[] { blockBase[0] + itemInset, blockBase[1] + 0.5f, blockBase[2] + pedestalTopHeight };
					// We assume that things on pedestals have some kind of "internal" light.
					float blockLightMultiplier = 0.5f;
					// And we check the block light above them.
					float skyLightMultiplier = ((base.z() + cuboidZ) == heightMap.getHeight(base.x(), base.y())) ? 1.0f : 0.0f;
					_drawStandingSquare(builder, itemBase, itemEdge
							, uvBase, textureSize
							, auxUv, auxTextureSize
							, blockLightMultiplier
							, skyLightMultiplier
					);
				}
			}
		}, null);
	}

	public static VertexArray createOutlinePrism(GL20 gl
			, Attribute[] attributes
			, FloatBuffer meshBuffer
			, Prism prism
			, AuxilliaryTextureAtlas auxAtlas
	)
	{
		// This is currently how we render entities so we can use the hard-coded coordinates.
		float[] uvBase = new float[] { 0.0f, 0.0f };
		float textureSize = 1.0f;
		
		// We will use no AUX texture.
		float[] auxUv = auxAtlas.baseOfTexture(AuxilliaryTextureAtlas.Variant.NONE);
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
		float[] base = new float[] { 0.0f, 0.0f, 0.0f };
		// The selection prism has no light blending.
		float[] blockLightMultipliers = new float[] {blockLightMultiplier, blockLightMultiplier, blockLightMultiplier, blockLightMultiplier};
		float[] skyLightMultipliers = new float[] {0.0f, 0.0f, 0.0f, 0.0f};
		
		// X-normal plane.
		_populateQuad(builder, base, new float[][] {
				v.v010, v.v000, v.v001, v.v011
			}, new float[] {-1.0f, 0.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
		);
		_populateQuad(builder, base, new float[][] {
				v.v100, v.v110, v.v111, v.v101
			}, new float[] {1.0f, 0.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
		);
		
		// Y-normal plane.
		_populateQuad(builder, base, new float[][] {
				v.v000, v.v100, v.v101, v.v001
			}, new float[] {0.0f, -1.0f,0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
		);
		_populateQuad(builder, base, new float[][] {
				v.v110, v.v010, v.v011, v.v111
			}, new float[] {0.0f, 1.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
		);
		
		// Z-normal plane.
		// Note that the Z-normal creates surfaces parallel to the ground so we will define "up" as "positive y".
		_populateQuad(builder, base, new float[][] {
				v.v100, v.v000, v.v010, v.v110
			}, new float[] {0.0f, 0.0f, -1.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
		);
		_populateQuad(builder, base, new float[][] {
				v.v001, v.v101, v.v111, v.v011
			}, new float[] {0.0f, 0.0f, 1.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
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
		// We won't bother light-blending the up-facing square.
		float[] blockLightMultipliers = new float[] {blockLightMultiplier, blockLightMultiplier, blockLightMultiplier, blockLightMultiplier};
		float[] skyLightMultipliers = new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier};
		
		_populateQuad(builder, base
				, new float[][] {bottomLeft, bottomRight, topRight, topLeft }
				, new float[] { 0.0f, 0.0f, 1.0f }
				, uvBase, textureSize
				, otherUvBase, otherTextureSize
				, blockLightMultipliers
				, skyLightMultipliers
		);
	}

	private static void _drawStandingSquare(BufferBuilder builder
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
		// The idea here is that we draw 2 quads with the same texture:  One facing North and one South.
		float[] OI = new float[] {
				0.0f,
				0.0f,
				edgeSize,
		};
		float[] II = new float[] {
				edgeSize,
				0.0f,
				edgeSize,
		};
		float[] IO = new float[] {
				edgeSize,
				0.0f,
				0.0f,
		};
		float[] OO = new float[] {
				0.0f,
				0.0f,
				0.0f,
		};
		// We won't bother light-blending single items.
		float[] blockLightMultipliers = new float[] {blockLightMultiplier, blockLightMultiplier, blockLightMultiplier, blockLightMultiplier};
		float[] skyLightMultipliers = new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier};
		
		_populateQuad(builder, base
				, new float[][] { OO, IO, II, OI }
				, new float[] { 0.0f, 1.0f, 0.0f }
				, uvBase, textureSize
				, otherUvBase, otherTextureSize
				, blockLightMultipliers
				, skyLightMultipliers
		);
		_populateQuad(builder, base
				, new float[][] { IO, OO, OI, II }
				, new float[] { 0.0f, -1.0f, 0.0f }
				, uvBase, textureSize
				, otherUvBase, otherTextureSize
				, blockLightMultipliers
				, skyLightMultipliers
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
			, float[] blockLightMultipliers
			, float[] skyLightMultipliers
	)
	{
		float[] bottomLeft = new float[] {
				base[0] + vertices[0][0],
				base[1] + vertices[0][1],
				base[2] + vertices[0][2],
		};
		float[] bottomLeftBlockLight = new float[] {blockLightMultipliers[0]};
		float[] bottomLeftSkyLight = new float[] {skyLightMultipliers[0]};
		float[] bottomRight = new float[] {
				base[0] + vertices[1][0],
				base[1] + vertices[1][1],
				base[2] + vertices[1][2],
		};
		float[] bottomRightBlockLight = new float[] {blockLightMultipliers[1]};
		float[] bottomRightSkyLight = new float[] {skyLightMultipliers[1]};
		float[] topRight = new float[] {
				base[0] + vertices[2][0],
				base[1] + vertices[2][1],
				base[2] + vertices[2][2],
		};
		float[] topRightBlockLight = new float[] {blockLightMultipliers[2]};
		float[] topRightSkyLight = new float[] {skyLightMultipliers[2]};
		float[] topLeft = new float[] {
				base[0] + vertices[3][0],
				base[1] + vertices[3][1],
				base[2] + vertices[3][2],
		};
		float[] topLeftBlockLight = new float[] {blockLightMultipliers[3]};
		float[] topLeftSkyLight = new float[] {skyLightMultipliers[3]};
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
		// blockLight
		
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
				, new float[] {otherU, otherV}
				, bottomLeftBlockLight
				, bottomLeftSkyLight
		);
		// Right Bottom.
		builder.appendVertex(bottomRight
				, normal
				, new float[] {uEdge, v}
				, new float[] {otherUEdge, otherV}
				, bottomRightBlockLight
				, bottomRightSkyLight
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
				, new float[] {otherUEdge, otherVEdge}
				, topRightBlockLight
				, topRightSkyLight
		);
		// Left Bottom.
		builder.appendVertex(bottomLeft
				, normal
				, new float[] {u, v}
				, new float[] {otherU, otherV}
				, bottomLeftBlockLight
				, bottomLeftSkyLight
		);
		// Right Top.
		builder.appendVertex(topRight
				, normal
				, new float[] {uEdge, vEdge}
				, new float[] {otherUEdge, otherVEdge}
				, topRightBlockLight
				, topRightSkyLight
		);
		// Left Top.
		builder.appendVertex(topLeft
				, normal
				, new float[] {u, vEdge}
				, new float[] {otherU, otherVEdge}
				, topLeftBlockLight
				, topLeftSkyLight
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
		private final Environment _env;
		private final BufferBuilder _builder;
		private final SparseShortProjection<AuxilliaryTextureAtlas.Variant> _projection;
		private final BasicBlockAtlas _blockAtlas;
		private final AuxilliaryTextureAtlas _auxAtlas;
		private final Predicate<Short> _shouldInclude;
		private final _PrismVertices _v;
		private final MeshInputData _inputData;
		
		public _CommonVertexWriter(Environment env
				, BufferBuilder builder
				, SparseShortProjection<AuxilliaryTextureAtlas.Variant> projection
				, BasicBlockAtlas blockAtlas
				, AuxilliaryTextureAtlas auxAtlas
				, Predicate<Short> shouldInclude
				, MeshInputData inputData
				, float blockHeight
		)
		{
			_env = env;
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
			boolean isActive = _isActive(baseX, baseY, baseZ, value);
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			float[] uvBaseTop = _blockAtlas.baseOfTopTexture(isActive, value);
			float[] uvBaseBottom = _blockAtlas.baseOfBottomTexture(isActive, value);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			float[] auxUv = _auxAtlas.baseOfTexture(_projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				byte z = (byte)(baseZ + 1);
				byte westX = (byte)(baseX - 1);
				byte eastX = (byte)(baseX + 1);
				byte southY = (byte)(baseY - 1);
				byte northY = (byte)(baseY + 1);
				byte thisBlockLight = _getBlockLight(_inputData, baseX, baseY, z);
				byte eastBlockLight = _getBlockLight(_inputData, eastX, baseY, z);
				byte westBlockLight = _getBlockLight(_inputData, westX, baseY, z);
				byte northBlockLight = _getBlockLight(_inputData, baseX, northY, z);
				byte southBlockLight = _getBlockLight(_inputData, baseX, southY, z);
				byte SWBlockLight = _getBlockLight(_inputData, westX, southY, z);
				byte SEBlockLight = _getBlockLight(_inputData, eastX, southY, z);
				byte NWBlockLight = _getBlockLight(_inputData, westX, northY, z);
				byte NEBlockLight = _getBlockLight(_inputData, eastX, northY, z);
				
				// We handle sky slight specially for z+ faces, since the sky is in that direction.
				// We actually want to average the 4 block faces adjacent to each corner, in this case.
				float skySW = _getUpFacingSkyMultipler(_inputData, westX, southY, z);
				float skyS = _getUpFacingSkyMultipler(_inputData, baseX, southY, z);
				float skySE = _getUpFacingSkyMultipler(_inputData, eastX, southY, z);
				float skyW = _getUpFacingSkyMultipler(_inputData, westX, baseY, z);
				float sky = _getUpFacingSkyMultipler(_inputData, baseX, baseY, z);
				float skyE = _getUpFacingSkyMultipler(_inputData, eastX, baseY, z);
				float skyNW = _getUpFacingSkyMultipler(_inputData, westX, northY, z);
				float skyN = _getUpFacingSkyMultipler(_inputData, baseX, northY, z);
				float skyNE = _getUpFacingSkyMultipler(_inputData, eastX, northY, z);
				
				_populateQuad(_builder, localBase, new float[][] {
						_v.v001, _v.v101, _v.v111, _v.v011
					}, new float[] {0.0f, 0.0f, 1.0f}
					, uvBaseTop, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, new float[] {_maxLightAsFloat(westBlockLight, southBlockLight, SWBlockLight, thisBlockLight)
						, _maxLightAsFloat(eastBlockLight, southBlockLight, SEBlockLight, thisBlockLight)
						, _maxLightAsFloat(eastBlockLight, northBlockLight, NEBlockLight, thisBlockLight)
						, _maxLightAsFloat(westBlockLight, northBlockLight, NWBlockLight, thisBlockLight)
					}
					, new float[] {
							_blendSkyLight(skyW, skyS, skySW, sky),
							_blendSkyLight(skyE, skyS, skySE, sky),
							_blendSkyLight(skyE, skyN, skyNE, sky),
							_blendSkyLight(skyW, skyN, skyNW, sky),
					}
				);
			}
			else
			{
				byte z = (byte)(baseZ - 1);
				byte thisBlockLight = _getBlockLight(_inputData, baseX, baseY, z);
				byte eastBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), baseY, z);
				byte westBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), baseY, z);
				byte northBlockLight = _getBlockLight(_inputData, baseX, (byte)(baseY + 1), z);
				byte southBlockLight = _getBlockLight(_inputData, baseX, (byte)(baseY - 1), z);
				byte SWBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), (byte)(baseY - 1), z);
				byte SEBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), (byte)(baseY - 1), z);
				byte NWBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), (byte)(baseY + 1), z);
				byte NEBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), (byte)(baseY + 1), z);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v100, _v.v000, _v.v010, _v.v110
					}, new float[] {0.0f, 0.0f, -1.0f}
					, uvBaseBottom, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, new float[] {_maxLightAsFloat(eastBlockLight, southBlockLight, SEBlockLight, thisBlockLight)
						, _maxLightAsFloat(westBlockLight, southBlockLight, SWBlockLight, thisBlockLight)
						, _maxLightAsFloat(westBlockLight, northBlockLight, NWBlockLight, thisBlockLight)
						, _maxLightAsFloat(eastBlockLight, northBlockLight, NEBlockLight, thisBlockLight)
					}
					, new float[] {SKY_LIGHT_SHADOW, SKY_LIGHT_SHADOW, SKY_LIGHT_SHADOW, SKY_LIGHT_SHADOW}
				);
			}
		}
		@Override
		public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			boolean isActive = _isActive(baseX, baseY, baseZ, value);
			float[] uvBaseSide = _blockAtlas.baseOfSideTexture(isActive, value);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			float[] auxUv = _auxAtlas.baseOfTexture(_projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				byte y = (byte)(baseY + 1);
				byte thisBlockLight = _getBlockLight(_inputData, baseX, y, baseZ);
				byte eastBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), y, baseZ);
				byte westBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), y, baseZ);
				byte upBlockLight = _getBlockLight(_inputData, baseX, y, (byte)(baseZ + 1));
				byte downBlockLight = _getBlockLight(_inputData, baseX, y, (byte)(baseZ - 1));
				byte WDBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), y, (byte)(baseZ - 1));
				byte WUBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), y, (byte)(baseZ + 1));
				byte EDBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), y, (byte)(baseZ - 1));
				byte EUBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), y, (byte)(baseZ + 1));
				float skyLightMultiplier = _getSkyLightMultiplier(_inputData, baseX, y, baseZ, SKY_LIGHT_PARTIAL);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v110, _v.v010, _v.v011, _v.v111
					}, new float[] {0.0f, 1.0f, 0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, new float[] {_maxLightAsFloat(eastBlockLight, thisBlockLight, downBlockLight, EDBlockLight)
						, _maxLightAsFloat(westBlockLight, thisBlockLight, downBlockLight, WDBlockLight)
						, _maxLightAsFloat(westBlockLight, thisBlockLight, upBlockLight, WUBlockLight)
						, _maxLightAsFloat(eastBlockLight, thisBlockLight, upBlockLight, EUBlockLight)
					}
					, new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier}
				);
			}
			else
			{
				byte y = (byte)(baseY - 1);
				byte thisBlockLight = _getBlockLight(_inputData, baseX, y, baseZ);
				byte eastBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), y, baseZ);
				byte westBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), y, baseZ);
				byte upBlockLight = _getBlockLight(_inputData, baseX, y, (byte)(baseZ + 1));
				byte downBlockLight = _getBlockLight(_inputData, baseX, y, (byte)(baseZ - 1));
				byte WDBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), y, (byte)(baseZ - 1));
				byte WUBlockLight = _getBlockLight(_inputData, (byte)(baseX - 1), y, (byte)(baseZ + 1));
				byte EDBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), y, (byte)(baseZ - 1));
				byte EUBlockLight = _getBlockLight(_inputData, (byte)(baseX + 1), y, (byte)(baseZ + 1));
				float skyLightMultiplier = _getSkyLightMultiplier(_inputData, baseX, y, baseZ, SKY_LIGHT_PARTIAL);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v000, _v.v100, _v.v101, _v.v001
					}, new float[] {0.0f, -1.0f,0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, new float[] {_maxLightAsFloat(westBlockLight, thisBlockLight, downBlockLight, WDBlockLight)
						, _maxLightAsFloat(eastBlockLight, thisBlockLight, downBlockLight, EDBlockLight)
						, _maxLightAsFloat(eastBlockLight, thisBlockLight, upBlockLight, EUBlockLight)
						, _maxLightAsFloat(westBlockLight, thisBlockLight, upBlockLight, WUBlockLight)
					}
					, new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier}
				);
			}
		}
		@Override
		public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value)
		{
			float[] localBase = new float[] { (float)baseX, (float)baseY, (float)baseZ };
			boolean isActive = _isActive(baseX, baseY, baseZ, value);
			float[] uvBaseSide = _blockAtlas.baseOfSideTexture(isActive, value);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			float[] auxUv = _auxAtlas.baseOfTexture(_projection.get(new BlockAddress(baseX, baseY, baseZ)));
			if (isPositiveNormal)
			{
				byte x = (byte)(baseX + 1);
				byte thisBlockLight = _getBlockLight(_inputData, x, baseY, baseZ);
				byte northBlockLight = _getBlockLight(_inputData, x, (byte)(baseY + 1), baseZ);
				byte southBlockLight = _getBlockLight(_inputData, x, (byte)(baseY - 1), baseZ);
				byte upBlockLight = _getBlockLight(_inputData, x, baseY, (byte)(baseZ + 1));
				byte downBlockLight = _getBlockLight(_inputData, x, baseY, (byte)(baseZ - 1));
				byte SDBlockLight = _getBlockLight(_inputData, x, (byte)(baseY - 1), (byte)(baseZ - 1));
				byte SUBlockLight = _getBlockLight(_inputData, x, (byte)(baseY - 1), (byte)(baseZ + 1));
				byte NDBlockLight = _getBlockLight(_inputData, x, (byte)(baseY + 1), (byte)(baseZ - 1));
				byte NUBlockLight = _getBlockLight(_inputData, x, (byte)(baseY + 1), (byte)(baseZ + 1));
				float skyLightMultiplier = _getSkyLightMultiplier(_inputData, x, baseY, baseZ, SKY_LIGHT_PARTIAL);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v100, _v.v110, _v.v111, _v.v101
					}, new float[] {1.0f, 0.0f, 0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, new float[] {_maxLightAsFloat(thisBlockLight, southBlockLight, downBlockLight, SDBlockLight)
						, _maxLightAsFloat(thisBlockLight, northBlockLight, downBlockLight, NDBlockLight)
						, _maxLightAsFloat(thisBlockLight, northBlockLight, upBlockLight, NUBlockLight)
						, _maxLightAsFloat(thisBlockLight, southBlockLight, upBlockLight, SUBlockLight)
					}
					, new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier}
				);
			}
			else
			{
				byte x = (byte)(baseX - 1);
				byte thisBlockLight = _getBlockLight(_inputData, x, baseY, baseZ);
				byte northBlockLight = _getBlockLight(_inputData, x, (byte)(baseY + 1), baseZ);
				byte southBlockLight = _getBlockLight(_inputData, x, (byte)(baseY - 1), baseZ);
				byte upBlockLight = _getBlockLight(_inputData, x, baseY, (byte)(baseZ + 1));
				byte downBlockLight = _getBlockLight(_inputData, x, baseY, (byte)(baseZ - 1));
				byte SDBlockLight = _getBlockLight(_inputData, x, (byte)(baseY - 1), (byte)(baseZ - 1));
				byte SUBlockLight = _getBlockLight(_inputData, x, (byte)(baseY - 1), (byte)(baseZ + 1));
				byte NDBlockLight = _getBlockLight(_inputData, x, (byte)(baseY + 1), (byte)(baseZ - 1));
				byte NUBlockLight = _getBlockLight(_inputData, x, (byte)(baseY + 1), (byte)(baseZ + 1));
				float skyLightMultiplier = _getSkyLightMultiplier(_inputData, x, baseY, baseZ, SKY_LIGHT_PARTIAL);
				_populateQuad(_builder, localBase, new float[][] {
						_v.v010, _v.v000, _v.v001, _v.v011
					}, new float[] {-1.0f, 0.0f, 0.0f}
					, uvBaseSide, uvCoordinateSize
					, auxUv, _auxAtlas.coordinateSize
					, new float[] {_maxLightAsFloat(thisBlockLight, northBlockLight, downBlockLight, NDBlockLight)
						, _maxLightAsFloat(thisBlockLight, southBlockLight, downBlockLight, SDBlockLight)
						, _maxLightAsFloat(thisBlockLight, southBlockLight, upBlockLight, SUBlockLight)
						, _maxLightAsFloat(thisBlockLight, northBlockLight, upBlockLight, NUBlockLight)
					}
					, new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier}
				);
			}
		}
		private boolean _isActive(byte baseX, byte baseY, byte baseZ, short value)
		{
			boolean hasActiveVariant = _env.blocks.hasActiveVariant(_env.blocks.fromItem(_env.items.ITEMS_BY_TYPE[value]));
			boolean isActive = hasActiveVariant
					? FlagsAspect.isSet(_inputData.cuboid.getData7(AspectRegistry.FLAGS, new BlockAddress(baseX, baseY, baseZ)), FlagsAspect.FLAG_ACTIVE)
					: false
			;
			return isActive;
		}
		private static float _maxLightAsFloat(byte one, byte two, byte three, byte four)
		{
			// We just want to take the maximum of the given 4 light values and convert them to a float light multiplier.
			byte max = (byte)Math.max(Math.max(one, two), Math.max(three, four));
			return _mapBlockLight(max);
		}
		private static float _blendSkyLight(float one, float two, float three, float four)
		{
			// We will average these so that blocks in the open are brighter than those in corners.
			return (one + two + three + four) / 4.0f;
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
		int indexX = 1;
		int indexY = 1;
		int indexZ = 1;
		
		if (baseX < 0)
		{
			baseX = (byte)(baseX + Encoding.CUBOID_EDGE_SIZE);
			indexX -= 1;
		}
		else if (baseX >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseX = (byte)(baseX - Encoding.CUBOID_EDGE_SIZE);
			indexX += 1;
		}
		
		if (baseY < 0)
		{
			baseY = (byte)(baseY + Encoding.CUBOID_EDGE_SIZE);
			indexY -= 1;
		}
		else if (baseY >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseY = (byte)(baseY - Encoding.CUBOID_EDGE_SIZE);
			indexY += 1;
		}
		
		if (baseZ < 0)
		{
			baseZ = (byte)(baseZ + Encoding.CUBOID_EDGE_SIZE);
			indexZ -= 1;
		}
		else if (baseZ >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseZ = (byte)(baseZ - Encoding.CUBOID_EDGE_SIZE);
			indexZ += 1;
		}
		
		IReadOnlyCuboidData toRead = data.cuboidsXYZ[indexX][indexY][indexZ];
		return (null != toRead)
				? toRead.getData7(AspectRegistry.LIGHT, new BlockAddress(baseX, baseY, baseZ))
				: 0
		;
	}

	private static float _getUpFacingSkyMultipler(MeshInputData data, byte baseX, byte baseY, byte baseZ)
	{
		int indexX = 1;
		int indexY = 1;
		
		if (baseX < 0)
		{
			baseX = (byte)(baseX + Encoding.CUBOID_EDGE_SIZE);
			indexX -= 1;
		}
		else if (baseX >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseX = (byte)(baseX - Encoding.CUBOID_EDGE_SIZE);
			indexX += 1;
		}
		
		if (baseY < 0)
		{
			baseY = (byte)(baseY + Encoding.CUBOID_EDGE_SIZE);
			indexY -= 1;
		}
		else if (baseY >= Encoding.CUBOID_EDGE_SIZE)
		{
			baseY = (byte)(baseY - Encoding.CUBOID_EDGE_SIZE);
			indexY += 1;
		}
		
		ColumnHeightMap toRead = data.columnHeightXY[indexX][indexY];
		int realZ = data.cuboid.getCuboidAddress().getBase().z() + baseZ - 1;
		
		boolean isLit;
		if (null != toRead)
		{
			isLit = (realZ >= toRead.getHeight(baseX, baseY));
		}
		else
		{
			isLit = true;
		}
		
		return isLit
				? SKY_LIGHT_DIRECT
				: SKY_LIGHT_SHADOW
		;
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
			, SparseShortProjection<AuxilliaryTextureAtlas.Variant> projection
			, AuxilliaryTextureAtlas auxAtlas
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
		float[] auxUv = auxAtlas.baseOfTexture(projection.get(new BlockAddress(baseX, baseY, baseZ)));
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
			
			, IReadOnlyCuboidData[][][] cuboidsXYZ
			, ColumnHeightMap[][] columnHeightXY
	) {}
}
