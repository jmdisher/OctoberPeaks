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
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.BlockVariant;
import com.jeffdisher.october.peaks.ItemVariant;
import com.jeffdisher.october.peaks.SparseShortProjection;
import com.jeffdisher.october.peaks.TextureAtlas;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;
import com.jeffdisher.october.utils.Encoding;


/**
 * Currently just contains the cuboids loaded in the system but is mostly in place to introduce a design which will
 * later allow for background cuboid mesh generation (as this is a heavy-weight operation which should be pulled out of
 * the main thread).
 */
public class CuboidMeshManager
{
	public static final int SCRATCH_BUFFER_COUNT = 2;
	public static final int BUFFER_SIZE = 64 * 1024 * 1024;

	// Non-mutated data.
	private final Environment _env;
	private final IGpu _gpu;
	private final Attribute[] _programAttributes;
	private final TextureAtlas<ItemVariant> _itemAtlas;
	private final TextureAtlas<BlockVariant> _blockTextures;
	private final TextureAtlas<SceneMeshHelpers.AuxVariant> _auxBlockTextures;
	private final short[] _itemToBlockIndexMapper;

	// Foreground-only data.
	private final Map<CuboidAddress, _InternalData> _foregroundCuboids;
	private final Queue<CuboidAddress> _foregroundRequestOrder;
	private final Queue<FloatBuffer> _foregroundGraphicsBuffers;
	
	// Background-only data.
	private final Map<CuboidAddress, IReadOnlyCuboidData> _backgroundCuboids;

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
		
		// Foreground-only data.
		_foregroundCuboids = new HashMap<>();
		_foregroundRequestOrder = new LinkedList<>();
		_foregroundGraphicsBuffers = new LinkedList<>();
		for (int i = 0; i < SCRATCH_BUFFER_COUNT; ++i)
		{
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer buffer = direct.asFloatBuffer();
			_foregroundGraphicsBuffers.add(buffer);
		}
		
		// Background-only data.
		_backgroundCuboids = new HashMap<>();
		
		// Setup the background processing thread.
		_keepRunning = true;
		_requests = new LinkedList<>();
		_responses = new LinkedList<>();
		_background = new Thread(() -> _backgroundMain()
				, "Cuboid Mesh Baking Thread"
		);
		_background.start();
	}

	public Collection<CuboidMeshes> viewCuboids()
	{
		return _foregroundCuboids.values().stream()
				.map((_InternalData internal) -> internal.vertices)
				.collect(Collectors.toList())
		;
	}

	public void setCuboid(IReadOnlyCuboidData cuboid, Set<BlockAddress> changedBlocks)
	{
		// We want to create the _InternalData instance of a newer generation, potentially just a placeholder for empty data.
		CuboidAddress address = cuboid.getCuboidAddress();
		_InternalData existing = _foregroundCuboids.remove(address);
		_InternalData internal;
		if (null != existing)
		{
			internal = new _InternalData(true
					, cuboid
					, existing.vertices
			);
		}
		else
		{
			internal = new _InternalData(true
					, cuboid
					, new CuboidMeshes(address, null, null, null, null)
			);
		}
		_foregroundCuboids.put(address, internal);
		// We need to enqueue a request to re-bake this (will be skipped if this is a redundant change).
		_foregroundRequestOrder.add(address);
		
		// See if we need to re-bake any adjacent cuboids.
		if (null == existing)
		{
			// Changed blocks should be null if this is newly loaded.
			Assert.assertTrue(null == changedBlocks);
			
			// There was nothing here so re-bake all adjacent blocks.
			_markDirty(address.getRelative(0, 0, 1));
			_markDirty(address.getRelative(0, 0, -1));
			_markDirty(address.getRelative(0, 1, 0));
			_markDirty(address.getRelative(0, -1, 0));
			_markDirty(address.getRelative(1, 0, 0));
			_markDirty(address.getRelative(-1, 0, 0));
		}
		else
		{
			// Changed blocks should ONLY be null if this is newly loaded.
			Assert.assertTrue(null != changedBlocks);
			
			// There was already something here so check the changed blocks, see if any are on any changed adjacent faces.
			boolean up = false;
			boolean down = false;
			boolean north = false;
			boolean south = false;
			boolean east = false;
			boolean west = false;
			byte zero = 0;
			byte edge = Encoding.CUBOID_EDGE_SIZE - 1;
			IReadOnlyCuboidData oldCuboid = existing.cuboid;
			for (BlockAddress changed : changedBlocks)
			{
				if (zero == changed.z())
				{
					down |= _didBlockChange(oldCuboid, cuboid, changed);
				}
				else if (edge == changed.z())
				{
					up |= _didBlockChange(oldCuboid, cuboid, changed);
				}
				if (zero == changed.y())
				{
					south |= _didBlockChange(oldCuboid, cuboid, changed);
				}
				else if (edge == changed.y())
				{
					north |= _didBlockChange(oldCuboid, cuboid, changed);
				}
				if (zero == changed.x())
				{
					west |= _didBlockChange(oldCuboid, cuboid, changed);
				}
				else if (edge == changed.x())
				{
					east |= _didBlockChange(oldCuboid, cuboid, changed);
				}
			}
			
			if (up)
			{
				_markDirty(address.getRelative(0, 0, 1));
			}
			if (down)
			{
				_markDirty(address.getRelative(0, 0, -1));
			}
			if (north)
			{
				_markDirty(address.getRelative(0, 1, 0));
			}
			if (south)
			{
				_markDirty(address.getRelative(0, -1, 0));
			}
			if (east)
			{
				_markDirty(address.getRelative(1, 0, 0));
			}
			if (west)
			{
				_markDirty(address.getRelative(-1, 0, 0));
			}
		}
	}

	public void removeCuboid(CuboidAddress address)
	{
		_InternalData previous = _foregroundCuboids.remove(address);
		
		// This can't be missing if we were told to unload it.
		Assert.assertTrue(null != previous);
		
		// Tell the background to drop its copy.
		_enqueueRequest(new _Request(null, previous.cuboid));
		
		// Delete any buffers backing it.
		_deleteBuffers(previous.vertices);
	}

	public void processBackground()
	{
		// First, see if anything has come back to us.
		_Response response = _dequeueResponse();
		while (null != response)
		{
			CuboidAddress address = response.cuboid.getCuboidAddress();
			_InternalData internal = _foregroundCuboids.remove(address);
			
			// We only replace this if it wasn't deleted.
			if (null != internal)
			{
				// Delete the old GPU resources.
				_deleteBuffers(internal.vertices);
				
				// We will still store an empty CuboidData if all of these are null, just for simplicity.
				VertexArray opaqueData = (null != response.opaqueBuffer) ? _gpu.uploadBuffer(response.opaqueBuffer) : null;
				VertexArray itemsOnGroundArray = (null != response.itemsOnGroundBuffer) ? _gpu.uploadBuffer(response.itemsOnGroundBuffer) : null;
				VertexArray transparentData = (null != response.transparentBuffer) ? _gpu.uploadBuffer(response.transparentBuffer) : null;
				VertexArray waterData = (null != response.waterBuffer) ? _gpu.uploadBuffer(response.waterBuffer) : null;
				CuboidMeshes newData = new CuboidMeshes(response.cuboid.getCuboidAddress()
						, opaqueData
						, itemsOnGroundArray
						, transparentData
						, waterData
				);
				// We only clear internal.requiresProcessing when sending the request, not handling the response.
				_InternalData newInstance = new _InternalData(internal.requiresProcessing, internal.cuboid, newData);
				_foregroundCuboids.put(address, newInstance);
			}
			
			// We can now return the scratch buffer since we uploaded the related buffers.
			_foregroundGraphicsBuffers.add(response.meshBuffer);
			
			response = _dequeueResponse();
		}
		
		// Now that we have freed up any scratch buffers, see if we can request something else.
		// Adjacent cuboid views assume the order of requests so check that queue for the order.
		Iterator<CuboidAddress> iterator = _foregroundRequestOrder.iterator();
		List<_InternalData> toReplace = new ArrayList<>();
		while (!_foregroundGraphicsBuffers.isEmpty() && iterator.hasNext())
		{
			CuboidAddress address = iterator.next();
			_InternalData next = _foregroundCuboids.get(address);
			// It is possible that there are redundant requests in the list so double-check that processing is still required.
			if ((null != next) && next.requiresProcessing)
			{
				// This is stale so regenerate it.
				FloatBuffer meshBuffer = _foregroundGraphicsBuffers.poll();
				_Request request = new _Request(meshBuffer
						, next.cuboid
				);
				_enqueueRequest(request);
				toReplace.add(new _InternalData(false, next.cuboid, next.vertices));
			}
			// Whether we processed this or not, we have handled the request.
			iterator.remove();
		}
		for (_InternalData replace : toReplace)
		{
			_foregroundCuboids.put(replace.cuboid.getCuboidAddress(), replace);
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
		for (_InternalData data : _foregroundCuboids.values())
		{
			_deleteBuffers(data.vertices);
		}
		_foregroundCuboids.clear();
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
		//'We need a way to remove unloaded cuboids from the background collection so we use a null buffer in those cases (only using cuboid for its address).
		_Response response;
		if (null != request.meshBuffer)
		{
			// Real request so run it and get a response.
			_backgroundCuboids.put(request.cuboid.getCuboidAddress(), request.cuboid);
			response = _backgroundBuildMesh(request);
		}
		else
		{
			// In this case, just remove it and don't send a response.
			_backgroundCuboids.remove(request.cuboid.getCuboidAddress());
			response = null;
		}
		return response;
	}

	private _Response _backgroundBuildMesh(_Request request)
	{
		// Collect information about the cuboid.
		IReadOnlyCuboidData cuboid = request.cuboid;
		CuboidAddress address = cuboid.getCuboidAddress();
		SparseShortProjection<SceneMeshHelpers.AuxVariant> variantProjection = SceneMeshHelpers.buildAuxProjection(_env, cuboid);
		
		BufferBuilder builder = new BufferBuilder(request.meshBuffer, _programAttributes);
		
		// Get the adjacent cuboids to pre-seed the FaceBuilders.
		IReadOnlyCuboidData otherUp = _backgroundCuboids.get(address.getRelative(0, 0, 1));
		IReadOnlyCuboidData otherDown = _backgroundCuboids.get(address.getRelative(0, 0, -1));
		IReadOnlyCuboidData otherNorth = _backgroundCuboids.get(address.getRelative(0, 1, 0));
		IReadOnlyCuboidData otherSouth = _backgroundCuboids.get(address.getRelative(0, -1, 0));
		IReadOnlyCuboidData otherEast = _backgroundCuboids.get(address.getRelative(1, 0, 0));
		IReadOnlyCuboidData otherWest = _backgroundCuboids.get(address.getRelative(-1, 0, 0));
		SceneMeshHelpers.MeshInputData inputData = new SceneMeshHelpers.MeshInputData(cuboid
				, otherUp
				, otherDown
				, otherNorth
				, otherSouth
				, otherEast
				, otherWest
		);
		
		// Create the opaque cuboid vertices.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env
				, builder
				, _blockTextures
				, variantProjection
				, _auxBlockTextures
				, _itemToBlockIndexMapper
				, inputData
				, true
		);
		BufferBuilder.Buffer opaqueBuffer = builder.finishOne();
		
		// Create the vertex array for any items dropped on the ground.
		SceneMeshHelpers.populateMeshForDroppedItems(_env, builder, _itemAtlas, _auxBlockTextures, cuboid);
		BufferBuilder.Buffer itemsOnGroundBuffer = builder.finishOne();
		
		// Create the transparent (non-water) cuboid vertices.
		// Note that this may be removed in the future if we end up with no transparent block textures after converting associated blocks to models.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env
				, builder
				, _blockTextures
				, variantProjection
				, _auxBlockTextures
				, _itemToBlockIndexMapper
				, inputData
				, false
		);
		BufferBuilder.Buffer transparentBuffer = builder.finishOne();
		
		// Create the water cuboid vertices.
		SceneMeshHelpers.populateWaterMeshBufferForCuboid(_env
				, builder
				, _blockTextures
				, variantProjection
				, _auxBlockTextures
				,_itemToBlockIndexMapper
				, inputData
		);
		BufferBuilder.Buffer waterBuffer = builder.finishOne();
		
		return new _Response(request.meshBuffer
				, cuboid
				, opaqueBuffer
				, itemsOnGroundBuffer
				, transparentBuffer
				, waterBuffer
		);
	}

	private void _deleteBuffers(CuboidMeshes previous)
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

	private void _markDirty(CuboidAddress address)
	{
		// We just replace the data for this cuboid, if it exists.
		_InternalData existing = _foregroundCuboids.remove(address);
		if (null != existing)
		{
			_foregroundCuboids.put(address, new _InternalData(true, existing.cuboid, existing.vertices));
			// We need to enqueue a request to re-bake this (will be skipped if this is a redundant change).
			_foregroundRequestOrder.add(address);
		}
	}

	private boolean _didBlockChange(IReadOnlyCuboidData oldCuboid, IReadOnlyCuboidData newCuboid, BlockAddress blockAddress)
	{
		// See if the block changed type.
		// TODO:  When implementing block lighting, add a lighting change check here, too.
		
		// NOTE:  Reading the block value, directly, can be somewhat expensive.
		short oldBlockValue = oldCuboid.getData15(AspectRegistry.BLOCK, blockAddress);
		short newBlockValue = newCuboid.getData15(AspectRegistry.BLOCK, blockAddress);
		return (oldBlockValue != newBlockValue);
	}


	/**
	 * This interface replaces the direct dependency on the GL20 object in order to enable testing.
	 */
	public static interface IGpu
	{
		VertexArray uploadBuffer(BufferBuilder.Buffer buffer);
		void deleteBuffer(VertexArray array);
	}

	public static record CuboidMeshes(CuboidAddress address
			, VertexArray opaqueArray
			, VertexArray itemsOnGroundArray
			, VertexArray transparentArray
			, VertexArray waterArray
	) {}

	private static record _InternalData(boolean requiresProcessing
			, IReadOnlyCuboidData cuboid
			, CuboidMeshes vertices
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
