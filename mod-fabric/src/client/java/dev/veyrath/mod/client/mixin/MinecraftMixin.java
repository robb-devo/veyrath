package dev.veyrath.mod.client.mixin;

import dev.veyrath.mod.client.combat.LocalCombat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In combat stance the mouse buttons are light and heavy attacks: no vanilla attack swing, block
 * breaking, item use or block picking.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void veyrath$noVanillaAttack(CallbackInfoReturnable<Boolean> cir) {
        if (LocalCombat.active()) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void veyrath$noMining(boolean leftClick, CallbackInfo ci) {
        if (leftClick && LocalCombat.active()) ci.cancel();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void veyrath$noItemUse(CallbackInfo ci) {
        if (LocalCombat.active()) ci.cancel();
    }

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void veyrath$noPick(CallbackInfo ci) {
        if (LocalCombat.active()) ci.cancel();
    }
}
