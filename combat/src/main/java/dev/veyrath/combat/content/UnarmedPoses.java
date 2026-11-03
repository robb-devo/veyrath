package dev.veyrath.combat.content;

import dev.veyrath.combat.anim.BoneSpec;
import dev.veyrath.combat.anim.PoseSpec;

import static dev.veyrath.combat.rig.Bone.BODY;
import static dev.veyrath.combat.rig.Bone.HEAD;
import static dev.veyrath.combat.rig.Bone.LEFT_ARM;
import static dev.veyrath.combat.rig.Bone.LEFT_LEG;
import static dev.veyrath.combat.rig.Bone.RIGHT_ARM;
import static dev.veyrath.combat.rig.Bone.RIGHT_LEG;
import static dev.veyrath.combat.rig.Bone.ROOT;

/**
 * Key poses of the unarmed (Stormhand) style. Angles are Minecraft ModelPart degrees: arm and leg
 * pitch -90 points the limb straight forward; body pitch + leans forward; yaw + turns toward the
 * character's right; arm and leg yaw are relative to the torso (arms) or hips (legs), so a turned
 * torso needs the opposite arm yaw to keep a fist on target. Offsets are pixels (right, up,
 * forward).
 */
public final class UnarmedPoses {
    private UnarmedPoses() {}

    static BoneSpec r(double pitch, double yaw, double roll) { return BoneSpec.rot(pitch, yaw, roll); }

    /** Fighting stance: lead (left) side slightly forward, fists up, knees soft. */
    public static final PoseSpec GUARD = PoseSpec.REST
            .with(ROOT, r(0, 12, 0).offset(0, -1, 0))
            .with(BODY, r(7, 6, 0))
            .with(HEAD, r(-6, -17, 0))
            .with(LEFT_ARM, r(-66, -6, -4))
            .with(RIGHT_ARM, r(-76, -36, 6))
            .with(LEFT_LEG, r(-14, -6, -5))
            .with(RIGHT_LEG, r(16, 4, 6));

    /** Guard breathing low point (idle loop). */
    public static final PoseSpec GUARD_LOW = GUARD
            .add(ROOT, BoneSpec.REST.offset(0, -0.6, 0))
            .add(BODY, r(2, 0, 0))
            .add(LEFT_ARM, r(3, 0, 0))
            .add(RIGHT_ARM, r(3, 0, 0));

    // ---------------------------------------------------------------- light string

    /** Lead jab, extended. Torso turns right (+34 total), so the left arm aims left of torso. */
    public static final PoseSpec JAB = PoseSpec.REST
            .with(ROOT, r(0, 18, 0).offset(0, -1, 1.5))
            .with(BODY, r(9, 16, 0))
            .with(HEAD, r(-6, -30, 0))
            .with(LEFT_ARM, r(-92, -30, 0))
            .with(RIGHT_ARM, r(-74, -50, 8))
            .with(LEFT_LEG, r(-18, -8, -5))
            .with(RIGHT_LEG, r(18, 4, 6));

    /** Rear cross, extended. Hips and torso turn left (-34 total). */
    public static final PoseSpec CROSS = PoseSpec.REST
            .with(ROOT, r(0, -10, 0).offset(0, -1.5, 2.5))
            .with(BODY, r(11, -24, 0))
            .with(HEAD, r(-8, 30, 0))
            .with(RIGHT_ARM, r(-92, 30, 0))
            .with(LEFT_ARM, r(-60, 44, -8))
            .with(LEFT_LEG, r(-20, 0, -5))
            .with(RIGHT_LEG, r(28, 6, 8));

    /** Hook wind-up: torso turned right, lead arm out to the side. */
    public static final PoseSpec HOOK_WIND = PoseSpec.REST
            .with(ROOT, r(0, 14, 0).offset(0, -1.5, 0))
            .with(BODY, r(8, 12, -4))
            .with(HEAD, r(-6, -24, 0))
            .with(LEFT_ARM, r(-84, -74, -10))
            .with(RIGHT_ARM, r(-76, -46, 6))
            .with(LEFT_LEG, r(-14, -4, -5))
            .with(RIGHT_LEG, r(16, 4, 6));

    /** Hook impact: torso whipped left, lead arm across the centre line. */
    public static final PoseSpec HOOK = PoseSpec.REST
            .with(ROOT, r(0, -14, 0).offset(0, -2, 1.5))
            .with(BODY, r(10, -16, 6))
            .with(HEAD, r(-6, 26, 0))
            .with(LEFT_ARM, r(-90, 42, -14))
            .with(RIGHT_ARM, r(-72, 30, 8))
            .with(LEFT_LEG, r(-18, -10, -6))
            .with(RIGHT_LEG, r(18, 10, 8));

    /** Roundhouse chamber: pivot on the lead foot, rear knee up and out. */
    public static final PoseSpec ROUNDHOUSE_CHAMBER = PoseSpec.REST
            .with(ROOT, r(0, -34, 0).offset(0, 0, 0.5))
            .with(BODY, r(-4, -4, 10))
            .with(HEAD, r(-4, 34, -6))
            .with(LEFT_ARM, r(-58, 34, -18))
            .with(RIGHT_ARM, r(-30, 10, 34))
            .with(LEFT_LEG, r(4, 0, -4))
            .with(RIGHT_LEG, r(-78, 64, 4));

    /** Roundhouse impact: hips fully turned, rear leg horizontal at the target. */
    public static final PoseSpec ROUNDHOUSE = PoseSpec.REST
            .with(ROOT, r(0, -68, 0).offset(0, 1, 1))
            .with(BODY, r(-12, -6, 20))
            .with(HEAD, r(-6, 56, -10))
            .with(LEFT_ARM, r(-40, 40, -30))
            .with(RIGHT_ARM, r(-16, 0, 52))
            .with(LEFT_LEG, r(6, 0, -4))
            .with(RIGHT_LEG, r(-98, 70, 0));

    /** Roundhouse follow-through. */
    public static final PoseSpec ROUNDHOUSE_THROUGH = ROUNDHOUSE
            .with(ROOT, r(0, -92, 0).offset(0, 0.5, 1))
            .with(RIGHT_LEG, r(-80, 40, 4))
            .with(HEAD, r(-6, 64, -8));

    // ---------------------------------------------------------------- heavies

    /** Power straight wind-up: rear shoulder pulled far back, weight on the back leg. */
    public static final PoseSpec STRAIGHT_WIND = PoseSpec.REST
            .with(ROOT, r(-4, 34, 0).offset(0, -2, -1.5))
            .with(BODY, r(0, 18, 0))
            .with(HEAD, r(-4, -44, 0))
            .with(RIGHT_ARM, r(18, -10, 14))
            .with(LEFT_ARM, r(-88, -44, -6))
            .with(LEFT_LEG, r(-22, -10, -6))
            .with(RIGHT_LEG, r(12, 6, 8));

    /** Power straight impact: full turn, lunge, rear arm through the target. */
    public static final PoseSpec STRAIGHT = PoseSpec.REST
            .with(ROOT, r(6, -16, 0).offset(0, -3, 4))
            .with(BODY, r(14, -30, 0))
            .with(HEAD, r(-12, 42, 0))
            .with(RIGHT_ARM, r(-94, 40, 0))
            .with(LEFT_ARM, r(-20, 40, -20))
            .with(LEFT_LEG, r(-34, 4, -6))
            .with(RIGHT_LEG, r(36, 8, 8));

    /** Charging: coiled low, rear fist chambered at the hip, trembling between this and CHARGE_B. */
    public static final PoseSpec CHARGE_A = PoseSpec.REST
            .with(ROOT, r(-2, 40, 0).offset(0, -3.5, -1))
            .with(BODY, r(10, 22, 0))
            .with(HEAD, r(-10, -52, 0))
            .with(RIGHT_ARM, r(28, -14, 18))
            .with(LEFT_ARM, r(-82, -50, -10))
            .with(LEFT_LEG, r(-30, -12, -8))
            .with(RIGHT_LEG, r(18, 8, 10));

    public static final PoseSpec CHARGE_B = CHARGE_A
            .add(ROOT, BoneSpec.REST.offset(0.3, -0.3, 0))
            .add(BODY, r(1, -1, 1))
            .add(RIGHT_ARM, r(2, 0, -1));

    /** Rising launcher crouch. */
    public static final PoseSpec LAUNCH_CROUCH = PoseSpec.REST
            .with(ROOT, r(0, 10, 0).offset(0, -4, 0.5))
            .with(BODY, r(26, 8, 0))
            .with(HEAD, r(-26, -16, 0))
            .with(RIGHT_ARM, r(14, -8, 10))
            .with(LEFT_ARM, r(-70, -24, -6))
            .with(LEFT_LEG, r(-34, -6, -8))
            .with(RIGHT_LEG, r(14, 6, 10));

    /** Rising launcher top: fist driven straight up, body stretched. */
    public static final PoseSpec LAUNCH_TOP = PoseSpec.REST
            .with(ROOT, r(-6, -12, 0).offset(0, 2, 2))
            .with(BODY, r(-14, -16, -6))
            .with(HEAD, r(-30, 20, 0))
            .with(RIGHT_ARM, r(-172, 16, -4))
            .with(LEFT_ARM, r(-20, 20, -16))
            .with(LEFT_LEG, r(-10, 0, -4))
            .with(RIGHT_LEG, r(22, 0, 4));

    /** Spinning heel kick, leg out (the root spin is keyed separately). */
    public static final PoseSpec SPIN_HEEL = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, 1.5, 1))
            .with(BODY, r(-10, 0, 22))
            .with(HEAD, r(-6, 0, -10))
            .with(LEFT_ARM, r(-50, 30, -40))
            .with(RIGHT_ARM, r(-20, 0, 60))
            .with(LEFT_LEG, r(4, 0, -6))
            .with(RIGHT_LEG, r(-96, 82, 0));

    /** Flying knee (dash strike). */
    public static final PoseSpec KNEE = PoseSpec.REST
            .with(ROOT, r(-8, -6, 0).offset(0, 3, 2))
            .with(BODY, r(-6, -6, 0))
            .with(HEAD, r(-4, 10, 0))
            .with(RIGHT_ARM, r(36, -10, 20))
            .with(LEFT_ARM, r(34, 10, -20))
            .with(RIGHT_LEG, r(-112, 0, 0))
            .with(LEFT_LEG, r(46, 0, 0));

    // ---------------------------------------------------------------- air

    /** Airborne tuck (legs folded). */
    public static final PoseSpec AIR_TUCK = PoseSpec.REST
            .with(BODY, r(8, 0, 0))
            .with(LEFT_ARM, r(-60, -16, -10))
            .with(RIGHT_ARM, r(-66, -24, 10))
            .with(LEFT_LEG, r(-54, 0, -4))
            .with(RIGHT_LEG, r(-24, 0, 4));

    public static final PoseSpec AIR_JAB = AIR_TUCK
            .with(ROOT, r(0, -10, 0).offset(0, 0, 1))
            .with(BODY, r(12, -16, 0))
            .with(HEAD, r(-10, 24, 0))
            .with(RIGHT_ARM, r(-92, 24, 0))
            .with(LEFT_ARM, r(-50, 40, -10));

    public static final PoseSpec AIR_HOOK = AIR_TUCK
            .with(ROOT, r(0, -16, 0).offset(0, 0, 1))
            .with(BODY, r(10, -14, 6))
            .with(HEAD, r(-8, 28, 0))
            .with(LEFT_ARM, r(-92, 44, -14))
            .with(RIGHT_ARM, r(-60, 30, 10));

    /** Axe kick raised. */
    public static final PoseSpec AXE_UP = PoseSpec.REST
            .with(ROOT, r(-16, 0, 0).offset(0, 2, 0))
            .with(BODY, r(-10, 0, 0))
            .with(HEAD, r(10, 0, 0))
            .with(LEFT_ARM, r(-30, 0, -30))
            .with(RIGHT_ARM, r(-30, 0, 30))
            .with(RIGHT_LEG, r(-168, 0, 0))
            .with(LEFT_LEG, r(20, 0, 0));

    /** Axe kick slammed down. */
    public static final PoseSpec AXE_DOWN = PoseSpec.REST
            .with(ROOT, r(18, 0, 0).offset(0, 0, 1.5))
            .with(BODY, r(20, 0, 0))
            .with(HEAD, r(-16, 0, 0))
            .with(LEFT_ARM, r(30, 0, -24))
            .with(RIGHT_ARM, r(30, 0, 24))
            .with(RIGHT_LEG, r(-46, 0, 0))
            .with(LEFT_LEG, r(30, 0, 0));

    /** Meteor Drop: double fist above the head. */
    public static final PoseSpec METEOR_RAISE = PoseSpec.REST
            .with(ROOT, r(-10, 0, 0).offset(0, 1, 0))
            .with(BODY, r(-12, 0, 0))
            .with(HEAD, r(-10, 0, 0))
            .with(RIGHT_ARM, r(-176, -10, 0))
            .with(LEFT_ARM, r(-176, 10, 0))
            .with(RIGHT_LEG, r(-30, 0, 4))
            .with(LEFT_LEG, r(-50, 0, -4));

    /** Meteor Drop: diving, fists driven down. */
    public static final PoseSpec METEOR_DIVE = PoseSpec.REST
            .with(ROOT, r(26, 0, 0))
            .with(BODY, r(16, 0, 0))
            .with(HEAD, r(-30, 0, 0))
            .with(RIGHT_ARM, r(-40, -12, 0))
            .with(LEFT_ARM, r(-40, 12, 0))
            .with(RIGHT_LEG, r(30, 0, 6))
            .with(LEFT_LEG, r(10, 0, -6));

    /** Meteor landing: crouched, fists in the ground. */
    public static final PoseSpec METEOR_LAND = PoseSpec.REST
            .with(ROOT, r(10, 0, 0).offset(0, -6, 1))
            .with(BODY, r(40, 0, 0))
            .with(HEAD, r(-36, 0, 0))
            .with(RIGHT_ARM, r(-26, -14, 6))
            .with(LEFT_ARM, r(-26, 14, -6))
            .with(RIGHT_LEG, r(-50, 0, 14))
            .with(LEFT_LEG, r(-10, 0, -20));

    // ---------------------------------------------------------------- defence and movement

    /** High guard: forearms crossed in front of the face. */
    public static final PoseSpec BLOCK = PoseSpec.REST
            .with(ROOT, r(0, 4, 0).offset(0, -2, -0.5))
            .with(BODY, r(14, 0, 0))
            .with(HEAD, r(4, -4, 0))
            .with(RIGHT_ARM, r(-118, -40, 6))
            .with(LEFT_ARM, r(-112, 40, -6))
            .with(LEFT_LEG, r(-18, -4, -6))
            .with(RIGHT_LEG, r(18, 4, 6));

    public static final PoseSpec BLOCK_IMPACT = BLOCK
            .add(ROOT, BoneSpec.REST.offset(0, -0.5, -1.5))
            .add(BODY, r(-6, 0, 0))
            .add(HEAD, r(-8, 0, 0));

    /** Parry: lead arm sweeps the blow outward. */
    public static final PoseSpec PARRY = PoseSpec.REST
            .with(ROOT, r(0, 20, 0).offset(0, -2, 0))
            .with(BODY, r(6, 18, 0))
            .with(HEAD, r(-6, -34, 0))
            .with(LEFT_ARM, r(-104, -76, -20))
            .with(RIGHT_ARM, r(-72, -52, 10))
            .with(LEFT_LEG, r(-16, -6, -6))
            .with(RIGHT_LEG, r(18, 4, 6));

    /** Riposte palm strike. */
    public static final PoseSpec PALM = PoseSpec.REST
            .with(ROOT, r(4, -14, 0).offset(0, -3, 4))
            .with(BODY, r(14, -26, 0))
            .with(HEAD, r(-12, 38, 0))
            .with(RIGHT_ARM, r(-90, 36, -30))
            .with(LEFT_ARM, r(-30, 40, -20))
            .with(LEFT_LEG, r(-32, 0, -6))
            .with(RIGHT_LEG, r(32, 6, 8));

    /** Tucked roll (the root rotation is keyed separately). */
    public static final PoseSpec ROLL_TUCK = PoseSpec.REST
            .with(ROOT, BoneSpec.REST.offset(0, -6, 0))
            .with(BODY, r(40, 0, 0))
            .with(HEAD, r(30, 0, 0))
            .with(RIGHT_ARM, r(-60, -10, 10))
            .with(LEFT_ARM, r(-60, 10, -10))
            .with(RIGHT_LEG, r(-100, 0, 4))
            .with(LEFT_LEG, r(-90, 0, -4));

    /** Backstep: weight back, guard kept. */
    public static final PoseSpec BACKSTEP = GUARD
            .with(ROOT, r(-10, 10, 0).offset(0, -1, -2))
            .with(BODY, r(-6, 6, 0))
            .with(RIGHT_LEG, r(30, 4, 6))
            .with(LEFT_LEG, r(-30, -6, -4));

    /** Sidestep to the right (mirror for the left). */
    public static final PoseSpec SIDESTEP_RIGHT = GUARD
            .with(ROOT, r(0, 10, -14).offset(1, -2, 0))
            .with(BODY, r(8, 6, -8))
            .with(RIGHT_LEG, r(0, 0, 26))
            .with(LEFT_LEG, r(0, 0, -6));

    /** Air dash: body stretched forward, arms trailing. */
    public static final PoseSpec AIR_DASH = PoseSpec.REST
            .with(ROOT, r(58, 0, 0))
            .with(BODY, r(6, 0, 0))
            .with(HEAD, r(-50, 0, 0))
            .with(RIGHT_ARM, r(40, 0, 14))
            .with(LEFT_ARM, r(40, 0, -14))
            .with(RIGHT_LEG, r(12, 0, 4))
            .with(LEFT_LEG, r(24, 0, -4));

    /** Gale Step: low ninja dash. */
    public static final PoseSpec GALE_RUN = PoseSpec.REST
            .with(ROOT, r(30, 0, 0).offset(0, -4, 0))
            .with(BODY, r(26, 0, 0))
            .with(HEAD, r(-46, 0, 0))
            .with(RIGHT_ARM, r(66, 0, 16))
            .with(LEFT_ARM, r(66, 0, -16))
            .with(RIGHT_LEG, r(-50, 0, 4))
            .with(LEFT_LEG, r(34, 0, -4));

    /** Quake Palm: deep horse stance, both palms thrust forward. */
    public static final PoseSpec QUAKE_PUSH = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, -5, 2))
            .with(BODY, r(16, 0, 0))
            .with(HEAD, r(-14, 0, 0))
            .with(RIGHT_ARM, r(-92, -16, -6))
            .with(LEFT_ARM, r(-92, 16, 6))
            .with(RIGHT_LEG, r(-20, 0, 26))
            .with(LEFT_LEG, r(-20, 0, -26));

    public static final PoseSpec QUAKE_GATHER = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, -4, -1))
            .with(BODY, r(-4, 0, 0))
            .with(HEAD, r(-6, 0, 0))
            .with(RIGHT_ARM, r(16, 0, 20))
            .with(LEFT_ARM, r(16, 0, -20))
            .with(RIGHT_LEG, r(-12, 0, 24))
            .with(LEFT_LEG, r(-12, 0, -24));

    // ---------------------------------------------------------------- reactions

    public static final PoseSpec FLINCH = PoseSpec.REST
            .with(ROOT, r(-8, 0, 0).offset(0, -1, -1.5))
            .with(BODY, r(-12, 0, 0))
            .with(HEAD, r(-22, 8, 0))
            .with(RIGHT_ARM, r(-30, 10, 24))
            .with(LEFT_ARM, r(-24, -10, -24))
            .with(RIGHT_LEG, r(10, 0, 4))
            .with(LEFT_LEG, r(-12, 0, -4));

    public static final PoseSpec STAGGER = PoseSpec.REST
            .with(ROOT, r(-14, 0, 0).offset(0, -2, -3))
            .with(BODY, r(-18, 0, 4))
            .with(HEAD, r(-30, 10, 0))
            .with(RIGHT_ARM, r(-60, 20, 40))
            .with(LEFT_ARM, r(-40, -20, -40))
            .with(RIGHT_LEG, r(24, 0, 6))
            .with(LEFT_LEG, r(-24, 0, -6));

    /** Airborne, flung back (launch and knockback). */
    public static final PoseSpec TUMBLE = PoseSpec.REST
            .with(ROOT, r(-44, 0, 0))
            .with(BODY, r(-16, 0, 0))
            .with(HEAD, r(-20, 0, 0))
            .with(RIGHT_ARM, r(-150, 0, 30))
            .with(LEFT_ARM, r(-140, 0, -30))
            .with(RIGHT_LEG, r(-50, 0, 10))
            .with(LEFT_LEG, r(-20, 0, -10));

    public static final PoseSpec SPIKED = PoseSpec.REST
            .with(ROOT, r(50, 0, 0))
            .with(BODY, r(20, 0, 0))
            .with(HEAD, r(-30, 0, 0))
            .with(RIGHT_ARM, r(-160, 0, 20))
            .with(LEFT_ARM, r(-160, 0, -20))
            .with(RIGHT_LEG, r(30, 0, 6))
            .with(LEFT_LEG, r(10, 0, -6));

    /** On the back on the ground. */
    public static final PoseSpec DOWN = PoseSpec.REST
            .with(ROOT, r(-88, 0, 0).offset(0, -10.5, 0))
            .with(HEAD, r(-6, 20, 0))
            .with(RIGHT_ARM, r(-20, 0, 36))
            .with(LEFT_ARM, r(-10, 0, -40))
            .with(RIGHT_LEG, r(-6, 0, 8))
            .with(LEFT_LEG, r(-24, 0, -6));

    public static final PoseSpec OPENED = PoseSpec.REST
            .with(ROOT, r(-16, 0, 0).offset(0, -1, -3))
            .with(BODY, r(-24, 0, 0))
            .with(HEAD, r(-34, 0, 0))
            .with(RIGHT_ARM, r(-150, 0, 50))
            .with(LEFT_ARM, r(-150, 0, -50))
            .with(RIGHT_LEG, r(24, 0, 8))
            .with(LEFT_LEG, r(-22, 0, -8));

    // ---------------------------------------------------------------- ultimate

    /** Unbound Fist: gathering power, chin up, fists low and back. */
    public static final PoseSpec ULT_GATHER = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, -3, -1))
            .with(BODY, r(-10, 0, 0))
            .with(HEAD, r(-16, 0, 0))
            .with(RIGHT_ARM, r(26, 0, 18))
            .with(LEFT_ARM, r(26, 0, -18))
            .with(RIGHT_LEG, r(-10, 0, 20))
            .with(LEFT_LEG, r(-10, 0, -20));

    /** Final wind-up: the whole body coiled behind the right fist. */
    public static final PoseSpec ULT_COIL = PoseSpec.REST
            .with(ROOT, r(-6, 52, 0).offset(0, -3, -2))
            .with(BODY, r(6, 26, 0))
            .with(HEAD, r(-10, -66, 0))
            .with(RIGHT_ARM, r(40, -14, 22))
            .with(LEFT_ARM, r(-96, -60, -10))
            .with(LEFT_LEG, r(-36, -14, -10))
            .with(RIGHT_LEG, r(20, 10, 10));

    /** Final strike. */
    public static final PoseSpec ULT_STRIKE = PoseSpec.REST
            .with(ROOT, r(8, -26, 0).offset(0, -4, 6))
            .with(BODY, r(16, -36, 0))
            .with(HEAD, r(-16, 56, 0))
            .with(RIGHT_ARM, r(-94, 52, 0))
            .with(LEFT_ARM, r(-10, 50, -30))
            .with(LEFT_LEG, r(-44, 6, -8))
            .with(RIGHT_LEG, r(44, 10, 10));
}
