package com.johnaconda.pandora.prayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;

import java.util.*;

final class NpcStore
{
    private static final String GROUP = "prayerflickhelper";
    private static final String KEY_NPC_IDS = "npc_ids";            // CSV of ids
    private static final String KEY_NPC_JSON_PREFIX = "npc_";       // npc_<id> -> json

    private final ConfigManager cfg;
    private final Logger log;
    private final Gson gson = new GsonBuilder().create();
    private final Map<Integer, NpcProfile> cache = new HashMap<>();

    NpcStore(ConfigManager cfg, Logger log) {
        this.cfg = cfg; this.log = log;
        for (int id : csvToIds(cfg.getConfiguration(GROUP, KEY_NPC_IDS))) {
            NpcProfile p = load(id);
            if (p != null) cache.put(id, p);
        }
    }

    Collection<NpcProfile> all() { return cache.values(); }

    NpcProfile get(int id) {
        NpcProfile p = cache.get(id);
        if (p != null) return p;
        p = load(id);
        if (p != null) cache.put(id, p);
        return p;
    }

    NpcProfile ensure(int id, String nameHint) {
        NpcProfile p = get(id);
        if (p == null) {
            p = new NpcProfile(id, nameHint);
            save(p);
        } else if (nameHint != null && !nameHint.trim().isEmpty() && (p.name == null || p.name.trim().isEmpty())) {
            p.name = nameHint.trim();
            save(p);
        }
        return p;
    }

    // ---- Rows (never overwrite user labels/flags silently) ----
    boolean hasAnim(int npcId, int animId) {
        NpcProfile p = get(npcId);
        return p != null && p.animations.containsKey(animId);
    }
    boolean hasProj(int npcId, int projId) {
        NpcProfile p = get(npcId);
        return p != null && p.projectiles.containsKey(projId);
    }

    void putAnimIfAbsent(int npcId, int animId) {
        NpcProfile p = ensure(npcId, null);
        if (!p.animations.containsKey(animId)) {
            p.animations.put(animId, new NpcProfile.AnimInfo(null, false, 1));
            save(p);
        }
    }

    void putProjIfAbsent(int npcId, int projId) {
        NpcProfile p = ensure(npcId, null);
        if (!p.projectiles.containsKey(projId)) {
            p.projectiles.put(projId, null);
            save(p);
        }
    }

    void setAnimLabel(int npcId, int animId, AttackStyle style) {
        NpcProfile p = ensure(npcId, null);
        NpcProfile.AnimInfo ai = p.animations.get(animId);
        if (ai == null) ai = new NpcProfile.AnimInfo(null, false, 1);
        ai.style = style; // may be null
        p.animations.put(animId, ai);
        save(p);
    }

    void setAnimRole(int npcId, int animId, boolean start) {
        NpcProfile p = ensure(npcId, null);
        NpcProfile.AnimInfo ai = p.animations.get(animId);
        if (ai == null) ai = new NpcProfile.AnimInfo(null, start, 1);
        ai.start = start;
        p.animations.put(animId, ai);
        save(p);
    }

    void setAnimOffset(int npcId, int animId, int offset) {
        NpcProfile p = ensure(npcId, null);
        NpcProfile.AnimInfo ai = p.animations.get(animId);
        if (ai == null) ai = new NpcProfile.AnimInfo(null, false, 1);
        ai.offset = Math.max(0, Math.min(4, offset));
        p.animations.put(animId, ai);
        save(p);
    }

    void setProjLabel(int npcId, int projId, AttackStyle style) {
        NpcProfile p = ensure(npcId, null);
        p.projectiles.put(projId, style); // may be null
        save(p);
    }

    // ---- Period learning (per animation id; only for Start rows) ----
    void addAnimPeriodSample(int npcId, int animId, int gap) {
        NpcProfile p = ensure(npcId, null);
        NpcProfile.AnimInfo ai = p.animations.get(animId);
        if (ai == null) {
            ai = new NpcProfile.AnimInfo(null, false, 1);
            p.animations.put(animId, ai);
        }
        if (ai.start) { // only learn from starts
            ai.addSample(gap); // freezes internally when consistent
            save(p);
        }
    }

    AttackStyle animStyle(int npcId, int animId) {
        NpcProfile p = get(npcId);
        return p == null ? null : p.styleForAnimation(animId);
    }

    boolean animIsStart(int npcId, int animId) {
        NpcProfile p = get(npcId);
        return p != null && Boolean.TRUE.equals(p.startForAnimation(animId));
    }

    Integer getAnimPeriod(int npcId, int animId) {
        NpcProfile p = get(npcId);
        return p == null ? null : p.periodForAnimation(animId);
    }

    int getAnimOffset(int npcId, int animId) {
        NpcProfile p = get(npcId);
        return p == null ? 1 : p.offsetForAnimation(animId);
    }

    AttackStyle projStyle(int npcId, int projId) {
        NpcProfile p = get(npcId);
        return p == null ? null : p.styleForProjectile(projId);
    }

    boolean remove(int id) {
        boolean existed = cache.remove(id) != null;
        cfg.unsetConfiguration(GROUP, KEY_NPC_JSON_PREFIX + id);
        Set<Integer> ids = csvToIds(cfg.getConfiguration(GROUP, KEY_NPC_IDS));
        if (ids.remove(id)) cfg.setConfiguration(GROUP, KEY_NPC_IDS, idsToCsv(ids));
        return existed;
    }

    void save(NpcProfile p) {
        cache.put(p.id, p);
        cfg.setConfiguration(GROUP, KEY_NPC_JSON_PREFIX + p.id, gson.toJson(p));
        Set<Integer> ids = csvToIds(cfg.getConfiguration(GROUP, KEY_NPC_IDS));
        if (!ids.contains(p.id)) {
            ids.add(p.id);
            cfg.setConfiguration(GROUP, KEY_NPC_IDS, idsToCsv(ids));
        }
    }

    // ------ helpers ------
    private NpcProfile load(int id) {
        try {
            String json = cfg.getConfiguration(GROUP, KEY_NPC_JSON_PREFIX + id);
            return json == null ? null : gson.fromJson(json, NpcProfile.class);
        } catch (Exception ex) {
            if (log != null) log.warn("Failed to load NPC {} profile", id, ex);
            return null;
        }
    }
    private static Set<Integer> csvToIds(String csv) {
        Set<Integer> out = new TreeSet<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        for (String s : csv.split(",")) try { out.add(Integer.parseInt(s.trim())); } catch (Exception ignored) {}
        return out;
    }
    private static String idsToCsv(Set<Integer> ids) {
        StringBuilder sb = new StringBuilder(); boolean first = true;
        for (Integer i : new TreeSet<>(ids)) { if (!first) sb.append(','); sb.append(i); first = false; }
        return sb.toString();
    }
}
