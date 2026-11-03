package dev.veyrath.mod.gametest;

import java.util.Arrays;

/** Shared bits of the client game tests. */
final class GameTests {
    private GameTests() {}

    /**
     * Which tests run: system property {@code veyrath.tests}, a comma list of "tour", "combat" and "arena"
     * (gradle: {@code -Pveyrath.tests=combat}); default "tour", the Chapter 01 seed tour.
     */
    static boolean selected(String name) {
        String list = System.getProperty("veyrath.tests", "tour");
        return list.equals("all") || Arrays.stream(list.split(",")).map(String::trim).anyMatch(name::equals);
    }

    /** F1 state; the 26.3 Hud keeps it in a private field. */
    static void setHudHidden(net.minecraft.client.Minecraft mc, boolean hidden) {
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.Hud.class.getDeclaredField("isHidden");
            f.setAccessible(true);
            f.setBoolean(mc.gui.hud, hidden);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot toggle the HUD", e);
        }
    }
}
