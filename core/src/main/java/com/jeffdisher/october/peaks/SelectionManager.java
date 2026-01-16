package com.jeffdisher.october.peaks;

import java.util.Map;
import java.util.Set;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.peaks.utils.GeometryHelpers;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;


/**
 * This class handles the selection of blocks and entities under the cursor.
 * It doesn't directly use this information, just listens for updates to the camera, entities, and cuboids in order to
 * answer a per-frame question about what is selected, by the top-level.
 */
public class SelectionManager
{
	private final Map<Block, Prism> _specialBounds;
	private final WorldCache _worldCache;
	private final Set<Block> _ignoreCommon;
	private final Set<Block> _ignoreCommonAndWaterSource;
	private final Item _emptyBucketItem;
	private Vector _eye;
	private Vector _target;
	private boolean _isEmptyBucketSelected;

	public SelectionManager(Environment environment
		, Map<Block, Prism> specialBounds
		, WorldCache worldCache
	)
	{
		_specialBounds = specialBounds;
		_worldCache = worldCache;
		
		Block waterSource = environment.blocks.fromItem(environment.items.getItemById("op.water_source"));
		Block waterStrong = environment.blocks.fromItem(environment.items.getItemById("op.water_strong"));
		Block waterWeak = environment.blocks.fromItem(environment.items.getItemById("op.water_weak"));
		Block lavaSource = environment.blocks.fromItem(environment.items.getItemById("op.lava_source"));
		Block lavaStrong = environment.blocks.fromItem(environment.items.getItemById("op.lava_strong"));
		Block lavaWeak = environment.blocks.fromItem(environment.items.getItemById("op.lava_weak"));
		_ignoreCommon = Set.of(environment.special.AIR
				, waterWeak
				, waterStrong
				, lavaWeak
				, lavaStrong
		);
		_ignoreCommonAndWaterSource = Set.of(environment.special.AIR
				, waterWeak
				, waterStrong
				, waterSource
				, lavaWeak
				, lavaStrong
				, lavaSource
		);
		_emptyBucketItem = environment.items.getItemById("op.bucket_empty");
	}

	public void updatePosition(Vector eye, Vector target)
	{
		_eye = eye;
		_target = target;
	}

	public void setThisEntity(Entity projectedEntity)
	{
		// We only use care about an empty bucket being selected, for now, so we only store that.
		// (even if we want more information than this, it is probably just other specific items).
		int selectedKey = projectedEntity.hotbarItems()[projectedEntity.hotbarIndex()];
		boolean isEmptyBucket;
		if (Entity.NO_SELECTION != selectedKey)
		{
			Inventory inv = projectedEntity.isCreativeMode()
					? CreativeInventory.fakeInventory()
					: projectedEntity.inventory()
			;
			// The bucket is not stackable.
			NonStackableItem nonStack = inv.getNonStackableForKey(selectedKey);
			isEmptyBucket = (null != nonStack)
					? (_emptyBucketItem == nonStack.type())
					: false
			;
		}
		else
		{
			isEmptyBucket = false;
		}
		_isEmptyBucketSelected = isEmptyBucket;
	}

	public WorldSelection findSelection()
	{
		// Find any selected entity or block.
		Vector delta = Vector.delta(_eye, _target).normalize();
		// Even though the reach to an entity is shorter than that to a block, we will use the block limit and check the entity, later, so that the entity will mask out blocks behind it but still in range.
		Vector end = _eye.add(delta.scale(MiscConstants.REACH_BLOCK));
		GeometryHelpers.SelectedEntity selectedEntity = GeometryHelpers.findSelectedEntity(_eye, end, _worldCache.getOtherEntities());
		if (null != selectedEntity)
		{
			float entityDistance = selectedEntity.distance();
			end = _eye.add(delta.scale(entityDistance));
		}
		final Vector finalEnd = end;
		GeometryHelpers.RayResult selectedBlock = GeometryHelpers.findFirstCollision(_eye, end, (AbsoluteLocation location) -> {
			BlockProxy proxy = _worldCache.blockLookup.apply(location);
			boolean shouldStop = true;
			if (null != proxy)
			{
				Block block = proxy.getBlock();
				// If we can select the water source, don't ignore it.
				boolean shouldIgnore = _isEmptyBucketSelected
						? _ignoreCommon.contains(block)
						: _ignoreCommonAndWaterSource.contains(block)
				;
				
				if (shouldIgnore)
				{
					// If we should ignore this, we can continue.
					shouldStop = false;
				}
				else if (_specialBounds.containsKey(block))
				{
					// This is a model block so we need special intersection.
					shouldStop = GeometryHelpers.doesIntersect(_eye, finalEnd, _specialBounds.get(block).getRelative(location.x(), location.y(), location.z()));
				}
				else
				{
					// This is just a normal block so stop.
					shouldStop = true;
				}
			}
			return shouldStop;
		});
		
		// We will return a tuple if we matched either of these - prioritize the block since the entity will restrict the block check distance.
		WorldSelection tuple;
		if (null != selectedBlock)
		{
			tuple = new WorldSelection(null, selectedBlock.stopBlock(), selectedBlock.preStopBlock());
		}
		else if (null != selectedEntity)
		{
			// Here, we will impose the limits of the entity selection range.
			tuple = (selectedEntity.distance() <= MiscConstants.REACH_ENTITY)
					? new WorldSelection(selectedEntity.entity(), null, null)
					: null
			;
		}
		else
		{
			// If nothing is selected, return null.
			tuple = null;
		}
		return tuple;
	}
}
