package com.johnaconda.pandora.prayer;

import javax.inject.Inject;
import java.awt.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.*;

public class PrayerFlickHelperOverlay extends Overlay
{
    private final PrayerFlickHelperPlugin plugin;
    private final PrayerFlickHelperConfig config;
    private final net.runelite.api.Client client;

    @Inject
    public PrayerFlickHelperOverlay(PrayerFlickHelperPlugin plugin, PrayerFlickHelperConfig config, net.runelite.api.Client client)
    {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        AttackStyle s = plugin.imminentStyle(config.warningTicks());
        if (s == null) return null;

        Widget w = client.getWidget(s.widgetInfo()); // overhead prayer widget
        if (w == null || w.isHidden()) return null;

        Rectangle r = w.getBounds();
        if (r == null) return null;

        // Pulse border
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 0, 160));
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setStroke(old);

        // Optional tiny countdown
        if (config.showCountdown()) {
            int t = plugin.ticksUntilImpact();
            if (t >= 0) {
                String txt = t == 0 ? "now" : t + "t";
                g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRect(r.x, r.y - 14, 22, 14);
                g.setColor(Color.WHITE);
                g.drawString(txt, r.x + 3, r.y - 3);
            }
        }
        return null;
    }
}
