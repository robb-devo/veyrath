package dev.veyrath.mod.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.veyrath.combat.content.Greatsword;
import dev.veyrath.mod.VeyrathMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws the drawn VEYRATH weapon in the right hand of players and humanoid fighters. The model
 * is built from boxes in the hand's space (after the posed right arm), with the blade along the
 * arm's forward axis, the same line the server's blade hit shape uses ({@link Greatsword}).
 * Textures are painted by {@code tools/textures/paint_weapon_textures.py}.
 */
public final class WeaponLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    static final Identifier OATHCLEAVER = VeyrathMod.id("textures/entity/weapon/oathcleaver.png");
    static final Identifier WREN_THORN = VeyrathMod.id("textures/entity/weapon/wren_thorn.png");
    static final Identifier SKYREACH = VeyrathMod.id("textures/entity/weapon/skyreach.png");
    static final Identifier VESPER_CODEX = VeyrathMod.id("textures/entity/weapon/vesper_codex.png");
    private static RenderType oathcleaverType, wrenThornType, skyreachType, codexType;

    public WeaponLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        String weapon = ((FlashState) state).veyrath$weapon();
        if (dev.veyrath.combat.content.TwinBlades.ID.equals(weapon)) {
            if (wrenThornType == null) wrenThornType = RenderTypes.entitySolid(WREN_THORN);
            for (boolean leftHand : new boolean[]{false, true}) {
                pose.pushPose();
                getParentModel().root().translateAndRotate(pose);
                (leftHand ? getParentModel().leftArm : getParentModel().rightArm).translateAndRotate(pose);
                final float hx = leftHand ? 1 : -1;
                collector.submitCustomGeometry(pose, wrenThornType, (p, vc) -> shortBlade(p, vc, light, hx));
                pose.popPose();
            }
            return;
        }
        if (dev.veyrath.combat.content.Grimoire.ID.equals(weapon)) {
            if (codexType == null) codexType = RenderTypes.entitySolid(VESPER_CODEX);
            pose.pushPose();
            getParentModel().root().translateAndRotate(pose);
            getParentModel().rightArm.translateAndRotate(pose);
            collector.submitCustomGeometry(pose, codexType, (p, vc) -> codex(p, vc, light));
            pose.popPose();
            return;
        }
        if (dev.veyrath.combat.content.Glaive.ID.equals(weapon)) {
            if (skyreachType == null) skyreachType = RenderTypes.entitySolid(SKYREACH);
            pose.pushPose();
            getParentModel().root().translateAndRotate(pose);
            getParentModel().rightArm.translateAndRotate(pose);
            collector.submitCustomGeometry(pose, skyreachType, (p, vc) -> skyreach(p, vc, light));
            pose.popPose();
            return;
        }
        if (!Greatsword.ID.equals(weapon)) return;
        if (oathcleaverType == null) oathcleaverType = RenderTypes.entitySolid(OATHCLEAVER);
        pose.pushPose();
        // Arms are flat children of the model root, which carries our root bone (turn, lean, offset).
        getParentModel().root().translateAndRotate(pose);
        getParentModel().rightArm.translateAndRotate(pose);
        collector.submitCustomGeometry(pose, oathcleaverType, (p, vc) -> oathcleaver(p, vc, light));
        pose.popPose();
    }

    /** Oathcleaver in hand space, pixels: grip around the hand, guard, a 30 px blade, pommel. */
    private static void oathcleaver(PoseStack.Pose p, VertexConsumer vc, int light) {
        float hx = -1, hy = 10.5f; // the hand, at the end of the arm
        // grip: from behind the fist to the guard
        box(p, vc, light, hx - 0.9f, hy - 0.9f, -4, hx + 0.9f, hy + 0.9f, 3.5f, 24, 0, 32, 8);
        // pommel
        box(p, vc, light, hx - 1.4f, hy - 1.4f, 3.5f, hx + 1.4f, hy + 1.4f, 5.5f, 16, 8, 24, 16);
        // guard: wide across the blade's flat
        box(p, vc, light, hx - 1.2f, hy - 4.5f, -5.5f, hx + 1.2f, hy + 4.5f, -4, 16, 0, 24, 8);
        // blade: broad, thin, along -z; fuller down its middle; a narrower tip
        box(p, vc, light, hx - 0.45f, hy - 2.4f, -31, hx + 0.45f, hy + 2.4f, -5.5f, 0, 0, 16, 16);
        box(p, vc, light, hx - 0.55f, hy - 0.8f, -29, hx + 0.55f, hy + 0.8f, -6, 0, 16, 16, 32);
        box(p, vc, light, hx - 0.35f, hy - 1.4f, -35, hx + 0.35f, hy + 1.4f, -31, 0, 0, 16, 16);
    }

    /**
     * Skyreach in the right hand's space. Unlike the swords it extends down the arm's own line
     * (+y, beyond the hand), so a forward arm thrusts the point forward: an ash shaft, a bronze
     * collar, a leaf head with a side crescent.
     */
    private static void skyreach(PoseStack.Pose p, VertexConsumer vc, int light) {
        float hx = -1, hz = 0;
        box(p, vc, light, hx - 0.6f, 6, hz - 0.6f, hx + 0.6f, 40, hz + 0.6f, 24, 0, 32, 8);       // shaft
        box(p, vc, light, hx - 0.8f, 4.5f, hz - 0.8f, hx + 0.8f, 6, hz + 0.8f, 16, 8, 24, 16);    // butt cap
        box(p, vc, light, hx - 0.9f, 40, hz - 0.9f, hx + 0.9f, 42, hz + 0.9f, 16, 0, 24, 8);      // collar
        box(p, vc, light, hx - 1.6f, 42, hz - 0.3f, hx + 1.6f, 56, hz + 0.3f, 0, 0, 16, 16);      // leaf head
        box(p, vc, light, hx - 0.5f, 43, hz - 0.35f, hx + 0.5f, 55, hz + 0.35f, 0, 16, 16, 32);
        box(p, vc, light, hx - 0.8f, 56, hz - 0.2f, hx + 0.8f, 60, hz + 0.2f, 0, 0, 16, 16);      // point
        box(p, vc, light, hx + 1.6f, 45, hz - 0.25f, hx + 4.2f, 49, hz + 0.25f, 0, 0, 16, 16);    // side crescent
    }

    /** The Vesper Codex held open in the right hand: two covers, the page block, a clasp and a gem. */
    private static void codex(PoseStack.Pose p, VertexConsumer vc, int light) {
        float hx = -1, hy = 11;
        box(p, vc, light, hx - 4.5f, hy, -4, hx - 0.2f, hy + 0.6f, 2, 24, 0, 32, 8);      // left cover
        box(p, vc, light, hx + 0.2f, hy, -4, hx + 4.5f, hy + 0.6f, 2, 24, 0, 32, 8);      // right cover
        box(p, vc, light, hx - 0.4f, hy - 0.2f, -4, hx + 0.4f, hy + 0.7f, 2, 24, 0, 32, 8); // spine
        box(p, vc, light, hx - 4.2f, hy - 1.2f, -3.6f, hx - 0.3f, hy, 1.6f, 0, 0, 16, 16); // pages, left
        box(p, vc, light, hx + 0.3f, hy - 1.2f, -3.6f, hx + 4.2f, hy, 1.6f, 0, 0, 16, 16); // pages, right
        box(p, vc, light, hx + 3.6f, hy - 0.2f, -1.6f, hx + 5.0f, hy + 0.9f, -0.4f, 16, 0, 24, 8); // clasp
        box(p, vc, light, hx - 0.6f, hy + 0.6f, -1.6f, hx + 0.6f, hy + 1.4f, -0.4f, 16, 8, 24, 16); // gem on the spine
    }

    /** One of the twin blades in its hand's space: short grip, small guard, a 15 px blade. */
    private static void shortBlade(PoseStack.Pose p, VertexConsumer vc, int light, float hx) {
        float hy = 10.5f;
        box(p, vc, light, hx - 0.7f, hy - 0.7f, -2.5f, hx + 0.7f, hy + 0.7f, 2.5f, 24, 0, 32, 8);
        box(p, vc, light, hx - 1.0f, hy - 1.0f, 2.5f, hx + 1.0f, hy + 1.0f, 3.6f, 16, 8, 24, 16);
        box(p, vc, light, hx - 0.8f, hy - 2.6f, -3.5f, hx + 0.8f, hy + 2.6f, -2.5f, 16, 0, 24, 8);
        box(p, vc, light, hx - 0.3f, hy - 1.3f, -16, hx + 0.3f, hy + 1.3f, -3.5f, 0, 0, 16, 16);
        box(p, vc, light, hx - 0.38f, hy - 0.4f, -15, hx + 0.38f, hy + 0.4f, -4, 0, 16, 16, 32);
        box(p, vc, light, hx - 0.2f, hy - 0.7f, -18.5f, hx + 0.2f, hy + 0.7f, -16, 0, 0, 16, 16);
    }

    /** A box in pixels with one texture region (pixels of the 32 x 32 texture) on every face. */
    private static void box(PoseStack.Pose p, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1,
                            int u0, int v0, int u1, int v1) {
        float s = 1 / 16f;
        x0 *= s; y0 *= s; z0 *= s; x1 *= s; y1 *= s; z1 *= s;
        float a = u0 / 32f, b = v0 / 32f, c = u1 / 32f, d = v1 / 32f;
        // -z, +z, -x, +x, -y, +y faces, counter-clockwise seen from outside
        quad(p, vc, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, a, b, c, d);
        quad(p, vc, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, a, b, c, d);
        quad(p, vc, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, a, b, c, d);
        quad(p, vc, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, a, b, c, d);
        quad(p, vc, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, a, b, c, d);
        quad(p, vc, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, a, b, c, d);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1) {
        vertex(p, vc, light, nx, ny, nz, ax, ay, az, u0, v1);
        vertex(p, vc, light, nx, ny, nz, bx, by, bz, u1, v1);
        vertex(p, vc, light, nx, ny, nz, cx, cy, cz, u1, v0);
        vertex(p, vc, light, nx, ny, nz, dx, dy, dz, u0, v0);
        // and the other winding, so the face shows whichever way the box was spelled
        vertex(p, vc, light, nx, ny, nz, dx, dy, dz, u0, v0);
        vertex(p, vc, light, nx, ny, nz, cx, cy, cz, u1, v0);
        vertex(p, vc, light, nx, ny, nz, bx, by, bz, u1, v1);
        vertex(p, vc, light, nx, ny, nz, ax, ay, az, u0, v1);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, int light, float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        vc.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }
}
