package com.johnaconda.pandora.attackcycle;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;

import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class AttackCycleOverlay extends Overlay
{
    private final AttackCyclePlugin plugin;
    private final AttackCycleConfig config;

    private final StringBuilder sb = new StringBuilder(4);
    private Font cachedFont = null;
    private int cachedFontSize = -1;

    @Inject
    public AttackCycleOverlay(AttackCyclePlugin plugin, AttackCycleConfig config)
    {
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public java.awt.Dimension render(Graphics2D g)
    {
        if (!config.showOverheadTimer()) return null;

        final int size = Math.max(10, Math.min(28, safeFontSize()));
        ensureFont(g, size);

        final Object oldAA = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        final Object oldFM = g.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
        final Font oldFont = g.getFont();
        g.setFont(cachedFont != null ? cachedFont : oldFont);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        for (AttackCyclePlugin.View v : plugin.views())
        {
            if (v.ticksLeft < 0) continue;
            final NPC npc = v.npc;
            if (npc == null) continue;

            sb.setLength(0);
            sb.append(v.ticksLeft);
            final String text = sb.toString();

            final int zOffset = Math.max(0, npc.getLogicalHeight()) + 35;
            final net.runelite.api.Point loc = npc.getCanvasTextLocation(g, text, zOffset);
            if (loc == null) continue;

            final Color color = safeOverheadColor();
            g.setColor(new Color(0, 0, 0, 180));
            g.drawString(text, loc.getX() + 1, loc.getY() + 1);

            g.setColor(color);
            g.drawString(text, loc.getX(), loc.getY());
        }

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldAA);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, oldFM);
        g.setFont(oldFont);
        return null;
    }

    private int safeFontSize(){ try { return config.overheadFontSize(); } catch (Throwable t){ return 14; } }

    private void ensureFont(Graphics2D g, int size)
    {
        if (cachedFont != null && cachedFontSize == size) return;
        try { cachedFont = new Font("Dialog", Font.BOLD, size); }
        catch (Exception ignored) { cachedFont = g.getFont(); }
        cachedFontSize = size;
    }

    private Color safeOverheadColor()
    {
        try { return config.overheadColor(); }
        catch (Throwable t) { return Color.WHITE; }
    }
}
