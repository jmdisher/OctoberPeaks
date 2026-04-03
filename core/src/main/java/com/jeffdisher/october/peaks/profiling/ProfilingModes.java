package com.jeffdisher.october.peaks.profiling;

import java.util.function.BiConsumer;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.types.CreatureEntity;


/**
 * Hard-coded behaviours for profiling runs.
 */
public class ProfilingModes
{
	public static final ProfilingModes[] ALL_MODES = new ProfilingModes[] {
		new ProfilingModes("Entities", (Environment env, ProfilingSession session) -> {
			int id = -1;
			EntityType cow = env.creatures.getTypeById("op.cow");
			for (int y = -10; y <= 10; ++y)
			{
				for (int x = -10; x <= 10; ++x)
				{
					CreatureEntity creature = new CreatureEntity(id
						, cow
						, new EntityLocation((float)x, (float)y, 0.0f)
						, new EntityLocation(0.0f, 0.0f, 0.0f)
						, (byte)0
						, (byte)0
						, (byte)100
						, (byte)100
						, null
						, null
					);
					PartialEntity entity = PartialEntity.fromCreature(creature);
					session.addOtherEntity(entity);
					id -= 1;
				}
			}
		}),
	};


	public final String name;
	public final BiConsumer<Environment, ProfilingSession> populate;

	public ProfilingModes(String name, BiConsumer<Environment, ProfilingSession> populate)
	{
		this.name = name;
		this.populate = populate;
	}
}
