package dev.veyrath.mod.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * VEYRATH's own effects engine. A fixed pool of particles simulated on the client and drawn as
 * our own textured quads (atlas {@code textures/vfx/atlas.png}, painted by
 * {@code tools/textures/paint_vfx_atlas.py}) through {@code submitCustomGeometry}. No Minecraft
 * particle is involved. Time follows the game's tick rate, so slow motion slows the effects, and
 * stops while the game is paused.
 *
 * <p>Shapes: camera-facing billboards, streaks stretched along their velocity, flat rings on the
 * ground plane and vertical arcs facing a yaw. Two blend groups: glow (emissive, full bright)
 * and smoke (translucent).
 */
public final class Vfx {
    private Vfx() {}

    public static final Identifier ATLAS = VeyrathMod.id("textures/vfx/atlas.png");
    static final int GRID = 4;
    static final int MAX = 6000;
    static final int LIGHT = 0xF000F0;

    /** Atlas cells, see paint_vfx_atlas.py. */
    public enum Sprite { GLOW, SPARK, STREAK, RING, WAVE, SHARD, SMOKE, FLARE, SLASH, DUST, WISP, CROSS }

    /** How a particle's quad is oriented. */
    public enum Shape { BILLBOARD, STREAK, GROUND, ARC }

    private static final Shape[] SHAPES = Shape.values();

    // ---- pool (structure of arrays)
    private static final double[] px = new double[MAX], py = new double[MAX], pz = new double[MAX];
    private static final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private static final float[] age = new float[MAX], life = new float[MAX];
    private static final float[] size0 = new float[MAX], size1 = new float[MAX], stretch = new float[MAX];
    private static final float[] rot = new float[MAX], spin = new float[MAX], drag = new float[MAX], gravity = new float[MAX];
    private static final int[] color0 = new int[MAX], color1 = new int[MAX];
    private static final byte[] sprite = new byte[MAX], shape = new byte[MAX];
    private static final boolean[] smoke = new boolean[MAX];
    private static final float[] fadeIn = new float[MAX];
    private static int count;
    private static long lastFrame;

    /**
     * Light-adding pipeline for glows, sparks and rings: Minecraft's emissive entity shader with
     * alpha-weighted additive blending (colour += src * alpha), no depth write, no culling, so
     * overlapping light brightens instead of muddying the colours behind it.
     */
    static final RenderPipeline ADDITIVE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(VeyrathMod.id("pipeline/vfx_additive"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build());

    /**
     * Smoke and dust: plain alpha blending without the cutout of Minecraft's entity shaders (which
     * tore soft edges), no depth write. Its colour is darkened on the CPU by the light at the
     * particle, so dust does not glow at night.
     */
    static final RenderPipeline SOFT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(VeyrathMod.id("pipeline/vfx_soft"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build());

    private static RenderType glowType, smokeType;

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(Vfx::submit);
        assignIrisPrograms();
    }

    /**
     * With Iris, a pipeline the shader pack does not know is not drawn at all. Tell Iris which of
     * its programs draws ours: glows as emissive entities (gbuffers_spidereyes, light-adding in
     * the packs we use), smoke as translucent particles. Reflective, so the mod runs without Iris.
     */
    private static void assignIrisPrograms() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) return;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object iris = api.getMethod("getInstance").invoke(null);
            var assign = api.getMethod("assignPipeline", RenderPipeline.class, program);
            assign.invoke(iris, ADDITIVE, program.getField("EMISSIVE_ENTITIES").get(null));
            assign.invoke(iris, SOFT, program.getField("PARTICLES_TRANSLUCENT").get(null));
            VeyrathMod.LOG.info("VEYRATH effects: pipelines assigned to Iris programs");
        } catch (ReflectiveOperationException | RuntimeException e) {
            VeyrathMod.LOG.warn("VEYRATH effects: could not assign pipelines to Iris ({}); effects may not show with shaders", e.toString());
        }
    }

    public static int count() { return count; }

    /** Frames that submitted geometry, quads drawn and particles spawned since start (tests and diagnostics). */
    public static long submittedFrames, quadsDrawn, spawned;

    public static void clear() { count = 0; }

    /** A particle builder; {@link P#spawn()} adds it (dropped silently when the pool is full). */
    public static P at(double x, double y, double z) { return new P(x, y, z); }

    public static P at(Vec3 v) { return new P(v.x, v.y, v.z); }

    public static final class P {
        final double x, y, z;
        float vx, vy, vz, life = 0.5f, s0 = 0.5f, s1 = 0.5f, stretch = 1, rot, spin, drag, gravity, fadeIn = 0.03f;
        int c0 = 0xFFFFFFFF, c1 = 0x00FFFFFF;
        Sprite sprite = Sprite.GLOW;
        Shape shape = Shape.BILLBOARD;
        boolean smoke;

        P(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

        public P vel(double x, double y, double z) { vx = (float) x; vy = (float) y; vz = (float) z; return this; }
        public P life(double seconds) { life = (float) Math.max(0.02, seconds); return this; }
        public P size(double from, double to) { s0 = (float) from; s1 = (float) to; return this; }
        public P size(double s) { return size(s, s); }
        /** ARGB at birth and at death (linear in between); alpha also fades in over {@link #fadeIn}. */
        public P color(int from, int to) { c0 = from; c1 = to; return this; }
        /** Fades from the colour to transparent. */
        public P color(int argb) { return color(argb, argb & 0x00FFFFFF); }
        public P sprite(Sprite s) { sprite = s; return this; }
        public P shape(Shape s) { shape = s; return this; }
        /** STREAK: extra length per block/second of speed. ARC: height relative to width. */
        public P stretch(double s) { stretch = (float) s; return this; }
        /** BILLBOARD: in-plane rotation; GROUND and ARC: yaw of the quad (radians). */
        public P rot(double r) { rot = (float) r; return this; }
        public P spin(double r) { spin = (float) r; return this; }
        /** Fraction of velocity lost per second. */
        public P drag(double d) { drag = (float) d; return this; }
        /** Blocks per second squared, downward. */
        public P gravity(double g) { gravity = (float) g; return this; }
        public P fadeIn(double s) { fadeIn = (float) s; return this; }
        public P smoke() { smoke = true; return this; }

        public void spawn() {
            if (count >= MAX) return;
            spawned++;
            int i = count++;
            px[i] = x; py[i] = y; pz[i] = z;
            Vfx.vx[i] = vx; Vfx.vy[i] = vy; Vfx.vz[i] = vz;
            age[i] = 0; Vfx.life[i] = life;
            size0[i] = s0; size1[i] = s1; Vfx.stretch[i] = stretch;
            Vfx.rot[i] = rot; Vfx.spin[i] = spin; Vfx.drag[i] = drag; Vfx.gravity[i] = gravity;
            color0[i] = c0; color1[i] = c1;
            Vfx.sprite[i] = (byte) sprite.ordinal(); Vfx.shape[i] = (byte) shape.ordinal();
            Vfx.smoke[i] = smoke; Vfx.fadeIn[i] = fadeIn;
        }
    }

    // ---- simulation

    static void update(double dt) {
        int i = 0;
        while (i < count) {
            age[i] += (float) dt;
            if (age[i] >= life[i]) {
                remove(i);
                continue;
            }
            float d = (float) Math.max(0, 1 - drag[i] * dt);
            vx[i] *= d; vy[i] *= d; vz[i] *= d;
            vy[i] -= (float) (gravity[i] * dt);
            px[i] += vx[i] * dt; py[i] += vy[i] * dt; pz[i] += vz[i] * dt;
            rot[i] += (float) (spin[i] * dt);
            i++;
        }
    }

    private static void remove(int i) {
        int j = --count;
        if (i == j) return;
        px[i] = px[j]; py[i] = py[j]; pz[i] = pz[j];
        vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j];
        age[i] = age[j]; life[i] = life[j];
        size0[i] = size0[j]; size1[i] = size1[j]; stretch[i] = stretch[j];
        rot[i] = rot[j]; spin[i] = spin[j]; drag[i] = drag[j]; gravity[i] = gravity[j];
        color0[i] = color0[j]; color1[i] = color1[j];
        sprite[i] = sprite[j]; shape[i] = shape[j]; smoke[i] = smoke[j]; fadeIn[i] = fadeIn[j];
    }

    // ---- rendering

    private static void submit(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (mc.level == null) {
            count = 0;
            return;
        }
        double scale = mc.isPaused() ? 0 : mc.level.tickRateManager().tickrate() / 20.0;
        update(dt * scale);
        if (count == 0) return;
        if (glowType == null) {
            glowType = RenderType.create("veyrath_vfx_glow", RenderSetup.builder(ADDITIVE).withTexture("Sampler0", ATLAS).useOverlay()
                    .createRenderSetup());
            smokeType = RenderType.create("veyrath_vfx_soft", RenderSetup.builder(SOFT).withTexture("Sampler0", ATLAS).useOverlay()
                    .sortOnUpload().createRenderSetup());
        }
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 c = cam.position();
        var l = cam.leftVector();
        var u = cam.upVector();
        var f = cam.forwardVector();
        // Right (not left) and up, so camera-facing quads wind counter-clockwise toward the camera.
        float[] axes = {-l.x(), -l.y(), -l.z(), u.x(), u.y(), u.z(), f.x(), f.y(), f.z()};
        for (int pass = 0; pass < 2; pass++) {
            final boolean wantSmoke = pass == 1;
            boolean any = false;
            for (int i = 0; i < count && !any; i++) any = smoke[i] == wantSmoke;
            if (!any) continue;
            submittedFrames++;
            ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), wantSmoke ? smokeType : glowType, (pose, vc) -> {
                for (int i = 0; i < count; i++) {
                    if (smoke[i] == wantSmoke) emit(pose, vc, i, c, axes);
                }
            });
        }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc, int i, Vec3 c, float[] cam) {
        float lx = cam[0], ly = cam[1], lz = cam[2], ux = cam[3], uy = cam[4], uz = cam[5], fx = cam[6], fy = cam[7], fz = cam[8];
        float t = age[i] / life[i];
        float s = size0[i] + (size1[i] - size0[i]) * t;
        int col = lerpColor(color0[i], color1[i], t);
        float a = ((col >>> 24) & 0xFF) / 255f;
        if (fadeIn[i] > 0 && age[i] < fadeIn[i]) a *= age[i] / fadeIn[i];
        int alpha = Math.round(a * 255);
        if (alpha <= 2 || s <= 0) return;
        int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
        if (smoke[i]) {
            float lit = light(px[i], py[i], pz[i]);
            r = Math.round(r * lit); g = Math.round(g * lit); b = Math.round(b * lit);
        }
        float x = (float) (px[i] - c.x), y = (float) (py[i] - c.y), z = (float) (pz[i] - c.z);
        // Quad half-axes: a (right) and b (up).
        float ax, ay, az, bx, by, bz;
        switch (SHAPES[shape[i]]) {
            case STREAK -> {
                float vlen = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
                if (vlen < 1e-4f) {
                    ax = lx * s; ay = ly * s; az = lz * s;
                } else {
                    float k = s * 0.5f + vlen * stretch[i] * 0.05f;
                    ax = vx[i] / vlen * k; ay = vy[i] / vlen * k; az = vz[i] / vlen * k;
                }
                // Width: perpendicular to the streak and the view direction.
                float wx = ay * fz - az * fy, wy = az * fx - ax * fz, wz = ax * fy - ay * fx;
                float wl = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
                if (wl < 1e-5f) {
                    bx = ux * s * 0.3f; by = uy * s * 0.3f; bz = uz * s * 0.3f;
                } else {
                    float k = s * 0.3f / wl;
                    bx = wx * k; by = wy * k; bz = wz * k;
                }
            }
            case GROUND -> {
                float cs = (float) Math.cos(rot[i]) * s, sn = (float) Math.sin(rot[i]) * s;
                ax = cs; ay = 0; az = sn;
                bx = -sn; by = 0; bz = cs;
            }
            case ARC -> {
                float cs = (float) Math.cos(rot[i]), sn = (float) Math.sin(rot[i]);
                ax = cs * s; ay = 0; az = sn * s;
                bx = 0; by = s * stretch[i]; bz = 0;
            }
            default -> {
                float cs = (float) Math.cos(rot[i]), sn = (float) Math.sin(rot[i]);
                ax = (lx * cs + ux * sn) * s; ay = (ly * cs + uy * sn) * s; az = (lz * cs + uz * sn) * s;
                bx = (ux * cs - lx * sn) * s; by = (uy * cs - ly * sn) * s; bz = (uz * cs - lz * sn) * s;
            }
        }
        quadsDrawn++;
        int cell = sprite[i];
        float u0 = (cell % GRID) / (float) GRID, v0 = (cell / GRID) / (float) GRID;
        float u1 = u0 + 1f / GRID, v1 = v0 + 1f / GRID;
        vertex(pose, vc, x - ax - bx, y - ay - by, z - az - bz, r, g, b, alpha, u0, v1);
        vertex(pose, vc, x + ax - bx, y + ay - by, z + az - bz, r, g, b, alpha, u1, v1);
        vertex(pose, vc, x + ax + bx, y + ay + by, z + az + bz, r, g, b, alpha, u1, v0);
        vertex(pose, vc, x - ax + bx, y - ay + by, z - az + bz, r, g, b, alpha, u0, v0);
        if (shape[i] != Shape.BILLBOARD.ordinal()) {
            // Streaks, rings and arcs are seen from either side: the back face too.
            vertex(pose, vc, x - ax + bx, y - ay + by, z - az + bz, r, g, b, alpha, u0, v0);
            vertex(pose, vc, x + ax + bx, y + ay + by, z + az + bz, r, g, b, alpha, u1, v0);
            vertex(pose, vc, x + ax - bx, y + ay - by, z + az - bz, r, g, b, alpha, u1, v1);
            vertex(pose, vc, x - ax - bx, y - ay - by, z - az - bz, r, g, b, alpha, u0, v1);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, int r, int g, int b, int a, float u, float v) {
        vc.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /** Brightness 0.15 to 1 at a point: block light, or sky light scaled by the time of day. */
    private static float light(double x, double y, double z) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 1;
        var pos = net.minecraft.core.BlockPos.containing(x, y, z);
        int block = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
        float day = 1 - level.getSkyDarken() / 15f;
        float l = Math.max(block / 15f, sky / 15f * day);
        return 0.15f + 0.85f * l;
    }

    static int lerpColor(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
        }
        return r;
    }
}
