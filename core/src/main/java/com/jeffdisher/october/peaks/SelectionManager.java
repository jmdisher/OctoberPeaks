package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.mutations.EntityChangeIncrementalBlockBreak;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * This class handles the selection of blocks and entities under the cursor.
 * It doesn't directly use this information, just listens for updates to the camera, entities, and cuboids in order to
 * answer a per-frame question about what is selected, by the top-level.
 */
public class SelectionManager
{
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final Map<Integer, PartialEntity> _entities;
	private Vector _eye;
	private Vector _target;

	public SelectionManager(Function<AbsoluteLocation, BlockProxy> blockLookup)
	{
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

	public SelectionTuple findSelection()
	{
		// Find any selected entity or block.
		Vector direction = Vector.delta(_eye, _target).scale(EntityChangeIncrementalBlockBreak.MAX_REACH);
		Vector endPoint = _eye.add(direction);
		GeometryHelpers.SelectedEntity selectedEntity = GeometryHelpers.findSelectedEntity(_eye, endPoint, _entities.values());
		Vector edgeLimit = endPoint;
		if (null != selectedEntity)
		{
			Vector relative = Vector.delta(_eye, endPoint).normalize().scale(selectedEntity.distance());
			edgeLimit = new Vector(_eye.x() + relative.x(), _eye.y() + relative.y(), _eye.z() + relative.z());
		}
		Environment env = Environment.getShared();
		GeometryHelpers.RayResult selectedBlock = GeometryHelpers.findFirstCollision(_eye, edgeLimit, (AbsoluteLocation location) -> {
			BlockProxy proxy = _blockLookup.apply(location);
			boolean shouldStop = true;
			if (null != proxy)
			{
				shouldStop = false;
				Block block = proxy.getBlock();
				// Check against the air block.
				if (env.special.AIR != block)
				{
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
