package com.osrsflipdesk;

final class FlipOpportunity
{
    final int itemId;
    final String name;
    final int buyPrice;
    final int sellPrice;
    final int patientBuy;
    final int patientSell;
    final int quantity;
    final int buyLimit;
    final int remainingBuyLimit;
    final int volume5m;
    final int profitPerItem;
    final long totalProfit;
    final double roi;
    final int confidence;
    final int quoteAgeSeconds;
    final int learnedFills;

    FlipOpportunity(
        int itemId,
        String name,
        int buyPrice,
        int sellPrice,
        int patientBuy,
        int patientSell,
        int quantity,
        int buyLimit,
        int remainingBuyLimit,
        int volume5m,
        int profitPerItem,
        long totalProfit,
        double roi,
        int confidence,
        int quoteAgeSeconds,
        int learnedFills)
    {
        this.itemId = itemId;
        this.name = name;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.patientBuy = patientBuy;
        this.patientSell = patientSell;
        this.quantity = quantity;
        this.buyLimit = buyLimit;
        this.remainingBuyLimit = remainingBuyLimit;
        this.volume5m = volume5m;
        this.profitPerItem = profitPerItem;
        this.totalProfit = totalProfit;
        this.roi = roi;
        this.confidence = confidence;
        this.quoteAgeSeconds = quoteAgeSeconds;
        this.learnedFills = learnedFills;
    }
}
