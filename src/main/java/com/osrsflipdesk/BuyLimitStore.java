package com.osrsflipdesk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * Tracks observed GE buys against each item's 4-hour buy limit.
 * Windows start on the first observed buy and reset after 4 hours.
 */
final class BuyLimitStore
{
    private static final String GROUP = FlipDeskConfig.GROUP;
    private static final String KEY = "buylimits.usage";
    private static final long WINDOW_MS = 4L * 60L * 60L * 1000L;
    private static final Type MAP_TYPE = new TypeToken<Map<String, Usage>>(){}.getType();

    private final ConfigManager configManager;
    private final Gson gson;

    BuyLimitStore(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    synchronized void recordBuy(int itemId, int quantity)
    {
        if (itemId <= 0 || quantity <= 0)
        {
            return;
        }

        Map<String, Usage> usages = readAll();
        String key = Integer.toString(itemId);
        long now = System.currentTimeMillis();
        Usage usage = usages.get(key);

        if (usage == null || now - usage.windowStartMs >= WINDOW_MS)
        {
            usage = new Usage();
            usage.windowStartMs = now;
            usage.bought = 0;
        }

        usage.bought += quantity;
        usages.put(key, usage);
        writeAll(usages);
    }

    synchronized int remaining(int itemId, int buyLimit)
    {
        if (buyLimit <= 0)
        {
            return 0;
        }

        Map<String, Usage> usages = readAll();
        Usage usage = usages.get(Integer.toString(itemId));
        long now = System.currentTimeMillis();

        if (usage == null || now - usage.windowStartMs >= WINDOW_MS)
        {
            return buyLimit;
        }

        return Math.max(0, buyLimit - usage.bought);
    }

    synchronized int used(int itemId, int buyLimit)
    {
        return Math.max(0, buyLimit - remaining(itemId, buyLimit));
    }

    synchronized long windowEndsAt(int itemId)
    {
        Usage usage = readAll().get(Integer.toString(itemId));
        long now = System.currentTimeMillis();
        if (usage == null || now - usage.windowStartMs >= WINDOW_MS)
        {
            return 0L;
        }
        return usage.windowStartMs + WINDOW_MS;
    }

    private Map<String, Usage> readAll()
    {
        String raw = configManager.getConfiguration(GROUP, KEY);
        if (raw == null || raw.trim().isEmpty())
        {
            return new HashMap<>();
        }

        try
        {
            Map<String, Usage> parsed = gson.fromJson(raw, MAP_TYPE);
            return parsed == null ? new HashMap<>() : parsed;
        }
        catch (Exception ex)
        {
            return new HashMap<>();
        }
    }

    private void writeAll(Map<String, Usage> usages)
    {
        // Drop expired entries so config stays small.
        long now = System.currentTimeMillis();
        usages.entrySet().removeIf(entry ->
            entry.getValue() == null || now - entry.getValue().windowStartMs >= WINDOW_MS);

        configManager.setConfiguration(GROUP, KEY, gson.toJson(usages));
    }

    static final class Usage
    {
        long windowStartMs;
        int bought;
    }
}
