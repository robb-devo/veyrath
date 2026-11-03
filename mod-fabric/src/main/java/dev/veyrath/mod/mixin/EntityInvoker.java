package dev.veyrath.mod.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Entity.markHurt() is protected in 26.3: it makes the tracker send the new velocity at once. */
@Mixin(Entity.class)
public interface EntityInvoker {
    @Invoker("markHurt")
    void veyrath$markHurt();
}
