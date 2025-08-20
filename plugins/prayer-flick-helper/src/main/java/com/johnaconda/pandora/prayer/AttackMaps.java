package com.johnaconda.pandora.prayer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

/**
 * Central mapping store. Seed a few known IDs and let learning mode / JSON fill the rest.
 */
final class AttackMaps
{
    private static final Map<Integer, AttackStyle> PROJECTILE_TO_STYLE = new HashMap<>();
    private static final Map<Integer, AttackStyle> ANIMATION_TO_STYLE  = new HashMap<>();
    private static final Map<Integer, Integer>    ANIMATION_TICKS      = new HashMap<>();

    private AttackMaps() {}

    // ------- Lookups -------
    static AttackStyle styleFromProjectile(int projectileId) {
        return PROJECTILE_TO_STYLE.get(projectileId);
    }

    static AttackStyle styleFromAnimation(int animationId) {
        return ANIMATION_TO_STYLE.get(animationId);
    }

    static int ticksToHitForAnimation(int animationId, int defaultTicks) {
        return ANIMATION_TICKS.getOrDefault(animationId, defaultTicks);
    }

    // ------- Learning-mode insertions -------
    static void putProjectile(int id, AttackStyle style) {
        PROJECTILE_TO_STYLE.put(id, style);
    }

    static void putAnimation(int id, AttackStyle style, int ticks) {
        ANIMATION_TO_STYLE.put(id, style);
        ANIMATION_TICKS.put(id, Math.max(1, ticks));
    }

    // ------- Deletions (for resets) -------
    static void removeProjectile(int id) {
        PROJECTILE_TO_STYLE.remove(id);
    }

    static void removeAnimation(int id) {
        ANIMATION_TO_STYLE.remove(id);
        ANIMATION_TICKS.remove(id);
    }

    // ------- JSON loading -------
    static void loadFromJson(InputStream in, Logger log) {
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = new Gson().fromJson(r, JsonObject.class);
            if (root == null) return;

            // projectiles: { "335":"RANGED", "1099":"MAGIC", ... }
            JsonObject proj = root.has("projectiles") && root.get("projectiles").isJsonObject()
                    ? root.getAsJsonObject("projectiles") : null;
            if (proj != null) {
                for (Map.Entry<String, JsonElement> e : proj.entrySet()) {
                    try {
                        int id = Integer.parseInt(e.getKey());
                        AttackStyle st = AttackStyle.valueOf(e.getValue().getAsString().trim().toUpperCase());
                        PROJECTILE_TO_STYLE.put(id, st);
                    } catch (Exception ex) {
                        if (log != null) log.warn("Bad projectile entry {} → {}", e.getKey(), e.getValue(), ex);
                    }
                }
            }

            // animations: { "4658": { "style":"MELEE", "ticks":1 }, ... }
            JsonObject anim = root.has("animations") && root.get("animations").isJsonObject()
                    ? root.getAsJsonObject("animations") : null;
            if (anim != null) {
                for (Map.Entry<String, JsonElement> e : anim.entrySet()) {
                    try {
                        int id = Integer.parseInt(e.getKey());
                        JsonObject obj = e.getValue().getAsJsonObject();
                        String styleName = obj.get("style").getAsString().trim().toUpperCase();
                        int ticks = obj.has("ticks") ? Math.max(1, obj.get("ticks").getAsInt()) : 2;
                        AttackStyle st = AttackStyle.valueOf(styleName);
                        ANIMATION_TO_STYLE.put(id, st);
                        ANIMATION_TICKS.put(id, ticks);
                    } catch (Exception ex) {
                        if (log != null) log.warn("Bad animation entry {} → {}", e.getKey(), e.getValue(), ex);
                    }
                }
            }
        } catch (Exception ex) {
            if (log != null) log.warn("Failed to load npc-attacks.json", ex);
        }
    }
}
