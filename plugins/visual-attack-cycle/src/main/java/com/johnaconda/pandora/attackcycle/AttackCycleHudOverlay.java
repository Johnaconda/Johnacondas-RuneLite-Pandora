package com.johnaconda.pandora.attackcycle;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.inject.Inject;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Compact on-screen list of active NPCs with their countdowns.
 * - Purely read-only: pulls from AttackCyclePlugin.views() each frame.
 * - No DB writes, no EDT blocking, minimal per-frame allocations.
 */
public class AttackCycleHudOverlay extends Overlay
{
    private final AttackCyclePlugin plugin;
    private final AttackCycleConfig config;

    private final StringBuilder sb = new StringBuilder(32);
    private Font cachedFont = null;
    private int cachedFontSize = -1;

    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;
    private static final int ROW_GAP = 1;
    private static final int MAX_ROWS_DEFAULT = 8;
    private static final int HUD_FONT_SIZE = 14;

    @Inject
    public AttackCycleHudOverlay(AttackCyclePlugin plugin, AttackCycleConfig config)
    {
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (config == null || !safeShowHud())
        {
            return null;
        }

        final List<AttackCyclePlugin.View> views = plugin.views();
        if (views == null || views.isEmpty())
        {
            return null;
        }

        ensureFont(g, HUD_FONT_SIZE);
        final Object oldAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        final Object oldFM = g.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        final Font oldFont = g.getFont();
        g.setFont(cachedFont != null ? cachedFont : oldFont);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        final int maxRows = MAX_ROWS_DEFAULT;
        final int rowHeight = g.getFontMetrics().getHeight() + ROW_GAP;

        int width = 0;
        int rows = Math.min(views.size(), maxRows);
        for (int i = 0; i < rows; i++)
        {
            final AttackCyclePlugin.View v = views.get(i);
            if (v == null || v.npc == null) continue;

            final String line = buildLine(v);
            width = Math.max(width, g.getFontMetrics().stringWidth(line));
        }
        if (width == 0)
        {
            restore(g, oldAA, oldFM, oldFont);
            return null;
        }

        final int height = PAD_Y * 2 + rowHeight * rows;

        final int x = 6, y = 6, w = width + PAD_X * 2, h = height;
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(new Color(255, 255, 255, 40));
        g.drawRoundRect(x, y, w, h, 10, 10);

        int cy = y + PAD_Y + g.getFontMetrics().getAscent();
        for (int i = 0; i < rows; i++)
        {
            final AttackCyclePlugin.View v = views.get(i);
            if (v == null || v.npc == null) continue;

            final String line = buildLine(v);

            if (v.ticksLeft >= 0 && v.ticksLeft <= 2) g.setColor(new Color(255, 180, 180));
            else g.setColor(Color.WHITE);

            g.drawString(line, x + PAD_X, cy);
            cy += rowHeight;
        }

        restore(g, oldAA, oldFM, oldFont);
        return new Dimension(w, h);
    }

    private String buildLine(AttackCyclePlugin.View v)
    {
        sb.setLength(0);
        final String name = v.npc.getName();
        sb.append(name == null ? "NPC" : name);
        sb.append(" : ");
        if (v.ticksLeft < 0) sb.append('-');
        else sb.append(v.ticksLeft);
        return sb.toString();
    }

    private boolean safeShowHud()
    {
        try { return config.showMiniHud(); }
        catch (Throwable t) { return true; }
    }

    private void ensureFont(Graphics2D g, int size)
    {
        if (cachedFont != null && cachedFontSize == size) return;
        try { cachedFont = new Font("Dialog", Font.PLAIN, size); }
        catch (Exception ignored) { cachedFont = g.getFont(); }
        cachedFontSize = size;
    }

    private void restore(Graphics2D g, Object oldAA, Object oldFM, Font oldFont)
    {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldAA);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, oldFM);
        g.setFont(oldFont);
    }
}
