package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.MiscConstants;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.CreativeInventory;
import com.jeffdisher.october.types.Entity;
import com.jeffdisher.october.types.Inventory;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.NonStackableItem;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * This class handles the selection of blocks and entities under the cursor.
 * It doesn't directly use this information, just listens for updates to the camera, entities, and cuboids in order to
 * answer a per-frame question about what is selected, by the top-level.
 */
public class SelectionManager
{
	private final Map<Block, Prism> _specialBounds;
	private final Set<Block> _ignoreCommon;
	private final Set<Block> _ignoreCommonAndWaterSource;
	private final Item _emptyBucketItem;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final Map<Integer, PartialEntity> _entities;
	private Vector _eye;
	private Vector _target;
	private boolean _isEmptyBucketSelected;

	public SelectionManager(Environment environment
			, Map<Block, Prism> specialBounds
			, Function<AbsoluteLocation, BlockProxy> blockLookup
	)
	{
		_specialBounds = specialBounds;
		_ignoreCommon = Set.of(environment.special.AIR
				, environment.special.WATER_WEAK
				, environment.special.WATER_STRONG
		);
		_ignoreCommonAndWaterSource = Set.of(environment.special.AIR
				, environment.special.WATER_WEAK
				, environment.special.WATER_STRONG
				, environment.special.WATER_SOURCE
		);
		_emptyBucketItem = environment.items.getItemById("op.bucket_empty");
		_blockLookup = blockLookup;
		_entities = new HashMap<>();
	}

	public void updatePosition(Vector eye, Vector target)
	{
		_eye = eye;
		_target = target;
	}

	public void setEntity(PartialEntity entity)
	{
		_entities.put(entity.id(), entity);
	}

	public void removeEntity(int id)
	{
		PartialEntity removed = _entities.remove(id);
		Assert.assertTrue(null != removed);
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

	public SelectionTuple findSelection()
	{
		// Find any selected entity or block.
		// TODO:  Use the appropriate reach constants for different phases of the selection.
		Vector direction = Vector.delta(_eye, _target).scale(MiscConstants.REACH_BLOCK);
		Vector endPoint = _eye.add(direction);
		GeometryHelpers.SelectedEntity selectedEntity = GeometryHelpers.findSelectedEntity(_eye, endPoint, _entities.values());
		Vector edgeLimit = endPoint;
		if (null != selectedEntity)
		{
			Vector relative = Vector.delta(_eye, endPoint).normalize().scale(selectedEntity.distance());
			edgeLimit = new Vector(_eye.x() + relative.x(), _eye.y() + relative.y(), _eye.z() + relative.z());
		}
		GeometryHelpers.RayResult selectedBlock = GeometryHelpers.findFirstCollision(_eye, edgeLimit, (AbsoluteLocation location) -> {
			BlockProxy proxy = _blockLookup.apply(location);
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
					shouldStop = GeometryHelpers.doesIntersect(_eye, endPoint, _specialBounds.get(block).getRelative(location.x(), location.y(), location.z()));
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
		SelectionTuple tuple;
		if (null != selectedBlock)
		{
			tuple = new SelectionTuple(null, selectedBlock.stopBlock(), selectedBlock.preStopBlock());
		}
		else if (null != selectedEntity)
		{
			tuple = new SelectionTuple(selectedEntity.entity(), null, null);
		}
		else
		{
			// If nothing is selected, return null.
			tuple = null;
		}
		return tuple;
	}


	// Either the entity or the blocks are non-null, never both.
	public static record SelectionTuple(PartialEntity entity
			, AbsoluteLocation stopBlock
			, AbsoluteLocation preStopBlock
	) {}
}
