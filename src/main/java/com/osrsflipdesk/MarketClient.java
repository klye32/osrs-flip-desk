package com.osrsflipdesk;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
final class MarketClient
{
    private static final Logger log = LoggerFactory.getLogger(MarketClient.class);

    static final String BASE = "https://prices.runescape.wiki/api/v1/osrs";
    private static final String USER_AGENT = "OSRSFlipDesk-Runelite/1.0 - personal GE analytics plugin";

    private final Gson gson;
    private final OkHttpClient httpClient;
    private final Map<Integer, MappingItem> mapping = new HashMap<>();
    private final Map<Integer, MarketSnapshot> snapshots = new ConcurrentHashMap<>();
    private volatile double activeSellMarkdownPct = 0.75;

    @Inject
    MarketClient(Gson gson, OkHttpClient httpClient)
    {
        this.gson = gson;
        this.httpClient = httpClient;
    }

    synchronized MarketSnapshot getSnapshot(int itemId)
    {
        return snapshots.get(itemId);
    }

    synchronized String getItemName(int itemId)
    {
        MappingItem item = mapping.get(itemId);
        return item == null || item.name == null ? "Item " + itemId : item.name;
    }

    synchronized int getBuyLimit(int itemId)
    {
        MappingItem item = mapping.get(itemId);
        if (item == null || item.limit == null || item.limit <= 0)
        {
            return 0;
        }
        return item.limit;
    }

    synchronized int getRecommendedSellPrice(int itemId, LearningStore learningStore)
    {
        MarketSnapshot snap = snapshots.get(itemId);
        if (snap == null || snap.avgHigh <= 0 || snap.latestHigh <= 0)
        {
            return 0;
        }

        int patientSell = Math.min(snap.latestHigh, snap.avgHigh);
        double sellMarkdownPct = Math.max(activeSellMarkdownPct, learningStore.getSellMarkdownPercent(itemId));
        sellMarkdownPct = Math.min(sellMarkdownPct, 5.0);

        int competitiveSell = (int) Math.floor(patientSell * (1.0 - sellMarkdownPct / 100.0));
        return Math.max(0, competitiveSell);
    }

    List<FlipOpportunity> scan(
        int gpOnHand,
        FlipSortMode sortMode,
        FlipStrictness strictness,
        FlipTicketSize ticketSize,
        BuyLimitStore buyLimitStore) throws Exception
    {
        ensureMapping();

        FlipStrictness mode = strictness == null ? FlipStrictness.BALANCED : strictness;
        FlipTicketSize tickets = ticketSize == null ? FlipTicketSize.ANY : ticketSize;
        int minVolume = Math.max(5, (int) Math.round(mode.minVolume() * tickets.minVolumeScale()));
        double minRoiPercent = mode.minRoiPercent();
        double minSpreadPercent = mode.minSpreadPercent();
        int maxQuoteAgeSeconds = mode.maxQuoteAgeSeconds();
        int sizeMinutes = mode.sizeMinutes();
        double buyPremiumPct = mode.buyPremiumPercent();
        double sellMarkdownPct = mode.sellMarkdownPercent();
        int minTotalProfit = tickets.minTotalProfit();
        activeSellMarkdownPct = sellMarkdownPct;

        log.debug("Fetching latest GE prices");
        LatestResponse latest = getJson("/latest", LatestResponse.class);
        log.debug("Fetching 5-minute GE averages");
        AverageResponse five = getJson("/5m", AverageResponse.class);

        List<FlipOpportunity> out = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000L;

        if (latest == null || latest.data == null || five == null || five.data == null)
        {
            return out;
        }

        FlipSortMode effectiveSort = sortMode == null ? FlipSortMode.TOTAL_PROFIT : sortMode;
        int quoteAgeLimit = Math.max(15, maxQuoteAgeSeconds);
        double volumeMultiplier = (Math.max(1, sizeMinutes) / 5.0) * tickets.volumeScale();

        for (Map.Entry<String, LatestPrice> entry : latest.data.entrySet())
        {
            int itemId;
            try
            {
                itemId = Integer.parseInt(entry.getKey());
            }
            catch (NumberFormatException ex)
            {
                continue;
            }

            MappingItem meta = mapping.get(itemId);
            AveragePrice avg = five.data.get(entry.getKey());
            LatestPrice last = entry.getValue();

            if (meta == null || avg == null || last == null
                || last.low == null || last.high == null
                || last.lowTime == null || last.highTime == null
                || avg.avgLowPrice == null || avg.avgHighPrice == null)
            {
                continue;
            }

            if (last.low <= 0 || last.high <= 0 || avg.avgLowPrice <= 0 || avg.avgHighPrice <= 0)
            {
                continue;
            }

            int quoteAge = (int) Math.max(now - last.lowTime, now - last.highTime);
            if (quoteAge > quoteAgeLimit)
            {
                continue;
            }

            int highVolume = avg.highPriceVolume == null ? 0 : avg.highPriceVolume;
            int lowVolume = avg.lowPriceVolume == null ? 0 : avg.lowPriceVolume;
            int volume5m = highVolume + lowVolume;
            int twoSidedVolume = Math.min(highVolume, lowVolume);

            if (volume5m < minVolume || twoSidedVolume <= 0)
            {
                continue;
            }

            int patientBuy = Math.max(last.low, avg.avgLowPrice);
            int patientSell = Math.min(last.high, avg.avgHighPrice);

            double spreadPct = (patientSell - patientBuy) * 100.0 / patientBuy;
            if (spreadPct < minSpreadPercent)
            {
                continue;
            }

            // Patient book must still clear GE tax — rejects inverted / tax-eaten spreads.
            int patientTax = geTax(patientSell);
            int patientProfitPerItem = patientSell - patientBuy - patientTax;
            if (patientProfitPerItem <= 0)
            {
                continue;
            }

            int competitiveBuy = (int) Math.ceil(patientBuy * (1.0 + buyPremiumPct / 100.0));
            int competitiveSell = (int) Math.floor(patientSell * (1.0 - sellMarkdownPct / 100.0));

            if (competitiveBuy <= 0 || competitiveSell <= competitiveBuy)
            {
                continue;
            }

            if (gpOnHand < competitiveBuy)
            {
                continue;
            }

            int tax = geTax(competitiveSell);
            int profitPerItem = competitiveSell - competitiveBuy - tax;
            if (profitPerItem <= 0)
            {
                continue;
            }

            double roi = profitPerItem * 100.0 / competitiveBuy;
            if (roi < minRoiPercent)
            {
                continue;
            }

            // Extra durability check: your size should be a modest share of recent low-side volume.
            int limit = meta.limit == null || meta.limit <= 0 ? 1 : meta.limit;
            int remainingLimit = buyLimitStore == null
                ? limit
                : buyLimitStore.remaining(itemId, limit);

            // Don't recommend items you've already maxed for this 4-hour window.
            if (remainingLimit <= 0)
            {
                continue;
            }

            int affordableQty = gpOnHand / competitiveBuy;
            int liquidityQty = Math.max(1, (int) Math.floor(twoSidedVolume * volumeMultiplier));
            int quantity = Math.min(remainingLimit, Math.min(affordableQty, liquidityQty));

            if (quantity <= 0)
            {
                continue;
            }

            // If this flip would consume most of recent two-sided flow, shrink and/or penalize.
            double impactShare = quantity / (double) Math.max(1, twoSidedVolume);
            if (impactShare > 1.0)
            {
                quantity = Math.min(remainingLimit, twoSidedVolume);
                impactShare = quantity / (double) Math.max(1, twoSidedVolume);
            }

            long totalProfit = (long) profitPerItem * quantity;
            if (totalProfit < minTotalProfit)
            {
                continue;
            }

            double freshness = Math.max(0.0, 1.0 - quoteAge / (double) quoteAgeLimit);
            double liquidity = Math.min(1.0, Math.log10(twoSidedVolume + 1.0) / 3.0);
            double impactPenalty = Math.min(0.40, impactShare * 0.35);
            double spreadBonus = Math.min(0.15, Math.max(0.0, spreadPct - minSpreadPercent) / 100.0);
            double limitPressure = 1.0 - (remainingLimit / (double) limit);
            double limitPenalty = Math.min(0.20, Math.max(0.0, limitPressure - 0.5) * 0.4);
            // Wide/fantasy books (huge ROI or spread) fill poorly — soft-penalize confidence.
            double fantasyPenalty = 0.0;
            if (spreadPct > 12.0)
            {
                fantasyPenalty += Math.min(0.15, (spreadPct - 12.0) / 80.0);
            }
            if (roi > 15.0)
            {
                fantasyPenalty += Math.min(0.15, (roi - 15.0) / 100.0);
            }

            int confidence = (int) Math.round(100.0 * Math.max(0.0, Math.min(
                1.0,
                0.40 * freshness + 0.40 * liquidity + 0.10 + spreadBonus
                    - impactPenalty - limitPenalty - fantasyPenalty)));

            MarketSnapshot snap = new MarketSnapshot();
            snap.itemId = itemId;
            snap.avgLow = avg.avgLowPrice;
            snap.avgHigh = avg.avgHighPrice;
            snap.latestLow = last.low;
            snap.latestHigh = last.high;
            snapshots.put(itemId, snap);

            out.add(new FlipOpportunity(
                itemId,
                meta.name == null ? "Item " + itemId : meta.name,
                competitiveBuy,
                competitiveSell,
                patientBuy,
                patientSell,
                quantity,
                limit,
                remainingLimit,
                volume5m,
                profitPerItem,
                totalProfit,
                roi,
                confidence,
                quoteAge,
                0));
        }

        if (effectiveSort == FlipSortMode.FILL_CONFIDENCE)
        {
            out.sort(
                Comparator.comparingInt((FlipOpportunity x) -> x.confidence).reversed()
                    .thenComparing(Comparator.comparingLong((FlipOpportunity x) -> x.totalProfit).reversed()));
        }
        else
        {
            out.sort(
                Comparator.comparingLong((FlipOpportunity x) -> x.totalProfit).reversed()
                    .thenComparing(Comparator.comparingInt((FlipOpportunity x) -> x.confidence).reversed()));
        }

        if (out.size() > 15)
        {
            return new ArrayList<>(out.subList(0, 15));
        }

        log.debug("Scan found {} candidate flips", out.size());
        return out;
    }

    private void ensureMapping() throws Exception
    {
        if (!mapping.isEmpty())
        {
            return;
        }

        log.debug("Fetching GE item mapping");
        MappingItem[] items = getJson("/mapping", MappingItem[].class);
        if (items == null)
        {
            return;
        }

        for (MappingItem item : items)
        {
            mapping.put(item.id, item);
        }

        log.debug("Loaded {} mapped items", mapping.size());
    }

    private <T> T getJson(String path, Class<T> type) throws IOException
    {
        Request request = new Request.Builder()
            .url(BASE + path)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null)
            {
                throw new IOException("Market API returned HTTP " + response.code());
            }

            return gson.fromJson(body.string(), type);
        }
    }

    static int geTax(int sellPrice)
    {
        return Math.min(5_000_000, (int) Math.floor(sellPrice * 0.02));
    }

    /**
     * Lowest sell price where {@code sell - geTax(sell) >= buyPrice}.
     */
    static int breakEvenSellPrice(int buyPrice)
    {
        if (buyPrice <= 0)
        {
            return 0;
        }

        // Below the 5M tax cap, net ≈ 98% of sell.
        int guess = (int) Math.ceil(buyPrice / 0.98);
        if (guess < buyPrice)
        {
            guess = buyPrice;
        }

        // Cap region: tax is flat 5M, so need sell >= buy + 5M.
        int cappedFloor = buyPrice + 5_000_000;
        if (guess >= 250_000_000)
        {
            guess = Math.max(guess, cappedFloor);
        }

        int guard = 0;
        while (guess - geTax(guess) < buyPrice && guard++ < 10_000_000)
        {
            guess++;
        }
        return guess;
    }

    static final class MarketSnapshot
    {
        int itemId;
        int avgLow;
        int avgHigh;
        int latestLow;
        int latestHigh;
    }

    static final class MappingItem
    {
        int id;
        String name;
        Integer limit;
        Boolean members;
    }

    static final class LatestResponse
    {
        Map<String, LatestPrice> data;
    }

    static final class LatestPrice
    {
        Integer high;
        Long highTime;
        Integer low;
        Long lowTime;
    }

    static final class AverageResponse
    {
        Map<String, AveragePrice> data;
    }

    static final class AveragePrice
    {
        Integer avgHighPrice;
        Integer highPriceVolume;
        Integer avgLowPrice;
        Integer lowPriceVolume;
        Long timestamp;
    }
}
