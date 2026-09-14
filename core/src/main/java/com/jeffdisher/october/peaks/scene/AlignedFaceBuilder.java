package com.jeffdisher.october.peaks.scene;

import com.jeffdisher.october.aspects.LightAspect;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.utils.Assert;


/**
 * Configured for a specific axis-aligned face, this generates the vertices for quads fed into the receiver.
 */
public class AlignedFaceBuilder
{
	private final MeshInputData _data;
	private final float[] _uvBase;
	private final float _uvSize;
	private final float[] _auxBase;
	private final float _auxSize;
	private final EntityLocation _location;
	private final Normal _normal;

	private final float[] _blockMultipliers;
	private final float[] _skyMultipliers;

	/**
	 * Configures a face builder with the given data (to describe the faces of a block).
	 * 
	 * @param data The mesh data.
	 * @param uvBase The base UV coordinates of the primary texture.
	 * @param uvSize The size of a single entry in the primary texture.
	 * @param auxBase The base UV coordinates of the auxiliary texture.
	 * @param auxSize The size of a single entry in the auxiliary texture.
	 * @param location The base location of the block being drawn.
	 * @param normal The normal vector of this face.
	 */
	public AlignedFaceBuilder(MeshInputData data
		, float[] uvBase
		, float uvSize
		, float[] auxBase
		, float auxSize
		, AbsoluteLocation location
		, Normal normal
	)
	{
		_data = data;
		_uvBase = uvBase;
		_uvSize = uvSize;
		_auxBase = auxBase;
		_auxSize = auxSize;
		_location = location.toEntityLocation();
		_normal = normal;
		
		BlockAddress blockAddress = location.getBlockAddress();
		byte[] blockLights = new byte[9];
		byte centreX = (byte)(blockAddress.x() + _normal.byteNormal[0]);
		byte centreY = (byte)(blockAddress.y() + _normal.byteNormal[1]);
		byte centreZ = (byte)(blockAddress.z() + _normal.byteNormal[2]);
		for (byte x = _normal.planeOffset[0]; x <= Math.abs(_normal.planeOffset[0]); ++x)
		{
			for (byte y = _normal.planeOffset[1]; y <= Math.abs(_normal.planeOffset[1]); ++y)
			{
				for (byte z = _normal.planeOffset[2]; z <= Math.abs(_normal.planeOffset[2]); ++z)
				{
					int index = (x - _normal.planeOffset[0]) * _normal.indexCoefficients[0]
						+ (y - _normal.planeOffset[1]) * _normal.indexCoefficients[1]
						+ (z - _normal.planeOffset[2]) * _normal.indexCoefficients[2]
					;
					blockLights[index] = LightReadingHelpers.getBlockLight(_data, (byte)(centreX+ x), (byte)(centreY+ y), (byte)(centreZ + z));
				}
			}
		}
		_blockMultipliers = new float[] {_maxLightAsFloat(blockLights[0], blockLights[1], blockLights[3], blockLights[4])
			, _maxLightAsFloat(blockLights[1], blockLights[2], blockLights[4], blockLights[5])
			, _maxLightAsFloat(blockLights[3], blockLights[4], blockLights[6], blockLights[7])
			, _maxLightAsFloat(blockLights[4], blockLights[5], blockLights[7], blockLights[8])
		};
		
		// The way sky light is managed is very different between faces.
		switch (_normal)
		{
		case UP:
			// In this case, we blend the sky light much like block light.
			byte z = centreZ;
			byte westX = (byte)(centreX - 1);
			byte eastX = (byte)(centreX + 1);
			byte southY = (byte)(centreY - 1);
			byte northY = (byte)(centreY + 1);
			float skySW = LightReadingHelpers.getUpFacingSkyMultipler(data, westX, southY, z);
			float skyS = LightReadingHelpers.getUpFacingSkyMultipler(data, centreX, southY, z);
			float skySE = LightReadingHelpers.getUpFacingSkyMultipler(data, eastX, southY, z);
			float skyW = LightReadingHelpers.getUpFacingSkyMultipler(data, westX, centreY, z);
			float sky = LightReadingHelpers.getUpFacingSkyMultipler(data, centreX, centreY, z);
			float skyE = LightReadingHelpers.getUpFacingSkyMultipler(data, eastX, centreY, z);
			float skyNW = LightReadingHelpers.getUpFacingSkyMultipler(data, westX, northY, z);
			float skyN = LightReadingHelpers.getUpFacingSkyMultipler(data, centreX, northY, z);
			float skyNE = LightReadingHelpers.getUpFacingSkyMultipler(data, eastX, northY, z);
			// Remember that these need to be addressed like the block light:  2 * x + y.
			_skyMultipliers = new float[] {
				_blendSkyLight(skyW, skyS, skySW, sky),
				_blendSkyLight(skyW, skyN, skyNW, sky),
				_blendSkyLight(skyE, skyS, skySE, sky),
				_blendSkyLight(skyE, skyN, skyNE, sky),
			};
			break;
		case DOWN:
			// This case is always shadow.
			_skyMultipliers = new float[] { LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.SKY_LIGHT_SHADOW, LightReadingHelpers.SKY_LIGHT_SHADOW };
			break;
		case EAST:
		case NORTH:
		case SOUTH:
		case WEST:
			// In these cases, we just use the sky light in front of the face.
			float skyLightMultiplier = LightReadingHelpers.getSkyLightMultiplier(data, centreX, centreY, centreZ, LightReadingHelpers.SKY_LIGHT_PARTIAL);
			_skyMultipliers = new float[] { skyLightMultiplier, skyLightMultiplier, skyLightMultiplier, skyLightMultiplier };
			break;
			default:
				throw Assert.unreachable();
		}
	}

	/**
	 * Renders a quad between localBase and localEdge based on the receiver's configuration.  Note that there are no
	 * assumptions as to the relative positioning of the base and edge as the receiver can properly find the
	 * axis-aligned face between them.
	 * 
	 * @param builder A mesh builder.
	 * @param localBase The 3 coordinates of the base of this quad.
	 * @param localEdge The 3 coordinates of the edge of this quad.
	 */
	public void generateQuad(MeshHelperBufferBuilder builder, float[] localBase, float[] localEdge)
	{
		// Find the quad to determine rotation.
		float minX = Math.min(localBase[0], localEdge[0]);
		float maxX = Math.max(localBase[0], localEdge[0]);
		float minY = Math.min(localBase[1], localEdge[1]);
		float maxY = Math.max(localBase[1], localEdge[1]);
		float minZ = Math.min(localBase[2], localEdge[2]);
		float maxZ = Math.max(localBase[2], localEdge[2]);
		
		// We want to select the counter-clockwise vertices we will render, which requires face-specific logic.
		// We will include the x, y, z, block_light, and sky_light in these vertices.
		float[] v0;
		float[] v1;
		float[] v2;
		float[] v3;
		switch (_normal)
		{
		case WEST:
			v0 = new float[] {minX, maxY, minZ, _blockMultipliers[2], _skyMultipliers[2]};
			v1 = new float[] {minX, minY, minZ, _blockMultipliers[0], _skyMultipliers[0]};
			v2 = new float[] {minX, minY, maxZ, _blockMultipliers[1], _skyMultipliers[1]};
			v3 = new float[] {minX, maxY, maxZ, _blockMultipliers[3], _skyMultipliers[3]};
			break;
		case EAST:
			v0 = new float[] {minX, minY, minZ, _blockMultipliers[0], _skyMultipliers[0]};
			v1 = new float[] {minX, maxY, minZ, _blockMultipliers[2], _skyMultipliers[2]};
			v2 = new float[] {minX, maxY, maxZ, _blockMultipliers[3], _skyMultipliers[3]};
			v3 = new float[] {minX, minY, maxZ, _blockMultipliers[1], _skyMultipliers[1]};
			break;
		case SOUTH:
			v0 = new float[] {minX, minY, minZ, _blockMultipliers[0], _skyMultipliers[0]};
			v1 = new float[] {maxX, minY, minZ, _blockMultipliers[2], _skyMultipliers[2]};
			v2 = new float[] {maxX, minY, maxZ, _blockMultipliers[3], _skyMultipliers[3]};
			v3 = new float[] {minX, minY, maxZ, _blockMultipliers[1], _skyMultipliers[1]};
			break;
		case NORTH:
			v0 = new float[] {maxX, minY, minZ, _blockMultipliers[2], _skyMultipliers[2]};
			v1 = new float[] {minX, minY, minZ, _blockMultipliers[0], _skyMultipliers[0]};
			v2 = new float[] {minX, minY, maxZ, _blockMultipliers[1], _skyMultipliers[1]};
			v3 = new float[] {maxX, minY, maxZ, _blockMultipliers[3], _skyMultipliers[3]};
			break;
		case DOWN:
			v0 = new float[] {maxX, minY, minZ, _blockMultipliers[2], _skyMultipliers[2]};
			v1 = new float[] {minX, minY, minZ, _blockMultipliers[0], _skyMultipliers[0]};
			v2 = new float[] {minX, maxY, minZ, _blockMultipliers[1], _skyMultipliers[1]};
			v3 = new float[] {maxX, maxY, minZ, _blockMultipliers[3], _skyMultipliers[3]};
			break;
		case UP:
			v0 = new float[] {minX, minY, minZ, _blockMultipliers[0], _skyMultipliers[0]};
			v1 = new float[] {maxX, minY, minZ, _blockMultipliers[2], _skyMultipliers[2]};
			v2 = new float[] {maxX, maxY, minZ, _blockMultipliers[3], _skyMultipliers[3]};
			v3 = new float[] {minX, maxY, minZ, _blockMultipliers[1], _skyMultipliers[1]};
			break;
		default:
			throw Assert.unreachable();
		}
		
		float[][] coords = new float[][] {
			v0, v1, v2,
			v0, v2, v3,
		};
		for (float[] coord : coords)
		{
			// Each element is:
			// vx, vy, vz
			// nx, ny, nz
			// u, v
			// aux_U, aux_V
			// blockLight
			// skyLight
			float[] positions = new float[] {
				_location.x() + coord[0],
				_location.y() + coord[1],
				_location.z() + coord[2],
			};
			float[] textures = new float[] {
				_uvBase[0] + (coord[_normal.uvIndices[0]] * _uvSize),
				_uvBase[1] + (coord[_normal.uvIndices[1]] * _uvSize),
			};
			float[] otherTextures = new float[] {
				_auxBase[0] + (coord[_normal.uvIndices[0]] * _auxSize),
				_auxBase[1] + (coord[_normal.uvIndices[1]] * _auxSize),
			};
			float blockLight = coord[3];
			float skyLight = coord[4];
			
			builder.appendVertex(positions
				, _normal.normal
				, textures
				, otherTextures
				, new float[] { blockLight }
				, new float[] { skyLight }
			);
		}
	}


	private static float _maxLightAsFloat(byte one, byte two, byte three, byte four)
	{
		// We just want to take the maximum of the given 4 light values and convert them to a float light multiplier.
		byte max = (byte)Math.max(Math.max(one, two), Math.max(three, four));
		return _mapBlockLight(max);
	}

	private static float _mapBlockLight(byte inputValue)
	{
		float maxLightFloat = (float)LightAspect.MAX_LIGHT;
		return LightReadingHelpers.MINIMUM_LIGHT + (((float)inputValue) / maxLightFloat);
	}

	private static float _blendSkyLight(float one, float two, float three, float four)
	{
		// We will average these so that blocks in the open are brighter than those in corners.
		return (one + two + three + four) / 4.0f;
	}


	public static enum Normal
	{
		EAST(new float[] {1.0f, 0.0f, 0.0f}
			, new byte[] { 1, 0, 0 }
			, new byte[] { 0, -1, -1 }
			, new byte[] { 0, 3, 1 }
			, new byte[] {1, 2}
		),
		WEST(new float[] {-1.0f, 0.0f, 0.0f}
			, new byte[] { -1, 0, 0 }
			, new byte[] { 0, -1, -1 }
			, new byte[] { 0, 3, 1 }
			, new byte[] {1, 2}
		),
		NORTH(new float[] {0.0f, 1.0f, 0.0f}
			, new byte[] { 0, 1, 0 }
			, new byte[] { -1, 0, -1 }
			, new byte[] { 3, 0, 1 }
			, new byte[] {0, 2}
		),
		SOUTH(new float[] {0.0f, -1.0f, 0.0f}
			, new byte[] { 0, -1, 0 }
			, new byte[] { -1, 0, -1 }
			, new byte[] { 3, 0, 1 }
			, new byte[] {0, 2}
		),
		UP(new float[] {0.0f, 0.0f, 1.0f}
			, new byte[] { 0, 0, 1 }
			, new byte[] { -1, -1, 0 }
			, new byte[] { 3, 1, 0 }
			, new byte[] {0, 1}
		),
		DOWN(new float[] {0.0f, 0.0f, -1.0f}
			, new byte[] { 0, 0, -1 }
			, new byte[] { -1, -1, 0 }
			, new byte[] { 3, 1, 0 }
			, new byte[] {0, 1}
		),
		;
		
		public final float[] normal;
		public final byte[] byteNormal;
		public final byte[] planeOffset;
		public final byte[] indexCoefficients;
		public final byte[] uvIndices;
		private Normal(float[] normal
			, byte[] byteNormal
			, byte[] planeOffset
			, byte[] indexCoefficients
			, byte[] uvIndices
		)
		{
			Assert.assertTrue(3 == normal.length);
			Assert.assertTrue(3 == byteNormal.length);
			Assert.assertTrue(3 == planeOffset.length);
			Assert.assertTrue(3 == indexCoefficients.length);
			Assert.assertTrue(2 == uvIndices.length);
			
			this.normal = normal;
			this.byteNormal = byteNormal;
			this.planeOffset = planeOffset;
			this.indexCoefficients = indexCoefficients;
			this.uvIndices = uvIndices;
		}
	}
}
