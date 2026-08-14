package com.osrsflipdesk;

public enum FlipStrictness
{
    MORE_FLIPS(
        "More flips",
        30,
        0.8,
        0.5,
        120,
        10,
        0.45,
        0.30),
    BALANCED(
        "Balanced",
        60,
        1.0,
        0.8,
        90,
        8,
        0.65,
        0.45),
    SAFER(
        "Safer fills",
        120,
        1.5,
        1.2,
        60,
        5,
        0.90,
        0.65);

    private final String label;
    private final int minVolume;
    private final double minRoiPercent;
    private final double minSpreadPercent;
    private final int maxQuoteAgeSeconds;
    private final int sizeMinutes;
    private final double buyPremiumPercent;
    private final double sellMarkdownPercent;

    FlipStrictness(
        String label,
        int minVolume,
        double minRoiPercent,
        double minSpreadPercent,
        int maxQuoteAgeSeconds,
        int sizeMinutes,
        double buyPremiumPercent,
        double sellMarkdownPercent)
    {
        this.label = label;
        this.minVolume = minVolume;
        this.minRoiPercent = minRoiPercent;
        this.minSpreadPercent = minSpreadPercent;
        this.maxQuoteAgeSeconds = maxQuoteAgeSeconds;
        this.sizeMinutes = sizeMinutes;
        this.buyPremiumPercent = buyPremiumPercent;
        this.sellMarkdownPercent = sellMarkdownPercent;
    }

    int minVolume()
    {
        return minVolume;
    }

    double minRoiPercent()
    {
        return minRoiPercent;
    }

    double minSpreadPercent()
    {
        return minSpreadPercent;
    }

    int maxQuoteAgeSeconds()
    {
        return maxQuoteAgeSeconds;
    }

    int sizeMinutes()
    {
        return sizeMinutes;
    }

    double buyPremiumPercent()
    {
        return buyPremiumPercent;
    }

    double sellMarkdownPercent()
    {
        return sellMarkdownPercent;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
