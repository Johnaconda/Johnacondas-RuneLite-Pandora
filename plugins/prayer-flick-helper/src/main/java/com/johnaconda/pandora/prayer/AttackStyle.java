package com.johnaconda.pandora.prayer;

/**
 * Stores the Prayer-tab widget group/child for the three overheads.
 * If your client uses different child ids, adjust with Dev Tools → Widget Inspector.
 */
public enum AttackStyle
{
    MAGIC(541, 10),   // Protect from Magic
    RANGED(541, 12),  // Protect from Missiles
    MELEE(541, 8);    // Protect from Melee

    private final int groupId;
    private final int childId;

    AttackStyle(int groupId, int childId) {
        this.groupId = groupId;
        this.childId = childId;
    }

    public int groupId() { return groupId; }
    public int childId() { return childId; }
}
