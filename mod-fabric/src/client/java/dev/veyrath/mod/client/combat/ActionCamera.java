package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.math.Angles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Third-person orbit camera of combat stance. The mouse turns the camera, not the character; the
 * character faces where it moves or attacks (the fighter decides). Lock-on swings the camera
 * toward the target while the mouse rests. Shake is trauma based: impacts add trauma, it decays,
 * and the offset grows with its square, so light hits barely move the view and heavy ones jolt it.
 * Vanilla's own collision zoom keeps the camera out of walls.
 */
public final class ActionCamera {
    private ActionCamera() {}

    public static final float DISTANCE = 4.2f;
    /** Pivot above the feet: a little over eye height, so the target in front stays in view. */
    public static final double PIVOT_HEIGHT = 1.7;
    static final float PITCH_MIN = -55, PITCH_MAX = 70;
    /** Lock-on: degrees per second the camera swings toward the target while the mouse rests. */
    static final double LOCK_SWING = 160;

    private static float yaw, pitch = 12;
    private static double trauma;
    private static long lastFrame, lastMouse;
    private static float shakeTime;

    public record View(float yaw, float pitch, Vec3 pivot, float distance) {}

    static void enter(Entity player) {
        yaw = player.getYRot();
        pitch = Mth.clamp(player.getXRot(), PITCH_MIN, PITCH_MAX);
        trauma = 0;
        lastFrame = 0;
    }

    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    /** Mouse look (from MouseHandler.turnPlayer). False when the stance is off: vanilla turns the player. */
    public static boolean turn(double dx, double dy) {
        if (!LocalCombat.active()) return false;
        yaw += (float) dx * 0.15f;
        pitch = Mth.clamp(pitch + (float) dy * 0.15f, PITCH_MIN, PITCH_MAX);
        if (Math.abs(dx) + Math.abs(dy) > 0.5) lastMouse = System.nanoTime();
        return true;
    }

    public static boolean controls(Entity cameraEntity) {
        return LocalCombat.active() && cameraEntity == Minecraft.getInstance().player;
    }

    public static void addTrauma(double amount) {
        trauma = Math.min(1, trauma + Math.max(0, amount));
    }

    /** Camera for this frame (Camera.alignWithEntity tail). */
    public static View view(Entity e, float partialTick) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;

        Entity lock = LocalCombat.lockTarget();
        if (lock != null && now - lastMouse > 400_000_000L) {
            Vec3 from = e.getPosition(partialTick), to = lock.getPosition(partialTick);
            float want = (float) Angles.yawOf(to.x - from.x, to.z - from.z);
            yaw = (float) Angles.approach(yaw, want, LOCK_SWING * dt) + Math.round((yaw - Mth.wrapDegrees(yaw)) / 360f) * 360f;
        }

        trauma = Math.max(0, trauma - dt * 1.6);
        shakeTime += (float) dt;
        double t2 = trauma * trauma;
        float sy = (float) (t2 * 4.0 * noise(shakeTime * 31, 0.0));
        float sp = (float) (t2 * 3.0 * noise(shakeTime * 29, 7.1));

        Vec3 feet = e.getPosition(partialTick);
        Vec3 pivot = new Vec3(feet.x, feet.y + PIVOT_HEIGHT * scale(e), feet.z);
        return new View(yaw + sy, pitch + sp, pivot, DISTANCE * scale(e));
    }

    private static float scale(Entity e) {
        return e instanceof net.minecraft.world.entity.LivingEntity le ? le.getScale() : 1f;
    }

    /** Smooth noise in [-1, 1]. */
    private static double noise(double t, double seed) {
        return 0.6 * Math.sin(t + seed) + 0.4 * Math.sin(2.3 * t + 1.7 * seed + 0.5);
    }
}
