package dev.veyrath.mod.client.dragon;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.dragon.DragonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws Vaelith: her generated mesh, skinned on the CPU each frame for the pose the animator
 * gives, through a lit, textured entity render type (so the world's light, and shader packs, light
 * her like any creature). Triangles go out as quads with a repeated last corner.
 */
public class DragonRenderer extends EntityRenderer<DragonEntity, DragonRenderer.State> {
    static final Identifier MESH = VeyrathMod.id("models/entity/vaelith.vmesh");
    static final Identifier TEXTURE = VeyrathMod.id("textures/entity/vaelith.png");
    private static SkinnedMesh mesh;
    private static boolean failed;

    public static final class State extends EntityRenderState {
        public float yaw;
        public int id;
        public float partial;
        public float lookYaw, lookPitch;
        public DragonEntity entity;
    }

    public DragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 2.6f;
    }

    static SkinnedMesh mesh() {
        if (mesh == null && !failed) {
            try (var in = Minecraft.getInstance().getResourceManager().open(MESH)) {
                mesh = SkinnedMesh.read(in);
                VeyrathMod.LOG.info("Vaelith mesh: {} bones, {} vertices, {} triangles", mesh.boneCount(), mesh.vertices, mesh.tris.length / 3);
            } catch (Exception e) {
                failed = true;
                VeyrathMod.LOG.error("Could not load the dragon mesh", e);
            }
        }
        return mesh;
    }

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(DragonEntity e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        s.yaw = Mth.rotLerp(partialTick, e.yRotO, e.getYRot());
        s.id = e.getId();
        s.partial = partialTick;
        s.lookYaw = Mth.rotLerp(partialTick, e.yHeadRotO, e.getYHeadRot());
        s.lookPitch = e.getXRot(partialTick);
        s.entity = e;
    }

    @Override
    protected AABB getBoundingBoxForCulling(DragonEntity e, float partialTick) {
        // Wings and tail reach far beyond the body box.
        return e.getBoundingBox().inflate(10, 4, 10);
    }

    @Override
    public void submit(State s, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        SkinnedMesh m = mesh();
        if (m == null) return;
        if (s.entity == null) return;
        SkinnedMesh.Pose pose = DragonAnimator.pose(m, s.entity, s.ageInTicks, s.yaw, s.lookYaw, s.lookPitch);
        m.skin(pose);
        poses.pushPose();
        poses.mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(-s.yaw)));
        int light = s.lightCoords;
        // The geometry may be drawn after the next dragon is skinned: keep this frame's copy.
        final float[] p = m.outPos.clone(), n = m.outNrm.clone();
        final int[] t = m.tris;
        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(TEXTURE), (pose2, vc) -> {
            for (int i = 0; i < t.length; i += 3) {
                int a = t[i], b = t[i + 1], c = t[i + 2];
                vert(vc, pose2, p, n, m, a, light);
                vert(vc, pose2, p, n, m, b, light);
                vert(vc, pose2, p, n, m, c, light);
                vert(vc, pose2, p, n, m, c, light);
            }
        });
        // Her eyes glow storm-blue in any light: drawn again, full bright and light-adding.
        final int[] eyes = eyeTriangles(m);
        if (eyes.length > 0) {
            collector.submitCustomGeometry(poses, RenderTypes.eyes(TEXTURE), (pose2, vc) -> {
                for (int i = 0; i < eyes.length; i += 3) {
                    int a = eyes[i], b = eyes[i + 1], c = eyes[i + 2];
                    vert(vc, pose2, p, n, m, a, 0xF000F0);
                    vert(vc, pose2, p, n, m, b, 0xF000F0);
                    vert(vc, pose2, p, n, m, c, 0xF000F0);
                    vert(vc, pose2, p, n, m, c, 0xF000F0);
                }
            });
        }
        poses.popPose();
        super.submit(s, poses, collector, camera);
    }

    private static int[] eyeTris;

    /** Triangles painted from the eye region of the texture atlas (see build_dragon.py UVR "eye"). */
    private static int[] eyeTriangles(SkinnedMesh m) {
        if (eyeTris != null) return eyeTris;
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < m.tris.length; i += 3) {
            boolean all = true;
            for (int k = 0; k < 3 && all; k++) {
                int v = m.tris[i + k];
                float u = m.u(v), vv = m.v(v);
                all = u >= 0.375f && u < 0.5f && vv >= 0.875f && vv <= 1f;
            }
            if (all) for (int k = 0; k < 3; k++) out.add(m.tris[i + k]);
        }
        eyeTris = out.stream().mapToInt(Integer::intValue).toArray();
        return eyeTris;
    }

    private static void vert(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, float[] p, float[] n, SkinnedMesh m, int v, int light) {
        vc.addVertex(pose, p[v * 3], p[v * 3 + 1], p[v * 3 + 2]).setColor(0xFFFFFFFF).setUv(m.u(v), m.v(v))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[v * 3], n[v * 3 + 1], n[v * 3 + 2]);
    }
}
