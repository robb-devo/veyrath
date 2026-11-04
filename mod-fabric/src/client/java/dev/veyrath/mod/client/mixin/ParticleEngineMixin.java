package dev.veyrath.mod.client.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla particles that our own effects engine replaces never spawn: the potion swirl around
 * anyone under an effect (VEYRATH shows effects on its HUD) and the crit and sweep sparks of
 * vanilla attacks (our combat draws its own impacts).
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void veyrath$noReplacedVanillaParticles(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz,
                                                   CallbackInfoReturnable<Particle> cir) {
        var t = options.getType();
        if (t == ParticleTypes.ENTITY_EFFECT || t == ParticleTypes.CRIT || t == ParticleTypes.ENCHANTED_HIT
                || t == ParticleTypes.SWEEP_ATTACK || t == ParticleTypes.DAMAGE_INDICATOR) cir.setReturnValue(null);
    }
}
