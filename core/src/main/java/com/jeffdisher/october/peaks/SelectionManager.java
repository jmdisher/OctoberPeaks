package com.jeffdisher.october.peaks;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * This class handles the selection of blocks and entities under the cursor.
 * It doesn't directly use this information, just listens for updates to the camera, entities, and cuboids in order to
 * answer a per-frame question about what is selected, by the top-level.
 */
public class SelectionManager
{
	private final Map<CuboidAddress, IReadOnlyCuboidData> _cuboids;
	private final Map<Integer, PartialEntity> _entities;
	private Vector _eye;
	private Vector _target;

	public SelectionManager()
	{
		_cuboids = new HashMap<>();
		_entities = new HashMap<>();
	}

	public void updatePosition(Vector eye, Vector target)
	{
		_eye = eye;
		_target = target;
	}

	public void setCuboid(IReadOnlyCuboidData cuboid)
	{
		_cuboids.put(cuboid.getCuboidAddress(), cuboid);
	}

	public void removeCuboid(CuboidAddress address)
	{
		IReadOnlyCuboidData removed = _cuboids.remove(address);
		Assert.assertTrue(null != removed);
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
		GeometryHelpers.SelectedEntity selectedEntity = GeometryHelpers.findSelectedEntity(_eye, _target, _entities.values());
		Vector edgeLimit = _target;
		if (null != selectedEntity)
		{
			Vector relative = Vector.delta(_eye, _target).scale(selectedEntity.distance());
			edgeLimit = new Vector(_eye.x() + relative.x(), _eye.y() + relative.y(), _eye.z() + relative.z());
		}
		GeometryHelpers.RayResult selectedBlock = GeometryHelpers.findFirstCollision(_eye, edgeLimit, (AbsoluteLocation location) -> {
			IReadOnlyCuboidData cuboid = _cuboids.get(location.getCuboidAddress());
			boolean shouldStop = true;
			if (null != cuboid)
			{
				shouldStop = false;
				short block = cuboid.getData15(AspectRegistry.BLOCK, location.getBlockAddress());
				// Check against the air block.
				if (0 != block)
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
