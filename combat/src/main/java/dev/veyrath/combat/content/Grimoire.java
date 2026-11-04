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
 * Grimoire "Vesper Codex": ranged, zoning. The open book rests in the right hand; the left hand
 * casts. Spells are server-authoritative hit shapes in front of the caster (a bolt is a narrow
 * line out to 9 blocks, live for one tick; the client draws the projectile), so they use the same
 * hit rules as every blade. Abilities: Arcane Lance (a sustained beam), Star Mine (a delayed burst
 * five blocks ahead), Rift Pull (drags enemies in). Ultimate: Vesper Nova, a collapsing star that
 * pulls and then bursts. Yaw + turns toward the character's right.
 */
public final class Grimoire {
    private Grimoire() {}

    public static final String ID = "grimoire";

    static BoneSpec r(double pitch, double yaw, double roll) { return BoneSpec.rot(pitch, yaw, roll); }

    /** A bolt: a narrow line from 1 to {@code range} blocks ahead at chest height. */
    static HitShape bolt(double range) { return HitShape.area(new Vec3(0, 1.3, 1.0), new Vec3(0, 1.3, range), 0.55); }

    // ---------------------------------------------------------------- poses

    /** Book held open at the chest, casting hand raised beside it. */
    static final PoseSpec GUARD = PoseSpec.REST
            .with(ROOT, r(0, -8, 0).offset(0, -0.5, 0))
            .with(BODY, r(2, -6, 0))
            .with(HEAD, r(-4, 10, 0))
            .with(RIGHT_ARM, r(-56, 26, 0))
            .with(LEFT_ARM, r(-50, -30, 0))
            .with(RIGHT_LEG, r(10, 0, 4))
            .with(LEFT_LEG, r(-12, 0, -4));

    static final PoseSpec GUARD_LOW = GUARD.add(ROOT, BoneSpec.REST.offset(0, -0.4, 0)).add(LEFT_ARM, r(4, 0, 0));
    /** Casting hand thrown forward, palm out. */
    static final PoseSpec CAST = GUARD.with(LEFT_ARM, r(-92, -6, 0)).with(BODY, r(6, 14, 0)).with(ROOT, r(0, 8, 0).offset(0, -0.8, 1));
    static final PoseSpec DRAW = GUARD.with(LEFT_ARM, r(-60, -60, 0)).with(BODY, r(0, -14, 0));
    /** Both arms raised: gathering power above. */
    static final PoseSpec RAISE = GUARD.with(LEFT_ARM, r(-170, -14, 0)).with(RIGHT_ARM, r(-120, 20, 0)).with(BODY, r(-8, 0, 0))
            .with(ROOT, r(0, 0, 0).offset(0, 0.5, 0));
    /** Casting hand slammed down to the ground in front. */
    static final PoseSpec SLAM = GUARD.with(LEFT_ARM, r(-20, -10, 0)).with(BODY, r(26, 0, 0)).with(ROOT, r(0, 0, 0).offset(0, -3, 1.5))
            .with(RIGHT_LEG, r(26, 0, 6)).with(LEFT_LEG, r(-30, 0, -6));

    // ---------------------------------------------------------------- clips

    private static ClipBuilder c(String id, double len) { return ClipBuilder.clip(id, len); }

    private static Clip cast(String id, double len, double at) {
        return c(id, len).planted().key(0, GUARD).key(at - 1.5, DRAW, Ease.OUT_QUAD).key(at, CAST, Ease.OUT_EXPO).key(at + 2, CAST, Ease.LINEAR)
                .key(len, GUARD, Ease.IN_OUT_SINE).event(at, "impact").build();
    }

    public static void clips(ClipLibrary lib) {
        lib.add(c("gr_stance_idle", 48).planted().loop().key(0, GUARD).key(24, GUARD_LOW).key(48, GUARD).build());
        lib.add(cast("gr_bolt_1", 10, 3));
        lib.add(cast("gr_bolt_2", 10, 3));
        lib.add(cast("gr_bolt_3", 16, 5));
        ClipBuilder charge = c("gr_charge", 40).planted().key(0, GUARD).key(6, RAISE, Ease.OUT_CUBIC);
        lib.add(charge.key(40, RAISE.add(ROOT, BoneSpec.REST.offset(0, 0.3, 0)), Ease.LINEAR).build());
        lib.add(c("gr_nova", 18).planted().key(0, GUARD).key(4, RAISE, Ease.OUT_QUAD).key(7, SLAM, Ease.OUT_EXPO).key(10, SLAM, Ease.LINEAR)
                .key(18, GUARD, Ease.IN_OUT_SINE).event(7, "impact").build());
        lib.add(c("gr_meteor", 26).planted().key(0, RAISE).key(8, CAST, Ease.OUT_EXPO).key(16, CAST, Ease.LINEAR).key(26, GUARD, Ease.IN_OUT_SINE)
                .event(14, "impact").build());
        lib.add(cast("gr_air_bolt", 12, 3));
        lib.add(c("gr_lance", 34).planted().key(0, GUARD).key(4, CAST, Ease.OUT_EXPO).key(28, CAST, Ease.LINEAR).key(34, GUARD, Ease.IN_OUT_SINE)
                .event(5, "impact").build());
        lib.add(cast("gr_star_mine", 20, 4));
        lib.add(c("gr_rift_pull", 22).planted().key(0, GUARD).key(4, CAST, Ease.OUT_EXPO).key(8, DRAW, Ease.IN_BACK).key(22, GUARD, Ease.IN_OUT_SINE)
                .event(8, "impact").build());
        lib.add(cast("gr_riposte", 12, 2));
        lib.add(c("gr_vesper_nova", 70).noSink().key(0, GUARD).key(8, RAISE, Ease.OUT_QUAD).key(44, RAISE, Ease.LINEAR).key(50, SLAM, Ease.OUT_EXPO)
                .key(58, SLAM, Ease.LINEAR).key(70, GUARD, Ease.IN_OUT_SINE).event(50, "impact").build());
    }

    // ---------------------------------------------------------------- moves

    public static MoveSet moveSet(MoveSet unarmed) {
        MoveSet s = new MoveSet();
        MoveSet.addStandardReactions(s);
        for (String id : new String[]{"dodge_forward", "dodge_back", "dodge_left", "dodge_right", "air_dash", "air_jump", "block_hit",
                "meteor_drop", "meteor_land"}) {
            s.add(unarmed.require(id));
        }
        HitSpec.Builder boltHit = HitSpec.of(3).reaction(Reaction.FLINCH).stun(10, 6).push(0.25).hitstop(2).shake(0.12).guard(10).poise(12).gauge(3);
        s.add(Move.builder("gr_bolt_1", Kind.ATTACK, 10).cost(0, 2, 0).hit(3, 3, boltHit.fx("arcane_hit", "hit_light").build(), bolt(9))
                .branch(Action.LIGHT, "gr_bolt_2", 4, 10).branch(Action.HEAVY, "gr_nova", 4, 10)
                .dodgeFrom(4).abilityFrom(4).freeFrom(9).motion(new Motion(10).plant(0, 5)).tracking(20, 3).blend(1).build());
        s.add(Move.builder("gr_bolt_2", Kind.ATTACK, 10).cost(0, 2, 0).hit(3, 3, boltHit.fx("arcane_hit", "hit_light").build(), bolt(9))
                .branch(Action.LIGHT, "gr_bolt_3", 4, 10).branch(Action.HEAVY, "gr_nova", 4, 10)
                .dodgeFrom(4).abilityFrom(4).freeFrom(9).motion(new Motion(10).plant(0, 5)).tracking(20, 3).blend(1).build());
        s.add(Move.builder("gr_bolt_3", Kind.ATTACK, 16).cost(0, 4, 0)
                .hit(5, 5, HitSpec.of(6).reaction(Reaction.KNOCKBACK).stun(16, 9).push(1.0).lift(0.2).hitstop(4).shake(0.35).guard(20).poise(30)
                        .gauge(4).fx("arcane_burst", "hit_heavy").build(), HitShape.area(new Vec3(0, 1.3, 1.0), new Vec3(0, 1.3, 10), 0.9))
                .dodgeFrom(8).abilityFrom(7).freeFrom(14).motion(new Motion(16).dash(5, 7, -0.25, -0.05)).tracking(16, 4).blend(1).build());
        s.add(Move.builder("gr_charge", Kind.CHARGE, 40).chargeMax(26).dodgeFrom(0).abilityFrom(0).motion(new Motion(40).steer(0.2))
                .tracking(18, 40).next("gr_meteor").blend(2).build());
        s.add(Move.builder("gr_nova", Kind.ATTACK, 18).cost(0, 6, 0)
                .hit(7, 8, HitSpec.of(6).reaction(Reaction.KNOCKBACK).stun(18, 10).push(1.4).lift(0.35).hitstop(5).radial().shake(0.5).guard(26)
                        .poise(40).gauge(4).fx("arcane_nova", "quake").build(), HitShape.sphere(0, 0.6, 0, 3.2))
                .armor(4, 8).dodgeFrom(11).abilityFrom(10).freeFrom(16).motion(new Motion(18).plant(0, 18)).blend(1).build());
        s.add(Move.builder("gr_meteor", Kind.ATTACK, 26).cost(0, 10, 0)
                .hit(14, 15, HitSpec.of(12).reaction(Reaction.LAUNCH).stun(24, 12).push(0.6).lift(0.8).hitstop(7).radial().shake(0.8).guard(50)
                        .poise(80).gauge(6).fx("arcane_nova", "ultimate").build(), HitShape.sphere(0, 0.5, 6.0, 2.8))
                .dodgeFrom(18).abilityFrom(17).freeFrom(24).motion(new Motion(26).plant(0, 26)).tracking(10, 10).blend(1).build());
        s.add(Move.builder("gr_air_bolt", Kind.ATTACK, 12).air().cost(0, 2, 0)
                .hit(3, 3, HitSpec.of(3).reaction(Reaction.SPIKE).stun(12, 6).push(0.2).hitstop(2).shake(0.15).gauge(3).fx("arcane_hit", "hit_light").build(),
                        HitShape.area(new Vec3(0, 1.0, 0.8), new Vec3(0, -1.5, 6), 0.7))
                .branch(Action.LIGHT, "gr_air_bolt", 6, 12).branch(Action.HEAVY, "meteor_drop", 6, 12)
                .dodgeFrom(5).abilityFrom(5).freeFrom(10).motion(new Motion(12).holdVertical(0, 8, 0.05).gravity(0.2)).blend(1).build());
        s.add(Move.builder("gr_parry", Kind.PARRY, 12).anywhere().clip("parry").iframes(0, 6)
                .branch(Action.LIGHT, "gr_riposte", 1, 12).branch(Action.HEAVY, "gr_riposte", 1, 12)
                .dodgeFrom(2).abilityFrom(2).freeFrom(10).blend(0.5).build());
        s.add(Move.builder("gr_riposte", Kind.ATTACK, 12)
                .hit(2, 2, HitSpec.of(9).reaction(Reaction.KNOCKBACK).stun(20, 12).push(1.4).lift(0.3).hitstop(6).shake(0.5).guard(40).poise(50)
                        .gauge(6).unparryable().fx("arcane_burst", "hit_charged").build(), HitShape.area(new Vec3(0, 1.3, 0.8), new Vec3(0, 1.3, 4), 1.0))
                .dodgeFrom(7).abilityFrom(6).freeFrom(10).motion(new Motion(12).plant(0, 12)).tracking(30, 1).blend(0.5).build());
        // abilities
        Move.Builder lance = Move.builder("gr_lance", Kind.ABILITY, 34).cost(0, 30, 0).cooldown(140).armor(4, 28);
        for (int t = 5; t <= 27; t += 4) {
            lance.hit(t, t, HitSpec.of(2.5).stun(10, 6).push(0.15).hitstop(1).shake(0.15).guard(12).gauge(2).fx("arcane_hit", "barrage").build(),
                    HitShape.area(new Vec3(0, 1.3, 1.0), new Vec3(0, 1.3, 13), 0.7));
        }
        s.add(lance.dodgeFrom(29).abilityFrom(29).freeFrom(32).motion(new Motion(34).plant(0, 34)).tracking(8, 28).blend(0.5).build());
        s.add(Move.builder("gr_star_mine", Kind.ABILITY, 20).cost(0, 25, 0).cooldown(100)
                .hit(16, 17, HitSpec.of(9).reaction(Reaction.LAUNCH).stun(22, 12).push(0.5).lift(0.75).hitstop(6).radial().shake(0.6).guard(40)
                        .poise(60).gauge(5).fx("arcane_nova", "quake").build(), HitShape.sphere(0, 0.5, 5.0, 2.4))
                .dodgeFrom(6).abilityFrom(6).freeFrom(8).motion(new Motion(20).plant(0, 6)).tracking(16, 3).blend(0.5).build());
        s.add(Move.builder("gr_rift_pull", Kind.ABILITY, 22).cost(0, 25, 0).cooldown(110)
                .hit(8, 9, HitSpec.of(3).reaction(Reaction.STAGGER).stun(22, 10).push(-1.3).hitstop(3).radial().shake(0.35).guard(20).poise(40)
                        .gauge(4).fx("arcane_pull", "gale").build(), HitShape.area(new Vec3(0, 1.0, 1.5), new Vec3(0, 1.0, 8), 2.0))
                .dodgeFrom(12).abilityFrom(11).freeFrom(18).motion(new Motion(22).plant(0, 22)).tracking(16, 6).blend(0.5).build());
        // ultimate: the star gathers (pulling hits), then bursts
        Move.Builder nova = Move.builder("gr_vesper_nova", Kind.ULTIMATE, 70).cost(0, 0, 100).iframes(0, 69).cinematic()
                .motion(new Motion(70).plant(0, 69)).tracking(10, 40).blend(1);
        for (int t = 14; t <= 42; t += 7) {
            nova.hit(t, t, HitSpec.of(2.5).stun(14, 10).push(-0.6).hitstop(1).radial().shake(0.25).guard(20).gauge(0).unblockable().unparryable()
                    .fx("arcane_pull", "barrage").build(), HitShape.sphere(0, 1.0, 4.0, 4.5));
        }
        nova.hit(50, 51, HitSpec.of(24).reaction(Reaction.LAUNCH).stun(32, 20).push(1.8).lift(1.0).hitstop(10).radial().shake(1.0).guard(200).poise(200)
                .gauge(0).unblockable().unparryable().fx("ultimate_impact", "ultimate").build(), HitShape.sphere(0, 1.0, 4.0, 5.0));
        s.add(nova.build());

        s.groundLight = "gr_bolt_1";
        s.airLight = "gr_air_bolt";
        s.heavyCharge = "gr_charge";
        s.heavyTap = "gr_nova";
        s.heavyCharged = "gr_meteor";
        s.airHeavy = "meteor_drop";
        s.dashAttack = "gr_bolt_1";
        s.dodgeForward = "dodge_forward";
        s.dodgeBack = "dodge_back";
        s.dodgeLeft = "dodge_left";
        s.dodgeRight = "dodge_right";
        s.airDash = "air_dash";
        s.airJump = "air_jump";
        s.parry = "gr_parry";
        s.riposte = "gr_riposte";
        s.blockHit = "block_hit";
        s.ultimate = "gr_vesper_nova";
        s.abilities[0] = "gr_lance";
        s.abilities[1] = "gr_star_mine";
        s.abilities[2] = "gr_rift_pull";
        s.validate();
        return s;
    }
}
