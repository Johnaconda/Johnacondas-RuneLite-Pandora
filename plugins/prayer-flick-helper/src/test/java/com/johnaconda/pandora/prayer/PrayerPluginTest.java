package com.johnaconda.pandora.prayer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PrayerPluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(PrayerFlickHelperPlugin.class);
        RuneLite.main(args);
    }
}
