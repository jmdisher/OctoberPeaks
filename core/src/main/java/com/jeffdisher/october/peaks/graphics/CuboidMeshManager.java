package com.jeffdisher.october.peaks.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.BlockVariant;
import com.jeffdisher.october.peaks.ItemVariant;
import com.jeffdisher.october.peaks.SparseShortProjection;
import com.jeffdisher.october.peaks.TextureAtlas;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Currently just contains the cuboids loaded in the system but is mostly in place to introduce a design which will
 * later allow for background cuboid mesh generation (as this is a heavy-weight operation which should be pulled out of
 * the main thread).
 */
public class CuboidMeshManager
{
	public static final int SCRATCH_BUFFER_COUNT = 2;
	public static final int BUFFER_SIZE = 64 * 1024 * 1024;

	private final Environment _env;
	private final IGpu _gpu;
	private final Attribute[] _programAttributes;
	private final TextureAtlas<ItemVariant> _itemAtlas;
	private final TextureAtlas<BlockVariant> _blockTextures;
	private final TextureAtlas<SceneMeshHelpers.AuxVariant> _auxBlockTextures;
	private final Map<CuboidAddress, _InternalData> _cuboids;
	private final Queue<FloatBuffer> _scratchGraphicsBuffers;
	private final short[] _itemToBlockIndexMapper;

	// Objects related to the handoff.
	private boolean _keepRunning;
	private final Queue<_Request> _requests;
	private final Queue<_Response> _responses;
	private final Thread _background;

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
		_scratchGraphicsBuffers = new LinkedList<>();
		for (int i = 0; i < SCRATCH_BUFFER_COUNT; ++i)
		{
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer buffer = direct.asFloatBuffer();
			_scratchGraphicsBuffers.add(buffer);
		}
		
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
		
		// Setup the background processing thread.
		_keepRunning = true;
		_requests = new LinkedList<>();
		_responses = new LinkedList<>();
		_background = new Thread(() -> _backgroundMain()
				, "Cuboid Mesh Baking Thread"
		);
		_background.start();
	}

	public Collection<CuboidData> viewCuboids()
	{
		return _cuboids.values().stream()
				.map((_InternalData internal) -> internal.data)
				.collect(Collectors.toList())
		;
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		// We want to create the _InternalData instance of a newer generation, potentially just a placeholder for empty data.
		CuboidAddress address = cuboid.getCuboidAddress();
		_InternalData existing = _cuboids.remove(address);
		_InternalData internal;
		if (null != existing)
		{
			internal = new _InternalData(true
					, cuboid
					, existing.data
			);
		}
		else
		{
			internal = new _InternalData(true
					, cuboid
					, new CuboidData(address, null, null, null, null)
			);
		}
		_cuboids.put(address, internal);
	}

	public void removeCuboid(CuboidAddress address)
	{
		_removeCuboid(address);
	}

	public void processBackground()
	{
		// First, see if anything has come back to us.
		_Response response = _dequeueResponse();
		while (null != response)
		{
			CuboidAddress address = response.cuboid.getCuboidAddress();
			_InternalData internal = _cuboids.remove(address);
			
			// We only replace this if it wasn't deleted.
			if (null != internal)
			{
				// Delete the old GPU resources.
				_deleteBuffers(internal.data);
				
				// We will still store an empty CuboidData if all of these are null, just for simplicity.
				VertexArray opaqueData = (null != response.opaqueBuffer) ? _gpu.uploadBuffer(response.opaqueBuffer) : null;
				VertexArray itemsOnGroundArray = (null != response.itemsOnGroundBuffer) ? _gpu.uploadBuffer(response.itemsOnGroundBuffer) : null;
				VertexArray transparentData = (null != response.transparentBuffer) ? _gpu.uploadBuffer(response.transparentBuffer) : null;
				VertexArray waterData = (null != response.waterBuffer) ? _gpu.uploadBuffer(response.waterBuffer) : null;
				CuboidData newData = new CuboidData(response.cuboid.getCuboidAddress()
						, opaqueData
						, itemsOnGroundArray
						, transparentData
						, waterData
				);
				// We only clear internal.requiresProcessing when sending the request, not handling the response.
				_InternalData newInstance = new _InternalData(internal.requiresProcessing, internal.cuboid, newData);
				_cuboids.put(address, newInstance);
			}
			
			// We can now return the scratch buffer since we uploaded the related buffers.
			_scratchGraphicsBuffers.add(response.meshBuffer);
			
			response = _dequeueResponse();
		}
		
		// Now that we have freed up any scratch buffers, see if we can request something else.
		Iterator<_InternalData> iterator = _cuboids.values().iterator();
		List<_InternalData> toReplace = new ArrayList<>();
		while (!_scratchGraphicsBuffers.isEmpty() && iterator.hasNext())
		{
			_InternalData next = iterator.next();
			if (next.requiresProcessing)
			{
				// This is stale so regenerate it.
				FloatBuffer meshBuffer = _scratchGraphicsBuffers.poll();
				_Request request = new _Request(meshBuffer
						, next.cuboid
				);
				_enqueueRequest(request);
				toReplace.add(new _InternalData(false, next.cuboid, next.data));
			}
		}
		for (_InternalData replace : toReplace)
		{
			_cuboids.put(replace.cuboid.getCuboidAddress(), replace);
		}
	}

	public void shutdown()
	{
		// First, stop the background thread.
		synchronized(this)
		{
			_keepRunning = false;
			this.notifyAll();
		}
		try
		{
			_background.join();
		}
		catch (InterruptedException e)
		{
			throw Assert.unexpected(e);
		}
		
		// Now we can clean up the buffers which made it to GPU memory.
		for (_InternalData data : _cuboids.values())
		{
			_deleteBuffers(data.data);
		}
		_cuboids.clear();
	}


	private void _backgroundMain()
	{
		_Request request = _backgroundGetRequest(null);
		while (null != request)
		{
			_Response response = _backgroundProcessRequest(request);
			request = _backgroundGetRequest(response);
		}
	}

	private synchronized _Request _backgroundGetRequest(_Response response)
	{
		if (null != response)
		{
			_responses.add(response);
			// (We don't notify here since the foreground thread never waits on this response - just picks it up later)
		}
		while (_keepRunning && _requests.isEmpty())
		{
			try
			{
				this.wait();
			}
			catch (InterruptedException e)
			{
				// Interruption not used.
				throw Assert.unexpected(e);
			}
		}
		return _keepRunning
				? _requests.poll()
				: null
		;
	}

	private synchronized void _enqueueRequest(_Request request)
	{
		_requests.add(request);
		this.notifyAll();
	}

	private synchronized _Response _dequeueResponse()
	{
		return _responses.poll();
	}


	private _Response _backgroundProcessRequest(_Request request)
	{
		// Collect information about the cuboid.
		SparseShortProjection<SceneMeshHelpers.AuxVariant> variantProjection = SceneMeshHelpers.buildAuxProjection(_env, request.cuboid);
		
		BufferBuilder builder = new BufferBuilder(request.meshBuffer, _programAttributes);
		
		// Create the opaque cuboid vertices.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env, builder, _blockTextures, variantProjection, _auxBlockTextures, _itemToBlockIndexMapper, request.cuboid, true);
		BufferBuilder.Buffer opaqueBuffer = builder.finishOne();
		
		// Create the vertex array for any items dropped on the ground.
		SceneMeshHelpers.populateMeshForDroppedItems(_env, builder, _itemAtlas, _auxBlockTextures, request.cuboid);
		BufferBuilder.Buffer itemsOnGroundBuffer = builder.finishOne();
		
		// Create the transparent (non-water) cuboid vertices.
		// Note that this may be removed in the future if we end up with no transparent block textures after converting associated blocks to models.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env, builder, _blockTextures, variantProjection, _auxBlockTextures, _itemToBlockIndexMapper, request.cuboid, false);
		BufferBuilder.Buffer transparentBuffer = builder.finishOne();
		
		// Create the water cuboid vertices.
		SceneMeshHelpers.populateWaterMeshBufferForCuboid(_env, builder, _blockTextures, variantProjection, _auxBlockTextures,_itemToBlockIndexMapper, request.cuboid);
		BufferBuilder.Buffer waterBuffer = builder.finishOne();
		
		return new _Response(request.meshBuffer
				, request.cuboid
				, opaqueBuffer
				, itemsOnGroundBuffer
				, transparentBuffer
				, waterBuffer
		);
	}

	private void _removeCuboid(CuboidAddress address)
	{
		_InternalData previous = _cuboids.remove(address);
		if (null != previous)
		{
			_deleteBuffers(previous.data);
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
		if (null != previous.waterArray)
		{
			_gpu.deleteBuffer(previous.waterArray);
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
			, VertexArray waterArray
	) {}

	private static record _InternalData(boolean requiresProcessing
			, IReadOnlyCuboidData cuboid
			, CuboidData data
	) {}

	private static record _Request(FloatBuffer meshBuffer
			, IReadOnlyCuboidData cuboid
	) {}

	private static record _Response(FloatBuffer meshBuffer
			, IReadOnlyCuboidData cuboid
			, BufferBuilder.Buffer opaqueBuffer
			, BufferBuilder.Buffer itemsOnGroundBuffer
			, BufferBuilder.Buffer transparentBuffer
			, BufferBuilder.Buffer waterBuffer
	) {}
}
