package dev.veyrath.mod.client.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import dev.veyrath.mod.client.combat.FlashState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements FlashState {
    @Unique
    private float veyrath$flash;

    @Override
    public float veyrath$flash() { return veyrath$flash; }

    @Override
    public void veyrath$setFlash(float flash) { veyrath$flash = flash; }
}
