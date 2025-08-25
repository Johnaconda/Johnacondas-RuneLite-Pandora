package com.johnaconda.pandora.attackcycle;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("attackcycle")
public interface AttackCycleConfig extends Config
{
    @ConfigSection(
            name = "Overlays",
            description = "Visual settings for overhead timers and the mini HUD.",
            position = 0
    )
    String overlaySection = "overlaySection";

    @ConfigItem(
            keyName = "showOverheadTimer",
            name = "Overhead timers",
            description = "Show tick countdown above NPC heads.",
            section = overlaySection,
            position = 1
    )
    default boolean showOverheadTimer() { return true; }

    @Alpha
    @ConfigItem(
            keyName = "overheadColor",
            name = "Overhead color",
            description = "Text color used for overhead timers.",
            section = overlaySection,
            position = 2
    )
    default Color overheadColor() { return Color.WHITE; }

    @ConfigItem(
            keyName = "overheadFontSize",
            name = "Overhead size",
            description = "Font size for overhead timers (px).",
            section = overlaySection,
            position = 3
    )
    default int overheadFontSize() { return 14; }

    @ConfigItem(
            keyName = "showMiniHud",
            name = "Mini HUD",
            description = "Show a compact on-screen list of NPCs with their timers.",
            section = overlaySection,
            position = 4
    )
    default boolean showMiniHud() { return true; }

    @ConfigItem(
            keyName = "countdownStyle",
            name = "Countdown style (-1)",
            description = "Display (ticks - 1) so that 1 means 'about to hit now'.",
            section = overlaySection,
            position = 5
    )
    default boolean countdownStyle() { return false; }
}
