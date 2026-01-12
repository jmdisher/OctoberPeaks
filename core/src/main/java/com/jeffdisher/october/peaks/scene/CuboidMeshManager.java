package com.jeffdisher.october.peaks.scene;

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
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.AuxilliaryTextureAtlas;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.CuboidColumnAddress;
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

	// Block type IDs which have some special use.
	public static final String ITEM_ID_PEDESTAL = "op.pedestal";
	public static final String ITEM_ID_ENCHANTING_TABLE = "op.enchanting_table";

	// Non-mutated data.
	private final Environment _env;
	private final IGpu _gpu;
	private final Attribute[] _programAttributes;
	private final ItemTextureAtlas _itemAtlas;
	private final BlockModelsAndAtlas _blockModels;
	private final BasicBlockAtlas _blockTextures;
	private final AuxilliaryTextureAtlas _auxBlockTextures;
	private final Set<Block> _itemSlotBlocks;

	// Foreground-only data.
	private final Map<CuboidAddress, _InternalData> _foregroundCuboids;
	private final Map<CuboidColumnAddress, _HeightWrapper> _foregroundHeightMaps;
	private final List<CuboidAddress> _foregroundRequestOrder;
	private final Queue<FloatBuffer> _foregroundGraphicsBuffers;
	
	// Objects related to the handoff.
	private boolean _keepRunning;
	private final Queue<_Request> _requests;
	private final Queue<_Response> _responses;
	private final Thread _background;

	public CuboidMeshManager(Environment env
			, IGpu gpu
			, Attribute[] programAttributes
			, ItemTextureAtlas itemAtlas
			, BlockModelsAndAtlas blockModels
			, BasicBlockAtlas blockTextures
			, AuxilliaryTextureAtlas auxBlockTextures
	)
	{
		_env = env;
		_gpu = gpu;
		_programAttributes = programAttributes;;
		_itemAtlas = itemAtlas;
		_blockModels = blockModels;
		_blockTextures = blockTextures;
		_auxBlockTextures = auxBlockTextures;
		_itemSlotBlocks = Set.of(env.blocks.fromItem(env.items.getItemById(ITEM_ID_PEDESTAL))
			, env.blocks.fromItem(env.items.getItemById(ITEM_ID_ENCHANTING_TABLE))
		);
		
		// Foreground-only data.
		_foregroundCuboids = new HashMap<>();
		_foregroundHeightMaps = new HashMap<>();
		_foregroundRequestOrder = new LinkedList<>();
		_foregroundGraphicsBuffers = new LinkedList<>();
		for (int i = 0; i < SCRATCH_BUFFER_COUNT; ++i)
		{
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer buffer = direct.asFloatBuffer();
			_foregroundGraphicsBuffers.add(buffer);
		}
		
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

	public void setCuboid(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
	{
		// Remove the old record and replace it, marking it needing processing.
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
					, new CuboidMeshes(address, null, null, null, null, null)
			);
		}
		_foregroundCuboids.put(address, internal);
		
		// Do the same thing to the column height maps.
		CuboidColumnAddress column = address.getColumn();
		_HeightWrapper wrapper = _foregroundHeightMaps.get(column);
		int count = (null != wrapper)
				? wrapper.refCount + 1
				: 1
		;
		_foregroundHeightMaps.put(column, new _HeightWrapper(count, heightMap));
		
		// We need to enqueue a request to re-bake this (will be skipped if this is a redundant change).
		if (null != changedBlocks)
		{
			// Changed cuboids are prioritized.
			_foregroundRequestOrder.add(0, address);
		}
		else
		{
			// New cuboids go to the end.
			_foregroundRequestOrder.add(address);
		}
		
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
		// We assume that this is an address we have received in the past so this can't be missing from any collection.
		_InternalData previous = _foregroundCuboids.remove(address);
		Assert.assertTrue(null != previous);
		
		// Decrement count on height map.
		CuboidColumnAddress column = address.getColumn();
		_HeightWrapper wrapper = _foregroundHeightMaps.remove(column);
		Assert.assertTrue(null != wrapper);
		if (wrapper.refCount > 1)
		{
			_foregroundHeightMaps.put(column, new _HeightWrapper(wrapper.refCount - 1, wrapper.heightMap));
		}
		
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
				VertexArray modelData = (null != response.modelBuffer) ? _gpu.uploadBuffer(response.modelBuffer) : null;
				VertexArray itemsOnGroundArray = (null != response.itemsOnGroundBuffer) ? _gpu.uploadBuffer(response.itemsOnGroundBuffer) : null;
				VertexArray transparentData = (null != response.transparentBuffer) ? _gpu.uploadBuffer(response.transparentBuffer) : null;
				VertexArray waterData = (null != response.waterBuffer) ? _gpu.uploadBuffer(response.waterBuffer) : null;
				CuboidMeshes newData = new CuboidMeshes(response.cuboid.getCuboidAddress()
						, opaqueData
						, modelData
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
						, _packageRequestInput(address)
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
		Assert.assertTrue(null != request.meshBuffer);
		_Response response = _backgroundBuildMesh(request);
		return response;
	}

	private _Response _backgroundBuildMesh(_Request request)
	{
		// Collect information about the cuboid.
		IReadOnlyCuboidData cuboid = request.inputs.cuboid();
		ColumnHeightMap heightMap = request.inputs.height();
		AuxVariantMap variantMap = new AuxVariantMap(_env, cuboid);
		
		BufferBuilder builder = new BufferBuilder(request.meshBuffer, _programAttributes);
		MeshHelperBufferBuilder builderWrapper = new MeshHelperBufferBuilder(builder, MeshHelperBufferBuilder.USE_ALL_ATTRIBUTES);
		
		// Create the opaque cuboid vertices.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env
				, builderWrapper
				, _blockTextures
				, variantMap
				, _auxBlockTextures
				, request.inputs
				, true
		);
		// Lava is also treated as an opaque surface.
		short lavaSourceNumber = _env.items.getItemById("op.lava_source").number();
		short lavaStrongNumber = _env.items.getItemById("op.lava_strong").number();
		short lavaWeakNumber = _env.items.getItemById("op.lava_weak").number();
		SceneMeshHelpers.populateWaterMeshBufferForCuboid(_env
				, builderWrapper
				, _blockTextures
				, _auxBlockTextures
				, request.inputs
				, lavaSourceNumber
				, lavaStrongNumber
				, lavaWeakNumber
				, false
		);
		BufferBuilder.Buffer opaqueBuffer = builder.finishOne();
		
		// We will render the complex models (they need a different texture binding so they can't be part of the opaque buffer).
		SceneMeshHelpers.populateBufferWithComplexModels(_env
				, builder
				, _blockModels
				, variantMap
				, _auxBlockTextures
				, request.inputs
		);
		BufferBuilder.Buffer modelBuffer = builder.finishOne();
		
		// Create the vertex array for any items visible in the world.
		SceneMeshHelpers.populateMeshForItemsInWorld(_env, builderWrapper, _itemAtlas, _auxBlockTextures, cuboid, heightMap, _itemSlotBlocks);
		BufferBuilder.Buffer itemsOnGroundBuffer = builder.finishOne();
		
		// Create the transparent (non-water) cuboid vertices.
		// Note that this may be removed in the future if we end up with no transparent block textures after converting associated blocks to models.
		SceneMeshHelpers.populateMeshBufferForCuboid(_env
				, builderWrapper
				, _blockTextures
				, variantMap
				, _auxBlockTextures
				, request.inputs
				, false
		);
		BufferBuilder.Buffer transparentBuffer = builder.finishOne();
		
		// Create the water cuboid vertices.
		short waterSourceNumber = _env.items.getItemById("op.water_source").number();
		short waterStrongNumber = _env.items.getItemById("op.water_strong").number();
		short waterWeakNumber = _env.items.getItemById("op.water_weak").number();
		SceneMeshHelpers.populateWaterMeshBufferForCuboid(_env
				, builderWrapper
				, _blockTextures
				, _auxBlockTextures
				, request.inputs
				, waterSourceNumber
				, waterStrongNumber
				, waterWeakNumber
				, true
		);
		BufferBuilder.Buffer waterBuffer = builder.finishOne();
		
		return new _Response(request.meshBuffer
				, cuboid
				, opaqueBuffer
				, modelBuffer
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
		if (null != previous.modelArray)
		{
			_gpu.deleteBuffer(previous.modelArray);
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
		_InternalData existing = _foregroundCuboids.get(address);
		if ((null != existing) && !existing.requiresProcessing)
		{
			_foregroundCuboids.put(address, new _InternalData(true, existing.cuboid, existing.vertices));
			// We need to enqueue a request to re-bake this (will be skipped if this is a redundant change).
			_foregroundRequestOrder.add(address);
		}
	}

	private boolean _didBlockChange(IReadOnlyCuboidData oldCuboid, IReadOnlyCuboidData newCuboid, BlockAddress blockAddress)
	{
		// Since the mesh depends on the block type (for textures) and light level (for multiplier), we need to check those here.
		// We will check light first since it is usually a cheaper lookup (direct block value reads can be somewhat expensive).
		return (oldCuboid.getData7(AspectRegistry.LIGHT, blockAddress) != newCuboid.getData7(AspectRegistry.LIGHT, blockAddress))
				|| (oldCuboid.getData15(AspectRegistry.BLOCK, blockAddress) != newCuboid.getData15(AspectRegistry.BLOCK, blockAddress))
		;
	}

	private SceneMeshHelpers.MeshInputData _packageRequestInput(CuboidAddress address)
	{
		CuboidAddress otherUpAddress = address.getRelative(0, 0, 1);
		CuboidAddress otherDownAddress = address.getRelative(0, 0, -1);
		CuboidAddress otherNorthAddress = address.getRelative(0, 1, 0);
		CuboidAddress otherSouthAddress = address.getRelative(0, -1, 0);
		CuboidAddress otherEastAddress = address.getRelative(1, 0, 0);
		CuboidAddress otherWestAddress = address.getRelative(-1, 0, 0);
		
		IReadOnlyCuboidData cuboid = _getCuboidOrNull(address);
		IReadOnlyCuboidData otherUp = _getCuboidOrNull(otherUpAddress);
		IReadOnlyCuboidData otherDown = _getCuboidOrNull(otherDownAddress);
		IReadOnlyCuboidData otherNorth = _getCuboidOrNull(otherNorthAddress);
		IReadOnlyCuboidData otherSouth = _getCuboidOrNull(otherSouthAddress);
		IReadOnlyCuboidData otherEast = _getCuboidOrNull(otherEastAddress);
		IReadOnlyCuboidData otherWest = _getCuboidOrNull(otherWestAddress);
		
		ColumnHeightMap heightMap = _getHeightMapOrNull(address);
		ColumnHeightMap mapUp = _getHeightMapOrNull(otherUpAddress);
		ColumnHeightMap mapDown = _getHeightMapOrNull(otherDownAddress);
		ColumnHeightMap mapNorth = _getHeightMapOrNull(otherNorthAddress);
		ColumnHeightMap mapSouth = _getHeightMapOrNull(otherSouthAddress);
		ColumnHeightMap mapEast = _getHeightMapOrNull(otherEastAddress);
		ColumnHeightMap mapWest = _getHeightMapOrNull(otherWestAddress);
		
		IReadOnlyCuboidData[][][] cuboidsXYZ = new IReadOnlyCuboidData[][][] {
			new IReadOnlyCuboidData[][] {
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(-1, -1, -1)), _getCuboidOrNull(address.getRelative(-1, -1, 0)), _getCuboidOrNull(address.getRelative(-1, -1, 1))},
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(-1, 0, -1)), otherWest, _getCuboidOrNull(address.getRelative(-1, 0, 1))},
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(-1, 1, -1)), _getCuboidOrNull(address.getRelative(-1, 1, 0)), _getCuboidOrNull(address.getRelative(-1, 1, 1))},
			},
			new IReadOnlyCuboidData[][] {
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(0, -1, -1)), otherSouth, _getCuboidOrNull(address.getRelative(0, -1, 1))},
				new IReadOnlyCuboidData[] {otherDown, cuboid, otherUp},
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(0, 1, -1)), otherNorth, _getCuboidOrNull(address.getRelative(0, 1, 1))},
			},
			new IReadOnlyCuboidData[][] {
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(1, -1, -1)), _getCuboidOrNull(address.getRelative(1, -1, 0)), _getCuboidOrNull(address.getRelative(1, -1, 1))},
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(1, 0, -1)), otherEast, _getCuboidOrNull(address.getRelative(1, 0, 1))},
				new IReadOnlyCuboidData[] {_getCuboidOrNull(address.getRelative(1, 1, -1)), _getCuboidOrNull(address.getRelative(1, 1, 0)), _getCuboidOrNull(address.getRelative(1, 1, 1))},
			},
		};
		ColumnHeightMap[][] columnHeightXY = new ColumnHeightMap[][] {
			new ColumnHeightMap[] {
					_getHeightMapOrNull(address.getRelative(-1, -1, 0)),
					mapWest,
					_getHeightMapOrNull(address.getRelative(-1, 1, 0)),
			},
			new ColumnHeightMap[] {
					mapSouth,
					heightMap,
					mapNorth,
			},
			new ColumnHeightMap[] {
					_getHeightMapOrNull(address.getRelative(1, -1, 0)),
					mapEast,
					_getHeightMapOrNull(address.getRelative(1, 1, 0)),
			},
		};
		return new SceneMeshHelpers.MeshInputData(cuboid
				, heightMap
				, otherUp
				, mapUp
				, otherDown
				, mapDown
				, otherNorth
				, mapNorth
				, otherSouth
				, mapSouth
				, otherEast
				, mapEast
				, otherWest
				, mapWest
				
				, cuboidsXYZ
				, columnHeightXY
		);
		
	}

	private IReadOnlyCuboidData _getCuboidOrNull(CuboidAddress address)
	{
		_InternalData wrapper = _foregroundCuboids.get(address);
		return (null != wrapper)
				? wrapper.cuboid
				: null
		;
	}

	private ColumnHeightMap _getHeightMapOrNull(CuboidAddress address)
	{
		_HeightWrapper wrapper = _foregroundHeightMaps.get(address.getColumn());
		return (null != wrapper)
				? wrapper.heightMap
				: null
		;
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
			, VertexArray modelArray
			, VertexArray itemsOnGroundArray
			, VertexArray transparentArray
			, VertexArray waterArray
	) {}

	private static record _InternalData(boolean requiresProcessing
			, IReadOnlyCuboidData cuboid
			, CuboidMeshes vertices
	) {}

	private static record _Request(FloatBuffer meshBuffer
			, SceneMeshHelpers.MeshInputData inputs
	) {}

	private static record _Response(FloatBuffer meshBuffer
			, IReadOnlyCuboidData cuboid
			, BufferBuilder.Buffer opaqueBuffer
			, BufferBuilder.Buffer modelBuffer
			, BufferBuilder.Buffer itemsOnGroundBuffer
			, BufferBuilder.Buffer transparentBuffer
			, BufferBuilder.Buffer waterBuffer
	) {}

	private static record _HeightWrapper(int refCount
			, ColumnHeightMap heightMap
	) {}
}
