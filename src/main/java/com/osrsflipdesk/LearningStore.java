package com.osrsflipdesk;

import java.util.Locale;
import net.runelite.client.config.ConfigManager;

final class LearningStore
{
    private static final String GROUP = FlipDeskConfig.GROUP;
    private static final double ALPHA = 0.25;

    private final ConfigManager configManager;

    LearningStore(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    double getBuyPremiumPercent(int itemId)
    {
        return readDouble("learn.buy." + itemId, 0.0);
    }

    double getSellMarkdownPercent(int itemId)
    {
        return readDouble("learn.sell." + itemId, 0.0);
    }

    int getFillCount(int itemId)
    {
        String value = configManager.getConfiguration(GROUP, "learn.count." + itemId);
        if (value == null)
        {
            return 0;
        }

        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ex)
        {
            return 0;
        }
    }

    void recordFill(
        int itemId,
        boolean buy,
        int actualExecutionPrice,
        MarketClient.MarketSnapshot market)
    {
        if (market == null || actualExecutionPrice <= 0)
        {
            return;
        }

        if (buy && market.avgLow > 0)
        {
            double observed = Math.max(
                0.0,
                (actualExecutionPrice - market.avgLow) * 100.0 / market.avgLow);
            update("learn.buy." + itemId, Math.min(2.0, observed));
        }
        else if (!buy && market.avgHigh > 0)
        {
            double observed = Math.max(
                0.0,
                (market.avgHigh - actualExecutionPrice) * 100.0 / market.avgHigh);
            update("learn.sell." + itemId, Math.min(2.0, observed));
        }

        int count = getFillCount(itemId);
        configManager.setConfiguration(GROUP, "learn.count." + itemId, Math.min(9999, count + 1));
    }

    private void update(String key, double observed)
    {
        double old = readDouble(key, observed);
        double next = old * (1.0 - ALPHA) + observed * ALPHA;
        configManager.setConfiguration(GROUP, key, String.format(Locale.US, "%.5f", next));
    }

    private double readDouble(String key, double fallback)
    {
        String value = configManager.getConfiguration(GROUP, key);
        if (value == null)
        {
            return fallback;
        }

        try
        {
            return Double.parseDouble(value);
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }
}
