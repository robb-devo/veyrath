package dev.veyrath.combat.content;

import dev.veyrath.combat.anim.BoneSpec;
import dev.veyrath.combat.anim.Clip;
import dev.veyrath.combat.anim.ClipBuilder;
import dev.veyrath.combat.anim.Ease;
import dev.veyrath.combat.anim.PoseSpec;
import dev.veyrath.combat.hit.ClipLibrary;

import static dev.veyrath.combat.content.UnarmedPoses.*;
import static dev.veyrath.combat.rig.Bone.LEFT_ARM;
import static dev.veyrath.combat.rig.Bone.RIGHT_ARM;
import static dev.veyrath.combat.rig.Bone.ROOT;

/**
 * Animation clips of the unarmed style. Strikes snap into the impact pose with OUT_EXPO, hold it
 * for a tick or two (so the blow reads), then recover with IN_OUT_SINE. Clip ids match move ids.
 */
public final class UnarmedClips {
    private UnarmedClips() {}

    private static ClipBuilder c(String id, double len) { return ClipBuilder.clip(id, len); }

    private static PoseSpec rootAt(PoseSpec p, double pitch, double yaw, double roll, double right, double up, double fwd) {
        return p.with(ROOT, BoneSpec.rot(pitch, yaw, roll).offset(right, up, fwd));
    }

    public static ClipLibrary library() {
        ClipLibrary lib = new ClipLibrary();

        // Stance and guard (layer clips, played by the animation composer, not by moves).
        lib.add(c("stance_idle", 40).loop().key(0, GUARD).key(20, GUARD_LOW).key(40, GUARD).build());
        lib.add(c("block_hold", 24).loop().key(0, BLOCK).key(12, BLOCK.add(ROOT, BoneSpec.REST.offset(0, -0.4, 0))).key(24, BLOCK).build());

        // Light string.
        lib.add(c("jab", 9)
                .key(0, GUARD)
                .key(1, GUARD.add(LEFT_ARM, BoneSpec.rot(8, 0, 0)), Ease.OUT_SINE)
                .key(2, JAB, Ease.OUT_EXPO)
                .key(3.5, JAB, Ease.LINEAR)
                .key(9, GUARD, Ease.IN_OUT_SINE)
                .event(2, "impact").build());
        lib.add(c("cross", 10)
                .key(0, GUARD)
                .key(2, CROSS, Ease.OUT_EXPO)
                .key(3.5, CROSS, Ease.LINEAR)
                .key(10, GUARD, Ease.IN_OUT_SINE)
                .event(2, "impact").build());
        lib.add(c("hook", 11)
                .key(0, GUARD)
                .key(2, HOOK_WIND, Ease.OUT_QUAD)
                .key(3.5, HOOK, Ease.OUT_EXPO)
                .key(5, HOOK, Ease.LINEAR)
                .key(11, GUARD, Ease.IN_OUT_SINE)
                .event(3.5, "impact").build());
        lib.add(c("roundhouse", 16)
                .key(0, GUARD)
                .key(3, ROUNDHOUSE_CHAMBER, Ease.OUT_QUAD)
                .key(4.5, ROUNDHOUSE, Ease.OUT_EXPO)
                .key(6.5, ROUNDHOUSE_THROUGH, Ease.OUT_SINE)
                .key(10, ROUNDHOUSE_CHAMBER.lerp(GUARD, 0.5), Ease.IN_OUT_SINE)
                .key(16, GUARD, Ease.IN_OUT_SINE)
                .event(4.5, "impact").build());

        // Heavies.
        ClipBuilder charge = c("heavy_charge", 40).key(0, GUARD).key(3, CHARGE_A, Ease.OUT_CUBIC);
        for (int t = 4; t <= 40; t++) charge.key(t, (t % 2 == 0) ? CHARGE_A : CHARGE_B, Ease.LINEAR);
        lib.add(charge.build());
        lib.add(c("heavy", 16)
                .key(0, CHARGE_A)
                .key(3, STRAIGHT_WIND, Ease.IN_BACK)
                .key(5, STRAIGHT, Ease.OUT_EXPO)
                .key(7, STRAIGHT, Ease.LINEAR)
                .key(16, GUARD, Ease.IN_OUT_SINE)
                .event(5, "impact").build());
        lib.add(c("heavy_charged", 18)
                .key(0, CHARGE_B)
                .key(3, STRAIGHT, Ease.OUT_EXPO)
                .key(6, STRAIGHT, Ease.LINEAR)
                .key(18, GUARD, Ease.IN_OUT_SINE)
                .event(3, "impact").build());
        lib.add(c("launcher", 14)
                .key(0, GUARD)
                .key(3, LAUNCH_CROUCH, Ease.OUT_QUAD)
                .key(4, LAUNCH_CROUCH.lerp(LAUNCH_TOP, 0.65), Ease.OUT_CUBIC)
                .key(5.5, LAUNCH_TOP, Ease.OUT_SINE)
                .key(8, LAUNCH_TOP, Ease.LINEAR)
                .key(14, GUARD, Ease.IN_OUT_SINE)
                .event(4, "impact").build());
        // Turns to the right with the kicking leg out to the right, so the heel leads the spin:
        // it sweeps from the left side through the front (body at about 278 degrees) during the
        // active ticks and ends a full turn later, facing forward again.
        lib.add(c("spin_heel", 18)
                .key(0, GUARD)
                .key(3, rootAt(SPIN_HEEL.lerp(GUARD, 0.45), 0, 120, 0, 0, 1, 0.5), Ease.OUT_QUAD)
                .key(5, rootAt(SPIN_HEEL, 0, 245, 0, 0, 1.5, 1), Ease.LINEAR)
                .key(6, rootAt(SPIN_HEEL, 0, 280, 0, 0, 1.5, 1), Ease.LINEAR)
                .key(8.5, rootAt(SPIN_HEEL, 0, 345, 0, 0, 1, 1), Ease.OUT_SINE)
                .key(18, rootAt(GUARD, 0, 360, 0, 0, 0, 0), Ease.IN_OUT_SINE)
                .event(6, "impact").build());
        lib.add(c("dash_strike", 14)
                .key(0, GUARD)
                .key(2, KNEE, Ease.OUT_CUBIC)
                .key(6, KNEE, Ease.LINEAR)
                .key(14, GUARD, Ease.IN_OUT_SINE)
                .event(3, "impact").build());

        // Air.
        lib.add(c("air_1", 8).key(0, AIR_TUCK).key(2, AIR_JAB, Ease.OUT_EXPO).key(3.5, AIR_JAB, Ease.LINEAR)
                .key(8, AIR_TUCK, Ease.IN_OUT_SINE).event(2, "impact").build());
        lib.add(c("air_2", 8).key(0, AIR_TUCK).key(2, AIR_HOOK, Ease.OUT_EXPO).key(3.5, AIR_HOOK, Ease.LINEAR)
                .key(8, AIR_TUCK, Ease.IN_OUT_SINE).event(2, "impact").build());
        lib.add(c("air_3", 12).key(0, AIR_TUCK).key(2.5, AXE_UP, Ease.OUT_QUAD).key(4, AXE_DOWN, Ease.OUT_EXPO)
                .key(6, AXE_DOWN, Ease.LINEAR).key(12, AIR_TUCK, Ease.IN_OUT_SINE).event(4, "impact").build());
        lib.add(c("meteor_drop", 30).key(0, AIR_TUCK).key(3, METEOR_RAISE, Ease.OUT_QUAD)
                .key(5, METEOR_DIVE, Ease.OUT_EXPO).key(30, METEOR_DIVE, Ease.LINEAR).build());
        lib.add(c("meteor_land", 14).key(0, METEOR_LAND).key(4, METEOR_LAND, Ease.LINEAR)
                .key(14, GUARD, Ease.IN_OUT_SINE).event(0, "impact").build());

        // Movement.
        lib.add(c("dodge_forward", 10)
                .key(0, GUARD)
                .key(1.5, rootAt(ROLL_TUCK, 60, 0, 0, 0, -5, 0), Ease.OUT_QUAD)
                .key(5, rootAt(ROLL_TUCK, 220, 0, 0, 0, -8, 0), Ease.LINEAR)
                .key(7.5, rootAt(ROLL_TUCK, 330, 0, 0, 0, -4, 0), Ease.LINEAR)
                .key(10, rootAt(GUARD, 360, 12, 0, 0, -1, 0), Ease.OUT_SINE).build());
        lib.add(c("dodge_back", 9).key(0, GUARD).key(2, BACKSTEP, Ease.OUT_CUBIC).key(6, BACKSTEP, Ease.LINEAR)
                .key(9, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("dodge_right", 9).key(0, GUARD).key(2, SIDESTEP_RIGHT, Ease.OUT_CUBIC).key(6, SIDESTEP_RIGHT, Ease.LINEAR)
                .key(9, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("dodge_left", 9).key(0, GUARD).key(2, SIDESTEP_RIGHT.mirrored(), Ease.OUT_CUBIC)
                .key(6, SIDESTEP_RIGHT.mirrored(), Ease.LINEAR).key(9, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("air_dash", 7).key(0, AIR_TUCK).key(1.5, AIR_DASH, Ease.OUT_EXPO).key(5, AIR_DASH, Ease.LINEAR)
                .key(7, AIR_TUCK, Ease.IN_OUT_SINE).build());
        lib.add(c("air_jump", 8).key(0, rootAt(AIR_TUCK, 0, 0, 0, 0, 0, 0))
                .key(4, rootAt(AIR_TUCK, 200, 0, 0, 0, 0, 0), Ease.LINEAR)
                .key(8, rootAt(AIR_TUCK, 360, 0, 0, 0, 0, 0), Ease.OUT_SINE).build());
        lib.add(c("launch_rise", 8).key(0, LAUNCH_TOP).key(8, AIR_TUCK, Ease.IN_OUT_SINE).build());

        // Defence.
        lib.add(c("parry", 12).key(0, GUARD).key(1.5, PARRY, Ease.OUT_EXPO).key(5, PARRY, Ease.LINEAR)
                .key(12, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("riposte", 14).key(0, PARRY).key(2, PALM, Ease.OUT_EXPO).key(5, PALM, Ease.LINEAR)
                .key(14, GUARD, Ease.IN_OUT_SINE).event(2, "impact").build());
        lib.add(c("block_hit", 6).key(0, BLOCK_IMPACT).key(6, BLOCK, Ease.OUT_SINE).build());

        // Abilities.
        lib.add(c("gale_step", 16)
                .key(0, GUARD)
                .key(1, GALE_RUN, Ease.OUT_CUBIC)
                .key(6, GALE_RUN, Ease.LINEAR)
                .key(7.5, PALM, Ease.OUT_EXPO)
                .key(10, PALM, Ease.LINEAR)
                .key(16, GUARD, Ease.IN_OUT_SINE)
                .event(7.5, "impact").build());
        lib.add(c("skybreaker", 20)
                .key(0, LAUNCH_CROUCH)
                .key(3, rootAt(LAUNCH_CROUCH, 0, 0, 0, 0, -4, 0.5), Ease.LINEAR)
                .key(5, rootAt(LAUNCH_TOP, -6, -240, 0, 0, 2, 0), Ease.OUT_QUAD)
                .key(8, rootAt(LAUNCH_TOP, -6, -540, 0, 0, 2, 0), Ease.LINEAR)
                .key(11, rootAt(LAUNCH_TOP, -6, -720, 0, 0, 2, 0), Ease.OUT_SINE)
                .key(20, rootAt(AIR_TUCK, 0, -720, 0, 0, 0, 0), Ease.IN_OUT_SINE)
                .event(3, "impact").build());
        lib.add(c("quake_palm", 22)
                .key(0, GUARD)
                .key(4, QUAKE_GATHER, Ease.OUT_SINE)
                .key(6.5, QUAKE_PUSH, Ease.OUT_EXPO)
                .key(10, QUAKE_PUSH, Ease.LINEAR)
                .key(22, GUARD, Ease.IN_OUT_SINE)
                .event(6.5, "impact").build());

        // Ultimate: gather, rush, barrage, coil, strike.
        ClipBuilder ult = c("unbound_fist", 72)
                .key(0, GUARD)
                .key(8, ULT_GATHER, Ease.IN_OUT_SINE)
                .key(10, ULT_GATHER, Ease.LINEAR)
                .key(12, GALE_RUN, Ease.OUT_CUBIC)
                .key(15, GUARD, Ease.OUT_SINE);
        for (int k = 0; k <= 11; k++) {
            double t = 16 + 2 * k;
            boolean right = k % 2 == 0;
            double jitter = ((k * 37) % 11 - 5) * 1.6;
            PoseSpec strike = (right ? CROSS : JAB)
                    .add(right ? RIGHT_ARM : LEFT_ARM, BoneSpec.rot(jitter, jitter * 0.8, 0))
                    .add(ROOT, BoneSpec.REST.offset(0, 0, 1));
            ult.key(t, strike, Ease.OUT_EXPO);
            ult.key(t + 1, strike.lerp(GUARD, 0.55), Ease.IN_SINE);
        }
        ult.key(41, ULT_COIL, Ease.OUT_CUBIC)
                .key(47, ULT_COIL.add(ROOT, BoneSpec.REST.offset(0, -0.5, -0.5)), Ease.LINEAR)
                .key(48.5, ULT_STRIKE, Ease.OUT_EXPO)
                .key(56, ULT_STRIKE, Ease.LINEAR)
                .key(72, GUARD, Ease.IN_OUT_SINE)
                .event(0, "ult_start").event(10, "ult_rush").event(16, "ult_barrage").event(40, "ult_coil")
                .event(48, "ult_impact").event(60, "ult_end");
        lib.add(ult.build());

        // Reactions (shared by every humanoid).
        lib.add(c("react_flinch", 12).key(0, FLINCH).key(4, FLINCH, Ease.LINEAR).key(12, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("react_stagger", 20).key(0, STAGGER).key(8, STAGGER.lerp(FLINCH, 0.4), Ease.OUT_SINE)
                .key(20, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("react_knockback", 20).key(0, rootAt(TUMBLE, -20, 0, 0, 0, 0, 0)).key(6, TUMBLE, Ease.OUT_QUAD)
                .key(20, rootAt(TUMBLE, -70, 0, 0, 0, 0, 0), Ease.LINEAR).build());
        lib.add(c("react_launch", 16).loop().key(0, TUMBLE).key(8, rootAt(TUMBLE, -60, 10, 8, 0, 0, 0))
                .key(16, TUMBLE).build());
        lib.add(c("react_spike", 12).key(0, SPIKED).key(12, SPIKED, Ease.LINEAR).build());
        lib.add(c("react_guard_break", 30).key(0, OPENED).key(20, OPENED.lerp(STAGGER, 0.3), Ease.LINEAR)
                .key(30, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("react_parried", 26).key(0, OPENED).key(18, OPENED.lerp(STAGGER, 0.3), Ease.LINEAR)
                .key(26, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("react_knockdown", 20).key(0, TUMBLE).key(3, DOWN, Ease.OUT_QUAD).key(20, DOWN, Ease.LINEAR).build());
        lib.add(c("knockdown", 20).key(0, TUMBLE).key(3, DOWN, Ease.OUT_QUAD).key(20, DOWN, Ease.LINEAR).build());
        lib.add(c("getup", 10).key(0, DOWN)
                .key(5, rootAt(GUARD, -30, 12, 0, 0, -4, 0), Ease.OUT_QUAD)
                .key(10, GUARD, Ease.OUT_SINE).build());
        return lib;
    }

    /** The clip a fighter shows for a phase that is not a move (stance, block). */
    public static Clip stance(ClipLibrary lib) { return lib.get("stance_idle"); }
}
