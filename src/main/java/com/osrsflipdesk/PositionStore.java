package com.osrsflipdesk;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

final class PositionStore
{
    private static final String GROUP = FlipDeskConfig.GROUP;
    private static final String ACTIVE_KEY = "positions.active";
    private static final String HISTORY_KEY = "positions.history";
    private static final String TOTAL_KEY = "positions.totalProfit";

    private static final Type POSITION_LIST = new TypeToken<List<Position>>(){}.getType();

    private final ConfigManager configManager;
    private final Gson gson;

    PositionStore(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    synchronized List<Position> getActive()
    {
        return readList(ACTIVE_KEY);
    }

    synchronized List<Position> getHistory()
    {
        List<Position> rows = readList(HISTORY_KEY);
        rows.sort(Comparator.comparingLong((Position p) -> p.closedAt).reversed());
        return rows;
    }

    synchronized long getTotalProfit()
    {
        String raw = configManager.getConfiguration(GROUP, TOTAL_KEY);
        if (raw == null)
        {
            return 0L;
        }

        try
        {
            return Long.parseLong(raw);
        }
        catch (NumberFormatException ex)
        {
            return 0L;
        }
    }

    synchronized Position getActivePosition(int itemId)
    {
        for (Position position : readList(ACTIVE_KEY))
        {
            if (position.itemId == itemId && position.quantityRemaining > 0)
            {
                return position;
            }
        }

        return null;
    }

    synchronized boolean removeActive(long positionId)
    {
        List<Position> active = readList(ACTIVE_KEY);
        boolean removed = active.removeIf(p -> p.id == positionId);
        if (removed)
        {
            writeList(ACTIVE_KEY, active);
        }
        return removed;
    }

    synchronized void clearActive()
    {
        writeList(ACTIVE_KEY, new ArrayList<>());
    }

    synchronized void recordBuyFill(
        int itemId,
        String itemName,
        int quantity,
        int executionPrice,
        int recommendedSellPrice)
    {
        if (quantity <= 0 || executionPrice <= 0)
        {
            return;
        }

        List<Position> active = readList(ACTIVE_KEY);

        Position target = null;
        for (Position p : active)
        {
            if (p.itemId == itemId && p.quantityRemaining > 0)
            {
                target = p;
                break;
            }
        }

        if (target == null)
        {
            target = new Position();
            target.id = System.currentTimeMillis();
            target.itemId = itemId;
            target.itemName = itemName == null ? "Item " + itemId : itemName;
            target.openedAt = System.currentTimeMillis();
            target.recommendedSellPrice = recommendedSellPrice;
            active.add(target);
        }

        long previousCost = target.totalBuyCost;
        target.totalBuyCost += (long) quantity * executionPrice;
        target.quantityBought += quantity;
        target.quantityRemaining += quantity;
        target.averageBuyPrice = target.quantityBought > 0
            ? (int) Math.round(target.totalBuyCost / (double) target.quantityBought)
            : executionPrice;

        // Keep the newest reasonable recommendation when more quantity is bought.
        if (recommendedSellPrice > 0)
        {
            target.recommendedSellPrice = recommendedSellPrice;
        }

        writeList(ACTIVE_KEY, active);
    }

    synchronized SellResult recordSellFill(
        int itemId,
        int quantity,
        int executionPrice)
    {
        SellResult result = new SellResult();

        if (quantity <= 0 || executionPrice <= 0)
        {
            return result;
        }

        List<Position> active = readList(ACTIVE_KEY);
        List<Position> history = readList(HISTORY_KEY);

        int remainingToAllocate = quantity;
        long realizedThisEvent = 0L;

        // FIFO across active positions of the same item.
        active.sort(Comparator.comparingLong(p -> p.openedAt));

        for (Position p : active)
        {
            if (remainingToAllocate <= 0)
            {
                break;
            }
            if (p.itemId != itemId || p.quantityRemaining <= 0)
            {
                continue;
            }

            int matched = Math.min(remainingToAllocate, p.quantityRemaining);
            long costBasis = allocateBuyCost(p, matched);
            long gross = (long) executionPrice * matched;
            long tax = (long) MarketClient.geTax(executionPrice) * matched;
            long profit = gross - tax - costBasis;

            p.quantitySold += matched;
            p.quantityRemaining -= matched;
            p.totalSellRevenue += gross;
            p.realizedProfit += profit;
            p.lastSellPrice = executionPrice;
            realizedThisEvent += profit;
            remainingToAllocate -= matched;

            if (p.quantityRemaining == 0)
            {
                p.closedAt = System.currentTimeMillis();
                history.add(copyOf(p));
            }
        }

        active.removeIf(p -> p.quantityRemaining <= 0);

        if (realizedThisEvent != 0L)
        {
            long newTotal = getTotalProfit() + realizedThisEvent;
            configManager.setConfiguration(GROUP, TOTAL_KEY, Long.toString(newTotal));
            result.realizedProfit = realizedThisEvent;
            result.totalProfit = newTotal;
        }
        else
        {
            result.totalProfit = getTotalProfit();
        }

        // Bound history so config storage does not grow forever.
        history.sort(Comparator.comparingLong((Position p) -> p.closedAt).reversed());
        if (history.size() > 250)
        {
            history = new ArrayList<>(history.subList(0, 250));
        }

        writeList(ACTIVE_KEY, active);
        writeList(HISTORY_KEY, history);

        result.unmatchedQuantity = remainingToAllocate;
        return result;
    }

    synchronized void clearHistory()
    {
        configManager.unsetConfiguration(GROUP, HISTORY_KEY);
        configManager.setConfiguration(GROUP, TOTAL_KEY, "0");
    }

    private List<Position> readList(String key)
    {
        String raw = configManager.getConfiguration(GROUP, key);
        if (raw == null || raw.trim().isEmpty())
        {
            return new ArrayList<>();
        }

        try
        {
            List<Position> positions = gson.fromJson(raw, POSITION_LIST);
            return positions == null ? new ArrayList<>() : positions;
        }
        catch (Exception ex)
        {
            return new ArrayList<>();
        }
    }

    private void writeList(String key, List<Position> positions)
    {
        configManager.setConfiguration(GROUP, key, gson.toJson(positions));
    }

    /**
     * Average-cost basis for {@code matched} units still held.
     * On the final lot of a position, uses residual cost so allocations sum to {@code totalBuyCost}.
     */
    private static long allocateBuyCost(Position p, int matched)
    {
        if (matched <= 0 || p.quantityBought <= 0)
        {
            return 0L;
        }

        if (matched >= p.quantityRemaining)
        {
            long previouslyAllocated = p.quantitySold <= 0
                ? 0L
                : (p.totalBuyCost * (long) p.quantitySold) / p.quantityBought;
            return p.totalBuyCost - previouslyAllocated;
        }

        return (p.totalBuyCost * (long) matched) / p.quantityBought;
    }

    /** Remaining inventory cost basis for unrealized P&amp;L estimates. */
    static long remainingBuyCost(Position p)
    {
        if (p == null || p.quantityRemaining <= 0 || p.quantityBought <= 0)
        {
            return 0L;
        }
        return allocateBuyCost(p, p.quantityRemaining);
    }

    private static Position copyOf(Position p)
    {
        Position x = new Position();
        x.id = p.id;
        x.itemId = p.itemId;
        x.itemName = p.itemName;
        x.openedAt = p.openedAt;
        x.closedAt = p.closedAt;
        x.quantityBought = p.quantityBought;
        x.quantitySold = p.quantitySold;
        x.quantityRemaining = p.quantityRemaining;
        x.averageBuyPrice = p.averageBuyPrice;
        x.recommendedSellPrice = p.recommendedSellPrice;
        x.lastSellPrice = p.lastSellPrice;
        x.totalBuyCost = p.totalBuyCost;
        x.totalSellRevenue = p.totalSellRevenue;
        x.realizedProfit = p.realizedProfit;
        return x;
    }

    static final class Position
    {
        long id;
        int itemId;
        String itemName;
        long openedAt;
        long closedAt;
        int quantityBought;
        int quantitySold;
        int quantityRemaining;
        int averageBuyPrice;
        int recommendedSellPrice;
        int lastSellPrice;
        long totalBuyCost;
        long totalSellRevenue;
        long realizedProfit;
    }

    static final class SellResult
    {
        long realizedProfit;
        long totalProfit;
        int unmatchedQuantity;
    }
}
