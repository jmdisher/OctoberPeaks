package com.jeffdisher.october.peaks.scene;

import com.jeffdisher.october.aspects.AspectRegistry;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.FlagsAspect;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.textures.AuxilliaryTextureAtlas;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;


/**
 * Used to combine various data sources we use to determine the AUX texture variant for a given block.
 */
public class AuxVariantMap
{
	private final Environment _env;
	private final IReadOnlyCuboidData _cuboid;

	public AuxVariantMap(Environment env, IReadOnlyCuboidData cuboid)
	{
		_env = env;
		_cuboid = cuboid;
	}

	public AuxilliaryTextureAtlas.Variant get(BlockAddress blockAddress)
	{
		AuxilliaryTextureAtlas.Variant variant;
		byte flags = _cuboid.getData7(AspectRegistry.FLAGS, blockAddress);
		if (FlagsAspect.isSet(flags, FlagsAspect.FLAG_BURNING))
		{
			variant = AuxilliaryTextureAtlas.Variant.BURNING;
		}
		else
		{
			Integer obj = _cuboid.getDataSpecial(AspectRegistry.DAMAGE, blockAddress);
			if (null != obj)
			{
				// We will favour showing cracks at a low damage, so the feedback is obvious
				int damage = obj.intValue();
				Block block = new BlockProxy(blockAddress, _cuboid).getBlock();
				float damaged = (float) damage / (float)_env.damage.getToughness(block);
				
				if (damaged > 0.6f)
				{
					variant = AuxilliaryTextureAtlas.Variant.BREAK_HIGH;
				}
				else if (damaged > 0.3f)
				{
					variant = AuxilliaryTextureAtlas.Variant.BREAK_MEDIUM;
				}
				else
				{
					variant = AuxilliaryTextureAtlas.Variant.BREAK_LOW;
				}
			}
			else
			{
				variant = AuxilliaryTextureAtlas.Variant.NONE;
			}
		}
		return variant;
	}
}
