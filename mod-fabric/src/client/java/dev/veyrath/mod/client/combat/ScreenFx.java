package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.fighter.Outcome;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Full-screen combat accents drawn over the world, under the HUD: a short impact flash (ultimate
 * finisher, parry, guard break, perfect dodge) and darkened edges while the game runs in slow
 * motion. Timed in real time, so slow motion does not stretch a flash. Both follow
 * {@link CombatSettings}.
 */
public final class ScreenFx {
    private ScreenFx() {}

    /** Seconds a full-strength flash takes to fade. */
    static final double FLASH_FADE = 0.22;
    static final int VIGNETTE_BANDS = 12;

    private static double flash;
    private static int flashRgb = 0xFFFFFF;
    private static double vignette;
    private static long lastFrame;

    /** Starts a flash; a stronger flash replaces a weaker one still fading. */
    public static void flash(int rgb, double strength) {
        if (!CombatSettings.screenFlash() || strength <= 0) return;
        if (strength >= flash) {
            flash = Math.min(1, strength);
            flashRgb = rgb & 0xFFFFFF;
        }
    }

    /** Accents for a resolved strike the local player took part in. */
    static void onOutcome(Outcome o, String vfx, boolean byMe) {
        switch (o.result()) {
            case PARRIED -> flash(0xBFE4FF, byMe ? 0.22 : 0.32);
            case GUARD_BREAK -> {
                flash(0xFFD27A, 0.28);
                ActionCamera.punch(0.6);
            }
            case DODGED -> {
                if (o.perfect()) flash(0xA8D8FF, 0.2);
            }
            default -> {
            }
        }
        if (o.result() == Outcome.Result.HIT || o.result() == Outcome.Result.ARMORED) impact(vfx);
    }

    /** Accents by the vfx id of a landed hit (also used for the attacker's own predicted hits). */
    static void impact(String vfx) {
        if (vfx == null) return;
        switch (vfx) {
            case "ultimate_impact" -> {
                flash(0xFFF4D6, 0.6);
                ActionCamera.punch(1.0);
            }
            case "impact_charged", "shockwave_ring" -> ActionCamera.punch(0.55);
            case "shockwave_cone", "impact_launch" -> ActionCamera.punch(0.35);
            case "impact_heavy" -> ActionCamera.punch(0.3);
            default -> {
            }
        }
    }

    public static void clear() {
        flash = 0;
        vignette = 0;
        lastFrame = 0;
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;

        boolean slow = mc.level != null && !mc.isPaused() && mc.level.tickRateManager().tickrate() < 19.5f;
        double want = slow && CombatSettings.slowMotionVignette() ? 1 : 0;
        // Eases in quickly and out a little slower, so the return to full speed reads as a release.
        vignette += (want - vignette) * Math.min(1, dt * (want > vignette ? 14 : 6));
        flash = Math.max(0, flash - dt / FLASH_FADE);

        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (vignette > 0.01) drawVignette(g, w, h, vignette);
        if (flash > 0.005) {
            // Ease out: bright for the first frames, then a soft tail.
            double a = flash * flash * 0.85;
            g.fill(0, 0, w, h, ((int) Math.round(a * 255) << 24) | flashRgb);
        }
    }

    /** Dark edges in rings that fade toward the centre; rings do not overlap, so alphas do not stack. */
    private static void drawVignette(GuiGraphicsExtractor g, int w, int h, double strength) {
        int band = Math.max(1, (int) Math.round(Math.min(w, h) * 0.02));
        for (int i = 0; i < VIGNETTE_BANDS; i++) {
            double f = 1 - (double) i / VIGNETTE_BANDS;
            int a = (int) Math.round(strength * 150 * f * f);
            if (a <= 0) continue;
            int c = (a << 24) | 0x070A14;
            int o = i * band, n = o + band;
            if (n * 2 >= Math.min(w, h)) break;
            g.fill(o, o, w - o, n, c);              // top
            g.fill(o, h - n, w - o, h - o, c);      // bottom
            g.fill(o, n, n, h - n, c);              // left
            g.fill(w - n, n, w - o, h - n, c);      // right
        }
    }
}
