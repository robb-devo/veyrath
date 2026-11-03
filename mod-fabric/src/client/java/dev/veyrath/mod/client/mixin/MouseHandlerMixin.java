package dev.veyrath.mod.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.veyrath.mod.client.combat.ActionCamera;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** In combat stance the mouse orbits the action camera instead of turning the player. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void veyrath$orbitCamera(LocalPlayer player, double dx, double dy, Operation<Void> original) {
        if (ActionCamera.turn(dx, dy)) return;
        original.call(player, dx, dy);
    }
}
