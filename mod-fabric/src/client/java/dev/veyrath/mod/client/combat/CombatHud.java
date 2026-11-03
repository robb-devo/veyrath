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
 * slots with cooldowns and costs beside them, the combo counter on the right and the lock-on
 * target at the top. Drawn only in stance.
 */
public final class CombatHud {
    private CombatHud() {}

    private static final int BAR_W = 92, BAR_H = 5;
    private static final int BG = 0xB0101418, FRAME = 0xFF2A3038;
    private static final int STAMINA = 0xFFE6C84A, AETHER = 0xFF4FB4FF, GAUGE = 0xFFFF9A3C, GAUGE_FULL = 0xFFFFE08A;
    private static final String[] SLOT_KEYS = {"1", "2", "3", "4"};
    private static final String[] SLOT_NAMES = {"Gale", "Sky", "Quake", "ULT"};

    private static double shownCombo;
    private static int comboHold;

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Fighter f = LocalCombat.fighter();
        if (!LocalCombat.active() || f == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

        int x = 8, y = h - 44;
        bar(g, font, x, y, "STA", f.stamina() / f.rules.staminaMax, STAMINA);
        bar(g, font, x, y + 11, "AET", f.aether() / f.rules.aetherMax, AETHER);
        double gauge = f.gauge() / f.rules.gaugeMax;
        bar(g, font, x, y + 22, gauge >= 1 ? "MAX" : "UNB", gauge, gauge >= 1 ? GAUGE_FULL : GAUGE);

        // Ability slots.
        int sx = x + BAR_W + 30, sy = y;
        String[] ids = {f.set.abilities[0], f.set.abilities[1], f.set.abilities[2], f.set.ultimate};
        for (int i = 0; i < ids.length; i++) {
            Move m = f.set.get(ids[i]);
            int bx = sx + i * 30;
            g.fill(bx, sy, bx + 26, sy + 27, BG);
            g.fill(bx, sy, bx + 26, sy + 1, FRAME);
            if (m == null) continue;
            boolean affordable = f.aether() >= m.aether - 1e-9 && f.gauge() >= m.gaugeCost - 1e-9;
            int cd = f.cooldown(m.id);
            int color = cd > 0 || !affordable ? 0xFF7A808A : (i == 3 ? GAUGE_FULL : 0xFFFFFFFF);
            g.text(font, SLOT_KEYS[i], bx + 2, sy + 2, 0xFFB8C0CC, false);
            g.text(font, SLOT_NAMES[i], bx + 13 - font.width(SLOT_NAMES[i]) / 2, sy + 11, color, true);
            if (cd > 0) {
                int fillH = (int) Math.ceil(27.0 * cd / Math.max(1, m.cooldown));
                g.fill(bx, sy + 27 - fillH, bx + 26, sy + 27, 0x80000000);
                String s = String.format(Locale.ROOT, "%.0f", Math.ceil(cd / 20.0));
                g.text(font, s, bx + 24 - font.width(s), sy + 19, 0xFFFFFFFF, true);
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

    private static void bar(GuiGraphicsExtractor g, Font font, int x, int y, String label, double frac, int color) {
        frac = Math.clamp(frac, 0, 1);
        g.text(font, label, x, y - 1, 0xFFB8C0CC, true);
        int bx = x + 22;
        g.fill(bx - 1, y - 1, bx + BAR_W + 1, y + BAR_H + 1, FRAME);
        g.fill(bx, y, bx + BAR_W, y + BAR_H, BG);
        g.fill(bx, y, bx + (int) Math.round(BAR_W * frac), y + BAR_H, color);
    }
}
