package com.johnaconda.pandora.prayer;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Highlights the correct Protect-from-X prayer button when an attack is landing within the warn window.
 * If the Prayer tab isn't open, falls back to a small on-screen hint box.
 */
public class PrayerFlickHelperOverlay extends Overlay
{
    private static final int PRAYER_GROUP = 541; // "Prayer" widget group

    // Child indices (current RL layouts). If a layout ever changes, we just use the fallback box.
    private static final int CHILD_PROTECT_MELEE   = 8;
    private static final int CHILD_PROTECT_MAGIC   = 10;
    private static final int CHILD_PROTECT_MISSILE = 12;

    private final Client client;
    private final PrayerFlickHelperPlugin plugin;
    private final PrayerFlickHelperConfig config;

    @Inject
    public PrayerFlickHelperOverlay(Client client,
                                    PrayerFlickHelperPlugin plugin,
                                    PrayerFlickHelperConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        AttackStyle style = plugin.imminentStyle(config.warningTicks());
        if (style == null)
        {
            return null;
        }

        Widget w = findProtectPrayerWidget(style);
        if (w != null && !w.isHidden())
        {
            Rectangle r = w.getBounds();
            if (r != null)
            {
                drawGlow(g, r, colorFor(style));
                // Optional: draw ticks text near the button
                int t = plugin.ticksUntilImpact();
                if (t >= 0)
                {
                    String txt = (t == 0) ? "now" : (t + "t");
                    drawBadge(g, r, txt);
                }
                return null;
            }
        }

        // Fallback: small hint box at top-left if prayer widget is unavailable (tab closed, etc.)
        int t = plugin.ticksUntilImpact();
        String txt = "Protect: " + style.name() + (t >= 0 ? ("  " + (t == 0 ? "now" : (t + "t"))) : "");
        drawCornerHint(g, txt, colorFor(style));

        return null;
    }

    private Widget findProtectPrayerWidget(AttackStyle style)
    {
        int child;
        switch (style)
        {
            case MAGIC:  child = CHILD_PROTECT_MAGIC;   break;
            case RANGED: child = CHILD_PROTECT_MISSILE; break;
            default:     child = CHILD_PROTECT_MELEE;   break;
        }
        int widgetId = (PRAYER_GROUP << 16) | child;
        return client.getWidget(widgetId);
    }

    private static Color colorFor(AttackStyle s)
    {
        switch (s)
        {
            case MAGIC:  return new Color(90, 160, 255);  // blue-ish
            case RANGED: return new Color(120, 200, 120); // green-ish
            default:     return new Color(255, 110, 110); // red-ish
        }
    }

    private static void drawGlow(Graphics2D g, Rectangle r, Color c)
    {
        Color old = g.getColor();
        java.awt.Stroke oldS = g.getStroke();

        // Outer glow
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
        g.fillRect(r.x - 4, r.y - 4, r.width + 8, r.height + 8);

        // Border
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 200));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);

        g.setStroke(oldS);
        g.setColor(old);
    }

    private static void drawBadge(Graphics2D g, Rectangle anchor, String text)
    {
        var fm = g.getFontMetrics();
        int pad = 4;
        int w = fm.stringWidth(text) + pad * 2;
        int h = fm.getAscent() + fm.getDescent() + pad;

        int x = anchor.x + anchor.width - w;
        int y = anchor.y - h - 2;
        if (y < 0) y = anchor.y + anchor.height + 2; // move below if above screen

        Color bg = new Color(0, 0, 0, 170);
        Color fg = Color.WHITE;

        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 8, 8);

        g.setColor(fg);
        g.drawString(text, x + pad, y + fm.getAscent());
    }

    private static void drawCornerHint(Graphics2D g, String txt, Color accent)
    {
        var fm = g.getFontMetrics();
        int pad = 6;
        int w = fm.stringWidth(txt) + pad * 2;
        int h = fm.getAscent() + fm.getDescent() + pad;

        int x = 10;
        int y = 10;

        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(x, y, w, h, 10, 10);

        g.setColor(accent);
        g.drawRoundRect(x, y, w, h, 10, 10);

        g.setColor(Color.WHITE);
        g.drawString(txt, x + pad, y + fm.getAscent());
    }
}
