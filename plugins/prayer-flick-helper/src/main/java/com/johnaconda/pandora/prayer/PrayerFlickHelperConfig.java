package com.johnaconda.pandora.prayer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("prayerflickhelper")
public interface PrayerFlickHelperConfig extends Config
{
    @Range(min = 0, max = 5)
    @ConfigItem(
            keyName = "warningTicks",
            name = "Warn at (ticks)",
            description = "How many ticks before impact to start highlighting."
    )
    default int warningTicks() { return 1; }

    // Visuals / UX
    @ConfigItem(
            keyName = "showHud",
            name = "Show HUD",
            description = "Small panel that shows next attack + countdown."
    )
    default boolean showHud() { return true; }

    @ConfigItem(
            keyName = "showCountdown",
            name = "Show countdown on overlay",
            description = "Draw a small tick countdown near the prayer highlight/HUD."
    )
    default boolean showCountdown() { return true; }

    @ConfigItem(
            keyName = "countdownAlways",
            name = "Countdown even outside warn window",
            description = "Show tick countdown even when not in warn window yet."
    )
    default boolean countdownAlways() { return true; }

    // Audio
    @ConfigItem(
            keyName = "metronome",
            name = "Beep in warn window",
            description = "Play a short beep only on ticks when an attack is inside your warn window."
    )
    default boolean metronome() { return false; }

    // Debug / Learning
    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug logging",
            description = "Log projectile/animation IDs and computed ticks."
    )
    default boolean debugLogging() { return false; }

    @ConfigItem(
            keyName = "learningMode",
            name = "Learning mode (1/2/3 to label)",
            description = "Press 1=MELEE, 2=RANGED, 3=MAGIC to label an unknown ID. Saved to config."
    )
    default boolean learningMode() { return true; }

    @ConfigItem(
            keyName = "fallbackProjectileAsRanged",
            name = "Fallback: unknown projectile = Ranged",
            description = "If a projectile's style is unknown, warn as Ranged by default."
    )
    default boolean fallbackProjectileAsRanged() { return true; }

    @Range(min = 1, max = 6)
    @ConfigItem(
            keyName = "defaultAnimTicks",
            name = "Default animation ticks-to-hit",
            description = "Ticks from swing animation to hit if unknown (used in learning mode)."
    )
    default int defaultAnimTicks() { return 2; }
}
