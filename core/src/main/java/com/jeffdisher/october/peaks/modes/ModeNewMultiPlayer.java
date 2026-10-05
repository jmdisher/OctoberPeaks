package com.jeffdisher.october.peaks.modes;

import java.net.InetSocketAddress;

import com.jeffdisher.october.peaks.InputCapture;
import com.jeffdisher.october.peaks.UiData;
import com.jeffdisher.october.peaks.persistence.MutableServerList;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.ui.FixedWindow;
import com.jeffdisher.october.peaks.ui.GlUi;
import com.jeffdisher.october.peaks.ui.IAction;
import com.jeffdisher.october.peaks.ui.Rect;
import com.jeffdisher.october.peaks.ui.ServerRecordTransformer;
import com.jeffdisher.october.peaks.ui.StatelessMultiLineButton;
import com.jeffdisher.october.peaks.ui.ViewOfStateless;
import com.jeffdisher.october.peaks.ui.ViewTextButton;
import com.jeffdisher.october.peaks.ui.ViewTextField;
import com.jeffdisher.october.peaks.ui.ViewTextLabel;


/**
 * The state where present an option to add a new one server to our list.
 */
public class ModeNewMultiPlayer implements IGameMode
{
	public static final int MAX_MULTIPLAYER_LENGTH = 32;

	private final ModeContainer _modeContainer;
	private final GlUi _ui;
	private final InputCapture _inputCapture;
	private final UiData _uiData;
	private final FixedWindow _newMultiPlayerStateWindow;

	public ModeNewMultiPlayer(ModeContainer modeContainer
		, GlUi ui
		, InputCapture inputCapture
		, UiData uiData
	)
	{
		_modeContainer = modeContainer;
		_ui = ui;
		_inputCapture = inputCapture;
		_uiData = uiData;
		
		_newMultiPlayerStateWindow = _buildNewMultiPlayerStateWindow(_ui, _uiData);
	}

	public ModeNewMultiPlayer becomeActive()
	{
		// Select the default text field.
		_inputCapture.textCapture = _uiData.newServerAddressBinding;
		_inputCapture.textLengthLimit = MAX_MULTIPLAYER_LENGTH;
		
		return this;
	}

	@Override
	public void didBecomeInactive()
	{
	}

	@Override
	public void handleEscape()
	{
		_goBack();
	}

	@Override
	public IAction drawRelevantWindows()
	{
		_ui.enterUiRenderMode();
		
		return _newMultiPlayerStateWindow.render(_inputCapture.glCursorLocation);
	}


	private FixedWindow _buildNewMultiPlayerStateWindow(GlUi ui, UiData uiData)
	{
		float margin = 0.6f;
		float divider = -0.2f;
		ViewOfStateless<MutableServerList.ServerRecord> currentlyTestingServerView = _buildServerTestingView(ui, uiData);
		ViewTextField<String> newServerAddressTextField = _buildNewServerAddressTextField(ui, uiData);
		ViewTextButton<String> testServerButton = _buildTestConnectionButton(ui);
		ViewTextButton<String> saveServerButton = _buildSaveConnectionButton(ui);
		ViewTextButton<String> backButton = _buildBackButton(ui);
		
		return new FixedWindow.Builder()
			.add(new ViewTextLabel(ui, new Binding<>("New Server Connection")), new Rect(-0.5f, 0.7f, 0.5f, 0.8f))
			.add(currentlyTestingServerView, new Rect(-margin, 0.4f, margin, 0.6f))
			
			.add(new ViewTextLabel(ui, new Binding<>("Server IP:port")), new Rect(-margin, 0.2f, divider, 0.3f))
			.add(newServerAddressTextField, new Rect(divider, 0.2f, margin, 0.3f))
			
			.add(testServerButton, new Rect(-margin, 0.0f, 0.0f, 0.1f))
			.add(saveServerButton, new Rect(0.0f, 0.0f, margin, 0.1f))
			
			.add(backButton, new Rect(-0.3f, -0.2f, 0.3f, -0.1f))
			.finish()
		;
	}

	private ViewOfStateless<MutableServerList.ServerRecord> _buildServerTestingView(GlUi ui, UiData uiData)
	{
		StatelessMultiLineButton<MutableServerList.ServerRecord> renderOnlyServerLine = new StatelessMultiLineButton<>(ui
			, new ServerRecordTransformer(ui)
			, null
		);
		return new ViewOfStateless<>(renderOnlyServerLine, uiData.currentlyTestingServerBinding);
	}

	private ViewTextField<String> _buildNewServerAddressTextField(GlUi ui, UiData uiData)
	{
		return new ViewTextField<>(ui, uiData.newServerAddressBinding
			, (String value) -> (_inputCapture.textCapture == uiData.newServerAddressBinding) ? (value + "_") : value
			, () -> (_inputCapture.textCapture == uiData.newServerAddressBinding) ? ui.pixelGreen : ui.pixelLightGrey
			, () -> {
				_action_clickServerAddressTextField();
			}
		);
	}

	private ViewTextButton<String> _buildTestConnectionButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Test Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickTestServerButton();
			}
		);
	}

	private ViewTextButton<String> _buildSaveConnectionButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Save Tested Connection")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickSaveServerButton();
			}
		);
	}

	private ViewTextButton<String> _buildBackButton(GlUi ui)
	{
		return new ViewTextButton<>(ui, new Binding<>("Back")
			, (String text) -> text
			, (ViewTextButton<String> button, String text) -> {
				_action_clickBackButton();
			}
		);
	}

	private void _action_clickServerAddressTextField()
	{
		// We want to enable text capture for this binding.
		if (_inputCapture.mouseClicked0)
		{
			_inputCapture.textCapture = _uiData.newServerAddressBinding;
			_inputCapture.textLengthLimit = MAX_MULTIPLAYER_LENGTH;
		}
	}

	private void _action_clickTestServerButton()
	{
		if (_inputCapture.mouseClicked0)
		{
			// We want to do the test for version, etc, and add this to our list on success.
			// We will need to parse this address from the binding.
			String rawAddress = _uiData.newServerAddressBinding.get();
			int colonIndex = rawAddress.indexOf(":");
			if (-1 != colonIndex)
			{
				String ipHostName = rawAddress.substring(0, colonIndex);
				int port = Integer.parseInt(rawAddress.substring(colonIndex + 1));
				InetSocketAddress address = new InetSocketAddress(ipHostName, port);
				
				// Create the socket and start the background test, storing the new token in the binding.
				MutableServerList.ServerRecord record = _uiData.serverList.beginSpecialPollRequest(address);
				_uiData.currentlyTestingServerBinding.set(record);
				_uiData.newServerAddressBinding.set("");
				_inputCapture.textCapture = null;
			}
		}
	}

	private void _action_clickSaveServerButton()
	{
		if (_inputCapture.mouseClicked0)
		{
			// If there is a binding, and it is good, add it to the server list and back out of this.
			MutableServerList.ServerRecord record = _uiData.currentlyTestingServerBinding.get();
			if ((null != record) && record.isGood)
			{
				_uiData.serverList.addServerToList(record);
				_uiData.currentlyTestingServerBinding.set(null);
				
				// We can escape this state.
				_goBack();
			}
		}
	}

	private void _action_clickBackButton()
	{
		if (_inputCapture.mouseClicked0)
		{
			_goBack();
		}
	}

	private void _goBack()
	{
		// Go back to the list.
		_modeContainer.setActive(_modeContainer.listMultiPlayer.becomeActive());
	}
}
