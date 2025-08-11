package com.johnaconda.pandora.prayer;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "Prayer Flick Helper+",
        description = "Highlights the correct overhead prayer based on incoming attacks (no automation).",
        tags = {"prayer","flick","tick","overlay"}
)
public class PrayerFlickHelperPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private PrayerFlickHelperConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private PrayerFlickHelperOverlay overlay;

    private final Deque<IncomingHit> queue = new ArrayDeque<>();
    private int currentTick;

    @Provides
    PrayerFlickHelperConfig provide(ConfigManager cm) {
        return cm.getConfig(PrayerFlickHelperConfig.class);
    }

    @Override
    protected void startUp() {
        queue.clear();
        currentTick = 0;
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        queue.clear();
    }

    @net.runelite.client.eventbus.Subscribe
    public void onGameTick(GameTick e) {
        currentTick++;
        prune();
        if (config.metronome()) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    @net.runelite.client.eventbus.Subscribe
    public void onProjectileMoved(ProjectileMoved e) {
        final Projectile p = e.getProjectile();

        // OPTIONAL: only consider projectiles aimed at us (when info is present)
        Actor target = p.getInteracting();
        if (target != null && target != client.getLocalPlayer()) {
            return;
        }

        AttackStyle style = AttackMaps.styleFromProjectile(p.getId());
        if (style == null) return;

        // Convert cycles→ticks using the difference to current cycle
        int cyclesUntilImpact = p.getEndCycle() - client.getGameCycle();
        if (cyclesUntilImpact < 0) return; // already expired

        int ticksUntilImpact = Math.max(0, cyclesUntilImpact / Constants.GAME_TICK_LENGTH);
        int landTick = currentTick + ticksUntilImpact;

        queue.add(new IncomingHit(style, landTick));
    }

    @net.runelite.client.eventbus.Subscribe
    public void onAnimationChanged(AnimationChanged e) {
        if (!(e.getActor() instanceof NPC)) return;
        final NPC npc = (NPC) e.getActor();

        // OPTIONAL: only track if that NPC is interacting with us
        Actor target = npc.getInteracting();
        if (target != null && target != client.getLocalPlayer()) {
            return;
        }

        final int anim = npc.getAnimation();
        final AttackStyle style = AttackMaps.styleFromAnimation(anim);
        if (style == null) return;

        final int tth = AttackMaps.ticksToHitForAnimation(anim);
        queue.add(new IncomingHit(style, currentTick + tth));
    }

    private void prune() {
        while (!queue.isEmpty() && queue.peek().getLandTick() < currentTick - 1) {
            queue.poll();
        }
    }

    public AttackStyle imminentStyle(int warnTicks) {
        int threshold = currentTick + warnTicks;
        for (Iterator<IncomingHit> it = queue.iterator(); it.hasNext();) {
            IncomingHit h = it.next();
            if (h.getLandTick() >= currentTick && h.getLandTick() <= threshold) {
                return h.getStyle();
            }
        }
        return null;
    }

    public int ticksUntilImpact() {
        int best = Integer.MAX_VALUE;
        for (IncomingHit h : queue) {
            int d = h.getLandTick() - currentTick;
            if (d >= 0 && d < best) best = d;
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }
}
