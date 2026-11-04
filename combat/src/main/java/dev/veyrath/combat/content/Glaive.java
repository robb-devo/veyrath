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
 * Glaive "Skyreach": measured, about spacing. The longest reach, thrusts that keep enemies at the
 * tip, sweeps that clear a circle, a vault over the enemy. Abilities: Vault Thrust, Crescent Sweep,
 * Piercing Gale. Ultimate: Heavenfall, the glaive thrown high and a rain of strikes around the
 * target, then the recall. The head is a capsule far out along the shaft; the shaft itself does
 * not cut. Yaw + turns toward the character's right.
 */
public final class Glaive {
    private Glaive() {}

    public static final String ID = "glaive";

    /** The head, along the arm's own line beyond the hand (the glaive is held as an extension of the arm). */
    static HitShape head() { return HitShape.onBone(RIGHT_ARM, new Vec3(-1, 40, 0), new Vec3(-1, 60, 0), 0.45); }

    static BoneSpec r(double pitch, double yaw, double roll) { return BoneSpec.rot(pitch, yaw, roll); }

    // ---------------------------------------------------------------- poses

    /** Side-on stance, glaive held level and forward, point at the enemy, rear hand by the hip. */
    static final PoseSpec GUARD = PoseSpec.REST
            .with(ROOT, r(0, 24, 0).offset(0, -1.5, 0))
            .with(BODY, r(6, 10, 0))
            .with(HEAD, r(-4, -30, 0))
            .with(RIGHT_ARM, r(-78, -34, 0))
            .with(LEFT_ARM, r(-40, -10, 0))
            .with(RIGHT_LEG, r(18, 0, 8))
            .with(LEFT_LEG, r(-22, 0, -8));

    static final PoseSpec GUARD_LOW = GUARD.add(ROOT, BoneSpec.REST.offset(0, -0.5, 0)).add(RIGHT_ARM, r(3, 0, 0));
    /** Drawn back for a thrust. */
    static final PoseSpec DRAWN = GUARD.with(RIGHT_ARM, r(-70, -40, 0)).with(BODY, r(2, 20, 0)).with(ROOT, r(0, 30, 0).offset(0, -1.5, -1.5));
    /** Full extension: the thrust lands. */
    static final PoseSpec THRUST = GUARD.with(RIGHT_ARM, r(-92, -12, 0)).with(LEFT_ARM, r(-80, 10, 0)).with(BODY, r(16, 0, 0))
            .with(ROOT, r(0, 6, 0).offset(0, -3, 4)).with(RIGHT_LEG, r(30, 0, 6)).with(LEFT_LEG, r(-36, 0, -6));

    // ---------------------------------------------------------------- clips

    private static ClipBuilder c(String id, double len) { return ClipBuilder.clip(id, len); }

    private static Clip thrust(String id, double len, double at) {
        return c(id, len).planted().key(0, GUARD).key(at - 2, DRAWN, Ease.OUT_QUAD).key(at, THRUST, Ease.OUT_EXPO).key(at + 2, THRUST, Ease.LINEAR)
                .key(len, GUARD, Ease.IN_OUT_SINE).event(at, "impact").build();
    }

    private static Clip spin(String id, double len, int from, int to, double turns) {
        ClipBuilder b = c(id, len).planted().key(0, Greatsword.COIL).key(from, Greatsword.SPIN, Ease.OUT_QUAD);
        int steps = (int) Math.ceil(turns * 6);
        for (int i = 1; i <= steps; i++) {
            b.key(from + (to - from) * i / (double) steps, Greatsword.SPIN.with(ROOT, r(0, -360.0 * turns * i / steps, 0).offset(0, -3, 0)), Ease.LINEAR);
        }
        return b.key(len, GUARD.with(ROOT, r(0, -360.0 * turns + 24, 0).offset(0, -1.5, 0)), Ease.IN_OUT_SINE).event(from + 1, "impact").build();
    }

    public static void clips(ClipLibrary lib) {
        lib.add(c("gl_stance_idle", 44).planted().loop().key(0, GUARD).key(22, GUARD_LOW).key(44, GUARD).build());
        lib.add(thrust("gl_thrust_1", 12, 4));
        lib.add(c("gl_thrust_2", 16).planted().key(0, GUARD).key(2, DRAWN, Ease.OUT_QUAD).key(4, THRUST, Ease.OUT_EXPO).key(6, DRAWN, Ease.IN_OUT_SINE)
                .key(8, THRUST, Ease.OUT_EXPO).key(10, THRUST, Ease.LINEAR).key(16, GUARD, Ease.IN_OUT_SINE).event(4, "impact").event(8, "impact").build());
        lib.add(c("gl_sweep", 20).planted().key(0, GUARD).key(5, Greatsword.COIL, Ease.OUT_QUAD).key(8, Greatsword.SWEPT, Ease.OUT_EXPO)
                .key(10, Greatsword.SWEPT, Ease.LINEAR).key(20, GUARD, Ease.IN_OUT_SINE).event(7, "impact").build());
        ClipBuilder charge = c("gl_charge", 40).planted().key(0, GUARD).key(5, DRAWN.add(ROOT, BoneSpec.REST.offset(0, -1, -1)), Ease.OUT_CUBIC);
        lib.add(charge.key(40, DRAWN.add(ROOT, BoneSpec.REST.offset(0, -1.2, -1)), Ease.LINEAR).build());
        lib.add(thrust("gl_heavy", 18, 5));
        lib.add(spin("gl_heavy_charged", 24, 3, 14, 1));
        lib.add(spin("gl_air_sweep", 14, 1, 10, 1));
        lib.add(c("gl_impale_dive", 30).key(0, Greatsword.OVERHEAD).key(4, Greatsword.CLEAVE, Ease.OUT_QUAD).key(30, Greatsword.CLEAVE).build());
        lib.add(c("gl_impale_land", 14).noSink().key(0, Greatsword.CLEAVE).key(5, Greatsword.CLEAVE, Ease.LINEAR).key(14, GUARD, Ease.IN_OUT_SINE)
                .event(0, "impact").build());
        lib.add(c("gl_vault_thrust", 30).noSink().key(0, GUARD).key(6, DRAWN, Ease.OUT_QUAD).key(14, Greatsword.OVERHEAD, Ease.IN_OUT_SINE)
                .key(20, Greatsword.CLEAVE, Ease.OUT_EXPO).key(24, Greatsword.CLEAVE, Ease.LINEAR).key(30, GUARD, Ease.IN_OUT_SINE).event(20, "impact").build());
        lib.add(spin("gl_crescent_sweep", 26, 3, 18, 1.25));
        lib.add(thrust("gl_piercing_gale", 22, 6));
        lib.add(thrust("gl_riposte", 14, 2));
        ClipBuilder fall = c("gl_heavenfall", 70).noSink().key(0, GUARD).key(6, Greatsword.OVERHEAD, Ease.OUT_QUAD).key(10, Greatsword.OVERHEAD.with(RIGHT_ARM, r(-180, 0, 0)), Ease.OUT_EXPO);
        fall.key(46, Greatsword.OVERHEAD, Ease.LINEAR).key(52, Greatsword.CLEAVE, Ease.OUT_EXPO).key(60, Greatsword.CLEAVE, Ease.LINEAR).key(70, GUARD, Ease.IN_OUT_SINE);
        for (int k = 0; k < 6; k++) fall.event(16 + 5 * k, "impact");
        lib.add(fall.event(52, "impact").build());
    }

    // ---------------------------------------------------------------- moves

    public static MoveSet moveSet(MoveSet unarmed) {
        MoveSet s = new MoveSet();
        MoveSet.addStandardReactions(s);
        for (String id : new String[]{"dodge_forward", "dodge_back", "dodge_left", "dodge_right", "air_dash", "air_jump", "block_hit"}) {
            s.add(unarmed.require(id));
        }
        HitShape h = head();
        s.add(Move.builder("gl_thrust_1", Kind.ATTACK, 12)
                .hit(4, 5, HitSpec.of(4).reaction(Reaction.FLINCH).stun(12, 7).push(0.45).hitstop(3).shake(0.2).guard(12).poise(16).gauge(3)
                        .fx("impact_light", "blade_cut").build(), h)
                .branch(Action.LIGHT, "gl_thrust_2", 5, 12).branch(Action.HEAVY, "gl_sweep", 5, 12)
                .dodgeFrom(6).onHitCancel(2).abilityFrom(6).freeFrom(11).motion(new Motion(12).dash(2, 4, 0.2, 0.04)).tracking(12, 3).blend(1).build());
        s.add(Move.builder("gl_thrust_2", Kind.ATTACK, 16)
                .hit(4, 5, HitSpec.of(3.5).reaction(Reaction.FLINCH).stun(12, 7).push(0.3).hitstop(2).shake(0.18).guard(10).gauge(2.5).fx("impact_light", "blade_cut").build(), h)
                .hit(8, 9, HitSpec.of(4.5).reaction(Reaction.STAGGER).stun(14, 8).push(0.7).hitstop(3).shake(0.25).guard(14).poise(20).gauge(3)
                        .fx("impact_heavy", "blade_cut").build(), h)
                .branch(Action.LIGHT, "gl_sweep", 9, 16).dodgeFrom(10).onHitCancel(2).abilityFrom(10).freeFrom(14)
                .motion(new Motion(16).dash(2, 9, 0.15, 0.05)).tracking(10, 6).blend(1).build());
        s.add(Move.builder("gl_sweep", Kind.ATTACK, 20)
                .hit(6, 9, HitSpec.of(6.5).reaction(Reaction.KNOCKBACK).stun(18, 10).push(1.1).lift(0.2).hitstop(5).radial().shake(0.45).guard(28)
                        .poise(40).gauge(4).fx("blade_sweep", "blade_cut").build(), h)
                .dodgeFrom(12).onHitCancel(2).abilityFrom(11).freeFrom(18).motion(new Motion(20).dash(3, 7, 0.15, 0.05)).tracking(8, 4).blend(1).build());
        s.add(Move.builder("gl_charge", Kind.CHARGE, 40).chargeMax(24).dodgeFrom(0).abilityFrom(0).motion(new Motion(40).steer(0.25))
                .tracking(16, 40).next("gl_heavy_charged").blend(2).build());
        s.add(Move.builder("gl_heavy", Kind.ATTACK, 18)
                .hit(5, 7, HitSpec.of(8).reaction(Reaction.KNOCKBACK).stun(18, 10).push(1.4).lift(0.15).hitstop(6).shake(0.5).guard(36).poise(50)
                        .gauge(5).fx("impact_charged", "hit_charged").build(), h, HitShape.area(new Vec3(0, 1.1, 1.2), new Vec3(0, 1.1, 4.0), 0.6))
                .armor(3, 7).dodgeFrom(10).abilityFrom(9).freeFrom(16).motion(new Motion(18).dash(3, 7, 0.9, 0.1)).tracking(10, 4).blend(1).build());
        s.add(Move.builder("gl_heavy_charged", Kind.ATTACK, 24)
                .hit(3, 14, HitSpec.of(10).reaction(Reaction.KNOCKBACK).stun(22, 12).push(1.6).lift(0.35).hitstop(7).radial().shake(0.7).guard(50)
                        .poise(80).gauge(6).fx("blade_spin", "hit_charged").build(), h, HitShape.sphere(0, 1.0, 0, 3.6))
                .armor(0, 14).dodgeFrom(17).abilityFrom(16).freeFrom(22).motion(new Motion(24).plant(0, 24)).blend(1).build());
        s.add(Move.builder("gl_air_sweep", Kind.ATTACK, 14).air()
                .hit(1, 9, HitSpec.of(4).reaction(Reaction.LAUNCH).stun(14, 7).push(0.3).lift(0.25).hitstop(3).radial().shake(0.25).gauge(3)
                        .fx("blade_spin", "blade_cut").build(), h, HitShape.sphere(0, 0.8, 0, 2.8))
                .branch(Action.HEAVY, "gl_impale_dive", 6, 14).dodgeFrom(8).abilityFrom(8).freeFrom(12)
                .motion(new Motion(14).holdVertical(0, 9, 0.02).gravity(0.3)).blend(1).build());
        s.add(Move.builder("gl_impale_dive", Kind.ATTACK, 30).air()
                .hit(3, 29, HitSpec.of(4).reaction(Reaction.SPIKE).stun(14, 8).push(0.05).hitstop(2).shake(0.2).build(),
                        HitShape.area(new Vec3(0, -0.5, 0.8), new Vec3(0, 1.0, 0.8), 1.0))
                .motion(new Motion(30).holdVertical(0, 2, 0.1).holdVertical(3, 29, -1.7).steer(0.1)).endsOnLanding("gl_impale_land").blend(1).build());
        s.add(Move.builder("gl_impale_land", Kind.ATTACK, 14)
                .hit(0, 1, HitSpec.of(8).reaction(Reaction.LAUNCH).stun(18, 10).push(0.6).lift(0.5).hitstop(5).shake(0.7).guard(40).poise(60).gauge(4)
                        .fx("shockwave_ring", "slam").build(), HitShape.sphere(0, 0.4, 1.0, 3.2))
                .dodgeFrom(8).abilityFrom(8).freeFrom(12).motion(new Motion(14).plant(0, 7)).blend(0.5).build());
        s.add(Move.builder("gl_parry", Kind.PARRY, 12).anywhere().clip("parry").iframes(0, 6)
                .branch(Action.LIGHT, "gl_riposte", 1, 12).branch(Action.HEAVY, "gl_riposte", 1, 12)
                .dodgeFrom(2).abilityFrom(2).freeFrom(10).blend(0.5).build());
        s.add(Move.builder("gl_riposte", Kind.ATTACK, 14)
                .hit(2, 4, HitSpec.of(10).reaction(Reaction.KNOCKBACK).stun(20, 12).push(1.3).lift(0.2).hitstop(6).shake(0.55).guard(44).poise(56)
                        .gauge(6).unparryable().fx("impact_charged", "blade_cut").build(), h)
                .dodgeFrom(8).abilityFrom(7).freeFrom(12).motion(new Motion(14).dash(0, 3, 0.5, 0.05)).tracking(30, 1).blend(0.5).build());
        // abilities
        s.add(Move.builder("gl_vault_thrust", Kind.ABILITY, 30).cost(0, 30, 0).cooldown(120).iframes(4, 16)
                .hit(20, 22, HitSpec.of(9).reaction(Reaction.SPIKE).stun(22, 12).push(0.3).hitstop(6).shake(0.6).guard(40).poise(60).gauge(5)
                        .unparryable().fx("blade_cleave", "slam").build(), h, HitShape.sphere(0, 0.6, -0.4, 2.4))
                // a short vault: it lands on or just past the enemy and strikes all around the landing
                .motion(new Motion(30).rise(5, 0.75).dash(5, 18, 0.2, 0.16).gravity(1.2).plant(21, 30))
                .dodgeFrom(24).abilityFrom(24).freeFrom(27).tracking(14, 14).blend(0.5).build());
        s.add(Move.builder("gl_crescent_sweep", Kind.ABILITY, 26).cost(0, 30, 0).cooldown(110).armor(0, 18)
                .hit(3, 18, HitSpec.of(8).reaction(Reaction.KNOCKBACK).stun(20, 11).push(1.5).lift(0.3).hitstop(5).radial().shake(0.55).guard(40)
                        .poise(60).gauge(5).fx("blade_spin", "hit_charged").build(), h, HitShape.sphere(0, 1.0, 0, 4.0))
                .dodgeFrom(20).abilityFrom(20).freeFrom(24).motion(new Motion(26).plant(0, 26)).blend(0.5).build());
        s.add(Move.builder("gl_piercing_gale", Kind.ABILITY, 22).cost(0, 35, 0).cooldown(140).armor(2, 10)
                .hit(6, 9, HitSpec.of(9).reaction(Reaction.KNOCKBACK).stun(22, 12).push(1.8).lift(0.2).hitstop(5).shake(0.55).guard(50).poise(70)
                        .gauge(5).fx("impact_gale", "gale").build(), h, HitShape.area(new Vec3(0, 1.1, 0.5), new Vec3(0, 1.1, 7.5), 1.0))
                .motion(new Motion(22).dash(4, 9, 1.6, 0.3)).dodgeFrom(14).abilityFrom(13).freeFrom(19).tracking(20, 5).blend(0.5).build());
        // ultimate
        Move.Builder fall = Move.builder("gl_heavenfall", Kind.ULTIMATE, 70).cost(0, 0, 100).iframes(0, 69).cinematic()
                .motion(new Motion(70).plant(0, 69)).tracking(20, 50).blend(1);
        for (int k = 0; k < 6; k++) {
            int t = 16 + 5 * k;
            fall.hit(t, t + 1, HitSpec.of(4).stun(16, 10).push(0.05).hitstop(2).radial().shake(0.35).guard(30).gauge(0).unblockable().unparryable()
                    .fx("blade_cleave", "slam").build(), HitShape.area(new Vec3(-1.2 + 0.5 * k, 0.6, 2.0), new Vec3(-1.2 + 0.5 * k, 0.6, 3.4), 1.3));
        }
        fall.hit(52, 53, HitSpec.of(20).reaction(Reaction.LAUNCH).stun(30, 20).push(1.6).lift(0.9).hitstop(9).radial().shake(1.0).guard(200).poise(200)
                .gauge(0).unblockable().unparryable().fx("ultimate_impact", "ultimate").build(), HitShape.sphere(0, 0.6, 2.6, 3.8));
        s.add(fall.build());

        s.groundLight = "gl_thrust_1";
        s.airLight = "gl_air_sweep";
        s.heavyCharge = "gl_charge";
        s.heavyTap = "gl_heavy";
        s.heavyCharged = "gl_heavy_charged";
        s.airHeavy = "gl_impale_dive";
        s.dashAttack = "gl_heavy";
        s.dodgeForward = "dodge_forward";
        s.dodgeBack = "dodge_back";
        s.dodgeLeft = "dodge_left";
        s.dodgeRight = "dodge_right";
        s.airDash = "air_dash";
        s.airJump = "air_jump";
        s.parry = "gl_parry";
        s.riposte = "gl_riposte";
        s.blockHit = "block_hit";
        s.ultimate = "gl_heavenfall";
        s.abilities[0] = "gl_vault_thrust";
        s.abilities[1] = "gl_crescent_sweep";
        s.abilities[2] = "gl_piercing_gale";
        s.validate();
        return s;
    }
}
