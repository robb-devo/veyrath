package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.math.Vec3;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Damage numbers: each strike the local player lands (or takes) shows its result at the impact
 * point, rising and fading over a second. Drawn on the HUD by projecting the world point through
 * the camera, so they stay readable at any distance and never hide behind blocks.
 */
public final class DamageNumbers {
    private DamageNumbers() {}

    static final double LIFE = 1.0;
    static final int MAX = 40;

    private record Num(double x, double y, double z, String text, int color, float size, long born) {}

    private static final List<Num> NUMS = new ArrayList<>();

    /** A strike resolved by the server; {@code mine}: the local player struck, otherwise was struck. */
    static void add(Vec3 at, Outcome o, boolean mine) {
        String text;
        int color;
        float size = 1;
        switch (o.result()) {
            case HIT, ARMORED -> {
                text = amount(o.damage());
                if (!mine) color = 0xFFFF5A50;
                else if (o.counter()) {
                    color = 0xFFFF9A3C;
                    text = text + "!";
                    size = 1.3f;
                } else color = o.result() == Outcome.Result.ARMORED ? 0xFFC8CCD2 : 0xFFFFFFFF;
                size *= (float) Math.min(1.8, 1 + o.damage() / 20);
            }
            case BLOCKED -> {
                text = "BLOCK";
                color = 0xFFB0B8C0;
                size = 0.8f;
            }
            case GUARD_BREAK -> {
                text = "GUARD BREAK";
                color = 0xFFFF7040;
                size = 1.1f;
            }
            case PARRIED -> {
                text = "PARRY";
                color = 0xFF6AD8FF;
                size = 1.2f;
            }
            case DODGED -> {
                text = o.perfect() ? "PERFECT" : "DODGE";
                color = 0xFF9AF0A0;
                size = o.perfect() ? 1.1f : 0.8f;
            }
            default -> {
                return;
            }
        }
        if (!mine) size *= 0.85f;
        if (NUMS.size() >= MAX) NUMS.removeFirst();
        // A little spread so numbers of a fast string do not stack on one spot.
        double jitter = (NUMS.size() % 5 - 2) * 0.12;
        NUMS.add(new Num(at.x() + jitter, at.y(), at.z() - jitter, text, color, size, System.nanoTime()));
    }

    private static String amount(double d) {
        return d >= 10 ? String.format(Locale.ROOT, "%.0f", d) : String.format(Locale.ROOT, "%.1f", d);
    }

    static void clear() { NUMS.clear(); }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (NUMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            NUMS.clear();
            return;
        }
        Camera cam = mc.gameRenderer.mainCamera();
        net.minecraft.world.phys.Vec3 cp = cam.position();
        var f = cam.forwardVector();
        var u = cam.upVector();
        var l = cam.leftVector();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double scale = (h / 2.0) / Math.tan(Math.toRadians(cam.getFov()) / 2);
        Font font = mc.font;
        long now = System.nanoTime();
        for (Iterator<Num> it = NUMS.iterator(); it.hasNext(); ) {
            Num n = it.next();
            double age = (now - n.born()) / 1e9;
            if (age > LIFE) {
                it.remove();
                continue;
            }
            double wy = n.y() + 0.3 + 0.9 * age * (1 - age / (2 * LIFE));
            double rx = n.x() - cp.x, ry = wy - cp.y, rz = n.z() - cp.z;
            double depth = rx * f.x() + ry * f.y() + rz * f.z();
            if (depth < 0.3) continue;
            double sx = -(rx * l.x() + ry * l.y() + rz * l.z()) / depth;
            double sy = (rx * u.x() + ry * u.y() + rz * u.z()) / depth;
            float px = (float) (w / 2.0 + sx * scale), py = (float) (h / 2.0 - sy * scale);
            float pop = age < 0.08 ? (float) (1.5 - age / 0.08 * 0.5) : 1;
            float size = n.size() * pop * (float) Math.clamp(6 / depth, 0.7, 1.4);
            int alpha = age > LIFE - 0.3 ? (int) (255 * (LIFE - age) / 0.3) : 255;
            if (alpha < 8) continue;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(px, py);
            pose.scale(size, size);
            g.text(font, n.text(), -font.width(n.text()) / 2, -4, (alpha << 24) | (n.color() & 0xFFFFFF), true);
            pose.popMatrix();
        }
    }
}
