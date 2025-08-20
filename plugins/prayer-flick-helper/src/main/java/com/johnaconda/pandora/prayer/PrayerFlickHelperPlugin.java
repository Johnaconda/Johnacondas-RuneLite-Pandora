package com.johnaconda.pandora.prayer;

import com.google.inject.Provides;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = "Prayer Flick Helper+",
        description = "Mark attack-start animations. We learn their period and use (period - Offset) or known hit delay to show the flick timing.",
        tags = {"prayer","flick","tick","overlay"}
)
public class PrayerFlickHelperPlugin extends Plugin implements PrayerFlickPanel.Callbacks
{
    private static final Logger log = LoggerFactory.getLogger(PrayerFlickHelperPlugin.class);
    private static final int CYCLES_PER_GAME_TICK =
            Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;

    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigManager configManager;

    @Inject private PrayerFlickHelperConfig config;
    @Inject private PrayerFlickHelperOverlay overlay;
    @Inject private PrayerFlickHudOverlay hud;

    private NpcStore store;

    private PrayerFlickPanel panel;
    private NavigationButton navButton;
    private boolean recordingEnabled = true;

    private final Deque<IncomingHit> queue = new ArrayDeque<>();
    private int currentTick;

    // last attacker (for HUD and attribution)
    private Integer lastNpcId = null;
    private String  lastNpcName = null;
    private int     lastNpcSeenTick = -9999;

    // For period learning per animation, per NPC instance
    private final Map<Integer, Map<Integer, Integer>> lastStartTickByIndexAnim = new HashMap<>();
    private Map<Integer, Integer> mapFor(int npcIndex) {
        return lastStartTickByIndexAnim.computeIfAbsent(npcIndex, k -> new HashMap<>());
    }

    @Provides
    PrayerFlickHelperConfig provide(ConfigManager cm){ return cm.getConfig(PrayerFlickHelperConfig.class); }

    @Override
    protected void startUp() {
        store = new NpcStore(configManager, log);

        // Optional seeds (global anim/proj mappings)
        try (java.io.InputStream in = PrayerFlickHelperPlugin.class.getResourceAsStream("/npc-attacks.json")) {
            if (in != null) { AttackMaps.loadFromJson(in, log); log.info("Loaded npc-attacks.json"); }
        } catch (Exception ex) { log.warn("Error loading npc-attacks.json", ex); }

        queue.clear(); currentTick = 0;
        lastStartTickByIndexAnim.clear();

        overlayManager.add(overlay);
        overlayManager.add(hud);

        buildPanel();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        overlayManager.remove(hud);
        if (navButton != null) clientToolbar.removeNavigation(navButton);
        queue.clear();
        lastStartTickByIndexAnim.clear();
    }

    private void buildPanel() {
        panel = new PrayerFlickPanel();
        panel.setCallbacks(this);
        panel.setRecording(recordingEnabled);

        for (NpcProfile p : store.all()) panel.ensureNpcInList(p.id);

        BufferedImage tiny = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        navButton = NavigationButton.builder()
                .tooltip("Prayer Flick Helper")
                .icon(tiny)
                .priority(6)
                .panel(panel)
                .build();
    }

    // ===== Events =====

    @Subscribe
    public void onGameTick(GameTick e){
        currentTick++;
        pruneExpired();

        if (config.metronome() && imminentStyle(config.warningTicks()) != null) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged e){
        if (!(e.getActor() instanceof NPC)) return;
        NPC npc = (NPC) e.getActor();

        // STRICT: only track NPCs targeting us
        Actor target = npc.getInteracting();
        if (target == null || target != client.getLocalPlayer()) return;

        lastNpcId = npc.getId();
        lastNpcName = npc.getName();
        lastNpcSeenTick = currentTick;
        store.ensure(lastNpcId, lastNpcName);

        SwingUtilities.invokeLater(() -> {
            panel.ensureNpcInList(lastNpcId);
            panel.selectNpc(lastNpcId);
            panel.setNpcName(lastNpcName);
        });

        int anim = npc.getAnimation();

        // Ensure row exists for editing
        if (!store.hasAnim(lastNpcId, anim) && recordingEnabled) {
            store.putAnimIfAbsent(lastNpcId, anim);
            SwingUtilities.invokeLater(() -> panel.ensureAnimRow(anim));
        }

        // Read user intent for this animation
        AttackStyle style = store.animStyle(lastNpcId, anim);
        boolean isStart = store.animIsStart(lastNpcId, anim);

        // Record a period sample ONLY for Start rows
        Map<Integer,Integer> m = mapFor(npc.getIndex());
        Integer prev = m.get(anim);
        if (isStart && prev != null) {
            int gap = currentTick - prev;
            if (gap >= 2 && gap <= 10) {
                store.addAnimPeriodSample(lastNpcId, anim, gap); // freezes when consistent
            }
        }
        m.put(anim, currentTick);

        // If not attack-start or not labeled with a style -> nothing to enqueue yet
        if (!isStart || style == null) return;

        // Decide hit delay:
        int hitDelay = AttackMaps.ticksToHitForAnimation(anim, -1); // -1 = unknown
        if (hitDelay <= 0) {
            Integer period = store.getAnimPeriod(lastNpcId, anim);
            if (period != null && period >= 2) {
                int offset = store.getAnimOffset(lastNpcId, anim); // default 1
                hitDelay = Math.max(1, period - Math.max(0, Math.min(4, offset)));
            } else {
                // Not enough info to help yet
                return;
            }
        }

        queue.add(new IncomingHit(style, currentTick + hitDelay, lastNpcName));
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved e){
        Projectile p = e.getProjectile();
        Actor target = p.getInteracting();
        if (target == null || target != client.getLocalPlayer()) return;

        AttackStyle style = null;

        if (lastNpcId != null && currentTick - lastNpcSeenTick <= 4) {
            boolean has = store.hasProj(lastNpcId, p.getId());
            AttackStyle labeled = store.projStyle(lastNpcId, p.getId());

            if (has) {
                if (labeled == null) {
                    SwingUtilities.invokeLater(() -> panel.ensureProjRow(p.getId()));
                    return;
                } else {
                    style = labeled;
                }
            }
        }

        if (style == null) {
            style = AttackMaps.styleFromProjectile(p.getId()); // seeds if any
        }

        if (style == null) {
            if (recordingEnabled && lastNpcId != null) {
                store.putProjIfAbsent(lastNpcId, p.getId());
                SwingUtilities.invokeLater(() -> panel.ensureProjRow(p.getId()));
            }
            return;
        }

        int cyclesUntilImpact = p.getEndCycle() - client.getGameCycle();
        if (cyclesUntilImpact < 0) return;
        int ticks = Math.max(0, cyclesUntilImpact / CYCLES_PER_GAME_TICK);

        String attackerName = (currentTick - lastNpcSeenTick <= 2) ? lastNpcName : null;
        queue.add(new IncomingHit(style, currentTick + ticks, attackerName));
    }

    // ===== Queue helpers for overlays =====
    private void pruneExpired(){ while(!queue.isEmpty() && queue.peek().getLandTick() < currentTick - 1) queue.poll(); }

    /** Real style landing within warn window, else null. */
    public AttackStyle imminentStyle(int warnTicks){
        int th = currentTick + warnTicks;
        for (IncomingHit h : queue) if (h.getLandTick() >= currentTick && h.getLandTick() <= th) return h.getStyle();
        return null;
    }

    /** Earliest real incoming. */
    public IncomingHit nextIncoming(){
        int best = Integer.MAX_VALUE; IncomingHit ans = null;
        for (IncomingHit h : queue){ int d = h.getLandTick() - currentTick; if (d>=0 && d<best){best=d; ans=h;} }
        return ans;
    }

    /** Ticks until earliest real incoming, or -1. */
    public int ticksUntilImpact(){
        int best = Integer.MAX_VALUE;
        for (IncomingHit h : queue){ int d=h.getLandTick()-currentTick; if(d>=0 && d<best) best=d; }
        return best==Integer.MAX_VALUE? -1:best;
    }

    // ===== Panel callbacks =====
    @Override public void onToggleRecording(boolean enabled) { recordingEnabled = enabled; }

    @Override
    public void onSaveProfile(int npcId, String name,
                              java.util.Map<Integer, LabelOption> proj,
                              java.util.Map<Integer, PrayerFlickPanel.AnimRow> anim) {
        NpcProfile p = store.ensure(npcId, name);
        p.name = name == null ? "" : name.trim();

        for (java.util.Map.Entry<Integer, LabelOption> e : proj.entrySet()) {
            store.setProjLabel(npcId, e.getKey(), e.getValue().toStyleOrNull());
        }
        for (java.util.Map.Entry<Integer, PrayerFlickPanel.AnimRow> e : anim.entrySet()) {
            store.setAnimLabel(npcId, e.getKey(), e.getValue().label.toStyleOrNull());
            store.setAnimRole(npcId, e.getKey(), e.getValue().role.toBool());
            store.setAnimOffset(npcId, e.getKey(), e.getValue().offset == null ? 1 : e.getValue().offset);
        }

        SwingUtilities.invokeLater(panel::loadFromStore);
    }

    @Override public void onResetProfile(int npcId) {
        store.remove(npcId);
        rebuildPanel();
    }

    @Override public void onAddNpcRequested() {
        String idStr = javax.swing.JOptionPane.showInputDialog(null, "Enter NPC id:");
        if (idStr == null) return;
        try {
            int id = Integer.parseInt(idStr.trim());
            String name = javax.swing.JOptionPane.showInputDialog(null, "Enter NPC name (optional):");
            store.ensure(id, name);
            rebuildPanel();
            panel.selectNpc(id);
            panel.setNpcName(name);
            panel.loadFromStore();
        } catch (NumberFormatException ignored) { }
    }

    @Override public void onNpcSelected(Integer npcId) {
        if (npcId == null) return;
        NpcProfile p = store.get(npcId);
        panel.setNpcName(p == null ? "" : p.name);
    }

    @Override public NpcProfile onRequestProfile(Integer npcId) {
        return npcId == null ? null : store.get(npcId);
    }

    @Override
    public void onAnimRowEdited(int npcId, int animId, LabelOption label, RoleOption role, int offset) {
        store.setAnimLabel(npcId, animId, label.toStyleOrNull());
        store.setAnimRole(npcId, animId, role.toBool());
        store.setAnimOffset(npcId, animId, offset);
    }

    @Override
    public void onAnimRoleEdited(int npcId, int animId, RoleOption role) {
        store.setAnimRole(npcId, animId, role.toBool());
    }

    @Override
    public void onAnimOffsetEdited(int npcId, int animId, int offset) {
        store.setAnimOffset(npcId, animId, offset);
    }

    @Override
    public void onProjRowEdited(int npcId, int projId, LabelOption label) {
        store.setProjLabel(npcId, projId, label.toStyleOrNull());
    }

    private void rebuildPanel() {
        clientToolbar.removeNavigation(navButton);
        buildPanel();
        clientToolbar.addNavigation(navButton);
    }
}
