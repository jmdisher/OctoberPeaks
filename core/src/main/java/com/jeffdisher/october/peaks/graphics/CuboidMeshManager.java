package com.jeffdisher.october.peaks.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
	private final IGpu _gpu;
	private final Attribute[] _programAttributes;
	private final TextureAtlas<ItemVariant> _itemAtlas;
	private final TextureAtlas<BlockVariant> _blockTextures;
	private final TextureAtlas<SceneMeshHelpers.AuxVariant> _auxBlockTextures;
	private final Map<CuboidAddress, CuboidData> _cuboids;
	private final FloatBuffer _meshBuffer;
	private final short[] _itemToBlockIndexMapper;

	public CuboidMeshManager(Environment env
			, IGpu gpu
			, Attribute[] programAttributes
			, TextureAtlas<ItemVariant> itemAtlas
			, TextureAtlas<BlockVariant> blockTextures
			, TextureAtlas<SceneMeshHelpers.AuxVariant> auxBlockTextures
	)
	{
		_env = env;
		_gpu = gpu;
		_programAttributes = programAttributes;;
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

	public Collection<CuboidData> viewCuboids()
	{
		return _cuboids.values();
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		// Delete any previous.
		CuboidAddress address = cuboid.getCuboidAddress();
		_removeCuboid(address);
		
		// Collect information about the cuboid.
		SparseShortProjection<SceneMeshHelpers.AuxVariant> variantProjection = SceneMeshHelpers.buildAuxProjection(_env, cuboid);
		
		BufferBuilder builder = new BufferBuilder(_meshBuffer, _programAttributes);
		
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
			VertexArray opaqueData = (null != opaqueBuffer) ? _gpu.uploadBuffer(opaqueBuffer) : null;
			VertexArray itemsOnGroundArray = (null != itemsOnGroundBuffer) ? _gpu.uploadBuffer(itemsOnGroundBuffer) : null;
			VertexArray transparentData = (null != transparentBuffer) ? _gpu.uploadBuffer(transparentBuffer) : null;
			_cuboids.put(address, new CuboidData(address, opaqueData, itemsOnGroundArray, transparentData));
		}
	}

	public void removeCuboid(CuboidAddress address)
	{
		_removeCuboid(address);
	}

	public void processBackground()
	{
		// TODO:  Implement.
	}

	public void shutdown()
	{
		for (CuboidData data : _cuboids.values())
		{
			_deleteBuffers(data);
		}
		_cuboids.clear();
	}


	private void _removeCuboid(CuboidAddress address)
	{
		CuboidData previous = _cuboids.remove(address);
		if (null != previous)
		{
			_deleteBuffers(previous);
		}
	}

	private void _deleteBuffers(CuboidData previous)
	{
		if (null != previous.opaqueArray)
		{
			_gpu.deleteBuffer(previous.opaqueArray);
		}
		if (null != previous.itemsOnGroundArray)
		{
			_gpu.deleteBuffer(previous.itemsOnGroundArray);
		}
		if (null != previous.transparentArray)
		{
			_gpu.deleteBuffer(previous.transparentArray);
		}
	}


	/**
	 * This interface replaces the direct dependency on the GL20 object in order to enable testing.
	 */
	public static interface IGpu
	{
		VertexArray uploadBuffer(BufferBuilder.Buffer buffer);
		void deleteBuffer(VertexArray array);
	}

	public static record CuboidData(CuboidAddress address
			, VertexArray opaqueArray
			, VertexArray itemsOnGroundArray
			, VertexArray transparentArray
	) {}
}
