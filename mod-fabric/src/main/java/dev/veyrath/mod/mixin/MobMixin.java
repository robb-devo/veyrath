package dev.veyrath.mod.mixin;

import dev.veyrath.mod.combat.CombatServer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A struck mob stops thinking (no pathing, no attacks) while it reacts or is held by hit-stop. */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void veyrath$stun(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!CombatServer.stunned(self)) return;
        self.setXxa(0);
        self.setYya(0);
        self.setZza(0);
        self.setJumping(false);
        ci.cancel();
    }
}
