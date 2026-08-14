package com.osrsflipdesk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes completed flip history as CSV (Excel-friendly, no extra dependencies).
 */
final class HistoryExport
{
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .withZone(ZoneOffset.UTC);

    private HistoryExport()
    {
    }

    static void writeCsv(Path path, List<PositionStore.Position> history) throws IOException
    {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
        {
            // Excel recognizes UTF-8 more reliably with a BOM.
            out.write('\ufeff');
            out.write(String.join(",",
                "id",
                "item_id",
                "item_name",
                "opened_at_utc",
                "closed_at_utc",
                "quantity_bought",
                "quantity_sold",
                "quantity_remaining",
                "average_buy_price",
                "recommended_sell_price",
                "last_sell_price",
                "total_buy_cost",
                "total_sell_revenue",
                "realized_profit",
                "roi_percent"));
            out.newLine();

            if (history == null)
            {
                return;
            }

            for (PositionStore.Position p : history)
            {
                if (p == null)
                {
                    continue;
                }

                double roi = p.totalBuyCost > 0
                    ? (p.realizedProfit * 100.0) / p.totalBuyCost
                    : 0.0;

                out.write(Long.toString(p.id));
                out.write(',');
                out.write(Integer.toString(p.itemId));
                out.write(',');
                out.write(csv(p.itemName));
                out.write(',');
                out.write(csv(formatUtc(p.openedAt)));
                out.write(',');
                out.write(csv(formatUtc(p.closedAt)));
                out.write(',');
                out.write(Integer.toString(p.quantityBought));
                out.write(',');
                out.write(Integer.toString(p.quantitySold));
                out.write(',');
                out.write(Integer.toString(p.quantityRemaining));
                out.write(',');
                out.write(Integer.toString(p.averageBuyPrice));
                out.write(',');
                out.write(Integer.toString(p.recommendedSellPrice));
                out.write(',');
                out.write(Integer.toString(p.lastSellPrice));
                out.write(',');
                out.write(Long.toString(p.totalBuyCost));
                out.write(',');
                out.write(Long.toString(p.totalSellRevenue));
                out.write(',');
                out.write(Long.toString(p.realizedProfit));
                out.write(',');
                out.write(String.format(Locale.US, "%.4f", roi));
                out.newLine();
            }
        }
    }

    private static String formatUtc(long epochMs)
    {
        if (epochMs <= 0L)
        {
            return "";
        }
        return ISO_UTC.format(Instant.ofEpochMilli(epochMs));
    }

    private static String csv(String value)
    {
        String raw = value == null ? "" : value;
        String escaped = raw.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
