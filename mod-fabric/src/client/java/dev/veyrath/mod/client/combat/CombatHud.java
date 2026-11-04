package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.move.Move;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * Combat stance HUD: stamina, Aether and Unbound gauge bars at the bottom left, the four ability
 * slots with cooldowns and costs where the hotbar sits (the hotbar is hidden in stance), the combo
 * counter on the right and the lock-on target at the top. Drawn only in stance.
 */
public final class CombatHud {
    private CombatHud() {}

    private static final int BAR_W = 92, BAR_H = 5;
    private static final int SLOT_W = 34, SLOT_H = 22, SLOT_GAP = 3;
    private static final int BG = 0xB0101418, FRAME = 0xFF2A3038, READY = 0xFF5A6674;
    private static final int STAMINA = 0xFFE6C84A, AETHER = 0xFF4FB4FF, GAUGE = 0xFFFF9A3C, GAUGE_FULL = 0xFFFFE08A;
    private static final String[] SLOT_KEYS = {"1", "2", "3", "4"};
    private static final String[] SLOT_NAMES = {"GALE", "SKY", "QUAKE", "ULT"};

    private static double shownCombo;
    private static int comboHold;

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Fighter f = LocalCombat.fighter();
        if (!LocalCombat.active() || f == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

        int x = 8, y = h - 44;
        String weapon = dev.veyrath.combat.content.Weapons.of(f.set).name().toUpperCase(Locale.ROOT);
        g.text(font, weapon, x, y - 13, 0xFFE8DCC0, true);
        bar(g, font, x, y, "STA", f.stamina() / f.rules.staminaMax, STAMINA);
        bar(g, font, x, y + 11, "AET", f.aether() / f.rules.aetherMax, AETHER);
        double gauge = f.gauge() / f.rules.gaugeMax;
        bar(g, font, x, y + 22, gauge >= 1 ? "MAX" : "UNB", gauge, gauge >= 1 ? GAUGE_FULL : GAUGE);

        // Ability slots, centred at the bottom where the hotbar is outside stance.
        int total = 4 * SLOT_W + 3 * SLOT_GAP;
        int sx = w / 2 - total / 2, sy = h - SLOT_H - 1;
        String[] ids = {f.set.abilities[0], f.set.abilities[1], f.set.abilities[2], f.set.ultimate};
        for (int i = 0; i < ids.length; i++) {
            Move m = f.set.get(ids[i]);
            int bx = sx + i * (SLOT_W + SLOT_GAP);
            if (m == null) continue;
            boolean affordable = f.aether() >= m.aether - 1e-9 && f.gauge() >= m.gaugeCost - 1e-9;
            int cd = f.cooldown(m.id);
            boolean ready = cd == 0 && affordable;
            g.fill(bx - 1, sy - 1, bx + SLOT_W + 1, sy + SLOT_H + 1, ready ? (i == 3 ? GAUGE_FULL : READY) : FRAME);
            g.fill(bx, sy, bx + SLOT_W, sy + SLOT_H, BG);
            int color = ready ? (i == 3 ? GAUGE_FULL : 0xFFFFFFFF) : 0xFF7A808A;
            g.text(font, SLOT_NAMES[i], bx + SLOT_W / 2 - font.width(SLOT_NAMES[i]) / 2, sy + 7, color, true);
            small(g, font, SLOT_KEYS[i], bx + 2, sy + 2, 0xFFB8C0CC);
            if (cd > 0) {
                int fillH = (int) Math.ceil((double) SLOT_H * cd / Math.max(1, m.cooldown));
                g.fill(bx, sy + SLOT_H - fillH, bx + SLOT_W, sy + SLOT_H, 0x80000000);
                String secs = String.format(Locale.ROOT, "%.0f", Math.ceil(cd / 20.0));
                small(g, font, secs, bx + SLOT_W - 2 - (int) Math.ceil(font.width(secs) * 0.6), sy + SLOT_H - 7, 0xFFFFFFFF);
            } else if (!affordable) {
                String cost = m.gaugeCost > 0 ? "MAX" : String.format(Locale.ROOT, "%.0f", m.aether);
                small(g, font, cost, bx + SLOT_W - 2 - (int) Math.ceil(font.width(cost) * 0.6), sy + SLOT_H - 7, i == 3 ? GAUGE : AETHER);
            }
        }

        // Combo counter, held briefly after the string ends.
        int combo = f.combo();
        if (combo >= 2) {
            shownCombo = combo;
            comboHold = 30;
        } else if (comboHold > 0) {
            comboHold--;
        }
        if (comboHold > 0 && shownCombo >= 2) {
            String n = String.valueOf((int) shownCombo);
            int cx = w - 70, cy = h / 2 - 30;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.scale(2.5f, 2.5f);
            g.text(font, n, -font.width(n), 0, 0xFFFFE08A, true);
            pose.popMatrix();
            g.text(font, "HITS", cx + 4, cy + 9, 0xFFFFFFFF, true);
        }

        Entity lock = LocalCombat.lockTarget();
        if (lock != null) {
            String s = "LOCK  " + lock.getDisplayName().getString();
            g.text(font, s, w / 2 - font.width(s) / 2, 6, 0xFFFF6A5A, true);
        }
    }

    /** Text at 60 % size (key numbers, cooldown seconds, costs). */
    private static void small(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(0.6f, 0.6f);
        g.text(font, s, 0, 0, color, true);
        pose.popMatrix();
    }

    private static void bar(GuiGraphicsExtractor g, Font font, int x, int y, String label, double frac, int color) {
        frac = Math.clamp(frac, 0, 1);
        g.text(font, label, x, y - 1, 0xFFB8C0CC, true);
        int bx = x + 22;
        g.fill(bx - 1, y - 1, bx + BAR_W + 1, y + BAR_H + 1, FRAME);
        g.fill(bx, y, bx + BAR_W, y + BAR_H, BG);
        g.fill(bx, y, bx + (int) Math.round(BAR_W * frac), y + BAR_H, color);
    }
}
