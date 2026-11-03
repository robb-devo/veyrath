package dev.veyrath.combat.content;

import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.Motion;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Move.Kind;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;
import dev.veyrath.combat.rig.Bone;

/**
 * Frame data of the unarmed (Stormhand) moveset. Ticks are 1/20 s; tick 0 is the first tick of a
 * move. Damage is in health points (a zombie has 20).
 *
 * <pre>
 * move            start active total  notes
 * jab               2    2-3     9    light 1, lead hand
 * cross             2    2-3    10    light 2; heavy branch = launcher
 * hook              3    3-4    11    light 3; heavy branch = spin heel
 * roundhouse        4    4-6    16    light 4, knockback finisher
 * heavy (tap)       5    5-6    16    stagger
 * heavy_charged     3    3-4    18    after 4-24 charge ticks, up to 2.2x damage
 * launcher          4    4-5    14    launches; jump cancels into launch_rise
 * spin_heel         5    5-7    18    wide arc knockback
 * dash_strike       3    3-6    14    light while sprinting or right after a dodge
 * air_1..3          2/2/3        8/8/12  air string with hang time; air_3 spikes
 * meteor_drop       5    5-29   30    dive, then meteor_land shockwave
 * </pre>
 */
public final class UnarmedMoves {
    private UnarmedMoves() {}

    static final double REACH = 16.5;

    static HitShape rightFist() { return HitShape.onBone(Bone.RIGHT_ARM, Bone.RIGHT_ELBOW, new Vec3(-1, REACH, 0), 0.5); }
    static HitShape leftFist() { return HitShape.onBone(Bone.LEFT_ARM, Bone.LEFT_ELBOW, new Vec3(1, REACH, 0), 0.5); }
    static HitShape rightKick() { return HitShape.onBone(Bone.RIGHT_LEG, Bone.KNEE, new Vec3(0, 17, 0), 0.55); }
    static HitShape rightKnee() { return HitShape.onBone(Bone.RIGHT_LEG, Vec3.ZERO, new Vec3(0, 9, 0), 0.6); }

    public static MoveSet moveSet() {
        MoveSet s = new MoveSet();
        MoveSet.addStandardReactions(s);

        // ------------------------------------------------ ground string
        s.add(Move.builder("jab", Kind.ATTACK, 9)
                .hit(2, 3, HitSpec.of(2).stun(9, 6).push(0.12).hitstop(2).shake(0.12).gauge(2).build(), leftFist())
                .branch(Action.LIGHT, "cross", 3, 9)
                .dodgeFrom(5).onHitCancel(2).abilityFrom(4).freeFrom(8)
                .motion(new Motion(9).dash(0, 2, 0.14, 0.04).steer(0.15))
                .tracking(14, 1).blend(1).build());
        s.add(Move.builder("cross", Kind.ATTACK, 10)
                .hit(2, 3, HitSpec.of(2.5).stun(10, 6).push(0.14).hitstop(2).shake(0.15).gauge(2).build(), rightFist())
                .branch(Action.LIGHT, "hook", 3, 10)
                .branch(Action.HEAVY, "launcher", 3, 10)
                .dodgeFrom(5).onHitCancel(2).abilityFrom(4).freeFrom(9)
                .motion(new Motion(10).dash(0, 2, 0.16, 0.04).steer(0.15))
                .tracking(14, 1).blend(1).build());
        s.add(Move.builder("hook", Kind.ATTACK, 11)
                .hit(3, 4, HitSpec.of(3).stun(11, 7).push(0.16).hitstop(3).shake(0.2).gauge(2.5).build(), leftFist())
                .branch(Action.LIGHT, "roundhouse", 4, 11)
                .branch(Action.HEAVY, "spin_heel", 4, 11)
                .dodgeFrom(6).onHitCancel(2).abilityFrom(5).freeFrom(10)
                .motion(new Motion(11).dash(0, 3, 0.15, 0.04).steer(0.1))
                .tracking(12, 2).blend(1).build());
        s.add(Move.builder("roundhouse", Kind.ATTACK, 16)
                .hit(4, 6, HitSpec.of(4.5).reaction(Reaction.KNOCKBACK).stun(16, 8).push(0.9).lift(0.3).hitstop(4)
                        .shake(0.35).guard(22).poise(28).gauge(4).fx("impact_heavy", "hit_heavy").build(), rightKick())
                .dodgeFrom(9).onHitCancel(2).abilityFrom(8).freeFrom(14)
                .motion(new Motion(16).dash(0, 4, 0.14, 0.06).steer(0.05))
                .tracking(10, 3).blend(1.5).build());

        // ------------------------------------------------ heavies
        s.add(Move.builder("heavy_charge", Kind.CHARGE, 40)
                .chargeMax(24).dodgeFrom(0).abilityFrom(0)
                .motion(new Motion(40).steer(0.25))
                .tracking(20, 40).next("heavy_charged").blend(2).build());
        s.add(Move.builder("heavy", Kind.ATTACK, 16)
                .hit(5, 6, HitSpec.of(5).reaction(Reaction.STAGGER).stun(16, 9).push(0.45).hitstop(4).shake(0.35)
                        .guard(26).poise(30).gauge(4).fx("impact_heavy", "hit_heavy").build(), rightFist())
                .dodgeFrom(9).onHitCancel(2).abilityFrom(8).freeFrom(14)
                .motion(new Motion(16).dash(3, 5, 0.32, 0.06))
                .tracking(10, 4).blend(1.5).build());
        s.add(Move.builder("heavy_charged", Kind.ATTACK, 18)
                .hit(3, 4, HitSpec.of(6).reaction(Reaction.KNOCKBACK).stun(18, 10).push(1.15).lift(0.3).hitstop(6)
                        .shake(0.6).guard(50).poise(60).gauge(6).fx("impact_charged", "hit_charged").build(),
                        rightFist(), HitShape.sphere(0, 1.2, 1.2, 0.9))
                .dodgeFrom(10).abilityFrom(9).freeFrom(16)
                .motion(new Motion(18).dash(0, 4, 0.55, 0.08))
                .blend(1).build());
        s.add(Move.builder("launcher", Kind.ATTACK, 14)
                .hit(4, 5, HitSpec.of(3.5).reaction(Reaction.LAUNCH).stun(20, 8).push(0.05).lift(0.66).hitstop(4)
                        .shake(0.3).guard(20).poise(26).gauge(4).fx("impact_launch", "hit_heavy").build(), rightFist())
                .branchOnHit(Action.JUMP, "launch_rise", 5, 14)
                .dodgeFrom(9).onHitCancel(3).abilityFrom(8).freeFrom(13)
                .motion(new Motion(14).dash(0, 3, 0.1, 0.02))
                .tracking(12, 3).blend(1).build());
        s.add(Move.builder("launch_rise", Kind.LEAP, 8)
                .motion(new Motion(8).rise(0, 0.74).steer(0.6).gravity(0.7))
                .dodgeFrom(3).abilityFrom(3).freeFrom(3).blend(1).build());
        s.add(Move.builder("spin_heel", Kind.ATTACK, 18)
                .hit(5, 7, HitSpec.of(4).reaction(Reaction.KNOCKBACK).stun(16, 8).push(1.1).lift(0.35).hitstop(5).radial()
                        .shake(0.45).guard(26).poise(32).gauge(4).fx("impact_heavy", "hit_heavy").build(), rightKick())
                .dodgeFrom(11).onHitCancel(2).abilityFrom(10).freeFrom(16)
                .motion(new Motion(18).dash(0, 5, 0.1, 0.04))
                .blend(1).build());
        s.add(Move.builder("dash_strike", Kind.ATTACK, 14)
                .hit(3, 6, HitSpec.of(3.5).reaction(Reaction.KNOCKBACK).stun(14, 8).push(0.6).lift(0.25).hitstop(3)
                        .shake(0.3).guard(18).poise(20).gauge(3).build(), rightKnee())
                .dodgeFrom(9).onHitCancel(2).abilityFrom(8).freeFrom(12)
                .motion(new Motion(14).dash(0, 6, 0.75, 0.2))
                .tracking(10, 2).blend(1).build());

        // ------------------------------------------------ air
        s.add(Move.builder("air_1", Kind.ATTACK, 8).air()
                .hit(2, 3, HitSpec.of(2).reaction(Reaction.LAUNCH).stun(12, 6).push(0.06).lift(0.22).hitstop(2)
                        .shake(0.12).gauge(2).build(), rightFist())
                .branch(Action.LIGHT, "air_2", 3, 8).branch(Action.HEAVY, "meteor_drop", 3, 8)
                .dodgeFrom(4).abilityFrom(4).freeFrom(7)
                .motion(new Motion(8).holdVertical(0, 5, 0.02).gravity(0.25).steer(0.2))
                .tracking(16, 1).blend(1).build());
        s.add(Move.builder("air_2", Kind.ATTACK, 8).air()
                .hit(2, 3, HitSpec.of(2).reaction(Reaction.LAUNCH).stun(12, 6).push(0.06).lift(0.22).hitstop(2)
                        .shake(0.14).gauge(2).build(), leftFist())
                .branch(Action.LIGHT, "air_3", 3, 8).branch(Action.HEAVY, "meteor_drop", 3, 8)
                .dodgeFrom(4).abilityFrom(4).freeFrom(7)
                .motion(new Motion(8).holdVertical(0, 5, 0.02).gravity(0.25).steer(0.2))
                .tracking(16, 1).blend(1).build());
        s.add(Move.builder("air_3", Kind.ATTACK, 12).air()
                .hit(3, 5, HitSpec.of(3).reaction(Reaction.SPIKE).stun(14, 8).push(0.1).hitstop(4)
                        .shake(0.35).gauge(3).fx("impact_heavy", "hit_heavy").build(), rightKick())
                .dodgeFrom(7).abilityFrom(7).freeFrom(10)
                .motion(new Motion(12).holdVertical(0, 6, 0.0).gravity(0.35).steer(0.15))
                .tracking(12, 2).blend(1).build());
        s.add(Move.builder("meteor_drop", Kind.ATTACK, 30).air()
                .hit(5, 29, HitSpec.of(3).reaction(Reaction.SPIKE).stun(14, 8).push(0.05).hitstop(2).shake(0.2).build(),
                        HitShape.area(new Vec3(0, -0.4, 0.3), new Vec3(0, 1.0, 0.3), 0.9))
                .motion(new Motion(30).holdVertical(0, 3, 0.08).holdVertical(4, 29, -1.5).steer(0.1))
                .endsOnLanding("meteor_land").blend(1).build());
        s.add(Move.builder("meteor_land", Kind.ATTACK, 14)
                .hit(0, 1, HitSpec.of(5).reaction(Reaction.LAUNCH).stun(16, 10).push(0.55).lift(0.45).hitstop(4)
                        .shake(0.6).guard(34).poise(40).gauge(4).fx("shockwave_ring", "slam").build(),
                        HitShape.sphere(0, 0.4, 0.4, 3.2))
                .dodgeFrom(8).abilityFrom(8).freeFrom(12)
                .motion(new Motion(14).plant(0, 6)).blend(0.5).build());

        // ------------------------------------------------ movement
        s.add(Move.builder("dodge_forward", Kind.DODGE, 10).cost(22, 0, 0).iframes(1, 6)
                .motion(new Motion(10).dash(0, 7, 0.78, 0.22).alongInput())
                .dodgeFrom(7).abilityFrom(6).freeFrom(8).blend(1).build());
        s.add(Move.builder("dodge_back", Kind.DODGE, 9).cost(20, 0, 0).iframes(1, 5)
                .motion(new Motion(9).dash(0, 6, 0.62, 0.15).alongInput())
                .dodgeFrom(7).abilityFrom(6).freeFrom(8).blend(1).build());
        s.add(Move.builder("dodge_left", Kind.DODGE, 9).cost(20, 0, 0).iframes(1, 5)
                .motion(new Motion(9).dash(0, 6, 0.7, 0.15).alongInput())
                .dodgeFrom(7).abilityFrom(6).freeFrom(8).blend(1).build());
        s.add(Move.builder("dodge_right", Kind.DODGE, 9).cost(20, 0, 0).iframes(1, 5)
                .motion(new Motion(9).dash(0, 6, 0.7, 0.15).alongInput())
                .dodgeFrom(7).abilityFrom(6).freeFrom(8).blend(1).build());
        s.add(Move.builder("air_dash", Kind.AIR_DASH, 7).air().cost(18, 0, 0).iframes(0, 3)
                .motion(new Motion(7).dash(0, 5, 0.9, 0.4).alongInput().holdVertical(0, 5, 0.0))
                .abilityFrom(4).freeFrom(6).blend(0.5).build());
        s.add(Move.builder("air_jump", Kind.AIR_JUMP, 8).air()
                .motion(new Motion(8).rise(0, 0.5).steer(1))
                .dodgeFrom(2).abilityFrom(2).freeFrom(2).blend(0.5).build());

        // ------------------------------------------------ defence
        s.add(Move.builder("parry", Kind.PARRY, 12).anywhere().iframes(0, 6)
                .branch(Action.LIGHT, "riposte", 1, 12).branch(Action.HEAVY, "riposte", 1, 12)
                .dodgeFrom(2).abilityFrom(2).freeFrom(10).blend(0.5).build());
        s.add(Move.builder("riposte", Kind.ATTACK, 14)
                .hit(2, 3, HitSpec.of(7).reaction(Reaction.KNOCKBACK).stun(18, 10).push(0.85).lift(0.28).hitstop(5)
                        .shake(0.5).guard(40).poise(50).gauge(6).unparryable().fx("impact_heavy", "hit_heavy").build(), rightFist())
                .dodgeFrom(8).abilityFrom(7).freeFrom(12)
                .motion(new Motion(14).dash(0, 2, 0.3, 0.05))
                .tracking(30, 1).blend(0.5).build());
        s.add(Move.builder("block_hit", Kind.BLOCK_HIT, 6).blend(0.5).motion(new Motion(6).plant(0, 2)).build());

        // ------------------------------------------------ abilities
        s.add(Move.builder("gale_step", Kind.ABILITY, 16).cost(0, 25, 0).cooldown(70).iframes(0, 7)
                .hit(7, 8, HitSpec.of(4.5).reaction(Reaction.STAGGER).stun(16, 8).push(0.4).hitstop(4).shake(0.35)
                        .guard(24).poise(30).gauge(4).fx("impact_heavy", "gale").build(),
                        rightFist(), HitShape.sphere(0, 1.2, 1.0, 0.9))
                .motion(new Motion(16).dash(1, 6, 1.4, 0.9).plant(8, 12))
                .dodgeFrom(11).abilityFrom(10).freeFrom(14)
                .tracking(25, 6).blend(0.5).build());
        s.add(Move.builder("skybreaker", Kind.ABILITY, 20).anywhere().cost(0, 35, 0).cooldown(140).armor(0, 10)
                .hit(3, 8, HitSpec.of(4).reaction(Reaction.LAUNCH).stun(22, 8).push(0.1).lift(0.95).hitstop(4)
                        .shake(0.45).guard(30).poise(45).gauge(5).fx("impact_launch", "skybreaker").build(),
                        HitShape.area(new Vec3(0, 0, 0), new Vec3(0, 2.6, 0), 2.3))
                .motion(new Motion(20).rise(3, 0.85).gravity(0.4).steer(0.3))
                .dodgeFrom(12).abilityFrom(12).freeFrom(14).blend(0.5).build());
        s.add(Move.builder("quake_palm", Kind.ABILITY, 22).cost(0, 40, 0).cooldown(200).armor(4, 10)
                .hit(6, 8, HitSpec.of(6).reaction(Reaction.KNOCKBACK).stun(20, 12).push(1.4).lift(0.35).hitstop(6)
                        .shake(0.7).guard(70).poise(80).gauge(5).fx("shockwave_cone", "quake").build(),
                        HitShape.area(new Vec3(0, 1.0, 0.8), new Vec3(0, 1.0, 6.5), 1.8))
                .motion(new Motion(22).plant(0, 12))
                .dodgeFrom(14).abilityFrom(14).freeFrom(18).tracking(20, 4).blend(1).build());

        // ------------------------------------------------ ultimate
        Move.Builder ult = Move.builder("unbound_fist", Kind.ULTIMATE, 72).cost(0, 0, 100).iframes(0, 71).cinematic()
                .motion(new Motion(72).dash(10, 14, 1.3, 0.6).plant(15, 71))
                .tracking(30, 15).blend(1);
        for (int k = 0; k <= 11; k++) {
            int t = 16 + 2 * k;
            ult.hit(t, t, HitSpec.of(1.2).stun(8, 6).push(0.02).hitstop(1).shake(0.12).guard(6).gauge(0).unparryable()
                    .fx("impact_light", "barrage").build(), k % 2 == 0 ? rightFist() : leftFist(), HitShape.sphere(0, 1.2, 1.3, 1.3));
        }
        ult.hit(48, 49, HitSpec.of(18).reaction(Reaction.KNOCKBACK).stun(30, 20).push(2.2).lift(0.55).hitstop(8).shake(1.0)
                .guard(200).poise(200).gauge(0).unblockable().unparryable().fx("ultimate_impact", "ultimate").build(),
                rightFist(), HitShape.area(new Vec3(0, 1.2, 0.8), new Vec3(0, 1.2, 5), 1.6));
        s.add(ult.build());

        // ------------------------------------------------ neutral starters
        s.groundLight = "jab";
        s.airLight = "air_1";
        s.heavyCharge = "heavy_charge";
        s.heavyTap = "heavy";
        s.heavyCharged = "heavy_charged";
        s.airHeavy = "meteor_drop";
        s.dashAttack = "dash_strike";
        s.dodgeForward = "dodge_forward";
        s.dodgeBack = "dodge_back";
        s.dodgeLeft = "dodge_left";
        s.dodgeRight = "dodge_right";
        s.airDash = "air_dash";
        s.airJump = "air_jump";
        s.parry = "parry";
        s.riposte = "riposte";
        s.blockHit = "block_hit";
        s.ultimate = "unbound_fist";
        s.abilities[0] = "gale_step";
        s.abilities[1] = "skybreaker";
        s.abilities[2] = "quake_palm";
        s.validate();
        return s;
    }
}
