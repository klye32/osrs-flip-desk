package com.osrsflipdesk;

public enum FlipSortMode
{
    TOTAL_PROFIT("Total profit"),
    FILL_CONFIDENCE("Fill confidence");

    private final String label;

    FlipSortMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
