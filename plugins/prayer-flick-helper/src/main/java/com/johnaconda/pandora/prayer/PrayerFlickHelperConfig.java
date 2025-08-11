package com.johnaconda.pandora.prayer;

import net.runelite.client.config.*;

@ConfigGroup("prayerflickhelper")
public interface PrayerFlickHelperConfig extends Config {
    @Range(min = 0, max = 5)
    @ConfigItem(
            keyName = "warningTicks",
            name = "Warn at (ticks before)",
            description = "How many ticks before impact to start highlighting."
    )
    default int warningTicks() { return 1; }

    @ConfigItem(
            keyName = "showCountdown",
            name = "Show countdown",
            description = "Show '2t/1t/now' near quick prayer."
    )
    default boolean showCountdown() { return true; }

    @ConfigItem(
            keyName = "metronome",
            name = "Tick beep",
            description = "Play a short beep on each GameTick."
    )
    default boolean metronome() { return false; }
}
