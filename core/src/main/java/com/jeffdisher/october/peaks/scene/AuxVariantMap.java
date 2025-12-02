package com.jeffdisher.october.peaks.scene;

import java.util.HashMap;
import java.util.Map;

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
	private final Map<BlockAddress, AuxilliaryTextureAtlas.Variant> _overlay;

	public AuxVariantMap(Environment env, IReadOnlyCuboidData cuboid)
	{
		_env = env;
		_cuboid = cuboid;
		_overlay = new HashMap<>();
		cuboid.walkData(AspectRegistry.FLAGS, (BlockAddress base, byte size, Byte value) -> {
			if (FlagsAspect.isSet(value, FlagsAspect.FLAG_BURNING))
			{
				_overlay.put(base, AuxilliaryTextureAtlas.Variant.BURNING);
			}
		}, (byte) 0);
	}

	public AuxilliaryTextureAtlas.Variant get(BlockAddress blockAddress)
	{
		AuxilliaryTextureAtlas.Variant variant = _overlay.get(blockAddress);
		if (null == variant)
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
