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
 * check (getMaxZoom) so it never enters terrain. When terrain behind the player would pull it in
 * close, it cranes up over the obstacle ({@link ActionCamera#settle}).
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
        var dragon = dev.veyrath.mod.client.dragon.DragonClient.mount(this.entity);
        if (this.detached && dragon != null) {
            // Riding the dragon: far behind and above her, lagging her turns, kept out of terrain.
            var v = dev.veyrath.mod.client.dragon.DragonClient.view(this.entity, dragon, partialTick);
            this.setRotation(v.yaw(), v.pitch());
            this.setPosition(v.pivot());
            this.move(-this.getMaxZoom(v.distance()), 0.0F, 0.0F);
            return;
        }
        if (!this.detached || !ActionCamera.controls(this.entity)) return;
        ActionCamera.View v = ActionCamera.view(this.entity, partialTick);
        // Probe how far back the camera gets as it is and craned up, then let the camera choose.
        this.setRotation(v.yaw(), v.pitch());
        this.setPosition(v.pivot());
        float plain = this.getMaxZoom(v.distance());
        ActionCamera.View up = v.craned();
        this.setRotation(up.yaw(), up.pitch());
        this.setPosition(up.pivot());
        float craned = this.getMaxZoom(up.distance());
        ActionCamera.View f = ActionCamera.settle(v, plain / v.distance(), craned / up.distance());
        this.setRotation(f.yaw(), f.pitch());
        this.setPosition(f.pivot());
        this.move(-this.getMaxZoom(f.distance()), 0.0F, 0.0F);
    }
}
