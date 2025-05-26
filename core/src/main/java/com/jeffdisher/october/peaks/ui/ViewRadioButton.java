package com.jeffdisher.october.peaks.ui;


/**
 * A view class for displaying enum data with an enum data binding.
 */
public class ViewRadioButton<T extends Enum<T>> implements IView
{
	private final StatelessViewRadioButton<T> _stateless;
	private final Binding<T> _binding;

	public ViewRadioButton(StatelessViewRadioButton<T> stateless
			, Binding<T> binding
	)
	{
		_stateless = stateless;
		_binding = binding;
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		T data = _binding.get();
		return _stateless.render(location, cursor, data);
	}
}
