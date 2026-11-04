package dev.veyrath.mod.client.dragon;

import dev.veyrath.mod.dragon.DragonEntities;
import dev.veyrath.mod.dragon.DragonEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the dragon: her renderer, the rider's keys for her flight, and the flight camera.
 * Mounting switches to the third-person camera, which then sits far behind and above her, lags
 * her turns, rolls a little with her bank and pulls back with speed; dismounting restores the view.
 */
public final class DragonClient {
    private DragonClient() {}

    private static CameraType before;
    private static boolean riding;
    private static float camYaw, camPitch, camDist = 13, roll;
    private static long lastTick;

    public static void init() {
        EntityRendererRegistry.register(DragonEntities.VAELITH, DragonRenderer::new);
        DragonEntity.clientKeys = () -> {
            var o = Minecraft.getInstance().options;
            return new boolean[]{o.keyJump.isDown(), o.keySprint.isDown()};
        };
        ClientTickEvents.END_CLIENT_TICK.register(DragonClient::tick);
    }

    private static void tick(Minecraft mc) {
        var p = mc.player;
        boolean now = p != null && p.getVehicle() instanceof DragonEntity;
        if (now && !riding) {
            before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            camYaw = p.getYRot();
            camPitch = 15;
        } else if (!now && riding) {
            if (before != null) mc.options.setCameraType(before);
        }
        riding = now;
    }

    /** The dragon the camera entity rides, or null. */
    public static DragonEntity mount(Entity e) {
        return e != null && e.getVehicle() instanceof DragonEntity d ? d : null;
    }

    /** Camera placement while riding: yaw, pitch, pivot, distance, roll. */
    public record View(float yaw, float pitch, Vec3 pivot, float distance, float roll) {}

    public static View view(Entity rider, DragonEntity d, float partial) {
        float wantYaw = rider.getViewYRot(partial);
        float wantPitch = Mth.clamp(rider.getViewXRot(partial) + 12, -40, 70);
        // Lag behind the look so turns read; tighter at speed.
        camYaw += Mth.wrapDegrees(wantYaw - camYaw) * 0.25f;
        camPitch += (wantPitch - camPitch) * 0.2f;
        double speed = d.getDeltaMovement().length();
        float wantDist = (float) (13 + speed * 7);
        camDist += (wantDist - camDist) * 0.05f;
        float bank = d.flying() ? Mth.clamp(Mth.wrapDegrees(wantYaw - d.getYRot()) * 0.25f, -12, 12) : 0;
        roll += (bank - roll) * 0.08f;
        Vec3 pivot = new Vec3(Mth.lerp(partial, d.xo, d.getX()), Mth.lerp(partial, d.yo, d.getY()) + 5.2, Mth.lerp(partial, d.zo, d.getZ()));
        return new View(camYaw, camPitch, pivot, camDist, roll);
    }
}
