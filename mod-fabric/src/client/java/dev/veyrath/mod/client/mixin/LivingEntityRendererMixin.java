package dev.veyrath.mod.client.mixin;

import dev.veyrath.mod.client.combat.CombatAnimations;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches the entity's combat pose (and reaction facing) to its render state. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void veyrath$combatPose(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        CombatAnimations.extract(entity, state, partialTick);
    }
}
