package dev.veyrath.mod.client.mixin;

import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No vanilla toasts (advancements, recipes, tutorials, system notices): they are the most
 * Minecraft-looking thing on screen. VEYRATH will announce its own milestones in its own style.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void veyrath$noVanillaToasts(Toast toast, CallbackInfo ci) {
        ci.cancel();
    }
}
