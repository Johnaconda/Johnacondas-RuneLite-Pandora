package com.johnaconda.pandora.prayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NpcProfile
{
    static final class AnimInfo {
        AttackStyle style;      // null = None/unlabeled
        boolean start;          // true = attack-start anim
        Integer periodTicks;    // learned ticks between same anim starts (frozen once consistent)
        List<Integer> samples;  // recent gaps collected until frozen
        int offset;             // user-tuned offset (used as: hitDelay = period - offset), default 1

        AnimInfo() { this(null, false, 1); }
        AnimInfo(AttackStyle style, boolean start) { this(style, start, 1); }
        AnimInfo(AttackStyle style, boolean start, int offset) {
            this.style = style;
            this.start = start;
            this.offset = Math.max(0, Math.min(4, offset));
            this.periodTicks = null;
            this.samples = new ArrayList<>();
        }

        void addSample(int gap) {
            if (periodTicks != null) return;         // frozen
            if (gap < 2 || gap > 10) return;
            samples.add(gap);
            if (samples.size() > 5) samples.remove(0);
            if (samples.size() >= 3) {
                int[] tmp = samples.stream().mapToInt(Integer::intValue).toArray();
                java.util.Arrays.sort(tmp);
                int min = tmp[0], max = tmp[tmp.length - 1];
                int median = tmp[tmp.length / 2];
                if (max - min <= 1) {
                    periodTicks = median;            // freeze when consistent (±1)
                    samples.clear();
                }
            }
        }
    }

    int id;
    String name; // optional

    // Keep insertion order for nicer UX
    Map<Integer, AnimInfo> animations = new LinkedHashMap<>();
    Map<Integer, AttackStyle> projectiles = new LinkedHashMap<>(); // value may be null

    NpcProfile() {}
    NpcProfile(int id, String name) {
        this.id = id;
        this.name = name == null ? "" : name.trim();
    }

    AnimInfo animInfo(int animId) { return animations.get(animId); }
    AttackStyle styleForAnimation(int animId) { AnimInfo ai = animations.get(animId); return ai == null ? null : ai.style; }
    Boolean startForAnimation(int animId) { AnimInfo ai = animations.get(animId); return ai == null ? Boolean.FALSE : ai.start; }
    Integer periodForAnimation(int animId) { AnimInfo ai = animations.get(animId); return ai == null ? null : ai.periodTicks; }
    Integer offsetForAnimation(int animId) { AnimInfo ai = animations.get(animId); return ai == null ? 1 : Math.max(0, Math.min(4, ai.offset)); }
    AttackStyle styleForProjectile(int projectileId) { return projectiles.get(projectileId); } // may be null
}
