package com.jeffdisher.october.peaks.ui;


/**
 * A container of 2 sub-views, arranging them horizontally.
 * This may be generalized to more than 2, in the future, but this keeps the API simple for now.
 */
public class StatelessHBox<T> implements IStatelessView<T>
{
	private final IStatelessView<T> _left;
	private final float _leftWidth;
	private final IStatelessView<T> _right;

	public StatelessHBox(IStatelessView<T> left, float leftWidth, IStatelessView<T> right)
	{
		_left = left;
		_leftWidth = leftWidth;
		_right = right;
	}

	@Override
	public IAction render(Rect bounds, Point cursor, T data)
	{
		float division = bounds.leftX() + _leftWidth;
		Rect leftBounds = new Rect(bounds.leftX(), bounds.bottomY(), division, bounds.topY());
		Rect rightBounds = new Rect(division, bounds.bottomY(), bounds.rightX(), bounds.topY());
		
		IAction leftAction = _left.render(leftBounds, cursor, data);
		IAction rightAction = _right.render(rightBounds, cursor, data);
		return (null != leftAction)
				? leftAction
				: rightAction
		;
	}

}
