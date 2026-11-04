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

    /** Seconds after a hit within which the next hit on the same target adds to its number. */
    static final double MERGE = 0.45;
    /** Hits of at least this much damage never merge into a running total. */
    static final double SUM_BELOW = 8;

    private static final class Num {
        final double x, y, z;
        final int target;
        final boolean mine, sums;
        String text;
        int color;
        float size;
        final long born;
        /** Latest hit added to this number: its fade and pop count from here. */
        long lastHit;
        double total;
        int hits = 1;

        Num(double x, double y, double z, int target, boolean mine, boolean sums, String text, int color, float size, double total) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.target = target;
            this.mine = mine;
            this.sums = sums;
            this.text = text;
            this.color = color;
            this.size = size;
            this.total = total;
            this.born = System.nanoTime();
            this.lastHit = born;
        }

        String text() { return text; }
        int color() { return color; }
        float size() { return size; }
        long born() { return born; }
        double x() { return x; }
        double y() { return y; }
        double z() { return z; }
    }

    private static final List<Num> NUMS = new ArrayList<>();

    /** A strike resolved by the server; {@code mine}: the local player struck, otherwise was struck. */
    static void add(int target, Vec3 at, Outcome o, boolean mine) {
        boolean hit = o.result() == Outcome.Result.HIT || o.result() == Outcome.Result.ARMORED;
        // Big blows (a finisher) keep a number of their own; light hits of a string add up.
        boolean small = hit && !o.counter() && o.damage() < SUM_BELOW;
        if (small && merge(target, o.damage(), mine)) return;
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
        if (!hit && repeatOf(target, text, mine)) return;
        if (NUMS.size() >= MAX) NUMS.removeFirst();
        // A little spread so numbers of a fast string do not stack on one spot.
        double jitter = (NUMS.size() % 5 - 2) * 0.12;
        NUMS.add(new Num(at.x() + jitter, at.y(), at.z() - jitter, target, mine, small, text, color, size,
                hit ? o.damage() : 0));
    }

    /**
     * A fast string (the ultimate's barrage above all) would stack a dozen small numbers on one
     * spot. Instead a hit soon after the last one on the same target adds to that number, which
     * pops again and grows with the total.
     */
    /** The same word (DODGE, BLOCK) on the same target shortly after: keep one, refreshed. */
    private static boolean repeatOf(int target, String text, boolean mine) {
        long now = System.nanoTime();
        for (int i = NUMS.size() - 1; i >= 0; i--) {
            Num n = NUMS.get(i);
            if (n.target != target || n.mine != mine || !n.text.equals(text)) continue;
            if ((now - n.lastHit) / 1e9 > MERGE) return false;
            n.lastHit = now;
            return true;
        }
        return false;
    }

    private static boolean merge(int target, double damage, boolean mine) {
        long now = System.nanoTime();
        for (int i = NUMS.size() - 1; i >= 0; i--) {
            Num n = NUMS.get(i);
            if (!n.sums || n.target != target || n.mine != mine) continue;
            if ((now - n.lastHit) / 1e9 > MERGE) return false;
            n.total += damage;
            n.hits++;
            n.text = amount(n.total);
            n.size = (float) Math.min(2.2, (mine ? 1 : 0.85) * (1 + n.total / 20) * (1 + Math.min(n.hits, 12) * 0.02));
            n.lastHit = now;
            return true;
        }
        return false;
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
            double age = (now - n.lastHit) / 1e9;
            if (age > LIFE) {
                it.remove();
                continue;
            }
            // Rises from its first hit (capped, so a long barrage does not carry it away).
            double rise = Math.min(LIFE, (now - n.born()) / 1e9);
            double wy = n.y() + 0.3 + 0.9 * rise * (1 - rise / (2 * LIFE));
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
