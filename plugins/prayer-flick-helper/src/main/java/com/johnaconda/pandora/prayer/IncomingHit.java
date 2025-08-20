package com.johnaconda.pandora.prayer;

final class IncomingHit
{
    private final AttackStyle style;
    private final int landTick;
    private final String sourceName; // attacker (NPC) name when known, else null/"".

    IncomingHit(AttackStyle style, int landTick, String sourceName) {
        this.style = style;
        this.landTick = landTick;
        this.sourceName = sourceName;
    }

    AttackStyle getStyle() { return style; }
    int getLandTick() { return landTick; }
    String getSourceName() { return sourceName; }
}
