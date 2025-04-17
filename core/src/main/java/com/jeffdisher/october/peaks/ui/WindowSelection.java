package com.jeffdisher.october.peaks.ui;

import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.PartialEntity;


/**
 * Rendering of the "currently selected block/entity under reticle" window.
 */
public class WindowSelection
{
	// Note that the size is variable for this window so we only know the location, not the width.
	public static final float SELECTED_BOX_LEFT = 0.05f;
	public static final float SELECTED_BOX_BOTTOM = 0.90f;
	public static final Rect LOCATION = new Rect(SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM, SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM);

	public static IView<Selection> buildRenderer(GlUi ui
			, Environment env
			, Function<AbsoluteLocation, BlockProxy> blockLookup
			, Map<Integer, String> otherPlayersById
	)
	{
		return (Rect location, Binding<Selection> binding, Point cursor) -> {
			// If there is anything selected, draw its description at the top of the screen (we always prioritize the block, but at most one of these can be non-null).
			// Note that the binding.data is assumed to be never null, but may be empty.
			if (null != binding.get().selectedBlock)
			{
				// Draw the block information.
				BlockProxy proxy = blockLookup.apply(binding.get().selectedBlock);
				if (null != proxy)
				{
					Block blockUnderMouse = proxy.getBlock();
					if (env.special.AIR != blockUnderMouse)
					{
						Item itemUnderMouse = blockUnderMouse.item();
						UiIdioms.drawTextInFrame(ui, location.leftX(), location.bottomY(), itemUnderMouse.name());
					}
				}
			}
			else if (null != binding.get().selectedEntity)
			{
				// Draw the entity information.
				// If this matches a player, show the name instead of the type name.
				String textToShow = otherPlayersById.get(binding.get().selectedEntity.id());
				if (null == textToShow)
				{
					textToShow = binding.get().selectedEntity.type().name();
				}
				UiIdioms.drawTextInFrame(ui, location.leftX(), location.bottomY(), textToShow);
			}
			
			// No hover or action.
			return null;
		};
	}

	public static record Selection(AbsoluteLocation selectedBlock, PartialEntity selectedEntity) {}
}
