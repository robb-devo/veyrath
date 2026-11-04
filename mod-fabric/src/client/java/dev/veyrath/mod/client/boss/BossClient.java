package dev.veyrath.mod.client.boss;

import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.boss.BossEntities;
import dev.veyrath.mod.boss.VarekEntity;
import dev.veyrath.mod.client.arena.FighterRenderer;
import dev.veyrath.mod.client.hud.VeyrathHud;
import dev.veyrath.mod.combat.CombatNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Client side of the bosses: Varek's model (the humanoid skeleton our poses drive, with his own
 * crest, pauldrons, tabard and cape on it) and texture, the name card when a boss wakes, and the
 * embers that pour off his blade once the brand burns.
 */
public final class BossClient {
    private BossClient() {}

    public static final ModelLayerLocation VAREK = new ModelLayerLocation(VeyrathMod.id("varek"), "main");

    public static void init() {
        ModelLayerRegistry.registerModelLayer(VAREK, BossClient::varekMesh);
        EntityRendererRegistry.register(BossEntities.VAREK, ctx -> new FighterRenderer<>(ctx, VAREK, VeyrathMod.id("textures/entity/varek.png")));
        ClientPlayNetworking.registerGlobalReceiver(CombatNet.BossIntroS2C.TYPE, (msg, ctx) -> VeyrathHud.bossIntro(msg.name(), msg.epithet()));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.level == null || mc.isPaused() || mc.player == null) return;
            for (var e : mc.level.getEntitiesOfClass(VarekEntity.class, mc.player.getBoundingBox().inflate(64), v -> v.isAlive() && v.shownPhase() >= 1)) {
                // Embers off the blade: right hand, a little ahead of him.
                double r = Math.toRadians(e.getYRot());
                double s = e.getScale();
                double hx = e.getX() - Math.cos(r) * 0.4 * s - Math.sin(r) * 0.5 * s, hz = e.getZ() - Math.sin(r) * 0.4 * s + Math.cos(r) * 0.5 * s;
                int n = e.shownPhase() >= 2 ? 3 : 1;
                for (int i = 0; i < n; i++) BossEffects.play(mc.level.getRandom(), "vk_ember", hx, e.getY() + e.getBbHeight() * 0.45, hz, e.getYRot());
            }
        });
    }

    /** The humanoid mesh (128x128 texels) plus Varek's armour pieces as children of its bones. */
    private static LayerDefinition varekMesh() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition root = mesh.getRoot();
        root.getChild("head").addOrReplaceChild("crest", CubeListBuilder.create().texOffs(64, 0).addBox(-0.5f, -12f, -5f, 1, 5, 10),
                PartPose.ZERO);
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("tabard", CubeListBuilder.create().texOffs(64, 16).addBox(-3.5f, 10f, -2.6f, 7, 9, 1), PartPose.ZERO);
        body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(64, 32).addBox(-5f, 0f, 2.2f, 10, 20, 1), PartPose.rotation(0.12f, 0, 0));
        root.getChild("right_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(96, 0).addBox(-4.5f, -3f, -3f, 6, 4, 6),
                PartPose.rotation(0, 0, 0.15f));
        root.getChild("left_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(96, 10).addBox(-1.5f, -3f, -3f, 6, 4, 6),
                PartPose.rotation(0, 0, -0.15f));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
