package dev.veyrath.mod.client.hud;

import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.move.Move;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.client.combat.LocalCombat;
import dev.veyrath.mod.client.mixin.BossHealthOverlayAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * VEYRATH's HUD, in place of every vanilla piece that would make the screen read as Minecraft:
 * a compass band at the top (cardinals, the hearth, the last grave, the locked foe, nearby
 * hostiles), our boss bars under it, framed vitals at the bottom (Aether or air left, health in
 * the middle, stamina or satiety right; they fill from the centre and fade when full and still),
 * a gilded hotbar with an XP medallion, the four ability slots in stance (the ultimate fills with
 * Unbound), status effects as gems top right, our crosshair, combo counter and the held item's
 * name. Art: tools/textures/paint_hud.py; type: Cinzel and Alegreya Sans.
 */
public final class VeyrathHud {
    private VeyrathHud() {}

    private static final Map<String, String> SLOT_LABELS = Map.ofEntries(
            Map.entry("gale_step", "GALE"), Map.entry("skybreaker", "SKY"), Map.entry("quake_palm", "QUAKE"),
            Map.entry("gs_rending_arc", "ARC"), Map.entry("gs_grave_drop", "DROP"), Map.entry("gs_guard_crush", "CRUSH"),
            Map.entry("tb_thousand_cuts", "CUTS"), Map.entry("tb_shadow_step", "STEP"), Map.entry("tb_talon_spiral", "TALON"),
            Map.entry("gl_vault_thrust", "VAULT"), Map.entry("gl_crescent_sweep", "ARC"), Map.entry("gl_piercing_gale", "GALE"),
            Map.entry("gr_lance", "LANCE"), Map.entry("gr_star_mine", "MINE"), Map.entry("gr_rift_pull", "PULL"));

    private static final Gauge HEALTH = new Gauge(true), LEFT = new Gauge(false), RIGHT = new Gauge(false);
    private static final Map<UUID, Gauge> BOSS = new HashMap<>();
    private static final Gauge LOCK = new Gauge(false);
    private static long lastNanos;
    private static long frame;
    private static float dt;
    private static int lastSlot = -1;
    private static long heldShownAt = -1000;
    private static String heldName = "";
    private static double shownCombo;
    private static float comboHold, comboPop;

    private static String introName = "", introEpithet = "";
    private static float introAge = 99;

    /** A boss woke: its name card, centred in the upper screen. */
    public static void bossIntro(String name, String epithet) {
        introName = name.toUpperCase(Locale.ROOT);
        introEpithet = epithet;
        introAge = 0;
    }

    public static void init() {
        // Vanilla pieces we draw ourselves: gone for good.
        for (var id : List.of(VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR,
                VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
                VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS,
                VanillaHudElements.BOSS_BAR, VanillaHudElements.CROSSHAIR)) {
            HudElementRegistry.replaceElement(id, vanilla -> (g, delta) -> {});
        }
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, VeyrathMod.id("hud"), VeyrathHud::extract);
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        long now = System.nanoTime();
        dt = lastNanos == 0 ? 0.016f : Math.clamp((now - lastNanos) / 1e9f, 0, 0.1f);
        lastNanos = now;
        frame++;
        Font font = mc.font;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        Fighter f = LocalCombat.active() ? LocalCombat.fighter() : null;
        float partial = delta.getGameTimeDeltaPartialTick(true);

        float below = compass(g, font, mc, p, w, partial);
        below = bossBars(g, font, mc, w, below);
        lockTarget(g, font, f, w, below);
        effects(g, font, p, w);
        if (p.isSpectator()) return;
        int rowTop = f != null ? h - 29 : h - 25;
        if (f != null) abilities(g, font, f, w, rowTop);
        else hotbar(g, font, mc, p, w, h, rowTop);
        vitals(g, font, p, f, w, rowTop - 17);
        heldName(g, font, p, f, w, rowTop - 31);
        if (f == null) crosshair(g, mc, w, h);
        combo(g, font, f, w, h);
        intro(g, font, w, h);
    }

    /** The boss name card: fades in, holds, fades out over five seconds. */
    private static void intro(GuiGraphicsExtractor g, Font font, int w, int h) {
        if (introAge > 5) return;
        introAge += dt;
        float a = introAge < 0.8f ? introAge / 0.8f : introAge > 4 ? 1 - (introAge - 4) : 1;
        if (a <= 0) return;
        float cy = h * 0.28f, cx = w / 2f;
        float spread = 70 + 60 * Math.min(1, introAge / 1.2f);
        // A soft dark band behind the text.
        for (int i = 0; i < 12; i++) {
            float k = i / 11f;
            int alpha = Math.round(110 * a * (1 - k));
            g.fill(0, (int) (cy - 16 - i * 2), w, (int) (cy - 14 - i * 2), alpha << 24);
            g.fill(0, (int) (cy + 22 + i * 2), w, (int) (cy + 24 + i * 2), alpha << 24);
        }
        g.fill(0, (int) cy - 16, w, (int) cy + 22, Math.round(110 * a) << 24);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy - 10);
        float s = 1.9f + 0.1f * Math.min(1, introAge / 4f);
        pose.scale(s, s);
        HudArt.centered(g, font, HudArt.TITLE, introName, 0, 0, HudArt.alpha(0xFFF2E2B8, a), true);
        pose.popMatrix();
        g.fill((int) (cx - spread), (int) cy + 11, (int) (cx - 8), (int) cy + 12, HudArt.alpha(0xFFE2C27A, a));
        g.fill((int) (cx + 8), (int) cy + 11, (int) (cx + spread), (int) cy + 12, HudArt.alpha(0xFFE2C27A, a));
        HudArt.icon(g, HudArt.Icon.WAYPOINT, cx, cy + 11.5f, 9, HudArt.alpha(0xFFE2C27A, a));
        HudArt.centered(g, font, HudArt.HEADING, introEpithet, cx, cy + 15, HudArt.alpha(0xFFC9B89A, a * 0.95f), true);
    }

    // ---------------------------------------------------------------- compass

    private static final float SPAN = 180, COMPASS_W = 236;
    private record Marker(float bearing, HudArt.Icon icon, int color, float size, double dist, boolean labelled) {}

    private static float compass(GuiGraphicsExtractor g, Font font, Minecraft mc, LocalPlayer p, int w, float partial) {
        float cx = w / 2f, top = 3;
        HudArt.Tex band = HudArt.COMPASS;
        float bx = cx - band.gw() / 2;
        HudArt.draw(g, band, bx, top, 0xFFFFFFFF);
        HudArt.draw(g, HudArt.CAP_L, bx + 22, top, 0xFFFFFFFF);
        HudArt.draw(g, HudArt.CAP_R, bx + band.gw() - 22 - HudArt.CAP_R.gw(), top, 0xFFFFFFFF);
        float heading = wrap(p.getViewYRot(partial) + 180);
        float half = COMPASS_W / 2 - 22, ppd = (COMPASS_W - 44) / SPAN, mid = top + 9;

        // Ticks every 15 degrees, cardinal and intercardinal names.
        for (int b = 0; b < 360; b += 15) {
            float dx = delta(b, heading) * ppd;
            if (Math.abs(dx) > half) continue;
            float a = edgeFade(dx, half);
            if (b % 90 == 0) {
                String name = switch (b) { case 0 -> "N"; case 90 -> "E"; case 180 -> "S"; default -> "W"; };
                int col = b == 0 ? 0xFFFFD98A : HudArt.PARCHMENT;
                HudArt.centered(g, font, HudArt.HEADING, name, cx + dx, mid - 4, HudArt.alpha(col, a), true);
            } else if (b % 45 == 0) {
                String name = switch (b) { case 45 -> "NE"; case 135 -> "SE"; case 225 -> "SW"; default -> "NW"; };
                HudArt.centered(g, font, HudArt.SMALL, name, cx + dx, mid - 2.5f, HudArt.alpha(HudArt.PARCHMENT_DIM, a * 0.9f), true);
            } else {
                g.fill((int) (cx + dx), (int) (mid - 1), (int) (cx + dx) + 1, (int) (mid + 2), HudArt.alpha(0xFFC8AE74, a * 0.55f));
            }
        }

        // Markers: hearth, grave, the locked foe, hostiles close by.
        List<Marker> marks = new ArrayList<>();
        Vec3 eye = p.getEyePosition(partial);
        var respawn = mc.level.getRespawnData();
        if (respawn != null && respawn.dimension() == mc.level.dimension()) {
            var pos = respawn.pos();
            marks.add(marker(eye, pos.getX() + 0.5, pos.getZ() + 0.5, HudArt.Icon.HEARTH, 0xFFFFB86A, 9, true));
        }
        p.getLastDeathLocation().ifPresent(gp -> {
            if (gp.dimension() == mc.level.dimension())
                marks.add(marker(eye, gp.pos().getX() + 0.5, gp.pos().getZ() + 0.5, HudArt.Icon.GRAVE, 0xFFC9CED8, 9, true));
        });
        Entity lock = LocalCombat.active() ? LocalCombat.lockTarget() : null;
        for (Entity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40),
                e -> e != p && e.isAlive() && (e instanceof Enemy || e == lock))) {
            double d = e.position().distanceTo(p.position());
            if (e == lock) marks.add(marker(eye, e.getX(), e.getZ(), HudArt.Icon.SWORDS, 0xFFFF5A4A, 10, true));
            else if (d < 32) marks.add(new Marker(bearingTo(eye, e.getX(), e.getZ()), HudArt.Icon.HOSTILE, 0xFFE0463C,
                    (float) (8 - Math.min(3, d / 10)), d, false));
        }
        marks.sort(Comparator.comparingDouble(m -> -m.dist));
        Marker centred = null;
        for (Marker m : marks) {
            if (m.dist < 3) continue;
            float dx = delta(m.bearing, heading) * ppd;
            boolean clamped = Math.abs(dx) > half - 12;
            if (clamped) {
                if (!m.labelled) continue;
                dx = Math.copySign(half - 12, dx);
            }
            float a = clamped ? 0.5f : Math.max(edgeFade(dx, half - 12), 0.3f);
            if (!m.labelled) a *= (float) Math.clamp(1.15 - m.dist / 32, 0.35, 1);
            HudArt.icon(g, m.icon, cx + dx, mid, m.size, HudArt.alpha(m.color, a));
            if (m.labelled && Math.abs(dx) < 6 && (centred == null || m.dist < centred.dist)) centred = m;
        }
        HudArt.draw(g, HudArt.POINTER, cx - HudArt.POINTER.gw() / 2, top - 1.5f, 0xFFFFFFFF);
        if (centred != null) {
            String s = String.format(Locale.ROOT, "%d m", Math.round(centred.dist));
            HudArt.centered(g, font, HudArt.SMALL, s, cx, top + 17, HudArt.alpha(HudArt.PARCHMENT, 0.9f), true);
            return top + 26;
        }
        return top + 21;
    }

    private static Marker marker(Vec3 eye, double x, double z, HudArt.Icon icon, int color, float size, boolean labelled) {
        double d = Math.hypot(x - eye.x, z - eye.z);
        return new Marker(bearingTo(eye, x, z), icon, color, size, d, labelled);
    }

    private static float bearingTo(Vec3 eye, double x, double z) {
        double yaw = Math.toDegrees(Math.atan2(-(x - eye.x), z - eye.z));
        return wrap((float) yaw + 180);
    }

    private static float wrap(float deg) {
        deg %= 360;
        return deg < 0 ? deg + 360 : deg;
    }

    private static float delta(float bearing, float heading) {
        float d = (bearing - heading + 540) % 360 - 180;
        return d;
    }

    private static float edgeFade(float dx, float half) {
        float t = Math.clamp((half - Math.abs(dx)) / 26f, 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- boss bars

    private static float bossBars(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, float y) {
        Map<UUID, LerpingBossEvent> events = ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).veyrath$events();
        BOSS.keySet().retainAll(events.keySet());
        long tick = mc.level.getGameTime();
        for (LerpingBossEvent e : events.values()) {
            Gauge gauge = BOSS.computeIfAbsent(e.getId(), k -> new Gauge(true));
            gauge.update(e.getProgress(), tick, dt);
            String name = e.getName().getString().toUpperCase(Locale.ROOT);
            HudArt.centered(g, font, HudArt.HEADING, name, w / 2f, y + 1, HudArt.PARCHMENT, true);
            gauge.draw(g, w / 2f - 80, y + 10, bossColor(e.getColor()), 1, 0);
            y += 28;
            if (y > mc.getWindow().getGuiScaledHeight() / 3f) break;
        }
        return y;
    }

    private static int bossColor(BossEvent.BossBarColor c) {
        return switch (c) {
            case PINK -> 0xFFE46AA8;
            case BLUE -> 0xFF4C8CFF;
            case GREEN -> 0xFF6CC24A;
            case YELLOW -> 0xFFE8C040;
            case PURPLE -> 0xFF9A5CE6;
            case WHITE -> 0xFFE8E8F0;
            default -> 0xFFC8282E;
        };
    }

    private static void lockTarget(GuiGraphicsExtractor g, Font font, Fighter f, int w, float y) {
        Entity e = f != null ? LocalCombat.lockTarget() : null;
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;
        if (le.getType() == dev.veyrath.mod.arena.ArenaEntities.DUELIST && !BOSS.isEmpty()) return;
        LOCK.update(le.getHealth() / Math.max(1, le.getMaxHealth()), le.level().getGameTime(), dt);
        String name = le.getDisplayName().getString().toUpperCase(Locale.ROOT);
        HudArt.centered(g, font, HudArt.LABEL, name, w / 2f, y + 1, 0xFFFF8A7A, true);
        LOCK.draw(g, w / 2f - 56, y + 10, HudArt.HP, 1, 0);
    }

    // ---------------------------------------------------------------- vitals

    private static void vitals(GuiGraphicsExtractor g, Font font, LocalPlayer p, Fighter f, int w, int y) {
        long tick = p.level().getGameTime();
        float hp = p.getHealth(), max = Math.max(1, p.getMaxHealth());
        HEALTH.update(hp / max, tick, dt);
        boolean fight = f != null;
        float leftValue, rightValue;
        int leftColor, rightColor;
        boolean leftWanted, rightWanted;
        if (fight) {
            leftValue = (float) (f.aether() / f.rules.aetherMax);
            rightValue = (float) (f.stamina() / f.rules.staminaMax);
            leftColor = HudArt.AETHER;
            rightColor = HudArt.STAMINA;
            leftWanted = rightWanted = true;
        } else {
            leftValue = p.getAirSupply() / (float) Math.max(1, p.getMaxAirSupply());
            rightValue = p.getFoodData().getFoodLevel() / 20f;
            leftColor = HudArt.AIR;
            rightColor = HudArt.FOOD;
            leftWanted = leftValue < 0.999f;
            rightWanted = rightValue < 0.999f || RIGHT.sinceChange(tick) < 60;
        }
        LEFT.update(leftValue, tick, dt);
        RIGHT.update(rightValue, tick, dt);
        boolean hpWanted = fight || hp < max - 0.01f || HEALTH.sinceChange(tick) < 80;
        float ha = HEALTH.visibility(hpWanted || leftWanted || rightWanted, dt);
        float la = LEFT.visibility(leftWanted, dt), ra = RIGHT.visibility(rightWanted, dt);

        float cx = w / 2f;
        boolean narrow = w < 410;
        float hx = cx - 80;
        float low = hp / max < 0.3f ? (float) (0.5 + 0.5 * Math.sin(frame * 0.18)) : 0;
        HEALTH.draw(g, hx, y, hp / max < 0.3f ? HudArt.HP_LOW : HudArt.HP, ha, low);
        float sy = narrow ? y - 13 : y + 1.5f;
        float lx = narrow ? cx - 114 : hx - 116, rx = narrow ? cx + 2 : hx + 164;
        LEFT.draw(g, lx, sy, leftColor, la, 0);
        RIGHT.draw(g, rx, sy, rightColor, ra, 0);
        if (!fight) {
            HudArt.icon(g, HudArt.Icon.AIR, lx - 5, sy + 5.5f, 8, HudArt.alpha(HudArt.AIR, la));
            HudArt.icon(g, HudArt.Icon.BREAD, rx + 117, sy + 5.5f, 8, HudArt.alpha(HudArt.FOOD, ra));
        }
        if (ha > 0.01f) {
            boolean numbers = fight || HEALTH.sinceChange(tick) < 80 || hp < max - 0.01f;
            if (numbers) {
                String s = String.format(Locale.ROOT, "%.0f / %.0f", Math.ceil(hp), max);
                HudArt.centered(g, font, HudArt.SMALL, s, cx, y + 4.2f, HudArt.alpha(HudArt.PARCHMENT, ha), true);
            }
            int armor = p.getArmorValue();
            if (armor > 0) {
                HudArt.icon(g, HudArt.Icon.SHIELD, hx + 10, y - 4.5f, 8, HudArt.alpha(0xFFC9CED8, ha));
                HudArt.text(g, font, HudArt.SMALL, String.valueOf(armor), hx + 15.5f, y - 7, HudArt.alpha(HudArt.PARCHMENT, ha), true);
            }
        }
        if (fight) {
            String weapon = dev.veyrath.combat.content.Weapons.of(f.set).name().toUpperCase(Locale.ROOT);
            HudArt.centered(g, font, HudArt.HEADING, weapon, cx, y - 10, HudArt.GOLD, true);
        }
    }

    // ---------------------------------------------------------------- hotbar and abilities

    private static void hotbar(GuiGraphicsExtractor g, Font font, Minecraft mc, LocalPlayer p, int w, int h, int top) {
        var inv = p.getInventory();
        int n = 9, size = 22, gap = 2;
        float total = n * size + (n - 1) * gap, x0 = w / 2f - total / 2;
        int sel = inv.getSelectedSlot();
        for (int i = 0; i < n; i++) {
            float x = x0 + i * (size + gap);
            float lift = i == sel ? 2 : 0;
            HudArt.draw(g, HudArt.SLOT, x, top - lift, i == sel ? 0xFFFFFFFF : 0xE0FFFFFF);
            item(g, font, mc, p, inv.getItem(i), x + 3, top + 3 - lift);
            if (i == sel) {
                HudArt.draw(g, HudArt.SLOT_SELECTED, x, top - lift, 0xFFFFFFFF);
                HudArt.icon(g, HudArt.Icon.WAYPOINT, x + size / 2f, top + size + 0.5f, 5, 0xFFE2C27A);
            }
        }
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            float x = x0 - size - 8;
            HudArt.draw(g, HudArt.SLOT, x, top, 0xE6FFFFFF);
            item(g, font, mc, p, off, x + 3, top + 3);
        }
        // Experience: a gilded hairline under the slots and the level on a medallion.
        float progress = Math.clamp(p.experienceProgress, 0, 1);
        int ly = h - 2;
        g.fill((int) x0 + 2, ly, (int) (x0 + total) - 2, ly + 1, 0x70000000);
        if (progress > 0) g.fill((int) x0 + 2, ly, (int) (x0 + 2 + (total - 4) * progress), ly + 1, 0xFFE2C27A);
        if (p.experienceLevel > 0) {
            float mx = x0 + total + 6;
            HudArt.draw(g, HudArt.MEDALLION, mx, top + 3, 0xFFFFFFFF);
            HudArt.centered(g, font, HudArt.LABEL, String.valueOf(p.experienceLevel), mx + 8, top + 7.5f, 0xFFB8F07A, true);
        }
        if (sel != lastSlot || !ItemStack.isSameItem(inv.getSelectedItem(), heldStack)) {
            lastSlot = sel;
            heldStack = inv.getSelectedItem().copy();
            heldName = heldStack.isEmpty() ? "" : heldStack.getHoverName().getString();
            heldShownAt = frame;
        }
    }

    private static ItemStack heldStack = ItemStack.EMPTY;

    private static void item(GuiGraphicsExtractor g, Font font, Minecraft mc, LocalPlayer p, ItemStack s, float x, float y) {
        if (s.isEmpty()) return;
        g.item(p, s, (int) x, (int) y, 0);
        if (s.isBarVisible()) {
            int bw = s.getBarWidth();
            g.fill((int) x + 2, (int) y + 14, (int) x + 15, (int) y + 15, 0xC0000000);
            g.fill((int) x + 2, (int) y + 14, (int) x + 2 + bw, (int) y + 15, 0xFF000000 | s.getBarColor());
        }
        if (s.getCount() > 1) {
            String c = String.valueOf(s.getCount());
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(0, 0);
            HudArt.text(g, font, HudArt.LABEL, c, x + 16.5f - HudArt.width(font, HudArt.LABEL, c), y + 7.5f, HudArt.PARCHMENT, true);
            pose.popMatrix();
        }
    }

    private static void abilities(GuiGraphicsExtractor g, Font font, Fighter f, int w, int top) {
        int size = 26, gap = 5;
        String[] ids = {f.set.abilities[0], f.set.abilities[1], f.set.abilities[2], f.set.ultimate};
        float total = 4 * size + 3 * gap, x0 = w / 2f - total / 2;
        double gauge = f.gauge() / f.rules.gaugeMax;
        for (int i = 0; i < 4; i++) {
            Move m = f.set.get(ids[i]);
            float x = x0 + i * (size + gap);
            boolean ult = i == 3;
            HudArt.draw(g, ult ? HudArt.SLOT_ULTIMATE : HudArt.SLOT_ABILITY, x, top, 0xF5FFFFFF);
            if (m == null) continue;
            boolean affordable = f.aether() >= m.aether - 1e-9 && f.gauge() >= m.gaugeCost - 1e-9;
            int cd = f.cooldown(m.id);
            boolean ready = cd == 0 && affordable;
            if (ult) {
                // Unbound rises in the ultimate's well; full, it glows.
                int fillH = (int) Math.round((size - 6) * Math.clamp(gauge, 0, 1));
                g.fill((int) x + 3, top + size - 3 - fillH, (int) x + size - 3, top + size - 3, gauge >= 1 ? 0x90FFB040 : 0x60FF7A20);
                if (gauge >= 1) {
                    float pulse = (float) (0.5 + 0.5 * Math.sin(frame * 0.12));
                    g.fill((int) x + 2, top + 2, (int) x + size - 2, top + size - 2, HudArt.rgb(0xFFC060, 0.10f + 0.12f * pulse));
                }
            }
            String label = ult ? "ULT" : SLOT_LABELS.getOrDefault(ids[i], "-");
            int col = ready ? (ult ? 0xFFFFE08A : HudArt.PARCHMENT) : 0xFF8A8478;
            float lw = HudArt.width(font, HudArt.LABEL, label);
            if (lw > size - 5) {
                var pose = g.pose();
                pose.pushMatrix();
                pose.translate(x + size / 2f, top + 10.5f);
                pose.scale((size - 5) / lw, (size - 5) / lw);
                HudArt.centered(g, font, HudArt.LABEL, label, 0, -1, col, true);
                pose.popMatrix();
            } else {
                HudArt.centered(g, font, HudArt.LABEL, label, x + size / 2f, top + 9.5f, col, true);
            }
            HudArt.text(g, font, HudArt.SMALL, String.valueOf(i + 1), x + 3.5f, top + 2.5f, HudArt.alpha(HudArt.GOLD, 0.85f), true);
            if (cd > 0) {
                int fillH = (int) Math.ceil((size - 4) * (double) cd / Math.max(1, m.cooldown));
                g.fill((int) x + 2, top + 2, (int) x + size - 2, top + 2 + fillH, 0x90000000);
                String secs = String.format(Locale.ROOT, "%.0f", Math.ceil(cd / 20.0));
                HudArt.centered(g, font, HudArt.LABEL, secs, x + size / 2f, top + 17, 0xFFFFFFFF, true);
            } else if (!affordable) {
                String cost = m.gaugeCost > 0 ? "MAX" : String.format(Locale.ROOT, "%.0f", m.aether);
                HudArt.centered(g, font, HudArt.SMALL, cost, x + size / 2f, top + 17, ult ? HudArt.GAUGE : HudArt.AETHER, true);
            }
        }
    }

    private static void heldName(GuiGraphicsExtractor g, Font font, LocalPlayer p, Fighter f, int w, int y) {
        if (f != null || heldName.isEmpty()) return;
        long age = frame - heldShownAt;
        float a = age < 90 ? 1 : 1 - (age - 90) / 30f;
        if (a <= 0) return;
        HudArt.centered(g, font, HudArt.HEADING, heldName, w / 2f, y - 2, HudArt.alpha(HudArt.PARCHMENT, a), true);
    }

    // ---------------------------------------------------------------- effects, crosshair, combo

    private static void effects(GuiGraphicsExtractor g, Font font, LocalPlayer p, int w) {
        var list = new ArrayList<>(p.getActiveEffects());
        if (list.isEmpty()) return;
        list.sort(Comparator.comparing((MobEffectInstance e) -> !e.getEffect().value().isBeneficial()).thenComparingInt(MobEffectInstance::getDuration));
        float y = 6;
        for (MobEffectInstance e : list) {
            if (!e.showIcon()) continue;
            var eff = e.getEffect().value();
            String name = eff.getDisplayName().getString();
            if (e.getAmplifier() > 0) name += " " + roman(e.getAmplifier() + 1);
            String time = e.isInfiniteDuration() ? "" : clock(e.getDuration());
            float tw = Math.max(HudArt.width(font, HudArt.LABEL, name), HudArt.width(font, HudArt.SMALL, time));
            float pw = tw + 26, x = w - 6 - pw;
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
            float a = ending ? (float) (0.6 + 0.4 * Math.sin(frame * 0.2)) : 1;
            HudArt.panel(g, x, y, pw, 22, HudArt.alpha(0xFFFFFFFF, 0.92f * a));
            HudArt.icon(g, HudArt.Icon.GEM, x + 11, y + 11, 11, HudArt.rgb(eff.getColor(), a));
            int nameCol = eff.isBeneficial() ? HudArt.PARCHMENT : 0xFFFF9A8A;
            HudArt.text(g, font, HudArt.LABEL, name, x + 19, y + 4, HudArt.alpha(nameCol, a), true);
            HudArt.text(g, font, HudArt.SMALL, time, x + 19, y + 13, HudArt.alpha(HudArt.PARCHMENT_DIM, a), true);
            y += 24;
        }
    }

    private static String clock(int ticks) {
        int s = ticks / 20;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static String roman(int n) {
        return switch (n) { case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    private static void crosshair(GuiGraphicsExtractor g, Minecraft mc, int w, int h) {
        if (!mc.options.getCameraType().isFirstPerson()) return;
        boolean onEntity = mc.crosshairPickEntity != null;
        HudArt.icon(g, HudArt.Icon.CROSSHAIR, w / 2f, h / 2f, onEntity ? 12 : 10, onEntity ? 0xF0FFC88A : 0xD8F4ECDA);
    }

    private static void combo(GuiGraphicsExtractor g, Font font, Fighter f, int w, int h) {
        int combo = f == null ? 0 : f.combo();
        if (combo >= 2) {
            if (combo != shownCombo) comboPop = 1;
            shownCombo = combo;
            comboHold = 1.5f;
        } else if (comboHold > 0) {
            comboHold -= dt;
        }
        comboPop = Math.max(0, comboPop - dt * 5);
        if (comboHold <= 0 || shownCombo < 2) return;
        float a = Math.clamp(comboHold / 0.4f, 0, 1);
        String n = String.valueOf((int) shownCombo);
        float cx = w - 64, cy = h / 2f - 34;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        float s = 2.2f + comboPop * 0.6f;
        pose.scale(s, s);
        HudArt.text(g, font, HudArt.TITLE, n, -HudArt.width(font, HudArt.TITLE, n), 0, HudArt.alpha(0xFFFFE08A, a), true);
        pose.popMatrix();
        HudArt.text(g, font, HudArt.HEADING, "HITS", cx + 4, cy + 12, HudArt.alpha(HudArt.PARCHMENT, a), true);
    }
}
