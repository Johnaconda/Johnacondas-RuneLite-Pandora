package com.johnaconda.pandora.prayer;

import java.util.HashMap;
import java.util.Map;

final class AttackMaps {
    private static final Map<Integer, AttackStyle> projectileToStyle = new HashMap<>();
    private static final Map<Integer, AttackStyle> animationToStyle = new HashMap<>();
    private static final Map<Integer, Integer> animationTicks = new HashMap<>();

    static {
        // TODO: Replace example IDs with real mappings as you gather them.
        // Example: Jad (illustrative IDs — fill with your tested values)
        animationToStyle.put(2656, AttackStyle.MAGIC);  animationTicks.put(2656, 4);
        animationToStyle.put(2652, AttackStyle.RANGED); animationTicks.put(2652, 4);

        // Common projectiles (examples)
        projectileToStyle.put(335, AttackStyle.RANGED); // e.g., arrow-type
        projectileToStyle.put(1099, AttackStyle.MAGIC); // e.g., bolt spell
    }

    static AttackStyle styleFromProjectile(int projectileId) { return projectileToStyle.get(projectileId); }
    static AttackStyle styleFromAnimation(int animationId) { return animationToStyle.get(animationId); }
    static int ticksToHitForAnimation(int animationId) { return animationTicks.getOrDefault(animationId, 4); }
}
