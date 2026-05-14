package com.jeffdisher.october.peaks.types;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.october.types.FacingDirection;


public class TestPrism
{
	@Test
	public void facingRotations() throws Throwable
	{
		float west = 0.1f;
		float south = 0.2f;
		float bottom = 0.3f;
		float east = 0.5f;
		float north = 0.6f;
		float top = 0.7f;
		
		Prism faceNorth = new Prism(west
			, south
			, bottom
			, east
			, north
			, top
		);
		Prism faceEast = new Prism(south
			, 1.0f - east
			, bottom
			, north
			, 1.0f - west
			, top
		);
		Prism faceUp = new Prism(west
			, 1.0f - top
			, south
			, east
			, 1.0f - bottom
			, north
		);
		_match(faceNorth, faceNorth.rotateAboutBlockCentre(FacingDirection.NORTH));
		_match(faceEast, faceNorth.rotateAboutBlockCentre(FacingDirection.EAST));
		_match(faceUp, faceNorth.rotateAboutBlockCentre(FacingDirection.UP));
	}


	private static void _match(Prism one, Prism two)
	{
		Assert.assertEquals(one.west(), two.west(), 0.01f);
		Assert.assertEquals(one.south(), two.south(), 0.01f);
		Assert.assertEquals(one.bottom(), two.bottom(), 0.01f);
		Assert.assertEquals(one.east(), two.east(), 0.01f);
		Assert.assertEquals(one.north(), two.north(), 0.01f);
		Assert.assertEquals(one.top(), two.top(), 0.01f);
	}
}
