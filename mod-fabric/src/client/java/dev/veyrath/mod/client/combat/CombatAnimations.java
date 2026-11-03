package dev.veyrath.mod.client.combat;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Animators of other entities (mobs, other players) driven by the server's messages, and the
 * render-state hook that hands each entity's combat pose to its model.
 */
public final class CombatAnimations {
    private CombatAnimations() {}

    public static final RenderStateDataKey<PoseSample> POSE = RenderStateDataKey.create(() -> "veyrath:combat_pose");

    private static final Map<Integer, Animator> REMOTE = new HashMap<>();

    public static Animator remote(int entityId) {
        return REMOTE.computeIfAbsent(entityId, id -> new Animator());
    }

    public static Animator find(int entityId) { return REMOTE.get(entityId); }

    /** True while a hit reaction holds this entity's facing (toward its attacker). */
    public static boolean facingOverridden(int entityId) {
        Animator a = REMOTE.get(entityId);
        return a != null && a.faceOverride;
    }

    /** End of each client tick: advance clocks, drop animators of entities that are gone or idle. */
    public static void tick(ClientLevel level) {
        if (REMOTE.isEmpty()) return;
        REMOTE.entrySet().removeIf(e -> {
            Animator a = e.getValue();
            a.tick();
            return level.getEntity(e.getKey()) == null || a.idleTicks > 100;
        });
    }

    public static void clear() { REMOTE.clear(); }

    /** LivingEntityRenderer.extractRenderState tail: attach the combat pose and the reaction facing. */
    public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTick) {
        FabricRenderState data = (FabricRenderState) (Object) state;
        Minecraft mc = Minecraft.getInstance();
        boolean local = entity == mc.player && LocalCombat.active();
        Animator a;
        float face = Float.NaN;
        if (local) {
            a = LocalCombat.animator();
            face = LocalCombat.renderYaw(partialTick);
        } else {
            a = REMOTE.get(entity.getId());
            if (a != null && a.faceOverride) face = a.yaw;
        }
        // Fighters with a base pose (the local player, players in stance, the Duelist) step with
        // vanilla's walk cycle under their guard; plain mobs only show reactions.
        boolean walks = local || (a != null && a.hasBase());
        PoseSample s = a == null ? null : a.sample(partialTick, walks ? state.walkAnimationSpeed * 1.6 : 0);
        data.setData(POSE, s);
        if (!Float.isNaN(face) && (local || s != null)) {
            state.bodyRot = face;
            state.yRot = 0;
            state.xRot = 0;
        }
        if (local && state instanceof ArmedEntityRenderState armed) hideHeldItems(armed);
    }

    /** Combat stance is unarmed in Chapter 02: no held item, no item arm poses. */
    private static void hideHeldItems(ArmedEntityRenderState s) {
        s.rightHandItemState.clear();
        s.rightHandItemStack = ItemStack.EMPTY;
        s.leftHandItemState.clear();
        s.leftHandItemStack = ItemStack.EMPTY;
        s.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    /** HumanoidModel.setupAnim tail. */
    public static void applyTo(HumanoidModel<?> model, HumanoidRenderState state) {
        PoseSample s = ((FabricRenderState) (Object) state).getData(POSE);
        if (s != null) PoseApplier.apply(model, s);
    }
}
