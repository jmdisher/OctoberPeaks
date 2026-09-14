package com.jeffdisher.october.peaks.scene;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IOctree;
import com.jeffdisher.october.logic.SparseByteCube;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.FaceBuilder;
import com.jeffdisher.october.peaks.graphics.SubBlockMesh;
import com.jeffdisher.october.peaks.textures.AuxilliaryTextureAtlas;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.wavefront.ModelBuffer;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.FacingDirection;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


public class SceneMeshHelpers
{
	public static void populateMeshBufferForCuboid(Environment env
			, MeshHelperBufferBuilder builder
			, BasicBlockAtlas blockAtlas
			, AuxVariantMap variantMap
			, AuxilliaryTextureAtlas auxAtlas
			, FireFaceBuilder fireTracker
			, MeshInputData inputData
			, boolean opaqueVertices
	)
	{
		Predicate<Short> shouldInclude;
		if (opaqueVertices)
		{
			short lava = env.items.getItemById("op.lava_source").number();
			shouldInclude = (Short value) -> {
				return blockAtlas.isInBasicAtlas(value)
					&& !blockAtlas.textureHasNonOpaquePixels(value)
					&& (lava != value)
				;
			};
		}
		else
		{
			short water = env.items.getItemById("op.water_source").number();
			shouldInclude = (Short value) -> {
				return blockAtlas.isInBasicAtlas(value)
					&& blockAtlas.textureHasNonOpaquePixels(value)
					&& (water != value)
				;
			};
		}
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
				, shouldInclude
				, null
				, inputData
		);
		faces.populateMasks(inputData.cuboid(), shouldInclude);
		faces.buildFaces(inputData.cuboid(), new _CommonVertexWriter(env
			, builder
			, variantMap
			, blockAtlas
			, auxAtlas
			, fireTracker
			, shouldInclude
			, inputData
		));
	}

	public static void populateBufferWithComplexModels(Environment env
			, BufferBuilder builder
			, BlockModelsAndAtlas blockModels
			, AuxVariantMap variantMap
			, AuxilliaryTextureAtlas auxAtlas
			, MeshInputData inputData
	)
	{
		Map<Short, Block> included = blockModels.getBlockSet().stream().collect(Collectors.toMap((Block block) -> block.item().number(), (Block block) -> block));
		float uvCoordinateSize = blockModels.getCoordinateSize();
		float auxCoordinateSize = auxAtlas.coordinateSize;
		inputData.cuboid().walkData(AspectRegistry.BLOCK, new IOctree.IWalkerCallback<Short>() {
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
								if (null == inputData.cuboid().getDataSpecial(AspectRegistry.MULTI_BLOCK_ROOT, thisAddress))
								{
									// We need to see if this block has an active variant, since that is required to select the appropriate model.
									boolean hasActiveVariant = env.blocks.hasActiveVariant(env.blocks.fromItem(env.items.ITEMS_BY_TYPE[value]));
									boolean isActive = hasActiveVariant
											? FlagsAspect.isSet(inputData.cuboid().getData7(AspectRegistry.FLAGS, new BlockAddress(baseX, baseY, baseZ)), FlagsAspect.FLAG_ACTIVE)
											: false
									;
									FacingDirection multiBlockDirection = FacingDirection.byteToDirection(inputData.cuboid().getData7(AspectRegistry.ORIENTATION, thisAddress));
									boolean isDown = (FacingDirection.DOWN == multiBlockDirection);
									if (isDown && blockModels.hasDownModel(includedBlock))
									{
										// If we have a special down-facing model for this block, use that without rotation (north).
										multiBlockDirection = FacingDirection.NORTH;
									}
									// Block-defined bytes are rare but they do usually involve different models.
									byte blockDefinedByte = inputData.cuboid().getData7(AspectRegistry.BLOCK_DEFINED_BYTE, thisAddress);
									float[] uv = blockModels.baseOfModelTexture(includedBlock, isActive, isDown, blockDefinedByte);
									
									BlockAddress blockAddress = new BlockAddress(baseX, baseY, baseZ);
									float[] auxUv = auxAtlas.baseOfTexture(variantMap.get(blockAddress));
									// We interpret the max of the adjacent blocks as the light value of a model (since it has interior surfaces on all sides).
									float[] blockLight = new float[] { _mapBlockLight(_getMaxAreaLight(inputData, baseX, baseY, baseZ)) };
									// Sky light never falls in this block but we still want to account for it so check the block above with partial lighting.
									float[] skyLight = new float[] { LightReadingHelpers.getSkyLightMultiplier(inputData, baseX, baseY, (byte)(baseZ + blockHeight), LightReadingHelpers.SKY_LIGHT_PARTIAL) };
									EntityLocation absoluteBase = inputData.cuboid().getCuboidAddress().getBase().relativeForBlock(blockAddress).toEntityLocation();
									
									SubBlockMesh subBlock = blockModels.getSubBlockMesh(includedBlock);
									if (null != subBlock)
									{
										// If this isn't a normal model buffer, it MUST be a sub-block, since we are filtering at the top.
										_renderSubBlock(builder
											, absoluteBase
											, uvCoordinateSize
											, auxCoordinateSize
											, uv
											, auxUv
											, subBlock
											, multiBlockDirection
											, blockLight
											, skyLight
										);
									}
									else
									{
										ModelBuffer bufferForType = blockModels.getModelForBlock(includedBlock, isActive, isDown, blockDefinedByte);
										_renderModel(builder
											, absoluteBase
											, uvCoordinateSize
											, auxCoordinateSize
											, uv
											, auxUv
											, bufferForType
											, multiBlockDirection
											, blockLight
											, skyLight
										);
									}
								}
							}
						}
					}
				}
			}
		}, (short)0);
	}

	public static void populateWaterMeshBufferForCuboid(Environment env
			, MeshHelperBufferBuilder builder
			, BasicBlockAtlas blockAtlas
			, AuxilliaryTextureAtlas auxAtlas
			, MeshInputData inputData
			, short sourceNumber
			, boolean drawInternalSurfaces
	)
	{
		Predicate<Short> shouldInclude = (Short value) -> {
			return (sourceNumber == value);
		};
		WaterSurfaceBuilder surface = new WaterSurfaceBuilder(sourceNumber);
		FaceBuilder faces = new FaceBuilder();
		_preSeed(faces
			, shouldInclude
			, new FaceBuilder.IEdgeWriter()
			{
				@Override
				public void writeEdgeValue(byte baseX, byte baseY, byte baseZ, short value, byte blockDefinedByte)
				{
					surface.setEdgeValue(baseX, baseY, baseZ, value, blockDefinedByte);
				}
			}
			, inputData
		);
		faces.populateMasks(inputData.cuboid(), shouldInclude);
		faces.buildFaces(inputData.cuboid(), surface);
		
		// For now, just use the same image for all faces.
		// (we assume liquids are never "active").
		boolean isActive = false;
		// (similarly, we only use block-defined for shape, not texture).
		byte blockDefinedByte = 0;
		float[] uvBase = blockAtlas.baseOfTopTexture(isActive, sourceNumber, blockDefinedByte);
		float textureSize = blockAtlas.getCoordinateSize();
		float[] auxUv = auxAtlas.baseOfTexture(AuxilliaryTextureAtlas.Variant.NONE);
		float auxTextureSize = auxAtlas.coordinateSize;
		
		AbsoluteLocation cuboidBase = inputData.cuboid().getCuboidAddress().getBase();
		surface.writeVertices(new WaterSurfaceBuilder.IQuadWriter() {
			float[] _base = new float[] { (float)cuboidBase.x(), (float)cuboidBase.y(), (float)cuboidBase.z() };
			@Override
			public void writeQuad(BlockAddress address, BlockAddress externalBlock, float[][] counterClockWiseVertices, float[] normal)
			{
				// We want to check the opacity since we won't draw the internal faces of the water if there is something opaque on the other side.
				// The exception to this rule is that we want to draw the top face of a liquid block.
				if ((1.0f == normal[2]) || !_isBlockOpaque(env, inputData, externalBlock))
				{
					// Liquids may be translucent or light emitters so we want to take the maximum of the external face light and the internal light.
					// (this avoids cases where lava is dark just because there is a partial block next to it).
					byte externalLight = LightReadingHelpers.getBlockLight(inputData, externalBlock.x(), externalBlock.y(), externalBlock.z());
					byte internalLight = LightReadingHelpers.getBlockLight(inputData, address.x(), address.y(), address.z());
					float blockLightMultiplier = _mapBlockLight((byte)Math.max(externalLight, internalLight));
					float skyLightMultiplier = LightReadingHelpers.getSkyLightMultiplier(inputData, externalBlock.x(), externalBlock.y(), externalBlock.z(), LightReadingHelpers.SKY_LIGHT_DIRECT);
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
						, false
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
							, true
						);
					}
				}
			}
		});
	}

	public static void populateOutlinePrism(GL20 gl
		, MeshHelperBufferBuilder builder
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
		
		// This is an outline so we want to increase the space around the prism before making the vertices.
		float outlineDistance = 0.01f;
		Prism outline = new Prism(prism.west() - outlineDistance
				, prism.south() - outlineDistance
				, prism.bottom() - outlineDistance
				, prism.east() + outlineDistance
				, prism.north() + outlineDistance
				, prism.top() + outlineDistance
		);
		
		// We always want the outline highlighted.
		float blockLightMultiplier = 1.0f;
		_buildCube(builder, uvBase, textureSize, auxUv, auxTextureSize, outline, blockLightMultiplier);
	}

	public static void drawPassiveStandingSquare(MeshHelperBufferBuilder builder
		, float itemEdge
		, float textureSize
	)
	{
		float[] uvBase = new float[] { 0.0f, 0.0f };
		
		// We don't use lighting for the passive items.
		float blockLightMultiplier = 0.0f;
		float skyLightMultiplier = 0.0f;
		
		// Note that standing squares never use AUX textures.
		float[] otherUvBase = new float[] { 0.0f, 0.0f };
		float otherTextureSize = 0.0f;
		
		// The idea here is that we draw 2 quads with the same texture:  One facing North and one South.
		float[] OI = new float[] {
			0.0f,
			0.0f,
			itemEdge,
		};
		float[] II = new float[] {
			itemEdge,
			0.0f,
			itemEdge,
		};
		float[] IO = new float[] {
			itemEdge,
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
		
		// Note that this draws the vertices around centre, such that the XY coordinates are offset, but Z is at the base.
		float halfEdge = itemEdge / 2.0f;
		float[] base = new float[] { -halfEdge, 0.0f, 0.0f};
		_populateQuad(builder, base
				, new float[][] { OO, IO, II, OI }
				, new float[] { 0.0f, 1.0f, 0.0f }
				, uvBase, textureSize
				, otherUvBase, otherTextureSize
				, blockLightMultipliers
				, skyLightMultipliers
				, false
		);
		_populateQuad(builder, base
				, new float[][] { IO, OO, OI, II }
				, new float[] { 0.0f, -1.0f, 0.0f }
				, uvBase, textureSize
				, otherUvBase, otherTextureSize
				, blockLightMultipliers
				, skyLightMultipliers
				, true
		);
	}

	public static void drawPassiveCube(MeshHelperBufferBuilder builder
		, float textureSize
	)
	{
		// As with above, the passive cube for "falling block" is also a bit of a hack due to our assumptions around
		// attributes.
		float[] uvBase = new float[] { 0.0f, 0.0f };
		
		// We will use no AUX texture.
		float[] auxUv = new float[] { 0.0f, 0.0f };
		float auxTextureSize = 0.0f;
		
		// These are always drawn as unit cubes.
		Prism outline = new Prism(0.0f
			, 0.0f
			, 0.0f
			, 1.0f
			, 1.0f
			, 1.0f
		);
		
		// Don't add any additional saturation to the cube.
		float blockLightMultiplier = 0.0f;
		_buildCube(builder, uvBase, textureSize, auxUv, auxTextureSize, outline, blockLightMultiplier);
	}

	public static void populateBurningFacesForCuboid(Environment env
		, MeshHelperBufferBuilder builder
		, BasicBlockAtlas blockAtlas
		, SparseByteCube fireFaces
		, AbsoluteLocation cuboidBase
	)
	{
		// We assume that the "air" block we are using as the block texture is the same on all sides.
		byte blockDefinedByte = 0;
		float airCoords[] = blockAtlas.baseOfSideTexture(false, env.special.AIR.item().number(), blockDefinedByte);
		float coordinateSize = blockAtlas.getCoordinateSize();
		
		// We rely on the fire texture being swapped between frames but is currently bound to the entire texture (might change for more animations, in the future).
		float fireCoords[] = new float[] {0.0f, 0.0f};
		float auxSize = 1.0f;
		
		// Other constants used in all cases.
		_PrismVertices prism = _PrismVertices.from(Prism.getBoundsAtOrigin(1.0f, 1.0f, 1.0f));
		float[] blockLightMultipliers = new float[] { 1.0f, 1.0f, 1.0f, 1.0f };
		float[] skyLightMultipliers = new float[] { 0.0f, 0.0f, 0.0f, 0.0f };
		boolean flipTexture = false;
		
		_IFaceWriter commonWriter = (float[] localBase, float[][] vertices, float[] normal) -> {
			_populateQuad(builder
				, localBase
				, vertices
				, normal
				, airCoords, coordinateSize
				, fireCoords, auxSize
				, blockLightMultipliers
				, skyLightMultipliers
				, flipTexture
			);
		};
		SparseByteCube.Walker walker = (int x, int y, int z, byte value) -> {
			float[] localBase = new float[] { (float)(cuboidBase.x() + x), (float)(cuboidBase.y() + y), (float)(cuboidBase.z() + z) };
			if (FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_UP))
			{
				// On the up face, we will draw the fire burning on top of the block.
				float blockIntersection = 0.2f;
				_PrismVertices upPrism = _PrismVertices.from(new Prism(0.2f
					, 0.2f
					, 1.0f - blockIntersection
					, 0.8f
					, 0.8f
					, 2.0f - blockIntersection
				));
				
				commonWriter.buildQuad(localBase
					, new float[][] {
						upPrism.v100, upPrism.v010, upPrism.v011, upPrism.v101
					}
					, new float[] {0.7f, 0.7f, 0.0f}
				);
				commonWriter.buildQuad(localBase
					, new float[][] {
						upPrism.v010, upPrism.v100, upPrism.v101, upPrism.v011
					}
					, new float[] {-0.7f, -0.7f, 0.0f}
				);
				commonWriter.buildQuad(localBase
					, new float[][] {
						upPrism.v000, upPrism.v110, upPrism.v111, upPrism.v001
					}
					, new float[] {0.7f, -0.7f, 0.0f}
				);
				commonWriter.buildQuad(localBase
					, new float[][] {
						upPrism.v110, upPrism.v000, upPrism.v001, upPrism.v111
					}
					, new float[] {-0.7f, 0.7f, 0.0f}
				);
			}
			if (FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_DOWN))
			{
				// We don't draw any fire on the down face.
			}
			if (FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_NORTH))
			{
				commonWriter.buildQuad(localBase
					, new float[][] {
						prism.v110, prism.v010, prism.v011, prism.v111
					}
					, new float[] {0.0f, 1.0f, 0.0f}
				);
			}
			if (FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_SOUTH))
			{
				commonWriter.buildQuad(localBase
					, new float[][] {
						prism.v000, prism.v100, prism.v101, prism.v001
					}
					, new float[] {0.0f, -1.0f,0.0f}
				);
			}
			if (FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_EAST))
			{
				commonWriter.buildQuad(localBase
					, new float[][] {
						prism.v100, prism.v110, prism.v111, prism.v101
					}
					, new float[] {1.0f, 0.0f, 0.0f}
				);
			}
			if (FireFaceBuilder.isBitSet(value, FireFaceBuilder.FACE_WEST))
			{
				commonWriter.buildQuad(localBase
					, new float[][] {
						prism.v010, prism.v000, prism.v001, prism.v011
					}
					, new float[] {-1.0f, 0.0f, 0.0f}
				);
			}
		};
		fireFaces.walkAllValues(walker, 0, 0, 0, Encoding.CUBOID_EDGE_SIZE);
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
		if (null != inputData.up())
		{
			faces.preSeedMasks(inputData.up(), shouldInclude, edgeWriter, zero, omit, omit, omit, omit, omit);
		}
		if (null != inputData.down())
		{
			faces.preSeedMasks(inputData.down(), shouldInclude, edgeWriter, omit, edge, omit, omit, omit, omit);
		}
		if (null != inputData.north())
		{
			faces.preSeedMasks(inputData.north(), shouldInclude, edgeWriter, omit, omit, zero, omit, omit, omit);
		}
		if (null != inputData.south())
		{
			faces.preSeedMasks(inputData.south(), shouldInclude, edgeWriter, omit, omit, omit, edge, omit, omit);
		}
		if (null != inputData.east())
		{
			faces.preSeedMasks(inputData.east(), shouldInclude, edgeWriter, omit, omit, omit, omit, zero, omit);
		}
		if (null != inputData.west())
		{
			faces.preSeedMasks(inputData.west(), shouldInclude, edgeWriter, omit, omit, omit, omit, omit, edge);
		}
	}

	private static void _populateQuad(MeshHelperBufferBuilder builder
			, float[] base
			, float[][] vertices
			, float[] normal
			, float[] uvBase
			, float textureSize
			, float[] otherUvBase
			, float otherTextureSize
			, float[] blockLightMultipliers
			, float[] skyLightMultipliers
			, boolean flipTexture
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
		float u = flipTexture ? (uvBase[0] + textureSize) : uvBase[0];
		float v = uvBase[1];
		float uEdge = flipTexture ? uvBase[0] : (u + textureSize);
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

	private static float _mapBlockLight(byte inputValue)
	{
		float maxLightFloat = (float)LightAspect.MAX_LIGHT;
		return LightReadingHelpers.MINIMUM_LIGHT + (((float)inputValue) / maxLightFloat);
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
		private final MeshHelperBufferBuilder _builder;
		private final AuxVariantMap _variantMap;
		private final BasicBlockAtlas _blockAtlas;
		private final AuxilliaryTextureAtlas _auxAtlas;
		private final FireFaceBuilder _fireFaces;
		private final Predicate<Short> _shouldInclude;
		private final _PrismVertices _v;
		private final MeshInputData _inputData;
		
		public _CommonVertexWriter(Environment env
			, MeshHelperBufferBuilder builder
			, AuxVariantMap variantMap
			, BasicBlockAtlas blockAtlas
			, AuxilliaryTextureAtlas auxAtlas
			, FireFaceBuilder fireFaces
			, Predicate<Short> shouldInclude
			, MeshInputData inputData
		)
		{
			_env = env;
			_builder = builder;
			_variantMap = variantMap;;
			_blockAtlas = blockAtlas;
			_auxAtlas = auxAtlas;
			_fireFaces = fireFaces;
			_shouldInclude = shouldInclude;
			_v = _PrismVertices.from(Prism.getBoundsAtOrigin(1.0f, 1.0f, 1.0f));
			_inputData = inputData;
		}
		@Override
		public boolean shouldInclude(short value)
		{
			return _shouldInclude.test(value);
		}
		@Override
		public void writeXYPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value, byte blockDefinedByte)
		{
			// Note that the Z-normal creates surfaces parallel to the ground so we will define "up" as "positive y".
			BlockAddress blockAddress = new BlockAddress(baseX, baseY, baseZ);
			boolean isActive = _isActive(baseX, baseY, baseZ, value);
			AbsoluteLocation absoluteBase = _inputData.cuboid().getCuboidAddress().getBase().relativeForBlock(blockAddress);
			float[] localBase = new float[] { (float)absoluteBase.x(), (float)absoluteBase.y(), (float)absoluteBase.z() };
			float[] uvBaseTop = _blockAtlas.baseOfTopTexture(isActive, value, blockDefinedByte);
			float[] uvBaseBottom = _blockAtlas.baseOfBottomTexture(isActive, value, blockDefinedByte);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			AuxilliaryTextureAtlas.Variant variant = _variantMap.get(blockAddress);
			float[] auxUv = _auxAtlas.baseOfTexture(variant);
			
			byte z = (byte)(baseZ + (isPositiveNormal ? 1 : -1));
			byte westX = (byte)(baseX - 1);
			byte eastX = (byte)(baseX + 1);
			byte southY = (byte)(baseY - 1);
			byte northY = (byte)(baseY + 1);
			byte thisBlockLight = LightReadingHelpers.getBlockLight(_inputData, baseX, baseY, z);
			byte eastBlockLight = LightReadingHelpers.getBlockLight(_inputData, eastX, baseY, z);
			byte westBlockLight = LightReadingHelpers.getBlockLight(_inputData, westX, baseY, z);
			byte northBlockLight = LightReadingHelpers.getBlockLight(_inputData, baseX, northY, z);
			byte southBlockLight = LightReadingHelpers.getBlockLight(_inputData, baseX, southY, z);
			byte SWBlockLight = LightReadingHelpers.getBlockLight(_inputData, westX, southY, z);
			byte SEBlockLight = LightReadingHelpers.getBlockLight(_inputData, eastX, southY, z);
			byte NWBlockLight = LightReadingHelpers.getBlockLight(_inputData, westX, northY, z);
			byte NEBlockLight = LightReadingHelpers.getBlockLight(_inputData, eastX, northY, z);
			
			if (isPositiveNormal)
			{
				
				// We handle sky slight specially for z+ faces, since the sky is in that direction.
				// We actually want to average the 4 block faces adjacent to each corner, in this case.
				float skySW = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, westX, southY, z);
				float skyS = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, baseX, southY, z);
				float skySE = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, eastX, southY, z);
				float skyW = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, westX, baseY, z);
				float sky = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, baseX, baseY, z);
				float skyE = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, eastX, baseY, z);
				float skyNW = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, westX, northY, z);
				float skyN = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, baseX, northY, z);
				float skyNE = LightReadingHelpers.getUpFacingSkyMultipler(_inputData, eastX, northY, z);
				
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
					, false
				);
			}
			else
			{
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
					, new float[] {LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.SKY_LIGHT_SHADOW}
					, false
				);
			}
			
			// Track any burning faces.
			if (_variantMap.isBurning(blockAddress))
			{
				byte bit = isPositiveNormal
					? FireFaceBuilder.FACE_UP
					: FireFaceBuilder.FACE_DOWN
				;
				_fireFaces.setBit(baseX, baseY, baseZ, bit);
			}
		}
		@Override
		public void writeXZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value, byte blockDefinedByte)
		{
			BlockAddress blockAddress = new BlockAddress(baseX, baseY, baseZ);
			AbsoluteLocation absoluteBase = _inputData.cuboid().getCuboidAddress().getBase().relativeForBlock(blockAddress);
			float[] localBase = new float[] { (float)absoluteBase.x(), (float)absoluteBase.y(), (float)absoluteBase.z() };
			boolean isActive = _isActive(baseX, baseY, baseZ, value);
			float[] uvBaseSide = _blockAtlas.baseOfSideTexture(isActive, value, blockDefinedByte);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			AuxilliaryTextureAtlas.Variant variant = _variantMap.get(blockAddress);
			float[] auxUv = _auxAtlas.baseOfTexture(variant);
			
			byte y = (byte)(baseY + (isPositiveNormal ? 1 : -1));
			byte westX = (byte)(baseX - 1);
			byte eastX = (byte)(baseX + 1);
			byte downZ = (byte)(baseZ - 1);
			byte upZ = (byte)(baseZ + 1);
			byte thisBlockLight = LightReadingHelpers.getBlockLight(_inputData, baseX, y, baseZ);
			byte eastBlockLight = LightReadingHelpers.getBlockLight(_inputData, eastX, y, baseZ);
			byte westBlockLight = LightReadingHelpers.getBlockLight(_inputData, westX, y, baseZ);
			byte upBlockLight = LightReadingHelpers.getBlockLight(_inputData, baseX, y, upZ);
			byte downBlockLight = LightReadingHelpers.getBlockLight(_inputData, baseX, y, downZ);
			byte WDBlockLight = LightReadingHelpers.getBlockLight(_inputData, westX, y, downZ);
			byte WUBlockLight = LightReadingHelpers.getBlockLight(_inputData, westX, y, upZ);
			byte EDBlockLight = LightReadingHelpers.getBlockLight(_inputData, eastX, y, downZ);
			byte EUBlockLight = LightReadingHelpers.getBlockLight(_inputData, eastX, y, upZ);
			float skyLightMultiplier = LightReadingHelpers.getSkyLightMultiplier(_inputData, baseX, y, baseZ, LightReadingHelpers.SKY_LIGHT_PARTIAL);
			float[] commonSkyLightMultipliers = new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier};
			
			if (isPositiveNormal)
			{
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
					, commonSkyLightMultipliers
					, false
				);
			}
			else
			{
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
					, commonSkyLightMultipliers
					, false
				);
			}
			
			// Track any burning faces.
			if (_variantMap.isBurning(blockAddress))
			{
				byte bit = isPositiveNormal
					? FireFaceBuilder.FACE_NORTH
					: FireFaceBuilder.FACE_SOUTH
				;
				_fireFaces.setBit(baseX, baseY, baseZ, bit);
			}
		}
		@Override
		public void writeYZPlane(byte baseX, byte baseY, byte baseZ, boolean isPositiveNormal, short value, byte blockDefinedByte)
		{
			BlockAddress blockAddress = new BlockAddress(baseX, baseY, baseZ);
			AbsoluteLocation absoluteBase = _inputData.cuboid().getCuboidAddress().getBase().relativeForBlock(blockAddress);
			float[] localBase = new float[] { (float)absoluteBase.x(), (float)absoluteBase.y(), (float)absoluteBase.z() };
			boolean isActive = _isActive(baseX, baseY, baseZ, value);
			float[] uvBaseSide = _blockAtlas.baseOfSideTexture(isActive, value, blockDefinedByte);
			float uvCoordinateSize = _blockAtlas.getCoordinateSize();
			AuxilliaryTextureAtlas.Variant variant = _variantMap.get(blockAddress);
			float[] auxUv = _auxAtlas.baseOfTexture(variant);
			
			byte x = (byte)(baseX + (isPositiveNormal ? 1 : -1));
			byte southY = (byte)(baseY - 1);
			byte northY = (byte)(baseY + 1);
			byte downZ = (byte)(baseZ - 1);
			byte upZ = (byte)(baseZ + 1);
			byte thisBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, baseY, baseZ);
			byte northBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, northY, baseZ);
			byte southBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, southY, baseZ);
			byte upBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, baseY, upZ);
			byte downBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, baseY, downZ);
			byte SDBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, southY, downZ);
			byte SUBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, southY, upZ);
			byte NDBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, northY, downZ);
			byte NUBlockLight = LightReadingHelpers.getBlockLight(_inputData, x, northY, upZ);
			float skyLightMultiplier = LightReadingHelpers.getSkyLightMultiplier(_inputData, x, baseY, baseZ, LightReadingHelpers.SKY_LIGHT_PARTIAL);
			float[] commonSkyLightMultipliers = new float[] {skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier};
			
			if (isPositiveNormal)
			{
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
					, commonSkyLightMultipliers
					, false
				);
			}
			else
			{
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
					, commonSkyLightMultipliers
					, false
				);
			}
			
			// Track any burning faces.
			if (_variantMap.isBurning(blockAddress))
			{
				byte bit = isPositiveNormal
					? FireFaceBuilder.FACE_EAST
					: FireFaceBuilder.FACE_WEST
				;
				_fireFaces.setBit(baseX, baseY, baseZ, bit);
			}
		}
		private boolean _isActive(byte baseX, byte baseY, byte baseZ, short value)
		{
			boolean hasActiveVariant = _env.blocks.hasActiveVariant(_env.blocks.fromItem(_env.items.ITEMS_BY_TYPE[value]));
			boolean isActive = hasActiveVariant
					? FlagsAspect.isSet(_inputData.cuboid().getData7(AspectRegistry.FLAGS, new BlockAddress(baseX, baseY, baseZ)), FlagsAspect.FLAG_ACTIVE)
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
		byte centre = LightReadingHelpers.getBlockLight(data, baseX, baseY, baseZ);
		byte xm = LightReadingHelpers.getBlockLight(data, (byte)(baseX - 1), baseY, baseZ);
		byte xp = LightReadingHelpers.getBlockLight(data, (byte)(baseX + 1), baseY, baseZ);
		byte ym = LightReadingHelpers.getBlockLight(data, baseX, (byte)(baseY - 1), baseZ);
		byte yp = LightReadingHelpers.getBlockLight(data, baseX, (byte)(baseY + 1), baseZ);
		byte zm = LightReadingHelpers.getBlockLight(data, baseX, baseY, (byte)(baseZ - 1));
		byte zp = LightReadingHelpers.getBlockLight(data, baseX, baseY, (byte)(baseZ + 1));
		
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

	private static boolean _isBlockOpaque(Environment env, MeshInputData data, BlockAddress address)
	{
		Block blockType;
		if (address.x() < 0)
		{
			if (null != data.west())
			{
				blockType = BlockProxy.load(new BlockAddress((byte)(address.x() + Encoding.CUBOID_EDGE_SIZE), address.y(), address.z()), data.west()).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.x() >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.east())
			{
				blockType = BlockProxy.load(new BlockAddress((byte)(address.x() - Encoding.CUBOID_EDGE_SIZE), address.y(), address.z()), data.east()).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.y() < 0)
		{
			if (null != data.south())
			{
				blockType = BlockProxy.load(new BlockAddress(address.x(), (byte)(address.y() + Encoding.CUBOID_EDGE_SIZE), address.z()), data.south()).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.y() >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.north())
			{
				blockType = BlockProxy.load(new BlockAddress(address.x(), (byte)(address.y() - Encoding.CUBOID_EDGE_SIZE), address.z()), data.north()).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.z() < 0)
		{
			if (null != data.down())
			{
				blockType = BlockProxy.load(new BlockAddress(address.x(), address.y(), (byte)(address.z() + Encoding.CUBOID_EDGE_SIZE)), data.down()).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else if (address.z() >= Encoding.CUBOID_EDGE_SIZE)
		{
			if (null != data.up())
			{
				blockType = BlockProxy.load(new BlockAddress(address.x(), address.y(), (byte)(address.z() - Encoding.CUBOID_EDGE_SIZE)), data.up()).getBlock();
			}
			else
			{
				blockType = null;
			}
		}
		else
		{
			blockType = BlockProxy.load(address, data.cuboid()).getBlock();
		}
		return (null != blockType)
				? (LightAspect.OPAQUE ==  env.lighting.getOpacity(blockType))
				: false
		;
	}

	private static void _renderModel(BufferBuilder builder
		, EntityLocation absoluteBase
		, float uvCoordinateSize
		, float auxCoordinateSize
		, float[] uv
		, float[] auxUv
		, ModelBuffer bufferForType
		, FacingDirection multiBlockDirection
		, float[] blockLight
		, float[] skyLight
	)
	{
		// The models are based in the 0-1 unit cube but we want to rotate around the centre so translate by X/Y.
		float centreX = 0.5f;
		float centreY = 0.5f;
		float centreZ = 0.5f;
		for (int i = 0; i < bufferForType.vertexCount; ++i)
		{
			float x = bufferForType.positionValues[3 * i + 0];
			float y = bufferForType.positionValues[3 * i + 1];
			float z = bufferForType.positionValues[3 * i + 2];
			if (FacingDirection.NORTH != multiBlockDirection)
			{
				float[] out = multiBlockDirection.rotateTripletAboutZ(new float[] { x - centreX, y - centreY, z - centreZ });
				x = out[0] + centreY;
				y = out[1] + centreY;
				z = out[2] + centreZ;
			}
			
			// Each element is:
			// vx, vy, vz
			// nx, ny, nz
			// u, v
			// otherU, otherV
			// blockLight
			float[] positions = new float[] {
				absoluteBase.x() + x,
				absoluteBase.y() + y,
				absoluteBase.z() + z,
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

	private static void _renderSubBlock(BufferBuilder builder
		, EntityLocation absoluteBase
		, float uvCoordinateSize
		, float auxCoordinateSize
		, float[] uv
		, float[] auxUv
		, SubBlockMesh subBlock
		, FacingDirection rotation
		, float[] blockLight
		, float[] skyLight
	)
	{
		// We need to walk each of the 6 faces (we know that the "up" is the last direct face).
		for (FacingDirection face : FacingDirection.values())
		{
			if (face.ordinal() <= FacingDirection.UP.ordinal())
			{
				_renderSubBlockFace(builder
					, face
					, absoluteBase
					, uvCoordinateSize
					, auxCoordinateSize
					, uv
					, auxUv
					, subBlock
					, rotation
					, blockLight
					, skyLight
				);
			}
		}
	}

	private static void _renderSubBlockFace(BufferBuilder builder
		, FacingDirection faceDirection
		, EntityLocation absoluteBase
		, float uvCoordinateSize
		, float auxCoordinateSize
		, float[] uv
		, float[] auxUv
		, SubBlockMesh subBlock
		, FacingDirection rotation
		, float[] blockLight
		, float[] skyLight
	)
	{
		// We need to walk each of the 6 faces.
		List<SubBlockMesh.Face> upFaces = subBlock.getFaces(faceDirection, rotation);
		
		// We use the same normal for the entire face, which we can derive from the output direction of the face.
		EntityLocation normalLocation = faceDirection.getOutputBlockLocation(new AbsoluteLocation(0, 0, 0)).toEntityLocation();
		float[] normal = new float[] { normalLocation.x(), normalLocation.y(), normalLocation.z() };
		
		// The texture offsets we will use are also derived by which face we are rendering.
		int uIndex;
		int vIndex;
		switch (faceDirection)
		{
		case WEST:
			uIndex = 1;
			vIndex = 2;
			break;
		case EAST:
			uIndex = 1;
			vIndex = 2;
			break;
		case SOUTH:
			uIndex = 0;
			vIndex = 2;
			break;
		case NORTH:
			uIndex = 0;
			vIndex = 2;
			break;
		case DOWN:
			uIndex = 0;
			vIndex = 1;
			break;
		case UP:
			uIndex = 0;
			vIndex = 1;
			break;
		default:
			throw Assert.unreachable();
		}
		
		for (SubBlockMesh.Face face : upFaces)
		{
			// Find the quad to determine rotation.
			float minX = Math.min(face.base3[0], face.edge3[0]);
			float maxX = Math.max(face.base3[0], face.edge3[0]);
			float minY = Math.min(face.base3[1], face.edge3[1]);
			float maxY = Math.max(face.base3[1], face.edge3[1]);
			float minZ = Math.min(face.base3[2], face.edge3[2]);
			float maxZ = Math.max(face.base3[2], face.edge3[2]);
			
			// We want to select the counter-clockwise vertices we will render, which requires face-specific logic (similar to _buildCube, below).
			float[] v0;
			float[] v1;
			float[] v2;
			float[] v3;
			switch (faceDirection)
			{
			case WEST:
				v0 = new float[] {minX, maxY, minZ};
				v1 = new float[] {minX, minY, minZ};
				v2 = new float[] {minX, minY, maxZ};
				v3 = new float[] {minX, maxY, maxZ};
				break;
			case EAST:
				v0 = new float[] {minX, minY, minZ};
				v1 = new float[] {minX, maxY, minZ};
				v2 = new float[] {minX, maxY, maxZ};
				v3 = new float[] {minX, minY, maxZ};
				break;
			case SOUTH:
				v0 = new float[] {minX, minY, minZ};
				v1 = new float[] {maxX, minY, minZ};
				v2 = new float[] {maxX, minY, maxZ};
				v3 = new float[] {minX, minY, maxZ};
				break;
			case NORTH:
				v0 = new float[] {maxX, minY, minZ};
				v1 = new float[] {minX, minY, minZ};
				v2 = new float[] {minX, minY, maxZ};
				v3 = new float[] {maxX, minY, maxZ};
				break;
			case DOWN:
				v0 = new float[] {maxX, minY, minZ};
				v1 = new float[] {minX, minY, minZ};
				v2 = new float[] {minX, maxY, minZ};
				v3 = new float[] {maxX, maxY, minZ};
				break;
			case UP:
				v0 = new float[] {minX, minY, minZ};
				v1 = new float[] {maxX, minY, minZ};
				v2 = new float[] {maxX, maxY, minZ};
				v3 = new float[] {minX, maxY, minZ};
				break;
			default:
				throw Assert.unreachable();
			}
			
			float[][] coords = new float[][] {
				v0, v1, v2,
				v0, v2, v3,
			};
			for (float[] coord : coords)
			{
				// Each element is:
				// vx, vy, vz
				// nx, ny, nz
				// u, v
				// aux_U, aux_V
				// blockLight
				// skyLight
				float[] positions = new float[] {
					absoluteBase.x() + coord[0],
					absoluteBase.y() + coord[1],
					absoluteBase.z() + coord[2],
				};
				float[] textures = new float[] {
					uv[0] + (coord[uIndex] * uvCoordinateSize),
					uv[1] + (coord[vIndex] * uvCoordinateSize),
				};
				float[] otherTextures = new float[] {
					auxUv[0] + (coord[uIndex] * auxCoordinateSize),
					auxUv[1] + (coord[vIndex] * auxCoordinateSize),
				};
				
				builder.appendVertex(positions
					, normal
					, textures
					, otherTextures
					, blockLight
					, skyLight
				);
			}
		}
	}

	private static void _buildCube(MeshHelperBufferBuilder builder
		, float[] uvBase
		, float textureSize
		, float[] auxUv
		, float auxTextureSize
		, Prism outline
		, float blockLightMultiplier
	)
	{
		// Note that no matter the scale, the quad vertices are the same magnitudes.
		_PrismVertices v = _PrismVertices.from(outline);
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
			, false
		);
		_populateQuad(builder, base, new float[][] {
				v.v100, v.v110, v.v111, v.v101
			}, new float[] {1.0f, 0.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
			, false
		);
		
		// Y-normal plane.
		_populateQuad(builder, base, new float[][] {
				v.v000, v.v100, v.v101, v.v001
			}, new float[] {0.0f, -1.0f,0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
			, false
		);
		_populateQuad(builder, base, new float[][] {
				v.v110, v.v010, v.v011, v.v111
			}, new float[] {0.0f, 1.0f, 0.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
			, false
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
			, false
		);
		_populateQuad(builder, base, new float[][] {
				v.v001, v.v101, v.v111, v.v011
			}, new float[] {0.0f, 0.0f, 1.0f}
			, uvBase, textureSize
			, auxUv, auxTextureSize
			, blockLightMultipliers
			, skyLightMultipliers
			, false
		);
	}

	private static interface _IFaceWriter
	{
		public void buildQuad(float[] localBase, float[][] vertices, float[] normal);
	}
}
