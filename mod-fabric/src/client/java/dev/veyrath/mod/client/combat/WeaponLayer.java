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
    private static RenderType oathcleaverType;

    public WeaponLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        String weapon = ((FlashState) state).veyrath$weapon();
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
