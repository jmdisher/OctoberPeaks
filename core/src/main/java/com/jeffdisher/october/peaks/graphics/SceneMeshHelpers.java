package com.jeffdisher.october.peaks.graphics;

import java.nio.FloatBuffer;
import java.util.Iterator;

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
			, boolean opaqueVertices
	)
	{
		float textureSize = blockAtlas.coordinateSize;
		float auxTextureSize = auxAtlas.coordinateSize;
		
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
					float[] baseCoord = new float[] { (float)base.x(), (float)base.y(), (float)base.z()};
					
					// Note that no matter the scale, the quad vertices are the same magnitudes.
					_PrismVertices v = _PrismVertices.from(new float[] { 0.0f, 0.0f, 0.0f }, new float[] { 1.0f, 1.0f, 1.0f });
					
					// We will fill in each quad by multiple instances, offset by different bases, by tiling along each plane up to scale.
					// We subtract one from the base scale since we would double-count the top "1.0f".
					byte oppositeBase = (byte)(size - 1);
					float baseScale = (float)oppositeBase;
					
					// X-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						float zBase = baseCoord[2] + (float)z;
						for (byte y = 0; y < size; ++y)
						{
							float yBase = baseCoord[1] + (float)y;
							float[] localBase = new float[] { baseCoord[0], yBase, zBase};
							float[] auxUv = auxAtlas.baseOfTexture((short)0, projection.get(new BlockAddress((byte)(base.x()), (byte)(base.y() + y), (byte)(base.z() + z))));
							_populateQuad(builder, localBase, new float[][] {
									v.v010, v.v000, v.v001, v.v011
								}, new float[] {-1.0f, 0.0f, 0.0f}
								, uvBaseSide, textureSize
								, auxUv, auxTextureSize
							);
							localBase[0] += baseScale;
							auxUv = auxAtlas.baseOfTexture((short)0, projection.get(new BlockAddress((byte)(base.x() + oppositeBase), (byte)(base.y() + y), (byte)(base.z() + z))));
							_populateQuad(builder, localBase, new float[][] {
									v.v100, v.v110, v.v111, v.v101
								}, new float[] {1.0f, 0.0f, 0.0f}
								, uvBaseSide, textureSize
								, auxUv, auxTextureSize
							);
						}
					}
					// Y-normal plane.
					for (byte z = 0; z < size; ++z)
					{
						float zBase = baseCoord[2] + (float)z;
						for (byte x = 0; x < size; ++x)
						{
							float xBase = baseCoord[0] + (float)x;
							float[] localBase = new float[] { xBase, baseCoord[1], zBase};
							float[] auxUv = auxAtlas.baseOfTexture((short)0, projection.get(new BlockAddress((byte)(base.x() + x), (byte)(base.y()), (byte)(base.z() + z))));
							_populateQuad(builder, localBase, new float[][] {
									v.v000, v.v100, v.v101, v.v001
								}, new float[] {0.0f, -1.0f,0.0f}
								, uvBaseSide, textureSize
								, auxUv, auxTextureSize
							);
							localBase[1] += baseScale;
							auxUv = auxAtlas.baseOfTexture((short)0, projection.get(new BlockAddress((byte)(base.x() + x), (byte)(base.y() + oppositeBase), (byte)(base.z() + z))));
							_populateQuad(builder, localBase, new float[][] {
									v.v110, v.v010, v.v011, v.v111
								}, new float[] {0.0f, 1.0f, 0.0f}
								, uvBaseSide, textureSize
								, auxUv, auxTextureSize
							);
						}
					}
					// Z-normal plane.
					// Note that the Z-normal creates surfaces parallel to the ground so we will define "up" as "positive y".
					for (byte y = 0; y < size; ++y)
					{
						float yBase = baseCoord[1] + (float)y;
						for (byte x = 0; x < size; ++x)
						{
							float xBase = baseCoord[0] + (float)x;
							float[] localBase = new float[] { xBase, yBase, baseCoord[2]};
							float[] auxUv = auxAtlas.baseOfTexture((short)0, projection.get(new BlockAddress((byte)(base.x() + x), (byte)(base.y() + y), (byte)(base.z()))));
							_populateQuad(builder, localBase, new float[][] {
									v.v100, v.v000, v.v010, v.v110
								}, new float[] {0.0f, 0.0f, -1.0f}
								, uvBaseBottom, textureSize
								, auxUv, auxTextureSize
							);
							localBase[2] += baseScale;
							auxUv = auxAtlas.baseOfTexture((short)0, projection.get(new BlockAddress((byte)(base.x() + x), (byte)(base.y() + y), (byte)(base.z() + oppositeBase))));
							_populateQuad(builder, localBase, new float[][] {
									v.v001, v.v101, v.v111, v.v011
								}, new float[] {0.0f, 0.0f, 1.0f}
								, uvBaseTop, textureSize
								, auxUv, auxTextureSize
							);
						}
					}
				}
			}
		}, (short)0);
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
}
