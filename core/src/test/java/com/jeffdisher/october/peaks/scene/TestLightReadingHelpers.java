package com.jeffdisher.october.peaks.scene;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.CuboidData;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.HeightMapHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.CuboidGenerator;


public class TestLightReadingHelpers
{
	private static Environment ENV;
	private static Item STONE;
	@BeforeClass
	public static void setup() throws Throwable
	{
		ENV = Environment.createSharedInstance();
		STONE = ENV.items.getItemById("op.stone");
	}
	@AfterClass
	public static void tearDown()
	{
		Environment.clearSharedInstance();
	}

	@Test
	public void readBlockLight() throws Throwable
	{
		// Check something in the cuboid, something outside the cuboid, and something not loaded.
		byte inByte = 5;
		byte outByte = 6;
		CuboidData central = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		central.setData7(AspectRegistry.LIGHT, BlockAddress.fromInt(5, 31, 8), inByte);
		CuboidData north = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 1, 0), ENV.special.AIR);
		north.setData7(AspectRegistry.LIGHT, BlockAddress.fromInt(5, 0, 8), outByte);
		MeshInputData data = _createMeshInput(central, north);
		
		Assert.assertEquals(inByte, LightReadingHelpers.getBlockLight(data, (byte)5, (byte)31, (byte)8));
		Assert.assertEquals(outByte, LightReadingHelpers.getBlockLight(data, (byte)5, (byte)32, (byte)8));
		Assert.assertEquals((byte)0, LightReadingHelpers.getBlockLight(data, (byte)5, (byte)-1, (byte)8));
	}

	@Test
	public void upFacingSkyLight() throws Throwable
	{
		// Check for both lit and in shadow.
		AbsoluteLocation blockLocation = new AbsoluteLocation(5, 6, 7);
		CuboidData central = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		central.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), STONE.number());
		MeshInputData data = _createMeshInput(central, null);
		
		Assert.assertEquals(LightReadingHelpers.SKY_LIGHT_DIRECT, LightReadingHelpers.getUpFacingSkyMultipler(data, (byte)5, (byte)6, (byte)8), 0.01f);
		Assert.assertEquals(LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.getUpFacingSkyMultipler(data, (byte)5, (byte)6, (byte)7), 0.01f);
	}

	@Test
	public void sideSkyLight() throws Throwable
	{
		// Check for both lit and in shadow.
		AbsoluteLocation blockLocation = new AbsoluteLocation(5, 6, 7);
		CuboidData central = CuboidGenerator.createFilledCuboid(CuboidAddress.fromInt(0, 0, 0), ENV.special.AIR);
		central.setData15(AspectRegistry.BLOCK, blockLocation.getBlockAddress(), STONE.number());
		MeshInputData data = _createMeshInput(central, null);
		
		float lit = 0.8f;
		Assert.assertEquals(lit, LightReadingHelpers.getSkyLightMultiplier(data, (byte)5, (byte)7, (byte)7, lit), 0.1f);
		Assert.assertEquals(LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.getSkyLightMultiplier(data, (byte)5, (byte)6, (byte)7, lit), 0.1f);
	}


	private static MeshInputData _createMeshInput(CuboidData central, CuboidData north)
	{
		ColumnHeightMap heightMap = ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(central), central.getCuboidAddress()).freeze();
		ColumnHeightMap northHeight = (null != north)
			? ColumnHeightMap.build().consume(HeightMapHelpers.buildHeightMap(north), north.getCuboidAddress()).freeze()
			: null
		;
		return new MeshInputData(central, heightMap
			, null, null
			, null, null
			, north, northHeight
			, null, null
			, null, null
			, null, null
			, new IReadOnlyCuboidData[][][] {
				new IReadOnlyCuboidData[][] {
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
				},
				new IReadOnlyCuboidData[][] {
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[] { null, central, null },
					new IReadOnlyCuboidData[] { null, north, null },
				},
				new IReadOnlyCuboidData[][] {
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
					new IReadOnlyCuboidData[3],
				},
			}
			, new ColumnHeightMap[][] {
				new ColumnHeightMap[] {
					null,
					null,
					null,
				},
				new ColumnHeightMap[] {
					null,
					heightMap,
					northHeight,
				},
				new ColumnHeightMap[] {
					null,
					null,
					null,
				},
			}
		);
	}
}
