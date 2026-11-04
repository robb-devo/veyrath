package dev.veyrath.mod.client.mixin;

import dev.veyrath.mod.client.combat.CombatAnimations;
import dev.veyrath.mod.client.combat.FlashState;
import dev.veyrath.mod.client.combat.HitFlash;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Attaches the entity's combat pose (and reaction facing) to its render state, and swaps vanilla's
 * red hurt tint for the combat hit flash ({@link HitFlash}).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void veyrath$combatPose(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        CombatAnimations.extract(entity, state, partialTick);
        int id = entity.getId();
        ((FlashState) state).veyrath$setFlash(HitFlash.progress(id));
        ((FlashState) state).veyrath$setWeapon(dev.veyrath.mod.client.combat.ClientWeapons.drawn(entity));
        if (HitFlash.suppressRed(id)) state.hasRedOverlay = false;
    }

    @Inject(method = "getWhiteOverlayProgress(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)F",
            at = @At("RETURN"), cancellable = true)
    private void veyrath$hitFlash(LivingEntityRenderState state, CallbackInfoReturnable<Float> cir) {
        float flash = ((FlashState) state).veyrath$flash();
        if (flash > cir.getReturnValueF()) cir.setReturnValue(flash);
    }
}
