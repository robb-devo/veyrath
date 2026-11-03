package dev.veyrath.mod.client.mixin;

import dev.veyrath.mod.client.combat.ActionCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Combat stance camera: after vanilla placed the third-person camera, re-place it from the action
 * camera's own yaw and pitch around a pivot above the player, pulled back with vanilla's collision
 * check (getMaxZoom) so it never enters terrain.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow private Entity entity;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Shadow protected abstract void move(float zoom, float dy, float dx);

    @Shadow
    private float getMaxZoom(float maxZoom) {
        throw new AssertionError();
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void veyrath$actionCamera(float partialTick, CallbackInfo ci) {
        if (!this.detached || !ActionCamera.controls(this.entity)) return;
        ActionCamera.View v = ActionCamera.view(this.entity, partialTick);
        this.setRotation(v.yaw(), v.pitch());
        this.setPosition(v.pivot());
        this.move(-this.getMaxZoom(v.distance()), 0.0F, 0.0F);
    }
}
