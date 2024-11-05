package com.jeffdisher.october.peaks.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.BlockVariant;
import com.jeffdisher.october.peaks.ItemVariant;
import com.jeffdisher.october.peaks.SparseShortProjection;
import com.jeffdisher.october.peaks.TextureAtlas;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;


/**
 * Currently just contains the cuboids loaded in the system but is mostly in place to introduce a design which will
 * later allow for background cuboid mesh generation (as this is a heavy-weight operation which should be pulled out of
 * the main thread).
 */
public class CuboidMeshManager
{
	public static final int BUFFER_SIZE = 64 * 1024 * 1024;

	private final Environment _env;
	private final GL20 _gl;
	private final Program _cuboidRenderProgram;
	private final TextureAtlas<ItemVariant> _itemAtlas;
	private final TextureAtlas<BlockVariant> _blockTextures;
	private final TextureAtlas<SceneMeshHelpers.AuxVariant> _auxBlockTextures;
	private final Map<CuboidAddress, CuboidData> _cuboids;
	private final FloatBuffer _meshBuffer;
	private final short[] _itemToBlockIndexMapper;

	public CuboidMeshManager(Environment env
			, GL20 gl
			, Program cuboidRenderProgram
			, TextureAtlas<ItemVariant> itemAtlas
			, TextureAtlas<BlockVariant> blockTextures
			, TextureAtlas<SceneMeshHelpers.AuxVariant> auxBlockTextures
	)
	{
		_env = env;
		_gl = gl;
		_cuboidRenderProgram = cuboidRenderProgram;
		_itemAtlas = itemAtlas;
		_blockTextures = blockTextures;
		_auxBlockTextures = auxBlockTextures;
		
		_cuboids = new HashMap<>();
		ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
		direct.order(ByteOrder.nativeOrder());
		_meshBuffer = direct.asFloatBuffer();
		
		// Extract the items which are blocks and create the index mapping function so we can pack the block atlas.
		short[] itemToBlockMap = new short[_env.items.ITEMS_BY_TYPE.length];
		short nextIndex = 0;
		for (int i = 0; i < _env.items.ITEMS_BY_TYPE.length; ++ i)
		{
			Item item = _env.items.ITEMS_BY_TYPE[i];
			if (null != _env.blocks.fromItem(item))
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
	}

	public Map<CuboidAddress, CuboidData> viewCuboids()
	{
		return Collections.unmodifiableMap(_cuboids);
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		// Delete any previous.
		CuboidAddress address = cuboid.getCuboidAddress();
		_removeCuboid(address);
		
		// Collect information about the cuboid.
		SparseShortProjection<SceneMeshHelpers.AuxVariant> variantProjection = SceneMeshHelpers.buildAuxProjection(_env, cuboid);
		
		BufferBuilder builder = new BufferBuilder(_meshBuffer, _cuboidRenderProgram.attributes);
		
		// Create the opaque cuboid vertices.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env, builder, _blockTextures, variantProjection, _auxBlockTextures, _itemToBlockIndexMapper, cuboid, true);
		BufferBuilder.Buffer opaqueBuffer = builder.finishOne();
		
		// Create the vertex array for any items dropped on the ground.
		SceneMeshHelpers.populateMeshForDroppedItems(_env, builder, _itemAtlas, _auxBlockTextures, cuboid);
		BufferBuilder.Buffer itemsOnGroundBuffer = builder.finishOne();
		
		// Create the transparent cuboid vertices.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env, builder, _blockTextures, variantProjection, _auxBlockTextures, _itemToBlockIndexMapper, cuboid, false);
		BufferBuilder.Buffer transparentBuffer = builder.finishOne();
		
		if ((null != opaqueBuffer) || (null != itemsOnGroundBuffer) || (null != transparentBuffer))
		{
			VertexArray opaqueData = (null != opaqueBuffer) ? opaqueBuffer.flush(_gl) : null;
			VertexArray itemsOnGroundArray = (null != itemsOnGroundBuffer) ? itemsOnGroundBuffer.flush(_gl) : null;
			VertexArray transparentData = (null != transparentBuffer) ? transparentBuffer.flush(_gl) : null;
			_cuboids.put(address, new CuboidData(opaqueData, itemsOnGroundArray, transparentData));
		}
	}

	public void removeCuboid(CuboidAddress address)
	{
		_removeCuboid(address);
	}


	private void _removeCuboid(CuboidAddress address)
	{
		CuboidData previous = _cuboids.remove(address);
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
	}


	public static record CuboidData(VertexArray opaqueArray
			, VertexArray itemsOnGroundArray
			, VertexArray transparentArray
	) {}
}
