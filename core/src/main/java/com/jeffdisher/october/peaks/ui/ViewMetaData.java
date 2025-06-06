package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Entity;


/**
 * Rendering of the meta-data window.
 */
public class ViewMetaData implements IView
{
	public static final float SMALL_TEXT_HEIGHT = 0.05f;
	public static final float META_DATA_LABEL_WIDTH = 0.1f;
	public static final float META_DATA_BOX_LEFT = 0.8f;
	public static final float META_DATA_BOX_BOTTOM = -0.95f;

	public static final Rect LOCATION = new Rect(META_DATA_BOX_LEFT, META_DATA_BOX_BOTTOM, META_DATA_BOX_LEFT + 1.5f * META_DATA_LABEL_WIDTH, META_DATA_BOX_BOTTOM + 3.0f * SMALL_TEXT_HEIGHT);

	private final GlUi _ui;
	private final Binding<Entity> _binding;

	public ViewMetaData(GlUi ui
			, Binding<Entity> binding
	)
	{
		_ui = ui;
		_binding = binding;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
		
		float valueMargin = location.leftX() + META_DATA_LABEL_WIDTH;
		
		// We will use the greater of authoritative and projected for most of these stats.
		// That way, we get the stability of the authoritative numbers but the quick response to eating/breathing actions)
		Entity entity = _binding.get();
		byte health = entity.health();
		float base = location.bottomY() + 2.0f * SMALL_TEXT_HEIGHT;
		float top = base + SMALL_TEXT_HEIGHT;
		_ui.drawLabel(location.leftX(), base, top, "Health");
		_ui.drawLabel(valueMargin, base, top, Byte.toString(health));
		
		byte food = entity.food();
		base = location.bottomY() + 1.0f * SMALL_TEXT_HEIGHT;
		top = base + SMALL_TEXT_HEIGHT;
		_ui.drawLabel(location.leftX(), base, top, "Food");
		_ui.drawLabel(valueMargin, base, top, Byte.toString(food));
		
		int breath = entity.breath();
		base = location.bottomY() + 0.0f * SMALL_TEXT_HEIGHT;
		top = base + SMALL_TEXT_HEIGHT;
		_ui.drawLabel(location.leftX(), base, top, "Breath");
		_ui.drawLabel(valueMargin, base, top, Integer.toString(breath));
		
		// No hover or action.
		return null;
	}
}
