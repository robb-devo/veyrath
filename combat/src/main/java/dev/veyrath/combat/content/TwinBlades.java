package dev.veyrath.combat.content;

import dev.veyrath.combat.anim.BoneSpec;
import dev.veyrath.combat.anim.Clip;
import dev.veyrath.combat.anim.ClipBuilder;
import dev.veyrath.combat.anim.Ease;
import dev.veyrath.combat.anim.PoseSpec;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.Motion;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Move.Kind;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;

import static dev.veyrath.combat.rig.Bone.BODY;
import static dev.veyrath.combat.rig.Bone.HEAD;
import static dev.veyrath.combat.rig.Bone.LEFT_ARM;
import static dev.veyrath.combat.rig.Bone.LEFT_LEG;
import static dev.veyrath.combat.rig.Bone.RIGHT_ARM;
import static dev.veyrath.combat.rig.Bone.RIGHT_LEG;
import static dev.veyrath.combat.rig.Bone.ROOT;

/**
 * Twin blades "Wren and Thorn": very fast, flowing, many small hits that build the gauge, cuts
 * that cancel into dashes, air dominance. One short blade in each hand. Abilities: Thousand Cuts
 * (a flurry in front), Shadow Step (a dash through the target with a cut on the way), Talon Spiral
 * (a rising spin that launches). Ultimate: Crimson Waltz, a chain of dashes through everything
 * around, then a cross cut. Yaw + turns toward the character's right.
 */
public final class TwinBlades {
    private TwinBlades() {}

    public static final String ID = "twin_blades";
    static final double BLADE = 15;

    static HitShape right() { return HitShape.onBone(RIGHT_ARM, new Vec3(-1, 10, -2), new Vec3(-1, 10, -2 - BLADE), 0.38); }
    static HitShape left() { return HitShape.onBone(LEFT_ARM, new Vec3(1, 10, -2), new Vec3(1, 10, -2 - BLADE), 0.38); }

    static BoneSpec r(double pitch, double yaw, double roll) { return BoneSpec.rot(pitch, yaw, roll); }

    // ---------------------------------------------------------------- poses

    /** Low, light stance: both blades forward, tips crossed, weight on the balls of the feet. */
    static final PoseSpec GUARD = PoseSpec.REST
            .with(ROOT, r(0, 10, 0).offset(0, -2, 0))
            .with(BODY, r(12, 4, 0))
            .with(HEAD, r(-10, -12, 0))
            .with(RIGHT_ARM, r(-58, 22, 0))
            .with(LEFT_ARM, r(-62, -22, 0))
            .with(RIGHT_LEG, r(18, 0, 6))
            .with(LEFT_LEG, r(-22, 0, -6));

    static final PoseSpec GUARD_LOW = GUARD.add(ROOT, BoneSpec.REST.offset(0, -0.5, 0));

    /** Right blade drawn back across the chest to the left (wind for a right-to-... cut). */
    static final PoseSpec R_WIND = GUARD.with(RIGHT_ARM, r(-90, -50, 0)).with(BODY, r(10, -24, 0)).with(ROOT, r(0, -14, 0).offset(0, -2, 0));
    /** Right blade swept out to the right. */
    static final PoseSpec R_CUT = GUARD.with(RIGHT_ARM, r(-88, 70, 0)).with(BODY, r(12, 26, 0)).with(ROOT, r(0, 22, 0).offset(0, -2.5, 1.5));
    static final PoseSpec L_WIND = GUARD.with(LEFT_ARM, r(-90, 50, 0)).with(BODY, r(10, 24, 0)).with(ROOT, r(0, 14, 0).offset(0, -2, 0));
    static final PoseSpec L_CUT = GUARD.with(LEFT_ARM, r(-88, -70, 0)).with(BODY, r(12, -26, 0)).with(ROOT, r(0, -22, 0).offset(0, -2.5, 1.5));
    /** Right blade low behind, ready to rise. */
    static final PoseSpec R_LOW = GUARD.with(RIGHT_ARM, r(-10, 30, 0)).with(BODY, r(20, 10, 0)).with(ROOT, r(0, 8, 0).offset(0, -3, 0));
    static final PoseSpec R_HIGH = GUARD.with(RIGHT_ARM, r(-165, 10, 0)).with(BODY, r(-6, 0, 0)).with(ROOT, r(0, 0, 0).offset(0, -1, 1));
    /** Both blades raised crossed above the head, then driven down in an X. */
    static final PoseSpec X_WIND = GUARD.with(RIGHT_ARM, r(-160, -30, 0)).with(LEFT_ARM, r(-160, 30, 0)).with(BODY, r(-8, 0, 0)).with(ROOT, r(0, 0, 0).offset(0, -1, -0.5));
    static final PoseSpec X_CUT = GUARD.with(RIGHT_ARM, r(-40, 40, 0)).with(LEFT_ARM, r(-40, -40, 0)).with(BODY, r(26, 0, 0)).with(ROOT, r(0, 0, 0).offset(0, -3.5, 2.5));
    /** Arms out to both sides for spins. */
    static final PoseSpec SPREAD = GUARD.with(RIGHT_ARM, r(-90, 85, 0)).with(LEFT_ARM, r(-90, -85, 0)).with(BODY, r(6, 0, 0)).with(ROOT, r(0, 0, 0).offset(0, -2, 0));
    /** Lunge with both blades thrust forward. */
    static final PoseSpec THRUST = GUARD.with(RIGHT_ARM, r(-96, 8, 0)).with(LEFT_ARM, r(-96, -8, 0)).with(BODY, r(20, 0, 0))
            .with(ROOT, r(0, 0, 0).offset(0, -3.5, 3.5)).with(RIGHT_LEG, r(32, 0, 6)).with(LEFT_LEG, r(-36, 0, -6));

    // ---------------------------------------------------------------- clips

    private static ClipBuilder c(String id, double len) { return ClipBuilder.clip(id, len); }

    private static Clip strike(String id, double len, PoseSpec wind, PoseSpec cut, double at) {
        return c(id, len).planted().key(0, GUARD).key(at - 1.5, wind, Ease.OUT_QUAD).key(at, cut, Ease.OUT_EXPO).key(at + 1.5, cut, Ease.LINEAR)
                .key(len, GUARD, Ease.IN_OUT_SINE).event(at, "impact").build();
    }

    private static Clip spin(String id, double len, int from, int to, double turns) {
        ClipBuilder b = c(id, len).noSink().key(0, GUARD).key(from, SPREAD, Ease.OUT_QUAD);
        int steps = (int) Math.ceil(turns * 6);
        for (int i = 1; i <= steps; i++) {
            b.key(from + (to - from) * i / (double) steps, SPREAD.with(ROOT, r(0, -360.0 * turns * i / steps, 0).offset(0, -2, 0)), Ease.LINEAR);
        }
        return b.key(len, GUARD.with(ROOT, r(0, -360.0 * turns + 10, 0).offset(0, -2, 0)), Ease.IN_OUT_SINE).event(from + 1, "impact").build();
    }

    public static void clips(ClipLibrary lib) {
        lib.add(c("tb_stance_idle", 32).planted().loop().key(0, GUARD).key(16, GUARD_LOW).key(32, GUARD).build());
        lib.add(strike("tb_cut_1", 9, R_WIND, R_CUT, 3));
        lib.add(strike("tb_cut_2", 9, L_WIND, L_CUT, 3));
        lib.add(strike("tb_cut_3", 10, R_LOW, R_HIGH, 3.5));
        lib.add(strike("tb_cut_4", 16, X_WIND, X_CUT, 6));
        ClipBuilder charge = c("tb_charge", 30).planted().key(0, GUARD).key(4, THRUST.with(ROOT, r(0, 0, 0).offset(0, -3, -1)), Ease.OUT_CUBIC);
        lib.add(charge.key(30, THRUST.with(ROOT, r(0, 0, 0).offset(0, -3.2, -1)), Ease.LINEAR).build());
        lib.add(strike("tb_heavy", 14, THRUST.with(ROOT, r(0, 0, 0).offset(0, -3, -1)), THRUST, 3));
        lib.add(spin("tb_cyclone", 24, 2, 18, 2));
        lib.add(spin("tb_air_spin", 12, 1, 10, 1));
        lib.add(c("tb_dive", 30).key(0, X_WIND).key(4, X_CUT, Ease.OUT_QUAD).key(30, X_CUT).build());
        lib.add(c("tb_dive_land", 12).noSink().key(0, X_CUT).key(4, X_CUT, Ease.LINEAR).key(12, GUARD, Ease.IN_OUT_SINE).event(0, "impact").build());
        ClipBuilder flurry = c("tb_thousand_cuts", 30).planted().key(0, GUARD);
        for (int k = 0; k < 8; k++) flurry.key(3 + 3 * k, k % 2 == 0 ? R_CUT : L_CUT, Ease.OUT_EXPO).event(3 + 3 * k, "impact");
        lib.add(flurry.key(30, GUARD, Ease.IN_OUT_SINE).build());
        lib.add(c("tb_shadow_step", 18).planted().key(0, GUARD).key(3, THRUST, Ease.OUT_EXPO).key(8, X_CUT, Ease.OUT_EXPO).key(18, GUARD, Ease.IN_OUT_SINE)
                .event(7, "impact").build());
        lib.add(spin("tb_talon_spiral", 20, 2, 12, 1.5));
        lib.add(c("tb_riposte", 12).planted().key(0, GUARD).key(2, THRUST, Ease.OUT_EXPO).key(4, THRUST, Ease.LINEAR).key(12, GUARD, Ease.IN_OUT_SINE)
                .event(2, "impact").build());
        ClipBuilder waltz = c("tb_crimson_waltz", 60).noSink().key(0, GUARD);
        for (int k = 0; k < 6; k++) {
            int t = 6 + 6 * k;
            waltz.key(t - 2, k % 2 == 0 ? R_WIND : L_WIND, Ease.OUT_QUAD).key(t, k % 2 == 0 ? R_CUT : L_CUT, Ease.OUT_EXPO).event(t, "impact");
        }
        waltz.key(44, X_WIND, Ease.OUT_QUAD).key(48, X_CUT, Ease.OUT_EXPO).key(54, X_CUT, Ease.LINEAR).key(60, GUARD, Ease.IN_OUT_SINE).event(48, "impact");
        lib.add(waltz.build());
    }

    // ---------------------------------------------------------------- moves

    public static MoveSet moveSet(MoveSet unarmed) {
        MoveSet s = new MoveSet();
        MoveSet.addStandardReactions(s);
        for (String id : new String[]{"dodge_forward", "dodge_back", "dodge_left", "dodge_right", "air_dash", "air_jump", "block_hit"}) {
            s.add(unarmed.require(id));
        }
        HitShape r = right(), l = left();
        HitSpec.Builder cut = HitSpec.of(2.2).stun(9, 6).push(0.1).hitstop(2).shake(0.14).guard(8).poise(10).gauge(3);
        s.add(Move.builder("tb_cut_1", Kind.ATTACK, 9).hit(2, 4, cut.fx("blade_light", "blade_cut").build(), r)
                .branch(Action.LIGHT, "tb_cut_2", 3, 9).branch(Action.HEAVY, "tb_heavy", 3, 9)
                .dodgeFrom(3).onHitCancel(1).abilityFrom(3).freeFrom(8).motion(new Motion(9).dash(0, 2, 0.2, 0.05).steer(0.2))
                .tracking(18, 2).blend(1).build());
        s.add(Move.builder("tb_cut_2", Kind.ATTACK, 9).hit(2, 4, cut.fx("blade_light", "blade_cut").build(), l)
                .branch(Action.LIGHT, "tb_cut_3", 3, 9).branch(Action.HEAVY, "tb_heavy", 3, 9)
                .dodgeFrom(3).onHitCancel(1).abilityFrom(3).freeFrom(8).motion(new Motion(9).dash(0, 2, 0.2, 0.05).steer(0.2))
                .tracking(18, 2).blend(1).build());
        s.add(Move.builder("tb_cut_3", Kind.ATTACK, 10)
                .hit(2, 4, HitSpec.of(2.6).reaction(Reaction.LAUNCH).stun(16, 6).push(0.05).lift(0.5).hitstop(3).shake(0.2).guard(10).poise(14)
                        .gauge(3).fx("impact_launch", "blade_cut").build(), r)
                .branch(Action.LIGHT, "tb_cut_4", 4, 10).branchOnHit(Action.JUMP, "air_jump", 4, 10)
                .dodgeFrom(4).onHitCancel(1).abilityFrom(4).freeFrom(9).motion(new Motion(10).dash(0, 2, 0.15, 0.04)).tracking(14, 2).blend(1).build());
        s.add(Move.builder("tb_cut_4", Kind.ATTACK, 16)
                .hit(5, 7, HitSpec.of(5).reaction(Reaction.KNOCKBACK).stun(16, 9).push(0.8).lift(0.25).hitstop(5).shake(0.4).guard(24).poise(30)
                        .gauge(5).fx("blade_cleave", "blade_cut").build(), r, l)
                .dodgeFrom(8).onHitCancel(2).abilityFrom(7).freeFrom(13).motion(new Motion(16).dash(3, 6, 0.35, 0.05)).tracking(12, 4).blend(1).build());
        s.add(Move.builder("tb_charge", Kind.CHARGE, 30).chargeMax(18).dodgeFrom(0).abilityFrom(0).motion(new Motion(30).steer(0.3))
                .tracking(20, 30).next("tb_cyclone").blend(2).build());
        s.add(Move.builder("tb_heavy", Kind.ATTACK, 14)
                .hit(2, 4, HitSpec.of(5).reaction(Reaction.STAGGER).stun(16, 9).push(0.5).hitstop(4).shake(0.3).guard(20).poise(26).gauge(4)
                        .fx("impact_heavy", "blade_cut").build(), r, l)
                .dodgeFrom(7).onHitCancel(2).abilityFrom(6).freeFrom(12).motion(new Motion(14).dash(0, 4, 0.75, 0.15)).tracking(16, 2).blend(1).build());
        s.add(Move.builder("tb_cyclone", Kind.ATTACK, 24)
                .hit(2, 7, HitSpec.of(2.5).stun(10, 6).push(0.15).hitstop(1).radial().shake(0.15).guard(8).gauge(2).fx("blade_light", "blade_cut").build(),
                        r, l, HitShape.sphere(0, 1.0, 0, 2.0))
                .hit(8, 13, HitSpec.of(2.5).stun(10, 6).push(0.15).hitstop(1).radial().shake(0.15).guard(8).gauge(2).fx("blade_light", "blade_cut").build(),
                        r, l, HitShape.sphere(0, 1.0, 0, 2.0))
                .hit(14, 18, HitSpec.of(4.5).reaction(Reaction.KNOCKBACK).stun(16, 9).push(1.0).lift(0.3).hitstop(4).radial().shake(0.4).guard(20)
                        .gauge(4).fx("blade_spin", "blade_cut").build(), r, l, HitShape.sphere(0, 1.0, 0, 2.3))
                .dodgeFrom(19).abilityFrom(19).freeFrom(22).motion(new Motion(24).dash(2, 18, 0.25, 0.25)).blend(1).build());
        s.add(Move.builder("tb_air_spin", Kind.ATTACK, 12).air()
                .hit(1, 5, HitSpec.of(2.2).reaction(Reaction.LAUNCH).stun(12, 6).push(0.05).lift(0.2).hitstop(2).radial().shake(0.12).gauge(2)
                        .fx("blade_light", "blade_cut").build(), r, l, HitShape.sphere(0, 0.8, 0, 1.6))
                .hit(6, 10, HitSpec.of(2.2).reaction(Reaction.LAUNCH).stun(12, 6).push(0.05).lift(0.2).hitstop(2).radial().shake(0.12).gauge(2)
                        .fx("blade_light", "blade_cut").build(), r, l, HitShape.sphere(0, 0.8, 0, 1.6))
                .branch(Action.LIGHT, "tb_air_spin", 10, 12).branch(Action.HEAVY, "tb_dive", 5, 12)
                .dodgeFrom(6).abilityFrom(6).freeFrom(11).motion(new Motion(12).holdVertical(0, 10, 0.03).gravity(0.2).steer(0.3)).blend(1).build());
        s.add(Move.builder("tb_dive", Kind.ATTACK, 30).air()
                .hit(3, 29, HitSpec.of(3).reaction(Reaction.SPIKE).stun(14, 8).push(0.1).hitstop(2).shake(0.2).build(),
                        HitShape.area(new Vec3(0, -0.3, 0.6), new Vec3(0, 1.0, 0.6), 0.9))
                .motion(new Motion(30).dash(0, 29, 0.6, 0.6).holdVertical(0, 2, 0.1).holdVertical(3, 29, -1.2))
                .endsOnLanding("tb_dive_land").blend(1).build());
        s.add(Move.builder("tb_dive_land", Kind.ATTACK, 12)
                .hit(0, 1, HitSpec.of(4).reaction(Reaction.LAUNCH).stun(14, 8).push(0.5).lift(0.4).hitstop(3).shake(0.4).guard(20).gauge(3)
                        .fx("shockwave_ring", "slam").build(), HitShape.sphere(0, 0.4, 0.4, 2.4))
                .dodgeFrom(5).abilityFrom(5).freeFrom(9).motion(new Motion(12).plant(0, 5)).blend(0.5).build());
        s.add(Move.builder("tb_parry", Kind.PARRY, 12).anywhere().clip("parry").iframes(0, 6)
                .branch(Action.LIGHT, "tb_riposte", 1, 12).branch(Action.HEAVY, "tb_riposte", 1, 12)
                .dodgeFrom(2).abilityFrom(2).freeFrom(10).blend(0.5).build());
        s.add(Move.builder("tb_riposte", Kind.ATTACK, 12)
                .hit(2, 3, HitSpec.of(8).reaction(Reaction.KNOCKBACK).stun(18, 10).push(0.8).lift(0.2).hitstop(5).shake(0.45).guard(36).poise(40)
                        .gauge(6).unparryable().fx("blade_cleave", "blade_cut").build(), r, l)
                .dodgeFrom(6).abilityFrom(5).freeFrom(10).motion(new Motion(12).dash(0, 2, 0.5, 0.05)).tracking(30, 1).blend(0.5).build());
        // abilities
        Move.Builder flurry = Move.builder("tb_thousand_cuts", Kind.ABILITY, 30).cost(0, 30, 0).cooldown(110).armor(2, 26);
        for (int k = 0; k < 8; k++) {
            int t = 3 + 3 * k;
            flurry.hit(t, t + 1, HitSpec.of(1.6).stun(8, 5).push(0.03).hitstop(1).shake(0.1).guard(6).gauge(1.5).fx("blade_light", "blade_cut").build(),
                    k % 2 == 0 ? r : l, HitShape.area(new Vec3(0, 1.0, 0.6), new Vec3(0, 1.0, 1.9), 0.9));
        }
        s.add(flurry.dodgeFrom(26).abilityFrom(26).freeFrom(28).motion(new Motion(30).dash(0, 26, 0.06, 0.06)).tracking(14, 26).blend(0.5).build());
        s.add(Move.builder("tb_shadow_step", Kind.ABILITY, 18).cost(0, 25, 0).cooldown(80).iframes(0, 9)
                .hit(5, 8, HitSpec.of(6).reaction(Reaction.STAGGER).stun(18, 9).push(0.2).hitstop(4).shake(0.35).guard(24).poise(30).gauge(4)
                        .unparryable().fx("impact_gale", "gale").build(), HitShape.area(new Vec3(0, 1.0, -0.5), new Vec3(0, 1.0, 1.2), 0.9))
                .motion(new Motion(18).dash(1, 8, 1.6, 0.6)).dodgeFrom(11).abilityFrom(10).freeFrom(15).tracking(25, 3).blend(0.5).build());
        s.add(Move.builder("tb_talon_spiral", Kind.ABILITY, 20).anywhere().cost(0, 30, 0).cooldown(120).armor(0, 10)
                .hit(2, 10, HitSpec.of(3.5).reaction(Reaction.LAUNCH).stun(22, 8).push(0.1).lift(0.9).hitstop(3).radial().shake(0.4).guard(26)
                        .poise(40).gauge(4).fx("impact_launch", "skybreaker").build(), r, l, HitShape.area(new Vec3(0, 0, 0), new Vec3(0, 2.4, 0), 2.0))
                .motion(new Motion(20).rise(2, 0.8).gravity(0.45).steer(0.3)).dodgeFrom(12).abilityFrom(12).freeFrom(14).blend(0.5).build());
        // ultimate: six dash-cuts through everything near, then the cross
        Move.Builder waltz = Move.builder("tb_crimson_waltz", Kind.ULTIMATE, 60).cost(0, 0, 100).iframes(0, 59).cinematic()
                .motion(new Motion(60).dash(4, 40, 0.55, 0.55).plant(41, 59)).tracking(40, 40).blend(1);
        for (int k = 0; k < 6; k++) {
            int t = 6 + 6 * k;
            waltz.hit(t, t + 1, HitSpec.of(3).stun(14, 8).push(0.05).hitstop(2).radial().shake(0.25).guard(20).gauge(0).unblockable().unparryable()
                    .fx("blade_light", "blade_cut").build(), r, l, HitShape.sphere(0, 1.0, 0, 2.6));
        }
        waltz.hit(48, 49, HitSpec.of(16).reaction(Reaction.LAUNCH).stun(30, 20).push(1.4).lift(0.8).hitstop(9).radial().shake(1.0).guard(200)
                .poise(200).gauge(0).unblockable().unparryable().fx("ultimate_impact", "ultimate").build(), HitShape.sphere(0, 1.0, 0.8, 3.4));
        s.add(waltz.build());

        s.groundLight = "tb_cut_1";
        s.airLight = "tb_air_spin";
        s.heavyCharge = "tb_charge";
        s.heavyTap = "tb_heavy";
        s.heavyCharged = "tb_cyclone";
        s.airHeavy = "tb_dive";
        s.dashAttack = "tb_heavy";
        s.dodgeForward = "dodge_forward";
        s.dodgeBack = "dodge_back";
        s.dodgeLeft = "dodge_left";
        s.dodgeRight = "dodge_right";
        s.airDash = "air_dash";
        s.airJump = "air_jump";
        s.parry = "tb_parry";
        s.riposte = "tb_riposte";
        s.blockHit = "block_hit";
        s.ultimate = "tb_crimson_waltz";
        s.abilities[0] = "tb_thousand_cuts";
        s.abilities[1] = "tb_shadow_step";
        s.abilities[2] = "tb_talon_spiral";
        s.validate();
        return s;
    }
}
