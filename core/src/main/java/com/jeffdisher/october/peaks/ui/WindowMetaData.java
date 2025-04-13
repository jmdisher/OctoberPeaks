package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.types.Entity;


/**
 * Rendering of the meta-data window.
 */
public class WindowMetaData
{
	public static final float SMALL_TEXT_HEIGHT = 0.05f;
	public static final float META_DATA_LABEL_WIDTH = 0.1f;
	public static final float META_DATA_BOX_LEFT = 0.8f;
	public static final float META_DATA_BOX_BOTTOM = -0.95f;

	public static final Rect LOCATION = new Rect(META_DATA_BOX_LEFT, META_DATA_BOX_BOTTOM, META_DATA_BOX_LEFT + 1.5f * META_DATA_LABEL_WIDTH, META_DATA_BOX_BOTTOM + 3.0f * SMALL_TEXT_HEIGHT);

	public static IView<Entity> buildRenderer(GlUi ui)
	{
		return (Rect location, Binding<Entity> binding) -> {
			UiIdioms.drawOverlayFrame(ui, ui.pixelDarkGreyAlpha, ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
			
			float valueMargin = location.leftX() + META_DATA_LABEL_WIDTH;
			
			// We will use the greater of authoritative and projected for most of these stats.
			// That way, we get the stability of the authoritative numbers but the quick response to eating/breathing actions)
			byte health = binding.data.health();
			float base = location.bottomY() + 2.0f * SMALL_TEXT_HEIGHT;
			float top = base + SMALL_TEXT_HEIGHT;
			ui.drawLabel(location.leftX(), base, top, "Health");
			ui.drawLabel(valueMargin, base, top, Byte.toString(health));
			
			byte food = binding.data.food();
			base = location.bottomY() + 1.0f * SMALL_TEXT_HEIGHT;
			top = base + SMALL_TEXT_HEIGHT;
			ui.drawLabel(location.leftX(), base, top, "Food");
			ui.drawLabel(valueMargin, base, top, Byte.toString(food));
			
			int breath = binding.data.breath();
			base = location.bottomY() + 0.0f * SMALL_TEXT_HEIGHT;
			top = base + SMALL_TEXT_HEIGHT;
			ui.drawLabel(location.leftX(), base, top, "Breath");
			ui.drawLabel(valueMargin, base, top, Integer.toString(breath));
		};
	}
}
