package com.johnaconda.pandora.prayer;

public enum RoleOption {
    START, OTHER;

    public boolean toBool() { return this == START; }
    public static RoleOption fromBool(Boolean b) { return (b != null && b) ? START : OTHER; }
}
