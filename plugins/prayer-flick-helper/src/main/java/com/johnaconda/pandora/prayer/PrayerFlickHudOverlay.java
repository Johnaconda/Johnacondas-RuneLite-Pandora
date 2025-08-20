package com.johnaconda.pandora.prayer;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Minimal HUD: Attacker + Prayer + Ticks (from the plugin's queued events).
 * The plugin already enqueues hits using either AttackMaps hit delays or the learned (period-1),
 * so we don't need a separate prediction line here.
 */
public class PrayerFlickHudOverlay extends OverlayPanel
{
    private final PrayerFlickHelperPlugin plugin;
    private final PrayerFlickHelperConfig config;

    @Inject
    public PrayerFlickHudOverlay(PrayerFlickHelperPlugin plugin, PrayerFlickHelperConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT); // draggable
        setPriority(OverlayPriority.MED);
    }

    @Override
    public Dimension render(java.awt.Graphics2D g)
    {
        if (!config.showHud())
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(
                TitleComponent.builder().text("Flick Helper").color(Color.WHITE).build()
        );

        // Earliest real incoming (plugin enqueues using learned period or maps)
        int ticks = plugin.ticksUntilImpact();
        IncomingHit next = plugin.nextIncoming();

        String attacker = (next == null || next.getSourceName() == null || next.getSourceName().trim().isEmpty())
                ? "—" : next.getSourceName().trim().replace(' ', '\u00A0'); // prevent wrapping
        if (attacker.length() > 24) attacker = attacker.substring(0, 24) + "…";

        panelComponent.getChildren().add(
                LineComponent.builder().left("Attacker").right(attacker).build()
        );

        if (next != null)
        {
            String prayer = protectNameFor(next.getStyle());
            String tickText = (ticks >= 0) ? (ticks == 0 ? "now" : (ticks + "t")) : "";
            panelComponent.getChildren().add(
                    LineComponent.builder().left("Prayer").right(prayer + (tickText.isEmpty() ? "" : "   " + tickText)).build()
            );
        }
        else
        {
            // Nothing queued (e.g., still learning periods/labels)
            panelComponent.getChildren().add(
                    LineComponent.builder().left("Prayer").right("—").build()
            );
        }

        return super.render(g);
    }

    private static String protectNameFor(AttackStyle s)
    {
        switch (s)
        {
            case MAGIC:  return "Protect from Magic";
            case RANGED: return "Protect from Missiles";
            default:     return "Protect from Melee";
        }
    }
}
