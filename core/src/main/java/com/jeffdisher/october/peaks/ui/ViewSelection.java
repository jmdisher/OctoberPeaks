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
public class ViewSelection implements IView
{
	// Note that the size is variable for this window so we only know the location, not the width.
	public static final float SELECTED_BOX_X = 0.0f;
	public static final float SELECTED_BOX_Y = 0.90f;
	public static final Rect LOCATION = new Rect(SELECTED_BOX_X, SELECTED_BOX_Y, SELECTED_BOX_X, SELECTED_BOX_Y);

	private final GlUi _ui;
	private final Environment _env;
	private final Binding<WorldSelection> _binding;
	private final Function<AbsoluteLocation, BlockProxy> _blockLookup;
	private final Map<Integer, String> _otherPlayersById;

	public ViewSelection(GlUi ui
			, Environment env
			, Binding<WorldSelection> binding
			, Function<AbsoluteLocation, BlockProxy> blockLookup
			, Map<Integer, String> otherPlayersById
	)
	{
		_ui = ui;
		_env = env;
		_binding = binding;
		_blockLookup = blockLookup;
		_otherPlayersById = otherPlayersById;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// If there is anything selected, draw its description at the top of the screen (we always prioritize the block, but at most one of these can be non-null).
		// Note that the binding.data is assumed to be never null, but may be empty.
		WorldSelection selection = _binding.get();
		if (null != selection)
		{
			AbsoluteLocation selectedBlock = _binding.get().stopBlock();
			PartialEntity selectedEntity = _binding.get().entity();
			if (null != selectedBlock)
			{
				// Draw the block information.
				BlockProxy proxy = _blockLookup.apply(selectedBlock);
				if (null != proxy)
				{
					Block blockUnderMouse = proxy.getBlock();
					if (_env.special.AIR != blockUnderMouse)
					{
						Item itemUnderMouse = blockUnderMouse.item();
						String name = itemUnderMouse.name();
						Rect bounds = _getTextBounds(name);
						UiIdioms.drawOutline(_ui, bounds, false);
						UiIdioms.drawTextCentred(_ui, bounds, name);
					}
				}
			}
			else
			{
				// At least one of these must be non-null.
				Assert.assertTrue(null != selectedEntity);
				// Draw the entity information.
				// If this matches a player, show the name instead of the type name.
				String textToShow = _otherPlayersById.get(selectedEntity.id());
				if (null == textToShow)
				{
					textToShow = selectedEntity.type().name();
				}
				
				Rect bounds = _getTextBounds(textToShow);
				UiIdioms.drawOutline(_ui, bounds, false);
				UiIdioms.drawTextCentred(_ui, bounds, textToShow);
			}
		}
		
		// No hover or action.
		return null;
	}


	private Rect _getTextBounds(String text)
	{
		float width = UiIdioms.getTextWidth(_ui, text, UiIdioms.GENERAL_TEXT_HEIGHT) + (2.0f * UiIdioms.OUTLINE_SIZE);
		float halfWidth = width / 2.0f;
		float leftX = SELECTED_BOX_X - halfWidth;
		float bottomY = SELECTED_BOX_Y;
		float rightX = SELECTED_BOX_X + halfWidth;
		float topY = SELECTED_BOX_Y + UiIdioms.GENERAL_TEXT_HEIGHT;
		return new Rect(leftX, bottomY, rightX, topY);
	}
}
