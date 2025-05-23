package com.jeffdisher.october.peaks.ui;


/**
 * A rectangle used to describe spaces in the 2D UI.
 */
public record Rect(float leftX, float bottomY, float rightX, float topY)
{
	public boolean containsPoint(Point point)
	{
		boolean contains;
		if (null != point)
		{
			float glX = point.x();
			float glY = point.y();
			contains = ((this.leftX <= glX) && (glX <= this.rightX) && (this.bottomY <= glY) && (glY <= this.topY));
		}
		else
		{
			contains = false;
		}
		return contains;
	}

	public float getWidth()
	{
		return this.rightX - this.leftX;
	}

	public float getHeight()
	{
		return this.topY - this.bottomY;
	}

	public float getCentreX()
	{
		return (this.leftX + this.rightX) / 2.0f;
	}

	public float getCentreY()
	{
		return (this.bottomY + this.topY) / 2.0f;
	}
}
