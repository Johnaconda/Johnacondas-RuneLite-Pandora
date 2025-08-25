package com.johnaconda.pandora.attackcycle;

/**
 * Ultra-light per-NPC swing tracker.
 *
 * Assumptions:
 * - All calls happen on the game thread (RuneLite event bus), so no extra locking.
 * - We don't persist anything here; DbService carries history. This is just runtime cadence.
 *
 * API:
 *  - noteAttackTickAndReturnGap(now): mark an observed attack animation this tick; returns gap to previous swing, or -1.
 *  - ticksUntilNext(now, interval): how many ticks until the next expected swing based on the last observed swing.
 *  - autoAdvanceIfDue(now, interval): rolls internal phase forward if we've passed predicted swing(s).
 *  - candidateSpeed(): last observed gap (useful as a heuristic before we’ve fully learned the profile).
 */
final class AttackTracker
{
    /** Tick of the last observed attack animation for this NPC; -1 if none yet. */
    private int lastAttackTick = -1;

    /** Most recent observed gap between two attack animations (in ticks); -1 if unknown. */
    private int lastGap = -1;

    /** Hard reset (e.g., despawn, stops targeting us, phase swap). */
    void reset()
    {
        lastAttackTick = -1;
        lastGap = -1;
    }

    /**
     * Note an observed attack animation now.
     * @param nowTick current game tick
     * @return gap between this and the previous observed attack, or -1 if no previous.
     */
    int noteAttackTickAndReturnGap(final int nowTick)
    {
        int gap = -1;
        if (lastAttackTick >= 0)
        {
            gap = nowTick - lastAttackTick;
            if (gap <= 0) gap = 1;       // guard against clock weirdness
            if (gap > 60) gap = 60;      // sanity cap; we don't care beyond this
            lastGap = gap;
        }
        lastAttackTick = nowTick;
        return gap;
    }

    /**
     * Returns ticks until the next expected swing given a nominal interval.
     * If we've never seen an attack, returns -1.
     */
    int ticksUntilNext(final int nowTick, final int interval)
    {
        if (lastAttackTick < 0) return -1;
        int next = lastAttackTick + Math.max(1, interval);
        // Ensure "next" is strictly in the future
        if (nowTick >= next)
        {
            int delta = nowTick - next;
            int jumps = (delta / Math.max(1, interval)) + 1;
            next += jumps * Math.max(1, interval);
        }
        return next - nowTick;
    }

    /**
     * Rolls internal phase forward if we've passed one or more predicted swings.
     * This keeps cadence stable even if we didn't observe every animation frame.
     */
    void autoAdvanceIfDue(final int nowTick, final int interval)
    {
        if (lastAttackTick < 0) return;
        final int iv = Math.max(1, interval);
        final int expected = lastAttackTick + iv;
        if (nowTick >= expected)
        {
            // Advance lastAttackTick by however many full intervals we've skipped
            int skipped = (nowTick - lastAttackTick) / iv;
            if (skipped > 0) lastAttackTick += skipped * iv;
        }
    }

    /**
     * Last observed gap (ticks) between two attacks; -1 if unknown.
     * Useful as a heuristic when auto-tagging off a near-synchronous hitsplat.
     */
    int candidateSpeed()
    {
        return lastGap;
    }
}
