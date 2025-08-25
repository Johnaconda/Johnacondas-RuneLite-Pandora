package com.johnaconda.pandora.attackcycle;

import com.google.gson.*;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Persistence for Attack Cycle. */
final class DbService
{
    private static final String GROUP = "attackcycle";
    private static final String KEY_DB = "db_v2";

    // ---- Change notifications ----
    enum Change { PROFILE, RECORDING, IMPORT, DELETE, RESET, SAVE }
    interface Listener { void onDbChanged(Change change); }
    private final Set<Listener> listeners = new HashSet<>();
    void addListener(Listener l){ if (l != null) listeners.add(l); }
    void removeListener(Listener l){ listeners.remove(l); }
    private void notify(Change c){ for (Listener l : listeners) try { l.onDbChanged(c); } catch (Throwable ignored) {} }
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AC-db"); t.setDaemon(true); return t;
    });

    // Manual saving only (no autosave). We just mark dirty and save on demand (Save button / shutdown).
    private volatile boolean dirtyProfiles = false;
    private final Object saveLock = new Object();

    private void markProfilesDirty() { dirtyProfiles = true; }
    private void flushProfilesIfDirty() {
        if (!dirtyProfiles) return;
        if (hardOffline) return;
        synchronized (saveLock) {
            dirtyProfiles = false;
            try { config.setConfiguration(GROUP, KEY_DB, gson.toJson(db)); } catch (Exception ignored) {}
            try { backupToFile(); } catch (Exception ignored) {}
        }
        notify(Change.SAVE);
    }

    private final ConfigManager config;
    private volatile boolean hardOffline = true;
    void setHardOffline(boolean on) { hardOffline = on; }

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(java.util.LinkedHashSet.class, new JsonSerializer<java.util.LinkedHashSet<Integer>>() {
                @Override public JsonElement serialize(java.util.LinkedHashSet<Integer> src, Type t, JsonSerializationContext c) {
                    return c.serialize(new java.util.ArrayList<>(src));
                }
            })
            .registerTypeAdapter(java.util.ArrayDeque.class, new JsonSerializer<java.util.ArrayDeque<Integer>>() {
                @Override public JsonElement serialize(java.util.ArrayDeque<Integer> src, Type t, JsonSerializationContext c) {
                    return c.serialize(new java.util.ArrayList<>(src));
                }
            })
            .registerTypeAdapter(java.util.ArrayDeque.class, new JsonDeserializer<java.util.ArrayDeque<Integer>>() {
                @Override public java.util.ArrayDeque<Integer> deserialize(JsonElement json, Type t, JsonDeserializationContext c) {
                    java.util.ArrayDeque<Integer> dq = new java.util.ArrayDeque<>();
                    if (json != null && json.isJsonArray()) for (JsonElement e : json.getAsJsonArray()) dq.add(e.getAsInt());
                    return dq;
                }
            })
            .create();

    private DbModels.AttackDb db = new DbModels.AttackDb();

    DbService(ConfigManager cm) {
        this.config = cm;
        load();
    }

    synchronized void load() {
        // 1) Try config blob
        String json = config.getConfiguration(GROUP, KEY_DB);
        if (json != null && !json.isEmpty()) {
            try {
                DbModels.AttackDb incoming = gson.fromJson(json, DbModels.AttackDb.class);
                db = (incoming != null ? incoming : new DbModels.AttackDb());
                if (db.version != 2) db.version = 2;
                return;
            } catch (Exception ignored) {}
        }
        // 2) Try backup file
        try {
            Path p = RuneLite.RUNELITE_DIR.toPath().resolve("attack-cycle-db-v2.json");
            if (Files.exists(p)) {
                String txt = Files.readString(p);
                DbModels.AttackDb incoming = gson.fromJson(txt, DbModels.AttackDb.class);
                db = (incoming != null ? incoming : new DbModels.AttackDb());
                if (db.version != 2) db.version = 2;
                // write it back into config for next time
                config.setConfiguration(GROUP, KEY_DB, gson.toJson(db));
                return;
            }
        } catch (Exception ignored) {}
        db = new DbModels.AttackDb();
    }

    /** Explicit save (Save button or shutdown). Saves profiles + current recordings. */
    synchronized void save() {
        if (hardOffline) return;
        dirtyProfiles = true;
        flushProfilesIfDirty();
    }

    synchronized void backupToFile() throws IOException {
        Path out = RuneLite.RUNELITE_DIR.toPath().resolve("attack-cycle-db-v2.json");
        Files.write(out, gson.toJson(db).getBytes());
    }

    synchronized void resetAll() {
        db = new DbModels.AttackDb();
        markProfilesDirty();
        notify(Change.RESET);
    }

    // ---- Profiles ----
    synchronized DbModels.NpcProfile getProfileByKey(String key) { return db.profiles.get(key); }

    synchronized DbModels.NpcProfile getOrCreateProfileByNpc(net.runelite.api.NPC n) {
        String name = n.getName();
        int lvl = n.getCombatLevel();
        DbModels.EntityKey ek = DbModels.EntityKey.of(name, lvl);

        DbModels.NpcProfile p = db.profiles.get(ek.key);
        boolean changed = false;
        if (p == null) {
            p = new DbModels.NpcProfile();
            p.key = ek.key;
            p.name = ek.name;
            p.level = ek.level;
            db.profiles.put(ek.key, p);
            changed = true;
        }
        if (n.getId() > 0) {
            if (p.variantIds == null) p.variantIds = new LinkedHashSet<>();
            if (p.variantIds.add(n.getId())) changed = true;
        }
        if (changed) { markProfilesDirty(); notify(Change.PROFILE); }
        return p;
    }

    synchronized void putProfile(DbModels.NpcProfile p) {
        db.profiles.put(p.key, p);
        markProfilesDirty();
        notify(Change.PROFILE);
    }

    synchronized void deleteProfile(String key) {
        db.profiles.remove(key);
        db.recording.rows.remove(key);
        markProfilesDirty();
        notify(Change.DELETE);
    }

    synchronized void clearProfileSelections(String key) {
        DbModels.NpcProfile p = db.profiles.get(key);
        if (p == null) return;
        clearBlock(p.base.melee); clearBlock(p.base.ranged); clearBlock(p.base.magic); clearBlock(p.base.chargeup);
        for (DbModels.PhaseProfile ph : p.phases.values()) {
            clearBlock(ph.melee); clearBlock(ph.ranged); clearBlock(ph.magic); clearBlock(ph.chargeup);
        }
        markProfilesDirty();
        notify(Change.PROFILE);
    }
    private void clearBlock(DbModels.StyleBlock b) {
        b.defaultAnimId = null; b.ticks = null; b.offset = 0; b.projSpeed = null;
        if (b.perAnim != null) b.perAnim.clear();
    }

    // ---- Recording (RAM; saved only on Save/shutdown) ----
    synchronized DbModels.RecRow getOrCreateRec(String npcKey, int animId) {
        Map<Integer, DbModels.RecRow> byAnim = db.recording.rows.computeIfAbsent(npcKey, k -> new HashMap<>());
        DbModels.RecRow r = byAnim.get(animId);
        if (r == null) {
            r = new DbModels.RecRow();
            r.animId = animId;
            byAnim.put(animId, r);
            notifyThrottled(Change.RECORDING);
        }
        return r;
    }

    synchronized Map<Integer, DbModels.RecRow> getRecsFor(String npcKey) {
        return db.recording.rows.getOrDefault(npcKey, java.util.Collections.emptyMap());
    }

    synchronized void putRec(String npcKey, DbModels.RecRow row) {
        db.recording.rows.computeIfAbsent(npcKey, k -> new HashMap<>()).put(row.animId, row);
        notifyThrottled(Change.RECORDING);
    }

    synchronized void removeRec(String npcKey, int animId) {
        Map<Integer, DbModels.RecRow> byAnim = db.recording.rows.get(npcKey);
        if (byAnim != null) byAnim.remove(animId);
        notifyThrottled(Change.RECORDING);
    }

    synchronized void relearnNpc(String key) {
        Map<Integer, DbModels.RecRow> recs = db.recording.rows.get(key);
        if (recs != null) {
            for (DbModels.RecRow r : recs.values()) {
                r.estProjSpeedUser = null;
                r.estTicksUser = null;
                r.gapsRecent.clear();
                r.projSpdRecent.clear();
                r.type = DbModels.AnimUiType.UNKNOWN;
                r.style = DbModels.Style.UNKNOWN;
            }
            notifyThrottled(Change.RECORDING);
        }
        DbModels.NpcProfile p = db.profiles.get(key);
        if (p != null) {
            clearBlock(p.base.melee); clearBlock(p.base.ranged); clearBlock(p.base.magic); clearBlock(p.base.chargeup);
            for (DbModels.PhaseProfile ph : p.phases.values()) {
                clearBlock(ph.melee); clearBlock(ph.ranged); clearBlock(ph.magic); clearBlock(ph.chargeup);
            }
            markProfilesDirty();
            notify(Change.PROFILE);
        }
    }

    synchronized Map<String, DbModels.NpcProfile> getAllProfiles() { return new HashMap<>(db.profiles); }

    synchronized List<DbModels.NpcProfile> searchProfiles(String q) {
        String s = (q == null ? "" : q.trim().toLowerCase());
        if (s.isEmpty()) return new ArrayList<>(db.profiles.values());
        List<DbModels.NpcProfile> res = new ArrayList<>();
        for (DbModels.NpcProfile e : db.profiles.values()) {
            String n = (e.name == null ? "" : e.name.toLowerCase());
            if (n.contains(s) || String.valueOf(e.level).contains(s)) res.add(e);
        }
        return res;
    }

    // Throttle frequent recording updates
    private volatile boolean recNotifyScheduled = false;
    private void notifyThrottled(Change c) {
        if (c != Change.RECORDING) { notify(c); return; }
        if (recNotifyScheduled) return;
        recNotifyScheduled = true;
        exec.schedule(() -> {
            recNotifyScheduled = false;
            notify(Change.RECORDING);
        }, 500, TimeUnit.MILLISECONDS);
    }
}
