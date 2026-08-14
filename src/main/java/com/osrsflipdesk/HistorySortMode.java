package com.osrsflipdesk;

public enum HistorySortMode
{
    MOST_RECENT("Most recent"),
    MOST_PROFITABLE("Most profitable");

    private final String label;

    HistorySortMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
