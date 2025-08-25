package com.johnaconda.pandora.attackcycle;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AttackCyclePluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(AttackCyclePlugin.class);
        RuneLite.main(new String[] { "--developer-mode", "--debug" });
    }
}