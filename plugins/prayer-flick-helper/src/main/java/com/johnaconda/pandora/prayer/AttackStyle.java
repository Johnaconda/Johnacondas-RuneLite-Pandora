package com.johnaconda.pandora.prayer;

import net.runelite.api.widgets.WidgetInfo;

public enum AttackStyle {
    MAGIC(WidgetInfo.PRAYER_PROTECT_FROM_MAGIC),
    RANGED(WidgetInfo.PRAYER_PROTECT_FROM_MISSILES),
    MELEE(WidgetInfo.PRAYER_PROTECT_FROM_MELEE);

    private final WidgetInfo widgetInfo;
    AttackStyle(WidgetInfo w) { this.widgetInfo = w; }
    public WidgetInfo widgetInfo() { return widgetInfo; }
}
