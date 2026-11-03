package dev.veyrath.mod.client.mixin;

import dev.veyrath.mod.client.combat.LocalCombat;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In combat stance the movement keys steer the fighter (camera relative) and Shift, Ctrl and Space
 * are combat inputs, so vanilla sees no movement input: no walking, sneaking, sprinting or jumping
 * of its own. The fighter sets the velocity directly.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void veyrath$stanceInput(CallbackInfo ci) {
        if (!LocalCombat.active()) return;
        this.keyPresses = Input.EMPTY;
        this.moveVector = Vec2.ZERO;
    }
}
