package dev.veyrath.mod.client.mixin;

import dev.veyrath.combat.math.Vec3;
import dev.veyrath.mod.client.combat.CombatEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A dying body leaves VEYRATH's own dissolve (rising light and ash), not vanilla's smoke puffs. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {
    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void veyrath$dissolve(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) return;
        ci.cancel();
        var c = self.getBoundingBox().getCenter();
        CombatEffects.play(self.level().getRandom(), "death", new Vec3(c.x, c.y, c.z), self.getYRot());
    }
}
