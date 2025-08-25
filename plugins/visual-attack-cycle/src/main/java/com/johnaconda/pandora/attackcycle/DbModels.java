package com.johnaconda.pandora.attackcycle;

import java.util.*;

/** Lightweight POJOs for Attack Cycle. */
final class DbModels
{
    private DbModels() {}

    // ================= Top-level =================
    static final class AttackDb
    {
        int version = 2;
        Map<String, NpcProfile> profiles = new HashMap<>();
        Recording recording = new Recording();
    }

    static final class Recording
    {
        /** npcKey -> (animId -> RecRow) */
        Map<String, Map<Integer, RecRow>> rows = new HashMap<>();
    }

    // ================= Keys =================
    static final class EntityKey
    {
        final String name;
        final int level;
        final String key;

        private EntityKey(String name, int level, String key)
        {
            this.name = name;
            this.level = level;
            this.key = key;
        }

        static EntityKey of(String rawName, int level)
        {
            final String n = (rawName == null ? "" : rawName.trim());
            final String sane = sanitizeName(n);
            final String key = sane + "#" + level;
            return new EntityKey(n, level, key);
        }

        private static String sanitizeName(String s)
        {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++)
            {
                char c = Character.toLowerCase(s.charAt(i));
                if (Character.isLetterOrDigit(c)) sb.append(c);
                else if (Character.isWhitespace(c) || c == '-' || c == '_' || c == '\'') sb.append('_');
            }
            String out = sb.toString();
            if (out.isEmpty()) out = "npc";
            return out;
        }
    }

    // ================= Profiles =================
    static final class NpcProfile
    {
        String key;
        String name;
        int level;
        Set<Integer> variantIds = new LinkedHashSet<>();

        PhaseProfile base = new PhaseProfile();
        Map<String, PhaseProfile> phases = new LinkedHashMap<>();

        @Override public String toString() { return (name == null ? "??" : name) + " · " + level; }
    }

    static final class PhaseProfile
    {
        StyleBlock melee    = new StyleBlock();
        StyleBlock ranged   = new StyleBlock();
        StyleBlock magic    = new StyleBlock();
        StyleBlock chargeup = new StyleBlock(); // special block fed by Type=CHARGEUP

        Set<Integer> triggerAnimIds = new LinkedHashSet<>();
        Set<Integer> triggerNpcIds  = new LinkedHashSet<>();
    }

    /** Style block with per-animation overrides. */
    static final class StyleBlock
    {
        Integer defaultAnimId;   // selected anim for this block
        Integer ticks;           // block default
        Integer offset = 0;      // block default
        Integer projSpeed;       // block default

        /** Per-animation overrides: if null, fall back to block defaults. */
        Map<Integer, StyleAnimSettings> perAnim = new LinkedHashMap<>();
    }

    static final class StyleAnimSettings
    {
        public Integer ticks;
        public Integer offset;
        public Integer projSpeed;
    }

    // ================= Recording rows =================
    /** UI tag about “what kind of animation is this?” */
    enum AnimUiType { UNKNOWN, ATTACK, NONATTACK, CHARGEUP }
    /** UI tag about “what combat style is this?” */
    enum Style { UNKNOWN, MELEE, RANGED, MAGIC }

    static final class RecRow
    {
        int animId;

        AnimUiType type = AnimUiType.UNKNOWN;
        Style style = Style.UNKNOWN;

        int seen = 0;
        int lastSeenTick = -1; // last time we saw this anim (any type)

        Integer estTicksUser;       // user override
        Integer estProjSpeedUser;   // user override

        ArrayDeque<Integer> gapsRecent = new ArrayDeque<>();
        ArrayDeque<Integer> projSpdRecent = new ArrayDeque<>();

        private static final int MAX_RECENT_GAPS = 16;
        private static final int MAX_RECENT_PROJ = 16;

        void pushGap(int gapTicks)
        {
            if (gapTicks <= 0) return;
            if (gapTicks > 60) gapTicks = 60;
            gapsRecent.addLast(gapTicks);
            while (gapsRecent.size() > MAX_RECENT_GAPS) gapsRecent.removeFirst();
        }

        void pushProj(int speed)
        {
            if (speed <= 0) return;
            projSpdRecent.addLast(speed);
            while (projSpdRecent.size() > MAX_RECENT_PROJ) projSpdRecent.removeFirst();
        }

        Integer liveEstTicksMedian()
        {
            if (estTicksUser != null) return estTicksUser;
            if (gapsRecent.isEmpty()) return null;
            int n = gapsRecent.size();
            int[] arr = new int[n]; int i=0; for (int v : gapsRecent) arr[i++]=v;
            Arrays.sort(arr);
            int mid = n>>>1;
            int val = (n%2==0) ? ((arr[mid-1]+arr[mid]) / 2) : arr[mid];
            if (val < 1) val = 1; if (val > 15) val = 15;
            return val;
        }

        Integer liveProjSpeedMedian()
        {
            if (estProjSpeedUser != null) return estProjSpeedUser;
            if (projSpdRecent.isEmpty()) return null;
            int n = projSpdRecent.size();
            int[] arr = new int[n]; int i=0; for (int v : projSpdRecent) arr[i++]=v;
            Arrays.sort(arr);
            int mid = n>>>1;
            int val = (n%2==0) ? ((arr[mid-1]+arr[mid]) / 2) : arr[mid];
            return Math.max(1, Math.min(60, val));
        }
    }
}
