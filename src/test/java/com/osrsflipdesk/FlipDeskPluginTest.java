package com.osrsflipdesk;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FlipDeskPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(FlipDeskPlugin.class);
        RuneLite.main(args);
    }
}
