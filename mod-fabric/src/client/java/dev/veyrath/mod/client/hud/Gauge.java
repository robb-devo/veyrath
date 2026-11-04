package dev.veyrath.mod.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One framed bar: smooth fill, a pale "ghost" of recent loss that drains after a beat, a flash when
 * it changes, and a fade-out when it has been full and still for a while (outside a fight).
 */
final class Gauge {
    final boolean large;
    private float shown = -1, ghost = -1, last = -1;
    private long lastChange = -1000, ghostHold;
    private float visible;

    Gauge(boolean large) { this.large = large; }

    /** Advances the animation (once per frame) towards value in 0..1. */
    void update(float value, long now, float dt) {
        value = Math.clamp(value, 0, 1);
        if (shown < 0) shown = ghost = last = value;
        if (Math.abs(value - last) > 1e-4f) {
            if (value < last) ghostHold = now + 10;
            lastChange = now;
            last = value;
        }
        float k = 1 - (float) Math.exp(-dt * 14);
        shown += (value - shown) * k;
        if (ghost < shown) ghost = shown;
        else if (now >= ghostHold) ghost = Math.max(shown, ghost - dt * 0.35f);
    }

    /** Fades to visible when wanted, else out over half a second. */
    float visibility(boolean want, float dt) {
        visible = Math.clamp(visible + (want ? dt * 5 : -dt * 2), 0, 1);
        return visible;
    }

    long sinceChange(long now) { return now - lastChange; }

    float value() { return last; }

    /** Draws the frame at (x, y) with a fill tinted color; alpha scales everything. */
    void draw(GuiGraphicsExtractor g, float x, float y, int color, float alpha, float pulse) {
        if (alpha <= 0.01f) return;
        HudArt.Tex frame = large ? HudArt.FRAME_L : HudArt.FRAME_S;
        HudArt.Tex fill = large ? HudArt.FILL_L : HudArt.FILL_S;
        float ix = x + 6, iy = y + (large ? 4 : 3);
        HudArt.draw(g, frame, x, y, HudArt.alpha(0xFFFFFFFF, alpha));
        // Bars fill from the centre outwards, like the classic open-world vitals.
        int full = fill.w();
        int ghostW = Math.round(full * ghost), shownW = Math.round(full * shown);
        if (ghostW > shownW) {
            int u = (full - ghostW) / 2;
            HudArt.region(g, fill, ix + u / (float) HudArt.S, iy, u, 0, ghostW, fill.h(), HudArt.alpha(HudArt.HP_GHOST, alpha * 0.75f));
        }
        int c = pulse > 0 ? HudArt.mix(color, 0xFFFFFFFF, pulse * 0.35f) : color;
        int u = (full - shownW) / 2;
        HudArt.region(g, fill, ix + u / (float) HudArt.S, iy, u, 0, shownW, fill.h(), HudArt.alpha(c, alpha));
    }
}
