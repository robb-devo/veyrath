package dev.veyrath.combat.content;

import dev.veyrath.combat.anim.BoneSpec;
import dev.veyrath.combat.anim.ClipBuilder;
import dev.veyrath.combat.anim.Ease;
import dev.veyrath.combat.anim.PoseSpec;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.Motion;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Move.Kind;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;
import dev.veyrath.combat.rig.Bone;

import static dev.veyrath.combat.rig.Bone.BODY;
import static dev.veyrath.combat.rig.Bone.HEAD;
import static dev.veyrath.combat.rig.Bone.LEFT_ARM;
import static dev.veyrath.combat.rig.Bone.LEFT_LEG;
import static dev.veyrath.combat.rig.Bone.RIGHT_ARM;
import static dev.veyrath.combat.rig.Bone.RIGHT_LEG;
import static dev.veyrath.combat.rig.Bone.ROOT;

/**
 * Greatsword "Oathcleaver": slow, heavy, committed. Two-handed grip (right hand on the grip, the
 * left below it), wide arcs with super armour on the big swings, guard damage, a held charge.
 * Abilities: Rending Arc (a full turn), Grave Drop (leap and slam), Guard Crush (shoulder charge
 * that breaks guards). Ultimate: Sundering Verdict, three cleaves and a ground-splitting finisher.
 *
 * <p>The blade is a capsule on the right arm, from the hand along the arm's forward axis (the
 * way a held item points), so every hit follows the animated swing. Clip ids match move ids.
 */
public final class Greatsword {
    private Greatsword() {}

    public static final String ID = "greatsword";
    /** Blade length in model pixels from the hand (32 px = 2 blocks). */
    static final double BLADE = 30;

    static HitShape blade() { return HitShape.onBone(RIGHT_ARM, new Vec3(-1, 10, -4), new Vec3(-1, 10, -4 - BLADE), 0.42); }

    static BoneSpec r(double pitch, double yaw, double roll) { return BoneSpec.rot(pitch, yaw, roll); }

    // ---------------------------------------------------------------- poses

    /** Guard: blade held diagonally across the body, tip high and forward, feet wide. */
    static final PoseSpec GUARD = PoseSpec.REST
            .with(ROOT, r(0, -14, 0).offset(0, -1.5, 0))
            .with(BODY, r(6, -8, 0))
            .with(HEAD, r(-4, 20, 0))
            .with(RIGHT_ARM, r(-52, -18, 0))
            .with(LEFT_ARM, r(-58, 34, 0))
            .with(RIGHT_LEG, r(16, 0, 8))
            .with(LEFT_LEG, r(-18, 0, -8));

    static final PoseSpec GUARD_LOW = GUARD.add(ROOT, BoneSpec.REST.offset(0, -0.6, 0)).add(RIGHT_ARM, r(4, 0, 0)).add(LEFT_ARM, r(4, 0, 0));

    /** High over the right shoulder, ready to cut down. */
    static final PoseSpec HIGH_RIGHT = PoseSpec.REST
            .with(ROOT, r(0, 26, 0).offset(0, -1, -0.5))
            .with(BODY, r(-6, 26, 0))
            .with(HEAD, r(-2, -30, 0))
            .with(RIGHT_ARM, r(-160, 38, 0))
            .with(LEFT_ARM, r(-150, -6, 0))
            .with(RIGHT_LEG, r(18, 0, 8))
            .with(LEFT_LEG, r(-16, 0, -8));

    /** End of the downward diagonal cut: low on the left. */
    static final PoseSpec LOW_LEFT = PoseSpec.REST
            .with(ROOT, r(0, -24, 0).offset(0, -2.5, 2))
            .with(BODY, r(18, -30, 0))
            .with(HEAD, r(-14, 24, 0))
            .with(RIGHT_ARM, r(-28, -44, 0))
            .with(LEFT_ARM, r(-34, -62, 0))
            .with(RIGHT_LEG, r(22, 0, 8))
            .with(LEFT_LEG, r(-24, 0, -8));

    /** End of the rising backhand: high on the right again, body opened. */
    static final PoseSpec RISEN = PoseSpec.REST
            .with(ROOT, r(0, 30, 0).offset(0, -1, 1))
            .with(BODY, r(-8, 34, 0))
            .with(HEAD, r(0, -30, 0))
            .with(RIGHT_ARM, r(-150, 60, 0))
            .with(LEFT_ARM, r(-136, 24, 0))
            .with(RIGHT_LEG, r(14, 0, 8))
            .with(LEFT_LEG, r(-18, 0, -8));

    /** Both arms straight up, blade behind the head: the overhead wind-up. */
    static final PoseSpec OVERHEAD = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, 0, -1))
            .with(BODY, r(-14, 0, 0))
            .with(HEAD, r(-6, 0, 0))
            .with(RIGHT_ARM, r(-176, -6, 0))
            .with(LEFT_ARM, r(-172, 6, 0))
            .with(RIGHT_LEG, r(10, 0, 6))
            .with(LEFT_LEG, r(-18, 0, -6));

    /** The cleave lands: blade driven down in front, deep lunge. */
    static final PoseSpec CLEAVE = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, -4, 3))
            .with(BODY, r(30, 0, 0))
            .with(HEAD, r(-26, 0, 0))
            .with(RIGHT_ARM, r(-46, -4, 0))
            .with(LEFT_ARM, r(-44, 4, 0))
            .with(RIGHT_LEG, r(30, 0, 6))
            .with(LEFT_LEG, r(-36, 0, -6));

    /** Blade drawn back low on the right, coiled: the charge and the sweep's wind-up. */
    static final PoseSpec COIL = PoseSpec.REST
            .with(ROOT, r(0, 36, 0).offset(0, -2.5, -0.5))
            .with(BODY, r(10, 40, 0))
            .with(HEAD, r(-8, -40, 0))
            .with(RIGHT_ARM, r(-24, 76, 0))
            .with(LEFT_ARM, r(-34, 40, 0))
            .with(RIGHT_LEG, r(22, 0, 10))
            .with(LEFT_LEG, r(-20, 0, -10));

    /** Through the horizontal sweep: arms swung across to the left. */
    static final PoseSpec SWEPT = PoseSpec.REST
            .with(ROOT, r(0, -40, 0).offset(0, -2.5, 1.5))
            .with(BODY, r(10, -44, 0))
            .with(HEAD, r(-8, 40, 0))
            .with(RIGHT_ARM, r(-84, -80, 0))
            .with(LEFT_ARM, r(-80, -96, 0))
            .with(RIGHT_LEG, r(22, 0, 10))
            .with(LEFT_LEG, r(-22, 0, -10));

    /** Arms out to the right side, blade level: held through a full turn. */
    static final PoseSpec SPIN = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, -3, 0))
            .with(BODY, r(8, 20, 0))
            .with(RIGHT_ARM, r(-86, 84, 0))
            .with(LEFT_ARM, r(-84, 60, 0))
            .with(RIGHT_LEG, r(18, 0, 12))
            .with(LEFT_LEG, r(-18, 0, -12));

    /** Shoulder charge: torso low and turned, blade held flat across the chest. */
    static final PoseSpec SHOULDER = PoseSpec.REST
            .with(ROOT, r(0, 30, 0).offset(0, -2, 2))
            .with(BODY, r(26, 34, 0))
            .with(HEAD, r(-20, -30, 0))
            .with(RIGHT_ARM, r(-70, 60, 0))
            .with(LEFT_ARM, r(-50, 80, 0))
            .with(RIGHT_LEG, r(30, 0, 8))
            .with(LEFT_LEG, r(-34, 0, -8));

    /** Riposte thrust: blade driven straight ahead. */
    static final PoseSpec THRUST = PoseSpec.REST
            .with(ROOT, r(0, -8, 0).offset(0, -3, 3.5))
            .with(BODY, r(16, -10, 0))
            .with(HEAD, r(-14, 10, 0))
            .with(RIGHT_ARM, r(-100, -6, 0))
            .with(LEFT_ARM, r(-96, 16, 0))
            .with(RIGHT_LEG, r(30, 0, 6))
            .with(LEFT_LEG, r(-34, 0, -6));

    // ---------------------------------------------------------------- clips

    private static ClipBuilder c(String id, double len) { return ClipBuilder.clip(id, len); }

    public static void clips(ClipLibrary lib) {
        lib.add(c("gs_stance_idle", 48).planted().loop().key(0, GUARD).key(24, GUARD_LOW).key(48, GUARD).build());
        lib.add(c("gs_slash_1", 16).planted()
                .key(0, GUARD).key(4, HIGH_RIGHT, Ease.OUT_QUAD).key(6, LOW_LEFT, Ease.OUT_EXPO).key(8, LOW_LEFT, Ease.LINEAR)
                .key(16, GUARD, Ease.IN_OUT_SINE).event(5, "impact").build());
        lib.add(c("gs_slash_2", 16).planted()
                .key(0, LOW_LEFT).key(3, LOW_LEFT.add(ROOT, r(0, 6, 0)), Ease.OUT_SINE).key(5, RISEN, Ease.OUT_EXPO)
                .key(7, RISEN, Ease.LINEAR).key(16, GUARD, Ease.IN_OUT_SINE).event(4, "impact").build());
        lib.add(c("gs_slash_3", 24).noSink()
                .key(0, RISEN).key(7, OVERHEAD, Ease.OUT_QUAD).key(9, CLEAVE, Ease.OUT_EXPO).key(13, CLEAVE, Ease.LINEAR)
                .key(24, GUARD, Ease.IN_OUT_SINE).event(9, "impact").build());
        ClipBuilder charge = c("gs_charge", 44).planted().key(0, GUARD).key(5, COIL, Ease.OUT_CUBIC);
        for (int t = 6; t <= 44; t += 2) charge.key(t, (t % 4 == 2) ? COIL : COIL.add(ROOT, BoneSpec.REST.offset(0, -0.3, 0)), Ease.LINEAR);
        lib.add(charge.build());
        lib.add(c("gs_heavy", 20).planted()
                .key(0, COIL).key(4, COIL.add(BODY, r(0, -6, 0)), Ease.OUT_SINE).key(7, SWEPT, Ease.OUT_EXPO).key(9, SWEPT, Ease.LINEAR)
                .key(20, GUARD, Ease.IN_OUT_SINE).event(6, "impact").build());
        lib.add(spin("gs_heavy_charged", 26, 4, 14));
        lib.add(spin("gs_rending_arc", 30, 4, 18));
        lib.add(c("gs_grave_drop", 34).noSink()
                .key(0, GUARD).key(5, OVERHEAD, Ease.OUT_QUAD).key(18, OVERHEAD, Ease.LINEAR).key(21, CLEAVE, Ease.OUT_EXPO)
                .key(26, CLEAVE, Ease.LINEAR).key(34, GUARD, Ease.IN_OUT_SINE).event(21, "impact").build());
        lib.add(c("gs_guard_crush", 22).planted()
                .key(0, GUARD).key(4, SHOULDER.add(ROOT, BoneSpec.REST.offset(0, 0, -1)), Ease.OUT_QUAD).key(8, SHOULDER, Ease.OUT_EXPO)
                .key(12, SHOULDER, Ease.LINEAR).key(22, GUARD, Ease.IN_OUT_SINE).event(8, "impact").build());
        lib.add(c("gs_riposte", 16).planted()
                .key(0, GUARD).key(2, THRUST, Ease.OUT_EXPO).key(5, THRUST, Ease.LINEAR).key(16, GUARD, Ease.IN_OUT_SINE)
                .event(2, "impact").build());
        lib.add(c("gs_air_cleave", 14)
                .key(0, OVERHEAD).key(3, CLEAVE.with(ROOT, r(0, 0, 0)), Ease.OUT_EXPO).key(6, CLEAVE.with(ROOT, r(0, 0, 0)), Ease.LINEAR)
                .key(14, GUARD, Ease.IN_OUT_SINE).event(3, "impact").build());
        lib.add(c("gs_plunge", 30).key(0, OVERHEAD).key(4, CLEAVE.with(ROOT, r(20, 0, 0)), Ease.OUT_QUAD).key(30, CLEAVE.with(ROOT, r(20, 0, 0))).build());
        lib.add(c("gs_plunge_land", 16).noSink().key(0, CLEAVE).key(6, CLEAVE, Ease.LINEAR).key(16, GUARD, Ease.IN_OUT_SINE).event(0, "impact").build());
        // Ultimate: three cleaves, each one lower and harder, then the ground splits.
        ClipBuilder ult = c("gs_sundering_verdict", 64).noSink().key(0, GUARD);
        int[] cuts = {6, 18, 30};
        for (int k = 0; k < 3; k++) {
            int t = cuts[k];
            ult.key(t - 4, OVERHEAD, Ease.OUT_QUAD).key(t, CLEAVE, Ease.OUT_EXPO).key(t + 3, CLEAVE, Ease.LINEAR).event(t, "impact");
        }
        ult.key(40, OVERHEAD.add(ROOT, BoneSpec.REST.offset(0, 2, 0)), Ease.OUT_QUAD).key(46, OVERHEAD.add(ROOT, BoneSpec.REST.offset(0, 2, 0)), Ease.LINEAR)
                .key(48, CLEAVE.add(ROOT, BoneSpec.REST.offset(0, -1, 1)), Ease.OUT_EXPO).key(54, CLEAVE, Ease.LINEAR)
                .key(64, GUARD, Ease.IN_OUT_SINE).event(48, "impact");
        lib.add(ult.build());
    }

    /** A full turn with the blade held out: root yaw from 0 to -360 between {@code from} and {@code to}. */
    private static dev.veyrath.combat.anim.Clip spin(String id, int len, int from, int to) {
        ClipBuilder b = c(id, len).planted().key(0, COIL).key(from, SPIN.with(ROOT, r(0, 0, 0).offset(0, -3, 0)), Ease.OUT_QUAD);
        int steps = 6;
        for (int i = 1; i <= steps; i++) {
            double t = from + (to - from) * i / (double) steps;
            b.key(t, SPIN.with(ROOT, r(0, -360.0 * i / steps, 0).offset(0, -3, 0)), Ease.LINEAR);
        }
        b.key(len, GUARD.with(ROOT, r(0, -374, 0).offset(0, -1.5, 0)), Ease.IN_OUT_SINE);
        b.event(from + 2, "impact");
        return b.build();
    }

    // ---------------------------------------------------------------- moves

    public static MoveSet moveSet(MoveSet unarmed) {
        MoveSet s = new MoveSet();
        MoveSet.addStandardReactions(s);
        // Movement and defence are shared with the body: the same dodges, jumps and parry.
        for (String id : new String[]{"dodge_forward", "dodge_back", "dodge_left", "dodge_right", "air_dash", "air_jump", "block_hit"}) {
            s.add(unarmed.require(id));
        }
        HitShape blade = blade();
        // ------------------------------------------------ light string: three big cuts
        s.add(Move.builder("gs_slash_1", Kind.ATTACK, 16)
                .hit(4, 6, HitSpec.of(6).reaction(Reaction.STAGGER).stun(14, 9).push(0.35).hitstop(4).shake(0.3).guard(18).poise(30)
                        .gauge(3).fx("blade_light", "hit_heavy").build(), blade)
                .branch(Action.LIGHT, "gs_slash_2", 6, 16).branch(Action.HEAVY, "gs_heavy", 6, 16)
                .armor(3, 6).dodgeFrom(9).onHitCancel(2).abilityFrom(8).freeFrom(14)
                .motion(new Motion(16).dash(2, 5, 0.22, 0.05).steer(0.1))
                .tracking(10, 3).blend(1.5).build());
        s.add(Move.builder("gs_slash_2", Kind.ATTACK, 16)
                .hit(3, 5, HitSpec.of(6.5).reaction(Reaction.STAGGER).stun(14, 9).push(0.4).lift(0.15).hitstop(4).shake(0.32).guard(20)
                        .poise(32).gauge(3).fx("blade_light", "hit_heavy").build(), blade)
                .branch(Action.LIGHT, "gs_slash_3", 5, 16).branch(Action.HEAVY, "gs_heavy", 5, 16)
                .armor(2, 5).dodgeFrom(8).onHitCancel(2).abilityFrom(7).freeFrom(14)
                .motion(new Motion(16).dash(1, 4, 0.2, 0.05).steer(0.1))
                .tracking(10, 2).blend(1).build());
        s.add(Move.builder("gs_slash_3", Kind.ATTACK, 24)
                .hit(8, 10, HitSpec.of(11).reaction(Reaction.KNOCKDOWN).stun(22, 12).push(0.7).hitstop(7).shake(0.6).guard(45).poise(70)
                        .gauge(5).fx("blade_cleave", "slam").build(), blade, HitShape.area(new Vec3(0, 0.2, 1.0), new Vec3(0, 0.2, 2.6), 0.9))
                .armor(4, 9).dodgeFrom(14).onHitCancel(3).abilityFrom(13).freeFrom(21)
                .motion(new Motion(24).dash(5, 9, 0.35, 0.05).plant(10, 18))
                .tracking(8, 5).blend(1).build());
        // ------------------------------------------------ heavies
        s.add(Move.builder("gs_charge", Kind.CHARGE, 44).chargeMax(30).dodgeFrom(0).abilityFrom(0)
                .motion(new Motion(44).steer(0.2)).tracking(14, 44).next("gs_heavy_charged").blend(2).build());
        s.add(Move.builder("gs_heavy", Kind.ATTACK, 20)
                .hit(5, 8, HitSpec.of(9).reaction(Reaction.KNOCKBACK).stun(18, 10).push(1.0).lift(0.2).hitstop(6).shake(0.5).guard(40)
                        .poise(60).gauge(5).fx("blade_sweep", "hit_charged").build(), blade)
                .armor(3, 8).dodgeFrom(12).onHitCancel(2).abilityFrom(11).freeFrom(18)
                .motion(new Motion(20).dash(3, 7, 0.3, 0.05)).tracking(8, 4).blend(1.5).build());
        s.add(Move.builder("gs_heavy_charged", Kind.ATTACK, 26)
                .hit(5, 14, HitSpec.of(14).reaction(Reaction.KNOCKBACK).stun(24, 12).push(1.6).lift(0.35).hitstop(8).radial().shake(0.8)
                        .guard(70).poise(100).gauge(7).fx("blade_spin", "hit_charged").build(),
                        blade, HitShape.sphere(0, 1.0, 0, 2.9))
                .armor(0, 14).dodgeFrom(18).abilityFrom(17).freeFrom(23)
                .motion(new Motion(26).dash(2, 12, 0.25, 0.1).plant(14, 26)).blend(1).build());
        // ------------------------------------------------ air
        s.add(Move.builder("gs_air_cleave", Kind.ATTACK, 14).air()
                .hit(2, 4, HitSpec.of(6).reaction(Reaction.SPIKE).stun(14, 8).push(0.1).hitstop(5).shake(0.4).guard(24).poise(40)
                        .gauge(3).fx("blade_cleave", "hit_heavy").build(), blade)
                .branch(Action.HEAVY, "gs_plunge", 4, 14).dodgeFrom(7).abilityFrom(7).freeFrom(12)
                .motion(new Motion(14).holdVertical(0, 5, 0.0).gravity(0.4)).tracking(12, 2).blend(1).build());
        s.add(Move.builder("gs_plunge", Kind.ATTACK, 30).air()
                .hit(4, 29, HitSpec.of(5).reaction(Reaction.SPIKE).stun(14, 8).push(0.05).hitstop(2).shake(0.2).build(),
                        HitShape.area(new Vec3(0, -0.4, 0.4), new Vec3(0, 1.0, 0.4), 1.0))
                .motion(new Motion(30).holdVertical(0, 3, 0.1).holdVertical(4, 29, -1.8).steer(0.1))
                .endsOnLanding("gs_plunge_land").blend(1).build());
        s.add(Move.builder("gs_plunge_land", Kind.ATTACK, 16)
                .hit(0, 1, HitSpec.of(9).reaction(Reaction.LAUNCH).stun(18, 10).push(0.7).lift(0.5).hitstop(6).shake(0.8).guard(50)
                        .poise(70).gauge(5).fx("shockwave_ring", "slam").build(), HitShape.sphere(0, 0.4, 0.6, 3.6))
                .dodgeFrom(10).abilityFrom(10).freeFrom(14).motion(new Motion(16).plant(0, 8)).blend(0.5).build());
        // ------------------------------------------------ defence follow-up
        s.add(Move.builder("gs_parry", Kind.PARRY, 12).anywhere().clip("parry").iframes(0, 6)
                .branch(Action.LIGHT, "gs_riposte", 1, 12).branch(Action.HEAVY, "gs_riposte", 1, 12)
                .dodgeFrom(2).abilityFrom(2).freeFrom(10).blend(0.5).build());
        s.add(Move.builder("gs_riposte", Kind.ATTACK, 16)
                .hit(2, 4, HitSpec.of(12).reaction(Reaction.KNOCKBACK).stun(20, 12).push(1.0).lift(0.25).hitstop(7).shake(0.6).guard(50)
                        .poise(60).gauge(6).unparryable().fx("blade_cleave", "hit_heavy").build(), blade)
                .dodgeFrom(9).abilityFrom(8).freeFrom(14).motion(new Motion(16).dash(0, 3, 0.45, 0.05))
                .tracking(30, 1).blend(0.5).build());
        // ------------------------------------------------ abilities
        s.add(Move.builder("gs_rending_arc", Kind.ABILITY, 30).cost(0, 30, 0).cooldown(120).armor(0, 18)
                .hit(5, 18, HitSpec.of(9).reaction(Reaction.KNOCKBACK).stun(22, 12).push(1.4).lift(0.4).hitstop(6).radial().shake(0.6)
                        .guard(50).poise(80).gauge(5).fx("blade_spin", "hit_charged").build(), blade, HitShape.sphere(0, 1.0, 0, 3.2))
                .dodgeFrom(22).abilityFrom(22).freeFrom(27)
                .motion(new Motion(30).dash(4, 18, 0.35, 0.2).plant(18, 30)).blend(0.5).build());
        s.add(Move.builder("gs_grave_drop", Kind.ABILITY, 34).anywhere().cost(0, 40, 0).cooldown(180).armor(0, 21).iframes(4, 12)
                .hit(21, 22, HitSpec.of(15).reaction(Reaction.LAUNCH).stun(24, 14).push(0.9).lift(0.7).hitstop(8).shake(0.9).guard(70)
                        .poise(110).gauge(6).fx("shockwave_ring", "slam").build(), HitShape.sphere(0, 0.4, 1.6, 4.0))
                .dodgeFrom(27).abilityFrom(27).freeFrom(30)
                .motion(new Motion(34).rise(3, 0.95).dash(4, 18, 0.24, 0.14).gravity(1.6).plant(21, 34)).tracking(12, 16).blend(0.5).build());
        s.add(Move.builder("gs_guard_crush", Kind.ABILITY, 22).cost(0, 25, 0).cooldown(100).armor(2, 12)
                .hit(7, 10, HitSpec.of(7).reaction(Reaction.STAGGER).stun(26, 14).push(1.2).hitstop(6).shake(0.5).guard(140).poise(90)
                        .gauge(4).fx("guard_break", "hit_heavy").build(), HitShape.area(new Vec3(0, 1.0, 0.4), new Vec3(0, 1.0, 1.6), 1.0))
                .dodgeFrom(14).abilityFrom(13).freeFrom(19)
                .motion(new Motion(22).dash(3, 10, 1.25, 0.4)).tracking(16, 6).blend(0.5).build());
        // ------------------------------------------------ ultimate
        Move.Builder ult = Move.builder("gs_sundering_verdict", Kind.ULTIMATE, 64).cost(0, 0, 100).iframes(0, 63).cinematic()
                .motion(new Motion(64).dash(0, 5, 0.6, 0.2).plant(6, 63)).tracking(20, 40).blend(1);
        int[] cuts = {6, 18, 30};
        for (int cut : cuts) {
            ult.hit(cut, cut + 1, HitSpec.of(8).reaction(Reaction.STAGGER).stun(14, 10).push(0.1).hitstop(5).shake(0.55).guard(60).gauge(0)
                    .unblockable().unparryable().fx("blade_cleave", "slam").build(), blade, HitShape.area(new Vec3(0, 0.2, 1.0), new Vec3(0, 0.2, 2.8), 1.1));
        }
        ult.hit(48, 49, HitSpec.of(24).reaction(Reaction.LAUNCH).stun(32, 20).push(1.8).lift(0.9).hitstop(10).shake(1.0).guard(200).poise(200)
                .gauge(0).unblockable().unparryable().fx("ultimate_impact", "ultimate").build(),
                HitShape.area(new Vec3(0, 0.3, 0.6), new Vec3(0, 0.3, 7.5), 1.8));
        s.add(ult.build());

        s.groundLight = "gs_slash_1";
        s.airLight = "gs_air_cleave";
        s.heavyCharge = "gs_charge";
        s.heavyTap = "gs_heavy";
        s.heavyCharged = "gs_heavy_charged";
        s.airHeavy = "gs_plunge";
        s.dashAttack = "gs_slash_1";
        s.dodgeForward = "dodge_forward";
        s.dodgeBack = "dodge_back";
        s.dodgeLeft = "dodge_left";
        s.dodgeRight = "dodge_right";
        s.airDash = "air_dash";
        s.airJump = "air_jump";
        s.parry = "gs_parry";
        s.riposte = "gs_riposte";
        s.blockHit = "block_hit";
        s.ultimate = "gs_sundering_verdict";
        s.abilities[0] = "gs_rending_arc";
        s.abilities[1] = "gs_grave_drop";
        s.abilities[2] = "gs_guard_crush";
        s.validate();
        return s;
    }

    /** The bone the blade hangs from (the renderer draws the model there). */
    public static Bone gripBone() { return RIGHT_ARM; }
}
