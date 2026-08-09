package com.osrsflipdesk;

public enum FlipTicketSize
{
    ANY(
        "Any size",
        0,
        1.0,
        1.0),
    MEDIUM(
        "10k+ profit",
        10_000,
        1.35,
        0.75),
    LARGE(
        "50k+ profit",
        50_000,
        1.75,
        0.55),
    WHALE(
        "200k+ profit",
        200_000,
        2.25,
        0.40);

    private final String label;
    private final int minTotalProfit;
    private final double volumeScale;
    private final double minVolumeScale;

    FlipTicketSize(
        String label,
        int minTotalProfit,
        double volumeScale,
        double minVolumeScale)
    {
        this.label = label;
        this.minTotalProfit = minTotalProfit;
        this.volumeScale = volumeScale;
        this.minVolumeScale = minVolumeScale;
    }

    int minTotalProfit()
    {
        return minTotalProfit;
    }

    double volumeScale()
    {
        return volumeScale;
    }

    double minVolumeScale()
    {
        return minVolumeScale;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
