package com.johnaconda.pandora.attackcycle;

import com.google.inject.Provides;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
        name = "Attack Cycle",
        description = "Phase-aware profiles + recording with multi-target countdowns.",
        tags = {"pvm","ticks","boss","phases","profiles","recording"}
)
public class AttackCyclePlugin extends Plugin implements DbService.Listener
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OverlayManager overlayManager;
    @Inject private AttackCycleOverlay overhead;
    @Inject private AttackCycleHudOverlay hud;
    @Inject private AttackCycleConfig config;
    @Inject private ConfigManager cfgMgr;

    private DbService db;
    private AttackCyclePanel panel;
    private NavigationButton nav;

    // npcIndex -> tracker/phase/lastAnim
    private final Map<Integer, AttackTracker> trackers = new ConcurrentHashMap<>();
    private final Map<Integer, String> activePhaseByNpc = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastAnimByNpc = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastAnimTickByNpc = new ConcurrentHashMap<>();

    // batch debug messages once per tick
    private final ArrayList<String> debugTickBuf = new ArrayList<>();
    private int debugSuppressed = 0;

    // Panel toggles
    private volatile boolean recordingEnabled = true;
    private volatile boolean autoTagEnabled = true;
    private volatile int autoTagWindow = 2;
    private volatile boolean autoUpdateProfile = false;
    private volatile int autoUpdateRepeat = 2;
    private volatile boolean debugEnabled = false;

    // ----- DI -----
    @Provides AttackCycleConfig provideConfig(ConfigManager cm){ return cm.getConfig(AttackCycleConfig.class); }

    // ----- Lifecycle -----
    @Override protected void startUp()
    {
        db = new DbService(cfgMgr);
        db.addListener(this);

        panel = new AttackCyclePanel(db, this);

        final BufferedImage icon = resolveIcon();
        nav = NavigationButton.builder()
                .tooltip("Attack Cycle")
                .icon(icon)
                .priority(7)
                .panel(panel)
                .build();

        overlayManager.add(overhead);
        overlayManager.add(hud);
        clientToolbar.addNavigation(nav);
    }

    @Override protected void shutDown()
    {
        overlayManager.remove(overhead);
        overlayManager.remove(hud);
        if (nav != null) clientToolbar.removeNavigation(nav);

        trackers.clear();
        activePhaseByNpc.clear();
        lastAnimByNpc.clear();
        lastAnimTickByNpc.clear();

        if (db != null)
        {
            db.setHardOffline(false);
            try { db.save(); } finally { db.setHardOffline(true); }
        }
    }

    // ----- Panel API -----
    void setRecordingEnabled(boolean on){ recordingEnabled = on; }
    void setAutoTagEnabled(boolean on){ autoTagEnabled = on; }
    void setAutoUpdateProfile(boolean on){ autoUpdateProfile = on; }
    void setDebugEnabled(boolean on){ debugEnabled = on; }
    boolean isRecordingEnabled(){ return recordingEnabled; }
    boolean isAutoTagEnabled(){ return autoTagEnabled; }
    boolean isAutoUpdateProfile(){ return autoUpdateProfile; }
    boolean isDebugEnabled(){ return debugEnabled; }
    void panelNudged(){}

    // ----- DbService.Listener -----
    @Override public void onDbChanged(DbService.Change change) { /* overlays pull on render */ }

    // ----- View model for overlays -----
    public static final class View {
        public final NPC npc; public final int ticksLeft; public final String profileKey;
        View(NPC n, int left, String key){ this.npc=n; this.ticksLeft=left; this.profileKey=key; }
    }

    public List<View> views()
    {
        final Player me = client.getLocalPlayer();
        if (me == null) return Collections.emptyList();

        final int now = client.getTickCount();
        final ArrayList<View> out = new ArrayList<>();

        for (NPC n : client.getNpcs())
        {
            if (n == null) continue;
            if (!isRelevant(n, me)) continue;

            final DbModels.EntityKey ek = DbModels.EntityKey.of(n.getName(), n.getCombatLevel());
            final DbModels.NpcProfile prof = db.getProfileByKey(ek.key);
            final DbModels.PhaseProfile ph = (prof == null) ? null : phaseFor(n, prof);
            final DbModels.StyleBlock block = (prof == null) ? null : selectStyleBlockForNpc(n, ph, prof);

            final Integer selectedAnim = (block != null ? block.defaultAnimId : null);
            final Integer interval = effectiveInterval(block, selectedAnim);
            final AttackTracker t = trackers.get(n.getIndex());

            int left = -1;
            if (interval != null && t != null) left = t.ticksUntilNext(now, interval);

            final Integer lastTick = lastAnimTickByNpc.get(n.getIndex());
            if (lastTick == null) left = -1;
            else if (interval != null && now - lastTick > interval * 2) left = -1;

            if (left == 0) left = 1;
            if (config.countdownStyle() && left > 0) left = Math.max(1, left - 1);

            out.add(new View(n, left, ek.key));
        }

        out.sort(Comparator.comparingInt(a -> a.npc.getIndex()));
        return out;
    }

    // Compute interval from block + per-anim overrides
    private Integer effectiveInterval(DbModels.StyleBlock b, Integer animId)
    {
        if (b == null) return null;
        int base = (b.ticks != null ? b.ticks : 4);
        int off  = (b.offset != null ? b.offset : 0);
        if (animId != null && b.perAnim != null)
        {
            DbModels.StyleAnimSettings s = b.perAnim.get(animId);
            if (s != null) { if (s.ticks != null) base = s.ticks; if (s.offset != null) off = s.offset; }
        }
        int eff = base + off;
        if (eff < 1) eff = 1; if (eff > 15) eff = 15;
        return eff;
    }

    private DbModels.PhaseProfile phaseFor(NPC n, DbModels.NpcProfile prof)
    {
        final String pk = activePhaseByNpc.get(n.getIndex());
        if (pk != null)
        {
            final DbModels.PhaseProfile ph = prof.phases.get(pk);
            if (ph != null) return ph;
            activePhaseByNpc.remove(n.getIndex());
        }
        return prof.base;
    }

    private DbModels.StyleBlock selectStyleBlockForNpc(NPC n, DbModels.PhaseProfile ph, DbModels.NpcProfile prof)
    {
        if (ph == null || prof == null) return null;

        // choose by last anim style
        final Integer lastAnim = lastAnimByNpc.get(n.getIndex());
        if (lastAnim != null)
        {
            final Map<Integer, DbModels.RecRow> recs = db.getRecsFor(prof.key);
            final DbModels.RecRow rr = recs.get(lastAnim);
            if (rr != null)
            {
                if (rr.type == DbModels.AnimUiType.CHARGEUP) return ph.chargeup;
                if (rr.style == DbModels.Style.RANGED) return ph.ranged;
                if (rr.style == DbModels.Style.MAGIC)  return ph.magic;
                if (rr.style == DbModels.Style.MELEE)  return ph.melee;
            }
        }
        // fallback preference
        if (ph.melee.defaultAnimId != null)  return ph.melee;
        if (ph.ranged.defaultAnimId != null) return ph.ranged;
        if (ph.magic.defaultAnimId != null)  return ph.magic;
        if (ph.chargeup.defaultAnimId != null) return ph.chargeup;
        return ph.melee;
    }

    // ----- Events -----
    @Subscribe public void onGameTick(GameTick e)
    {
        final Player me = client.getLocalPlayer();
        if (me == null) return;
        final int now = client.getTickCount();

        for (NPC n : client.getNpcs())
        {
            if (n == null) continue;
            if (!isRelevant(n, me)) continue;
            final AttackTracker t = trackers.get(n.getIndex());
            if (t == null) continue;

            final DbModels.EntityKey ek = DbModels.EntityKey.of(n.getName(), n.getCombatLevel());
            final DbModels.NpcProfile prof = db.getProfileByKey(ek.key);
            final DbModels.PhaseProfile ph = (prof == null) ? null : phaseFor(n, prof);
            final DbModels.StyleBlock b = (prof == null) ? null : selectStyleBlockForNpc(n, ph, prof);
            final Integer interval = effectiveInterval(b, (b != null ? b.defaultAnimId : null));
            if (interval != null) t.autoAdvanceIfDue(now, interval);
        }

        if (debugEnabled && (!debugTickBuf.isEmpty() || debugSuppressed > 0))
        {
            String combined = String.join(" | ", debugTickBuf);
            if (debugSuppressed > 0) combined += " … (+" + debugSuppressed + " more)";
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[AC] " + combined, null);
            debugTickBuf.clear(); debugSuppressed = 0;
        }
    }

    @Subscribe public void onNpcSpawned(NpcSpawned e) { trackers.put(e.getNpc().getIndex(), new AttackTracker()); }
    @Subscribe public void onNpcDespawned(NpcDespawned e)
    {
        final int idx = e.getNpc().getIndex();
        trackers.remove(idx); activePhaseByNpc.remove(idx); lastAnimByNpc.remove(idx); lastAnimTickByNpc.remove(idx);
    }

    @Subscribe public void onInteractingChanged(InteractingChanged e)
    {
        if (!(e.getSource() instanceof NPC)) return;
        final NPC n = (NPC) e.getSource();
        if (n.getInteracting() != client.getLocalPlayer())
        {
            final AttackTracker t = trackers.get(n.getIndex());
            if (t != null) t.reset();
        }
    }

    @Subscribe public void onAnimationChanged(AnimationChanged e)
    {
        if (!(e.getActor() instanceof NPC)) return;
        final NPC npc = (NPC) e.getActor();
        if (!recordingEnabled) return;
        if (!isRelevant(npc, client.getLocalPlayer())) return;

        final int anim = npc.getAnimation();
        if (anim <= 0) return;

        final DbModels.NpcProfile prof = db.getOrCreateProfileByNpc(npc);
        final DbModels.PhaseProfile ph = phaseFor(npc, prof);

        // Phase triggers
        for (Map.Entry<String, DbModels.PhaseProfile> ent : prof.phases.entrySet())
        {
            final DbModels.PhaseProfile p = ent.getValue();
            if (p.triggerAnimIds.contains(anim) || p.triggerNpcIds.contains(npc.getId()))
            { activePhaseByNpc.put(npc.getIndex(), ent.getKey()); if (debugEnabled) debug("phase->" + ent.getKey() + " via anim " + anim); break; }
        }

        final int now = client.getTickCount();
        final DbModels.RecRow row = db.getOrCreateRec(prof.key, anim);

        // per-animation gap: previous time we saw THIS anim
        final int prevSeen = row.lastSeenTick;
        row.seen++;
        row.lastSeenTick = now;
        db.putRec(prof.key, row);

        if (row.type == DbModels.AnimUiType.ATTACK && prevSeen > 0)
        {
            final int gap = now - prevSeen;
            if (gap > 0) { row.pushGap(gap); db.putRec(prof.key, row); if (debugEnabled) debug("ATTACK gap " + gap + "t for anim " + anim); }
        }

        lastAnimByNpc.put(npc.getIndex(), anim);
        lastAnimTickByNpc.put(npc.getIndex(), now);

        // If this anim equals selected style block anim, start a swing cycle for overlay
        DbModels.StyleBlock b = null;
        if (ph.melee.defaultAnimId != null && ph.melee.defaultAnimId == anim) b = ph.melee;
        else if (ph.ranged.defaultAnimId != null && ph.ranged.defaultAnimId == anim) b = ph.ranged;
        else if (ph.magic.defaultAnimId != null && ph.magic.defaultAnimId == anim) b = ph.magic;
        else if (ph.chargeup.defaultAnimId != null && ph.chargeup.defaultAnimId == anim) b = ph.chargeup;

        if (b != null) { trackers.computeIfAbsent(npc.getIndex(), i -> new AttackTracker()).noteAttackTickAndReturnGap(now); }
    }

    @Subscribe public void onProjectileMoved(ProjectileMoved e) { /* reserved */ }

    @Subscribe public void onHitsplatApplied(HitsplatApplied e)
    {
        if (e.getActor() != client.getLocalPlayer()) return;
        if (!autoTagEnabled) return;

        NPC src = null;
        for (NPC n : client.getNpcs()) { if (n != null && n.getInteracting() == client.getLocalPlayer()) { src = n; break; } }
        if (src == null) return;

        final Integer lastAnim = lastAnimByNpc.get(src.getIndex());
        final Integer atkTick  = lastAnimTickByNpc.get(src.getIndex());
        if (lastAnim == null || atkTick == null) return;

        final int now = client.getTickCount();
        final int delta = now - atkTick;
        if (delta < 0 || delta > autoTagWindow) return;

        final DbModels.NpcProfile prof = db.getOrCreateProfileByNpc(src);
        final DbModels.RecRow r = db.getOrCreateRec(prof.key, lastAnim);

        // Tag as ATTACK (hitsplats imply damage)
        r.type = DbModels.AnimUiType.ATTACK;

        // Gap sample (per-anim) from animation -> hitsplat
        r.pushGap(delta);
        db.putRec(prof.key, r);
        if (debugEnabled) debug("Auto-tag anim " + lastAnim + " as ATTACK (" + delta + "t)");

        // Optional: update selected style block’s per-anim ticks
        if (autoUpdateProfile)
        {
            DbModels.StyleBlock bSel = null;
            final Map<Integer, DbModels.RecRow> recs = db.getRecsFor(prof.key);
            final DbModels.RecRow rr = recs.get(lastAnim);
            if (rr != null)
            {
                if (rr.type == DbModels.AnimUiType.CHARGEUP) bSel = prof.base.chargeup;
                else if (rr.style == DbModels.Style.MELEE)  bSel = prof.base.melee;
                else if (rr.style == DbModels.Style.RANGED) bSel = prof.base.ranged;
                else if (rr.style == DbModels.Style.MAGIC)  bSel = prof.base.magic;
            }

            if (bSel != null)
            {
                if (bSel.defaultAnimId == null) bSel.defaultAnimId = lastAnim;
                DbModels.StyleAnimSettings st = bSel.perAnim.computeIfAbsent(lastAnim, k -> new DbModels.StyleAnimSettings());
                final Integer est = r.liveEstTicksMedian();
                if (est != null) st.ticks = est;
                db.putProfile(prof);
            }
        }
    }

    // ----- Helpers -----
    private boolean isRelevant(NPC n, Player me)
    {
        if (n == null || me == null) return false;
        return (n.getInteracting() == me) || (me.getInteracting() == n);
    }

    private void debug(String msg)
    {
        if (!debugEnabled) return;
        if (debugTickBuf.size() < 8) debugTickBuf.add(msg); else debugSuppressed++;
    }

    /** Load toolbar icon safely; fallback badge if missing. */
    private BufferedImage resolveIcon()
    {
        try { return ImageUtil.loadImageResource(getClass(), "attack_cycle.png"); } catch (Exception ignored) {}
        try { return ImageUtil.loadImageResource(getClass(), "/util/attack_style.png"); } catch (Exception ignored) {}
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 180)); g.fillRoundRect(1, 1, 22, 22, 6, 6);
            g.setColor(new Color(255, 255, 255, 220)); g.drawRoundRect(1, 1, 22, 22, 6, 6);
            g.setFont(new Font("Dialog", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics(); String txt = "AC";
            int tx = (24 - fm.stringWidth(txt)) / 2; int ty = (24 + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(txt, tx, ty);
        } finally { g.dispose(); }
        return img;
    }
}
