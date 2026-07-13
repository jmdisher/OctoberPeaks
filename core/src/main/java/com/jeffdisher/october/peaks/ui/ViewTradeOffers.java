package com.jeffdisher.october.peaks.ui;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.aspects.TradingRegistry;
import com.jeffdisher.october.creatures.ExtensionVillager;
import com.jeffdisher.october.subactions.EntitySubActionSendTrade;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.MinimalEntity;


/**
 * Renders a villager's buy and sell offers in a list (we currently expect them to fit in a single page).
 */
public class ViewTradeOffers implements IView
{
	public static final float WINDOW_MARGIN = 0.05f;
	public static final float WINDOW_TITLE_HEIGHT = 0.1f;
	public static final float WINDOW_ITEM_SIZE = 0.1f;
	public static final float BUTTON_WIDTH = 0.2f;

	private final GlUi _ui;
	private final Binding<Integer> _villagerIdBinding;
	private final IntFunction<MinimalEntity> _entityLookup;
	private final StatelessViewTextButton<Item> _buyButton;
	private final StatelessViewTextButton<Item> _sellButton;

	public ViewTradeOffers(GlUi ui
		, Binding<Integer> villagerIdBinding
		, IntFunction<MinimalEntity> entityLookup
		, Consumer<Item> mouseOverKeyConsumer
	)
	{
		_ui = ui;
		_villagerIdBinding = villagerIdBinding;
		_entityLookup = entityLookup;
		_buyButton = new StatelessViewTextButton<>(ui, (Item ignore) -> "Buy", mouseOverKeyConsumer);
		_sellButton = new StatelessViewTextButton<>(ui, (Item ignore) -> "Sell", mouseOverKeyConsumer);
	}

	@Override
	public IAction render(Rect location, Point cursor)
	{
		// Fetch bound data.
		MinimalEntity villager = _entityLookup.apply(_villagerIdBinding.get());
		Environment env = Environment.getShared();
		TradingRegistry.Profession profession = ((ExtensionVillager.Data)villager.extendedData()).profession();
		Map<Item, Integer> offers = EntitySubActionSendTrade.villagerTradeOffers(env, villager);
		
		// Draw the window outline.
		UiIdioms.drawOverlayFrame(_ui, _ui.pixelDarkGreyAlpha, _ui.pixelLightGrey, location.leftX(), location.bottomY(), location.rightX(), location.topY());
		
		// Draw the number of trades (so we know if this is empty).
		_ui.drawLabel(location.leftX(), location.topY() - WINDOW_TITLE_HEIGHT, location.topY(), String.format("%s offering %d trades", profession.name(), offers.size()));
		
		// Just start listing the trade options.
		IAction action = null;
		float nextItemTop = location.topY() - WINDOW_TITLE_HEIGHT;
		for (Map.Entry<Item, Integer> elt : offers.entrySet())
		{
			Item key = elt.getKey();
			int cost = elt.getValue();
			
			float itemBottom = nextItemTop - WINDOW_ITEM_SIZE;
			Rect innerBounds = new Rect(location.leftX(), itemBottom, location.leftX() + BUTTON_WIDTH, nextItemTop);
			
			// The cost is >0 for a villager "buy", meaning it is our "sell", so invert these.
			StatelessViewTextButton<Item> button = (cost > 0)
				? _sellButton
				: _buyButton
			;
			IAction thisAction = button.render(innerBounds, cursor, key);
			if (null != thisAction)
			{
				action = thisAction;
			}
			
			// Draw the other text describing the trade.
			_ui.drawLabel(location.leftX() + BUTTON_WIDTH, itemBottom, nextItemTop, String.format("%s for %d", key.name(), Math.abs(cost)));
			
			nextItemTop = itemBottom;
		}
		return action;
	}
}
