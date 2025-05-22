package com.jeffdisher.october.peaks.ui;

import com.jeffdisher.october.peaks.persistence.MutableServerList;


/**
 * Common data transformer for the StatelessMultiLineButton when rendering MutableServerList.ServerRecord instances.
 */
public class ServerRecordTransformer implements StatelessMultiLineButton.ITransformer<MutableServerList.ServerRecord>
{
	private final GlUi _ui;

	public ServerRecordTransformer(GlUi ui)
	{
		_ui = ui;
	}

	@Override
	public int getLineCount()
	{
		// This implementation is specifically 2 lines.
		return 2;
	}

	@Override
	public int getOutlineTexture(MutableServerList.ServerRecord data)
	{
		return data.isGood
				? _ui.pixelGreen
				: _ui.pixelRed
		;
	}

	@Override
	public String getLine(MutableServerList.ServerRecord data, int line)
	{
		return (0 == line)
				? data.address.getHostName() + ":" + data.address.getPort()
				: data.humanReadableStatus
		;
	}
}
