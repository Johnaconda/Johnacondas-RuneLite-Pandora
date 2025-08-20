package com.johnaconda.pandora.prayer;

enum LabelOption {
    NONE, MELEE, RANGED, MAGIC;

    AttackStyle toStyleOrNull() {
        switch (this) {
            case MELEE:  return AttackStyle.MELEE;
            case RANGED: return AttackStyle.RANGED;
            case MAGIC:  return AttackStyle.MAGIC;
            default:     return null;
        }
    }

    static LabelOption fromStyle(AttackStyle s) {
        if (s == null) return NONE;
        switch (s) {
            case MELEE:  return MELEE;
            case RANGED: return RANGED;
            case MAGIC:  return MAGIC;
        }
        return NONE;
    }
}
