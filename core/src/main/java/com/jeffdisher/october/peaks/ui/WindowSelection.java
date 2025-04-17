package com.jeffdisher.october.peaks.ui;

import java.util.Map;
import java.util.function.Function;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.BlockProxy;
import com.jeffdisher.october.peaks.types.WorldSelection;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.PartialEntity;
import com.jeffdisher.october.utils.Assert;


/**
 * Rendering of the "currently selected block/entity under reticle" window.
 */
public class WindowSelection
{
	// Note that the size is variable for this window so we only know the location, not the width.
	public static final float SELECTED_BOX_LEFT = 0.05f;
	public static final float SELECTED_BOX_BOTTOM = 0.90f;
	public static final Rect LOCATION = new Rect(SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM, SELECTED_BOX_LEFT, SELECTED_BOX_BOTTOM);

	public static IView<WorldSelection> buildRenderer(GlUi ui
			, Environment env
			, Function<AbsoluteLocation, BlockProxy> blockLookup
			, Map<Integer, String> otherPlayersById
	)
	{
		return (Rect location, Binding<WorldSelection> binding, Point cursor) -> {
			// If there is anything selected, draw its description at the top of the screen (we always prioritize the block, but at most one of these can be non-null).
			// Note that the binding.data is assumed to be never null, but may be empty.
			WorldSelection selection = binding.get();
			if (null != selection)
			{
				AbsoluteLocation selectedBlock = binding.get().stopBlock();
				PartialEntity selectedEntity = binding.get().entity();
				if (null != selectedBlock)
				{
					// Draw the block information.
					BlockProxy proxy = blockLookup.apply(selectedBlock);
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
				else
				{
					// At least one of these must be non-null.
					Assert.assertTrue(null != selectedEntity);
					// Draw the entity information.
					// If this matches a player, show the name instead of the type name.
					String textToShow = otherPlayersById.get(selectedEntity.id());
					if (null == textToShow)
					{
						textToShow = selectedEntity.type().name();
					}
					UiIdioms.drawTextInFrame(ui, location.leftX(), location.bottomY(), textToShow);
				}
			}
			
			// No hover or action.
			return null;
		};
	}
}
