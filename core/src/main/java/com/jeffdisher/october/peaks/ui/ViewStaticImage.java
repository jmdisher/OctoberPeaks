package com.jeffdisher.october.peaks.ui;


/**
 * Draws a texture into a location on screen.
 * The texture is not owned by the instance and is assumed to be long-lived within the GL context.
 */
public class ViewStaticImage implements IView
{
	private final GlUi _ui;
	private final int _texture;

	public ViewStaticImage(GlUi ui
		, int texture
	)
	{
		_ui = ui;
		_texture = texture;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		float leftX = location.leftX();
		float bottomY = location.bottomY();
		float rightX = location.rightX();
		float topY = location.topY();
		_ui.drawWholeTextureRect(_texture, leftX, bottomY, rightX, topY);
		return null;
	}
}
