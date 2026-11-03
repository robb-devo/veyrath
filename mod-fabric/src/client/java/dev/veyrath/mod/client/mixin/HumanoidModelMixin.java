package dev.veyrath.mod.client.mixin;

import dev.veyrath.mod.client.combat.CombatAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the combat pose after vanilla posed a humanoid model (players, zombies, skeletons...). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void veyrath$combatPose(HumanoidRenderState state, CallbackInfo ci) {
        CombatAnimations.applyTo((HumanoidModel<?>) (Object) this, state);
    }
}
