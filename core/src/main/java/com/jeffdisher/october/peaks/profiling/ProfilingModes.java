package com.jeffdisher.october.peaks.profiling;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;

import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.EntityType;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.CuboidGenerator;
import com.jeffdisher.october.utils.Encoding;
import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.CuboidHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CreatureEntity;
import com.jeffdisher.october.types.CuboidAddress;


/**
 * Hard-coded behaviours for profiling runs.
 */
public class ProfilingModes
{
	public static final ProfilingModes[] ALL_MODES = new ProfilingModes[] {
		new ProfilingModes("Entities", (Environment env, ProfilingSession session) -> {
			// This just renders a bunch of entities standing still.
			// This stresses the overhead of making the small calls related drawing the individually rigged limbs.
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
		new ProfilingModes("Basic Cuboids", (Environment env, ProfilingSession session) -> {
			// This renders a bunch of not-very-complicated cuboids.
			// This simplicity means that we are seeing the overhead of setting up every rendering call.
			CuboidData cuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), env.special.AIR);
			short blockNumber = env.items.getItemById("op.stone").number();
			short dirtNumber = env.items.getItemById("op.dirt").number();
			
			BlockAddress[] batch = new BlockAddress[] {
				BlockAddress.fromInt(0, 0, 0),
				BlockAddress.fromInt(0, 0, 31),
				BlockAddress.fromInt(0, 31, 0),
				BlockAddress.fromInt(0, 31, 31),
				BlockAddress.fromInt(31, 0, 0),
				BlockAddress.fromInt(31, 0, 31),
				BlockAddress.fromInt(31, 31, 0),
				BlockAddress.fromInt(31, 31, 31),
				BlockAddress.fromInt(15, 15, 15),
				BlockAddress.fromInt(15, 15, 16),
				BlockAddress.fromInt(15, 16, 15),
				BlockAddress.fromInt(15, 16, 16),
				BlockAddress.fromInt(16, 15, 15),
				BlockAddress.fromInt(16, 15, 16),
				BlockAddress.fromInt(16, 16, 15),
				BlockAddress.fromInt(16, 16, 16),
			};
			short[] blocks = new short[] {
				blockNumber,
				dirtNumber,
				blockNumber,
				dirtNumber,
				blockNumber,
				dirtNumber,
				blockNumber,
				dirtNumber,
				blockNumber,
				dirtNumber,
				blockNumber,
				dirtNumber,
				blockNumber,
				dirtNumber,
				blockNumber,
				dirtNumber,
			};
			Arrays.sort(batch, new IReadOnlyCuboidData.BlockAddressBatchComparator());
			cuboid.batchWiteData15(AspectRegistry.BLOCK, batch, blocks);
			CuboidHeightMap cuboidHeight = HeightMapHelpers.buildHeightMap(cuboid);
			ColumnHeightMap heightMap = HeightMapHelpers.buildSingleColumn(Map.of(CuboidAddress.fromInt(0, 0, -2), cuboidHeight));
			
			int viewDistance = 2;
			for (int z = -viewDistance; z <= viewDistance; ++z)
			{
				for (int y = -viewDistance; y <= viewDistance; ++y)
				{
					for (int x = -viewDistance; x <= viewDistance; ++x)
					{
						CuboidData local = CuboidData.mutableCloneWithAddress(CuboidAddress.fromInt(x, y, z), cuboid);
						session.addCuboid(local, heightMap);
					}
				}
			}
			float dayProgression = 0.0f;
			float skyLightMultiplier = 1.0f;
			session.scene.setDayTime(dayProgression, skyLightMultiplier);
		}),
		new ProfilingModes("Sponge Cuboids", (Environment env, ProfilingSession session) -> {
			// This renders a bunch of cuboids which have been designed as the worst-case scenario (every possible face is visible).
			// This seems to be GPU-bound in terms of how quickly that many polygons can be drawn.
			CuboidData airCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), env.special.AIR);
			CuboidData busyCuboid = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, -1), env.special.AIR);
			short blockNumber = env.items.getItemById("op.stone").number();
			
			BlockAddress[] batch = new BlockAddress[Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE / 2];
			short[] blocks = new short[Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE * Encoding.CUBOID_EDGE_SIZE / 2];
			int index = 0;
			for (byte z = 0; z < Encoding.CUBOID_EDGE_SIZE; ++z)
			{
				for (byte y = 0; y < Encoding.CUBOID_EDGE_SIZE; ++y)
				{
					for (byte x = 0; x < Encoding.CUBOID_EDGE_SIZE; ++x)
					{
						if (0 == ((x + y + z) % 2))
						{
							batch[index] = new BlockAddress(x, y, z);
							blocks[index] = blockNumber;
							index += 1;
						}
					}
				}
			}
			Arrays.sort(batch, new IReadOnlyCuboidData.BlockAddressBatchComparator());
			busyCuboid.batchWiteData15(AspectRegistry.BLOCK, batch, blocks);
			CuboidHeightMap cuboidHeight = HeightMapHelpers.buildHeightMap(busyCuboid);
			ColumnHeightMap heightMap = HeightMapHelpers.buildSingleColumn(Map.of(busyCuboid.getCuboidAddress(), cuboidHeight));
			
			int viewDistance = 2;
			for (int z = -viewDistance; z < 0; ++z)
			{
				for (int y = -viewDistance; y <= viewDistance; ++y)
				{
					for (int x = -viewDistance; x <= viewDistance; ++x)
					{
						CuboidData local = CuboidData.mutableCloneWithAddress(CuboidAddress.fromInt(x, y, z), busyCuboid);
						session.addCuboid(local, heightMap);
					}
				}
			}
			for (int z = 0; z <= viewDistance; ++z)
			{
				for (int y = -viewDistance; y <= viewDistance; ++y)
				{
					for (int x = -viewDistance; x <= viewDistance; ++x)
					{
						CuboidData local = CuboidData.mutableCloneWithAddress(CuboidAddress.fromInt(x, y, z), airCuboid);
						session.addCuboid(local, heightMap);
					}
				}
			}
			float dayProgression = 0.0f;
			float skyLightMultiplier = 1.0f;
			session.scene.setDayTime(dayProgression, skyLightMultiplier);
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
