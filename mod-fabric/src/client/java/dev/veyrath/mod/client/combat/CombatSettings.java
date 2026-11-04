package dev.veyrath.mod.client.combat;

import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Player comfort settings for combat effects, in {@code config/veyrath-client.properties}. The file
 * is written with the defaults on first start, so it can be edited by hand; changes apply on the
 * next game start.
 */
public final class CombatSettings {
    private CombatSettings() {}

    static final String FILE = "veyrath-client.properties";

    /** Multiplier on camera shake, 0 (off) to 2. */
    private static double screenShake = 1.0;
    /** Full-screen impact flashes (ultimate finisher, parry, guard break, perfect dodge). */
    private static boolean screenFlash = true;
    /** The ultimate's own camera path. Off keeps the normal orbit camera. */
    private static boolean cinematicCamera = true;
    /** Darkened screen edges while the game runs in slow motion. */
    private static boolean slowMotionVignette = true;
    /** Camera pull-in on heavy impacts. */
    private static boolean impactZoom = true;

    public static double screenShake() { return screenShake; }
    public static boolean screenFlash() { return screenFlash; }
    public static boolean cinematicCamera() { return cinematicCamera; }
    public static boolean slowMotionVignette() { return slowMotionVignette; }
    public static boolean impactZoom() { return impactZoom; }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException e) {
                VeyrathMod.LOG.warn("Could not read {}, using defaults: {}", file, e.toString());
            }
        }
        screenShake = Math.clamp(number(p, "screenShake", screenShake), 0, 2);
        screenFlash = flag(p, "screenFlash", screenFlash);
        cinematicCamera = flag(p, "cinematicCamera", cinematicCamera);
        slowMotionVignette = flag(p, "slowMotionVignette", slowMotionVignette);
        impactZoom = flag(p, "impactZoom", impactZoom);
        if (!Files.isRegularFile(file)) write(file);
    }

    private static void write(Path file) {
        String text = String.join("\n",
                "# VEYRATH combat effects. Changes apply on the next game start.",
                "# Camera shake multiplier: 0 = off, 1 = normal, 2 = strongest.",
                "screenShake=" + String.format(Locale.ROOT, "%.2f", screenShake),
                "# Full-screen impact flashes (ultimate finisher, parry, guard break, perfect dodge).",
                "screenFlash=" + screenFlash,
                "# The ultimate's own camera path. false keeps the normal orbit camera.",
                "cinematicCamera=" + cinematicCamera,
                "# Darkened screen edges during slow motion.",
                "slowMotionVignette=" + slowMotionVignette,
                "# Camera pull-in on heavy impacts.",
                "impactZoom=" + impactZoom,
                "");
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(text);
            }
        } catch (IOException e) {
            VeyrathMod.LOG.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private static double number(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            VeyrathMod.LOG.warn("{}: {} is not a number, using {}", FILE, key, fallback);
            return fallback;
        }
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        v = v.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("on") || v.equals("yes")) return true;
        if (v.equals("false") || v.equals("off") || v.equals("no")) return false;
        VeyrathMod.LOG.warn("{}: {} should be true or false, using {}", FILE, key, fallback);
        return fallback;
    }
}
