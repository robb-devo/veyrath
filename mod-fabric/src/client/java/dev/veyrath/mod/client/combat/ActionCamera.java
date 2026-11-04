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

    public record View(float yaw, float pitch, Vec3 pivot, float distance) {
        /** The same view craned up: higher pivot, looking further down (see {@link #settle}). */
        public View craned() {
            return new View(yaw, Mth.clamp(pitch + CRANE_PITCH, PITCH_MIN, PITCH_MAX), pivot.add(0, CRANE_LIFT, 0), distance);
        }
    }

    /** Crane on occlusion: degrees of extra pitch and blocks of extra pivot height at full crane. */
    static final float CRANE_PITCH = 22;
    static final double CRANE_LIFT = 0.7;
    private static double crane, lastDt;

    /**
     * When terrain behind the player pulls the camera in to less than half its distance (a slope,
     * a cliff wall), the camera cranes up and looks down over the obstacle instead of collapsing
     * onto the player's head, if that view is clearly freer. {@code plain} and {@code craned} are
     * the fractions of the wanted distance the collision check allows for each. Eased both ways
     * and held in between (hysteresis), so it does not flicker at the threshold.
     */
    public static View settle(View plainView, double plain, double craned) {
        double target = !CombatSettings.cameraCrane() ? 0
                : plain < 0.5 && craned > plain + 0.2 ? 1 : plain > 0.8 ? 0 : crane > 0.5 ? 1 : 0;
        crane += (target - crane) * Math.min(1, lastDt * 5);
        if (crane < 1e-3) {
            crane = 0;
            return plainView;
        }
        double k = crane * crane * (3 - 2 * crane);
        View c = plainView.craned();
        return new View(plainView.yaw(), (float) Mth.lerp(k, plainView.pitch(), c.pitch()),
                new Vec3(plainView.pivot().x, Mth.lerp(k, plainView.pivot().y, c.pivot().y), plainView.pivot().z),
                plainView.distance());
    }

    static void enter(Entity player) {
        yaw = player.getYRot();
        pitch = Mth.clamp(player.getXRot(), PITCH_MIN, PITCH_MAX);
        reset();
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
        trauma = Math.min(1, trauma + Math.max(0, amount) * CombatSettings.screenShake());
    }

    /**
     * Impact zoom: the camera is pulled in by up to {@link #PUNCH_ZOOM} of its distance and springs
     * back within a quarter second. Stands in for an FOV kick and keeps the target framed.
     */
    public static void punch(double amount) {
        if (CombatSettings.impactZoom()) punch = Math.min(1, Math.max(punch, amount));
    }

    static final double PUNCH_ZOOM = 0.16;
    /** Seconds the cinematic camera takes to blend in (and a little longer to blend out). */
    static final double CINE_BLEND = 0.18;

    private static double punch, cine;
    /** Which side of the fighter the cinematic camera swings to: +1 or -1, chosen per ultimate. */
    private static int cineSide = 1;
    private static Shot lastShot = Shot.NONE;

    /** Camera for this frame (Camera.alignWithEntity tail). */
    public static View view(Entity e, float partialTick) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        lastDt = dt;

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
        punch = Math.max(0, punch - dt * 4.5);

        // The ultimate's camera path, blended over the orbit camera.
        Shot shot = cinematicShot(partialTick);
        if (shot != Shot.NONE && cine == 0) cineSide = clearerSide(e, partialTick);
        if (shot != Shot.NONE) lastShot = shot;
        double wantCine = shot == Shot.NONE ? 0 : 1;
        cine += (wantCine - cine) * Math.min(1, dt / (wantCine > cine ? CINE_BLEND : CINE_BLEND * 1.6));
        if (cine < 1e-3) cine = 0;

        float viewYaw = yaw, viewPitch = pitch;
        float dist = DISTANCE;
        double lift = 0;
        if (cine > 0) {
            Shot s = shot == Shot.NONE ? lastShot : shot;
            float facing = LocalCombat.renderYaw(partialTick);
            double k = smooth(cine);
            viewYaw = yaw + (float) (Mth.wrapDegrees(facing + cineSide * s.yawOffset - yaw) * k);
            viewPitch = (float) Mth.lerp(k, pitch, s.pitch);
            dist = (float) Mth.lerp(k, DISTANCE, s.distance);
            lift = s.lift * k;
        }
        dist *= (float) (1 - PUNCH_ZOOM * smooth(punch));

        Vec3 feet = e.getPosition(partialTick);
        Vec3 pivot = new Vec3(feet.x, feet.y + (PIVOT_HEIGHT + lift) * scale(e), feet.z);
        return new View(viewYaw + sy, viewPitch + sp, pivot, dist * scale(e));
    }

    /**
     * Shots of a cinematic move by its tick: a low angle on the dash in, a slow side orbit through
     * the barrage, then a wide side view for the finisher and its shockwave.
     */
    enum Shot {
        NONE(0, 0, DISTANCE, 0),
        DASH(32, -6, 3.4f, -0.5),
        // The barrage looks down from above head height, so a crowd around the target does not
        // fill the frame, and cranes up slowly while it lasts.
        BARRAGE(80, 20, 3.6f, 0),
        BARRAGE_LATE(100, 26, 3.6f, 0),
        FINISHER(92, 14, 6.2f, 0.2);

        final float yawOffset, pitch, distance;
        final double lift;

        Shot(float yawOffset, float pitch, float distance, double lift) {
            this.yawOffset = yawOffset;
            this.pitch = pitch;
            this.distance = distance;
            this.lift = lift;
        }
    }

    private static Shot cinematicShot(float partialTick) {
        if (!CombatSettings.cinematicCamera()) return Shot.NONE;
        var f = LocalCombat.fighter();
        if (f == null || f.phase() != dev.veyrath.combat.fighter.Fighter.Phase.MOVE || f.move() == null || !f.move().cinematic) {
            return Shot.NONE;
        }
        // Shots follow the move's own hit windows: the dash runs until the first hit, the barrage
        // until the last (the finisher), and the camera hands back a little before the move ends.
        var m = f.move();
        int t = f.moveTick();
        if (t >= m.duration - 6) return Shot.NONE;
        if (m.hits.isEmpty()) return Shot.DASH;
        int first = m.hits.getFirst().from(), last = m.hits.getLast().from();
        if (t < first - 1) return Shot.DASH;
        if (t < last - 2) return t < (first + last) / 2 ? Shot.BARRAGE : Shot.BARRAGE_LATE;
        return Shot.FINISHER;
    }

    /**
     * The side for the cinematic camera with fewer bodies between it and the fighter, so a crowd
     * around the target does not fill the frame. Checked once, when the cinematic starts.
     */
    private static int clearerSide(Entity e, float partialTick) {
        var level = e.level();
        float facing = LocalCombat.renderYaw(partialTick);
        Vec3 feet = e.getPosition(partialTick);
        int best = 1, bestCount = Integer.MAX_VALUE;
        for (int side : new int[]{1, -1}) {
            // The camera sits DISTANCE behind the view direction; sample the line from fighter to camera.
            double yawR = Math.toRadians(facing + side * Shot.BARRAGE.yawOffset);
            double bx = Math.sin(yawR), bz = -Math.cos(yawR);
            int count = 0;
            for (double d = 1.0; d <= Shot.BARRAGE.distance + 0.5; d += 1.0) {
                var box = new net.minecraft.world.phys.AABB(feet.x + bx * d - 0.6, feet.y, feet.z + bz * d - 0.6,
                        feet.x + bx * d + 0.6, feet.y + 2.2, feet.z + bz * d + 0.6);
                count += level.getEntities(e, box, x -> x instanceof net.minecraft.world.entity.LivingEntity).size();
            }
            if (count < bestCount) {
                bestCount = count;
                best = side;
            }
        }
        return best;
    }

    static void reset() {
        crane = 0;
        trauma = 0;
        punch = 0;
        cine = 0;
        lastShot = Shot.NONE;
    }

    private static double smooth(double x) {
        x = Math.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float scale(Entity e) {
        return e instanceof net.minecraft.world.entity.LivingEntity le ? le.getScale() : 1f;
    }

    /** Smooth noise in [-1, 1]. */
    private static double noise(double t, double seed) {
        return 0.6 * Math.sin(t + seed) + 0.4 * Math.sin(2.3 * t + 1.7 * seed + 0.5);
    }
}
