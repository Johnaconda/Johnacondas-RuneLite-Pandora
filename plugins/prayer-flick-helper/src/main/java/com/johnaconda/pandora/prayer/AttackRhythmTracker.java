package com.johnaconda.pandora.prayer;

/**
 * Phase-locked rhythm learner with debounce and outlier rejection.
 * Learns from *attack-start* ticks (in game ticks).
 */
final class AttackRhythmTracker
{
    private static final int MIN_PERIOD = 2;
    private static final int MAX_PERIOD = 10;

    // how many accepted intervals before we say "stable"
    private static final int STABLE_SAMPLES = 3;

    // reject starts that are too soon relative to the current period
    private static final int EARLY_MARGIN = 2;

    // simple debounce to avoid double-counting same/next-tick starts
    private static final int DEBOUNCE_TICKS = 1;

    // smoothing factor for PLL-like update (0..1). Higher = faster adaptation.
    private static final double ALPHA = 0.3;

    private int lastStartTick = -1;
    private int cooldownUntil = -1;

    private double periodEst = 0;   // floating estimate
    private int acceptedIntervals = 0;

    void reset() {
        lastStartTick = -1;
        cooldownUntil = -1;
        periodEst = 0;
        acceptedIntervals = 0;
    }

    /** Track an attack start if it passes debounce/outlier checks. */
    void trackStart(int tick) {
        // Debounce
        if (cooldownUntil >= 0 && tick <= cooldownUntil) {
            return;
        }

        if (lastStartTick >= 0) {
            int gap = tick - lastStartTick;

            // Basic bounds
            if (gap < MIN_PERIOD || gap > MAX_PERIOD + 3) { // extreme late -> likely reset: accept but reset estimator
                lastStartTick = tick;
                cooldownUntil = tick + DEBOUNCE_TICKS;
                // don't update periodEst with this gap; instead treat next good gap as new seed
                return;
            }

            if (hasPeriod()) {
                // Too-soon guard: reject clearly early re-triggers
                if (gap < (int)Math.max(MIN_PERIOD, Math.round(periodEst) - EARLY_MARGIN)) {
                    cooldownUntil = tick + DEBOUNCE_TICKS;
                    return;
                }

                // PLL-like smoothing toward the new gap
                periodEst = clamp(periodEst + ALPHA * (gap - periodEst), MIN_PERIOD, MAX_PERIOD);
            } else {
                // First seed of estimator
                periodEst = gap;
                if (periodEst < MIN_PERIOD) periodEst = MIN_PERIOD;
                if (periodEst > MAX_PERIOD) periodEst = MAX_PERIOD;
            }
            acceptedIntervals++;
        }

        lastStartTick = tick;
        cooldownUntil = tick + DEBOUNCE_TICKS;
    }

    boolean hasPeriod() {
        return acceptedIntervals >= STABLE_SAMPLES && periodEst >= MIN_PERIOD && periodEst <= MAX_PERIOD;
    }

    /** Latest integer period (rounded), or 0 if not ready. */
    int getPeriod() {
        return hasPeriod() ? (int)Math.round(periodEst) : 0;
    }

    /** Ticks until next predicted start, or -1 if unknown. */
    int ticksUntilNextStart(int nowTick) {
        if (!hasPeriod() || lastStartTick < 0) return -1;
        int P = (int)Math.round(periodEst);
        int next = lastStartTick + P;
        if (next <= nowTick) {
            int k = (nowTick - next) / P + 1;
            next += k * P;
        }
        return next - nowTick;
    }

    int nextStartTick(int nowTick) {
        int dt = ticksUntilNextStart(nowTick);
        return dt < 0 ? -1 : nowTick + dt;
    }

    private static double clamp(double v, double lo, double hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }
}
