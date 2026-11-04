package dev.veyrath.mod.client.hud;

import dev.veyrath.mod.VeyrathMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * HUD art and type. Textures come from tools/textures/paint_hud.py at {@link #S} texture pixels per
 * GUI unit with linear filtering, so they are drawn at 1/S scale and stay smooth at every GUI
 * scale. Type is ours too: Cinzel (titles, compass) and Alegreya Sans (numbers, labels), both OFL.
 */
public final class HudArt {
    private HudArt() {}

    public static final int S = 4;

    public record Tex(Identifier id, int w, int h) {
        /** GUI size. */
        public float gw() { return (float) w / S; }
        public float gh() { return (float) h / S; }
    }

    private static Tex tex(String name, int gw, int gh) {
        return new Tex(VeyrathMod.id("textures/gui/hud/" + name + ".png"), gw * S, gh * S);
    }

    public static final Tex COMPASS = tex("compass_band", 256, 18);
    public static final Tex CAP_L = tex("compass_cap_l", 18, 18), CAP_R = tex("compass_cap_r", 18, 18);
    public static final Tex POINTER = tex("compass_pointer", 10, 7);
    public static final Tex FRAME_L = tex("bar_frame_l", 160, 14), FRAME_S = tex("bar_frame_s", 112, 11);
    public static final Tex FILL_L = tex("bar_fill_l", 148, 6), FILL_S = tex("bar_fill_s", 100, 5);
    public static final Tex SLOT = tex("slot", 22, 22), SLOT_SELECTED = tex("slot_selected", 22, 22);
    public static final Tex SLOT_ABILITY = tex("slot_ability", 26, 26), SLOT_ULTIMATE = tex("slot_ultimate", 26, 26);
    public static final Tex PANEL = tex("panel", 24, 24);
    public static final Tex MEDALLION = tex("medallion", 16, 16);
    public static final Tex ICONS = tex("icons", 64, 48);

    public enum Icon { WAYPOINT, HOSTILE, SWORDS, HEARTH, GRAVE, SHIELD, GEM, CROSSHAIR, BREAD, AIR, STAR, EYE }

    /** Draws a whole texture at GUI position (x, y), tinted (ARGB). */
    public static void draw(GuiGraphicsExtractor g, Tex t, float x, float y, int argb) {
        region(g, t, x, y, 0, 0, t.w, t.h, argb);
    }

    /** Draws a texture region (in texture pixels) at GUI position (x, y), 1/S scale. */
    public static void region(GuiGraphicsExtractor g, Tex t, float x, float y, int u, int v, int uw, int vh, int argb) {
        if (uw <= 0 || vh <= 0 || (argb >>> 24) == 0) return;
        var p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(1f / S, 1f / S);
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, 0, 0, u, v, uw, vh, t.w, t.h, argb);
        p.popMatrix();
    }

    /** Draws a texture scaled to a GUI size. */
    public static void sized(GuiGraphicsExtractor g, Tex t, float x, float y, float gw, float gh, int argb) {
        if ((argb >>> 24) == 0) return;
        var p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(gw / t.w, gh / t.h);
        g.blit(RenderPipelines.GUI_TEXTURED, t.id, 0, 0, 0, 0, t.w, t.h, t.w, t.h, argb);
        p.popMatrix();
    }

    /** An icon, centred on (cx, cy), at a GUI size. */
    public static void icon(GuiGraphicsExtractor g, Icon icon, float cx, float cy, float size, int argb) {
        if ((argb >>> 24) == 0) return;
        int cell = 16 * S, i = icon.ordinal();
        var p = g.pose();
        p.pushMatrix();
        p.translate(cx - size / 2, cy - size / 2);
        p.scale(size / cell, size / cell);
        g.blit(RenderPipelines.GUI_TEXTURED, ICONS.id, 0, 0, (i % 4) * cell, (i / 4) * cell, cell, cell, ICONS.w, ICONS.h, argb);
        p.popMatrix();
    }

    /** A nine-slice panel from PANEL (6 GUI border). */
    public static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, int argb) {
        int b = 6 * S, mid = PANEL.w - 2 * b;
        float gb = 6;
        float[] xs = {x, x + gb, x + w - gb}, ws = {gb, w - 2 * gb, gb};
        float[] ys = {y, y + gb, y + h - gb}, hs = {gb, h - 2 * gb, gb};
        int[] us = {0, b, b + mid}, uws = {b, mid, b};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (ws[i] <= 0 || hs[j] <= 0) continue;
                var p = g.pose();
                p.pushMatrix();
                p.translate(xs[i], ys[j]);
                p.scale(ws[i] / uws[i], hs[j] / uws[j]);
                g.blit(RenderPipelines.GUI_TEXTURED, PANEL.id, 0, 0, us[i], us[j], uws[i], uws[j], PANEL.w, PANEL.h, argb);
                p.popMatrix();
            }
        }
    }

    // ---------------------------------------------------------------- type

    public static final FontDescription TITLE = new FontDescription.Resource(VeyrathMod.id("title"));
    public static final FontDescription HEADING = new FontDescription.Resource(VeyrathMod.id("heading"));
    public static final FontDescription LABEL = new FontDescription.Resource(VeyrathMod.id("label"));
    public static final FontDescription SMALL = new FontDescription.Resource(VeyrathMod.id("small"));

    public static Component text(FontDescription font, String s) {
        return Component.literal(s).withStyle(st -> st.withFont(font));
    }

    public static int width(Font f, FontDescription font, String s) {
        return f.width(text(font, s));
    }

    public static void text(GuiGraphicsExtractor g, Font f, FontDescription font, String s, float x, float y, int argb, boolean shadow) {
        if ((argb >>> 24) < 4) return;
        var p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        g.text(f, text(font, s), 0, 0, argb, shadow);
        p.popMatrix();
    }

    public static void centered(GuiGraphicsExtractor g, Font f, FontDescription font, String s, float cx, float y, int argb, boolean shadow) {
        text(g, f, font, s, cx - width(f, font, s) / 2f, y, argb, shadow);
    }

    // ---------------------------------------------------------------- colour

    public static int alpha(int argb, float a) {
        int base = argb >>> 24;
        return (Math.round(base * Math.clamp(a, 0, 1)) << 24) | (argb & 0xFFFFFF);
    }

    public static int rgb(int rgb, float a) {
        return (Math.round(255 * Math.clamp(a, 0, 1)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.clamp(t, 0, 1);
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 0xFF, cb = (b >>> s) & 0xFF;
            r |= Math.round(ca + (cb - ca) * t) << s;
        }
        return r;
    }

    public static final int PARCHMENT = 0xFFEDE3C6, PARCHMENT_DIM = 0xFFB9AE92, GOLD = 0xFFE2C27A;
    public static final int HP = 0xFFD2363A, HP_LOW = 0xFFFF4636, HP_GHOST = 0xFFF5D7B0;
    public static final int STAMINA = 0xFF9CCB52, AETHER = 0xFF4CA8FF, GAUGE = 0xFFFF8A2E, FOOD = 0xFFD7A24E, AIR = 0xFF8FDDF2;
}
