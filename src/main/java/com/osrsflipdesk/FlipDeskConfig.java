package com.osrsflipdesk;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

@ConfigGroup(FlipDeskConfig.GROUP)
public interface FlipDeskConfig extends Config
{
    String GROUP = "osrsflipdesk";

    @ConfigSection(
        name = "Advanced",
        description = "Optional tracking tweaks. Most people can leave these alone.",
        position = 100,
        closedByDefault = true
    )
    String advancedSection = "advancedSection";

    @ConfigItem(
        keyName = "gpOnHand",
        name = "GP on hand",
        description = "Cash available for flipping. You can also edit this in the Flip Desk side panel.",
        position = 0
    )
    @Range(min = 0, max = Integer.MAX_VALUE)
    default int gpOnHand()
    {
        return 50_000_000;
    }

    @ConfigItem(
        keyName = "strictness",
        name = "Flip strictness",
        description = "More flips = looser filters, more opportunities. Safer fills = thicker markets and more fill cushion. Balanced is the default.",
        position = 1
    )
    default FlipStrictness strictness()
    {
        return FlipStrictness.BALANCED;
    }

    @ConfigItem(
        keyName = "ticketSize",
        name = "Minimum flip profit",
        description = "Hide small-ticket flips and allow larger position sizes. Needs enough GP on hand for expensive items. 50k+ / 200k+ also relax volume filters so mid-liquidity items can appear.",
        position = 2
    )
    default FlipTicketSize ticketSize()
    {
        return FlipTicketSize.MEDIUM;
    }

    @ConfigItem(
        keyName = "sortMode",
        name = "Sort flips by",
        description = "Total profit ranks biggest estimated payout first. Fill confidence ranks easier fills first (often smaller GP).",
        position = 3
    )
    default FlipSortMode sortMode()
    {
        return FlipSortMode.TOTAL_PROFIT;
    }

    @ConfigItem(
        keyName = "refreshSeconds",
        name = "Refresh seconds",
        description = "How often market data refreshes.",
        position = 4
    )
    @Range(min = 5, max = 60)
    default int refreshSeconds()
    {
        return 10;
    }

    @ConfigItem(
        keyName = "buyFillNotification",
        name = "Buy fill notification",
        description = "RuneLite tray/OS notification when a tracked flip buy fills (partial or complete).",
        position = 5
    )
    default Notification buyFillNotification()
    {
        return Notification.ON;
    }

    @ConfigItem(
        keyName = "sellFillNotification",
        name = "Sell fill notification",
        description = "RuneLite tray/OS notification when a tracked flip sell fills (partial or complete).",
        position = 6
    )
    default Notification sellFillNotification()
    {
        return Notification.ON;
    }

    @ConfigItem(
        keyName = "testFillNotifications",
        name = "Test fill notifications",
        description = "Turn this on to send sample buy and sell notifications using your settings above, then it turns itself back off. If RuneLite is focused and nothing appears, enable \"Send notifications when focused\" in RuneLite's notification settings.",
        position = 7
    )
    default boolean testFillNotifications()
    {
        return false;
    }

    @ConfigItem(
        keyName = "buyPriceToleranceTenths",
        name = "Buy price tolerance (tenths %)",
        description = "How far your actual buy can deviate from a recommendation and still auto-track. 200 = 20%.",
        position = 1,
        section = advancedSection
    )
    @Range(min = 0, max = 1000)
    default int buyPriceToleranceTenths()
    {
        return 200;
    }

    @ConfigItem(
        keyName = "recommendationMemoryMinutes",
        name = "Recommendation memory (minutes)",
        description = "Keep items trackable this long after they leave the top list.",
        position = 2,
        section = advancedSection
    )
    @Range(min = 5, max = 240)
    default int recommendationMemoryMinutes()
    {
        return 60;
    }

    @ConfigItem(
        keyName = "trackNearMarketBuys",
        name = "Track near-market buys",
        description = "Open an Active flip when a buy is close to the market low even if it is not in the current list.",
        position = 3,
        section = advancedSection
    )
    default boolean trackNearMarketBuys()
    {
        return true;
    }

    @ConfigItem(
        keyName = "marketDataNotice",
        name = "Market data notice",
        description = "Flip Desk requests public prices from prices.runescape.wiki. It does not send your account name, GP, or GE offers.",
        position = 4,
        section = advancedSection
    )
    default boolean marketDataNotice()
    {
        return true;
    }
}
