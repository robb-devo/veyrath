package dev.veyrath.combat.content;

import dev.veyrath.combat.ai.BossBrain;
import dev.veyrath.combat.ai.BossBrain.Attack;
import dev.veyrath.combat.ai.BossBrain.Tell;
import dev.veyrath.combat.anim.BoneSpec;
import dev.veyrath.combat.anim.ClipBuilder;
import dev.veyrath.combat.anim.Ease;
import dev.veyrath.combat.anim.PoseSpec;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.hit.ClipLibrary;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitShape;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.Motion;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Move.Kind;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;

import java.util.List;

import static dev.veyrath.combat.ai.BossBrain.ALL;
import static dev.veyrath.combat.ai.BossBrain.P1;
import static dev.veyrath.combat.ai.BossBrain.P2;
import static dev.veyrath.combat.ai.BossBrain.P3;
import static dev.veyrath.combat.content.Greatsword.CLEAVE;
import static dev.veyrath.combat.content.Greatsword.COIL;
import static dev.veyrath.combat.content.Greatsword.GUARD;
import static dev.veyrath.combat.content.Greatsword.GUARD_LOW;
import static dev.veyrath.combat.content.Greatsword.HIGH_RIGHT;
import static dev.veyrath.combat.content.Greatsword.LOW_LEFT;
import static dev.veyrath.combat.content.Greatsword.OVERHEAD;
import static dev.veyrath.combat.content.Greatsword.RISEN;
import static dev.veyrath.combat.content.Greatsword.SWEPT;
import static dev.veyrath.combat.content.Greatsword.THRUST;
import static dev.veyrath.combat.rig.Bone.BODY;
import static dev.veyrath.combat.rig.Bone.HEAD;
import static dev.veyrath.combat.rig.Bone.LEFT_ARM;
import static dev.veyrath.combat.rig.Bone.LEFT_LEG;
import static dev.veyrath.combat.rig.Bone.RIGHT_ARM;
import static dev.veyrath.combat.rig.Bone.RIGHT_LEG;
import static dev.veyrath.combat.rig.Bone.ROOT;

/**
 * Varek, the Oathbound: a knight-giant (scale {@link #SCALE}) with the Oathcleaver's twin. He
 * fights with the greatsword's body language, but slower and heavier: every attack has a long,
 * readable windup and a big opening after it. Area shapes are in his character space, so they
 * grow with him.
 *
 * <p>Phase 1: triple string, overhead slam, lunge, sweep. Phase 2 (the brand ignites): adds the
 * leap slam and the ground wave, and his strings burn. Phase 3: adds the oath strike
 * (unblockable, dodge it) and a faster string. Each phase change opens with a roar that pushes
 * the player away.
 */
public final class Varek {
    private Varek() {}

    public static final String ID = "varek";
    public static final double SCALE = 2.5;
    public static final double HEALTH = 520;

    static HitShape blade() { return HitShape.onBone(RIGHT_ARM, new Vec3(-1, 10, -4), new Vec3(-1, 10, -4 - 34), 0.5); }

    static BoneSpec r(double pitch, double yaw, double roll) { return BoneSpec.rot(pitch, yaw, roll); }

    /** Chest out, arms flung wide, head back: the roar between phases. */
    static final PoseSpec ROAR = PoseSpec.REST
            .with(ROOT, r(0, 0, 0).offset(0, -0.5, -1))
            .with(BODY, r(-18, 0, 0))
            .with(HEAD, r(-30, 0, 0))
            .with(RIGHT_ARM, r(-40, 0, 70))
            .with(LEFT_ARM, r(-40, 0, -70))
            .with(RIGHT_LEG, r(12, 0, 10))
            .with(LEFT_LEG, r(-12, 0, -10));

    /** Gathered low before the leap. */
    static final PoseSpec CROUCH = GUARD_LOW.add(ROOT, BoneSpec.REST.offset(0, -3, 0)).add(BODY, r(20, 0, 0))
            .add(RIGHT_LEG, r(-30, 0, 0)).add(LEFT_LEG, r(-30, 0, 0));

    /** The oath strike's held windup: blade raised high and back, the body drawn like a bow. */
    static final PoseSpec OATH = OVERHEAD.add(BODY, r(-14, 0, 0)).add(ROOT, BoneSpec.REST.offset(0, 1, -1.5)).add(HEAD, r(-10, 0, 0));

    private static ClipBuilder c(String id, double len) { return ClipBuilder.clip(id, len); }

    public static void clips(ClipLibrary lib) {
        lib.add(c("vk_idle", 64).planted().loop().key(0, GUARD).key(32, GUARD_LOW).key(64, GUARD).build());
        lib.add(triple("vk_triple", 76, 14));
        lib.add(triple("vk_triple_fast", 56, 9));
        lib.add(c("vk_overhead", 46).noSink()
                .key(0, GUARD).key(20, OVERHEAD, Ease.OUT_QUAD).key(23, CLEAVE, Ease.OUT_EXPO).key(31, CLEAVE, Ease.LINEAR)
                .key(46, GUARD, Ease.IN_OUT_SINE).event(23, "impact").build());
        lib.add(c("vk_thrust", 38).planted()
                .key(0, GUARD).key(15, COIL.add(BODY, r(0, -20, 0)), Ease.OUT_QUAD).key(19, THRUST, Ease.OUT_EXPO).key(25, THRUST, Ease.LINEAR)
                .key(38, GUARD, Ease.IN_OUT_SINE).event(19, "impact").build());
        lib.add(c("vk_sweep", 42).planted()
                .key(0, GUARD).key(17, COIL, Ease.OUT_QUAD).key(21, SWEPT, Ease.OUT_EXPO).key(26, SWEPT, Ease.LINEAR)
                .key(42, GUARD, Ease.IN_OUT_SINE).event(20, "impact").build());
        lib.add(c("vk_leap", 58).noSink()
                .key(0, GUARD).key(10, CROUCH, Ease.OUT_QUAD).key(14, OVERHEAD, Ease.OUT_EXPO).key(30, OVERHEAD, Ease.LINEAR)
                .key(33, CLEAVE, Ease.OUT_EXPO).key(42, CLEAVE, Ease.LINEAR).key(58, GUARD, Ease.IN_OUT_SINE).event(33, "impact").build());
        lib.add(c("vk_wave", 52).noSink()
                .key(0, GUARD).key(22, OVERHEAD.add(ROOT, BoneSpec.REST.offset(0, 1, 0)), Ease.OUT_QUAD).key(25, CLEAVE, Ease.OUT_EXPO)
                .key(34, CLEAVE, Ease.LINEAR).key(52, GUARD, Ease.IN_OUT_SINE).event(25, "impact").build());
        lib.add(c("vk_oath", 74).noSink()
                .key(0, GUARD).key(12, OATH, Ease.OUT_QUAD).key(36, OATH.add(ROOT, BoneSpec.REST.offset(0, 0.5, 0)), Ease.LINEAR)
                .key(39, CLEAVE.add(ROOT, BoneSpec.REST.offset(0, -1, 1)), Ease.OUT_EXPO).key(50, CLEAVE, Ease.LINEAR)
                .key(74, GUARD, Ease.IN_OUT_SINE).event(39, "impact").build());
        lib.add(c("vk_roar", 52).planted()
                .key(0, GUARD).key(14, ROAR, Ease.OUT_BACK).key(40, ROAR.add(HEAD, r(-6, 0, 0)), Ease.LINEAR)
                .key(52, GUARD, Ease.IN_OUT_SINE).event(16, "impact").build());
    }

    /** Three cuts: right-high to low-left, the rising backhand, then the overhead cleave. */
    private static dev.veyrath.combat.anim.Clip triple(String id, int len, int wind) {
        int a = wind, b = a + wind + 6, cc = b + wind + 8;
        return c(id, len).noSink()
                .key(0, GUARD).key(a - 2, HIGH_RIGHT, Ease.OUT_QUAD).key(a + 1, LOW_LEFT, Ease.OUT_EXPO).key(a + 4, LOW_LEFT, Ease.LINEAR)
                .key(b - 2, LOW_LEFT.add(ROOT, r(0, 8, 0)), Ease.OUT_SINE).key(b + 1, RISEN, Ease.OUT_EXPO).key(b + 4, RISEN, Ease.LINEAR)
                .key(cc - 3, OVERHEAD, Ease.OUT_QUAD).key(cc, CLEAVE, Ease.OUT_EXPO).key(cc + 5, CLEAVE, Ease.LINEAR)
                .key(len, GUARD, Ease.IN_OUT_SINE)
                .event(a, "impact").event(b, "impact").event(cc, "impact").build();
    }

    static int[] tripleHits(int wind) {
        int a = wind, b = a + wind + 6, cc = b + wind + 8;
        return new int[]{a, b, cc};
    }

    public static MoveSet moveSet() {
        MoveSet s = new MoveSet();
        MoveSet.addStandardReactions(s);
        HitShape blade = blade();
        s.add(tripleMove("vk_triple", 76, 14, "blade_cleave"));
        s.add(tripleMove("vk_triple_fast", 56, 9, "vk_burning_cut"));
        s.add(Move.builder("vk_overhead", Kind.ATTACK, 46).armor(0, 30)
                .hit(22, 24, HitSpec.of(7).reaction(Reaction.KNOCKDOWN).stun(24, 14).push(0.8).hitstop(8).shake(0.9).guard(60).poise(80)
                        .fx("vk_slam", "boss_slam").build(), blade, HitShape.area(new Vec3(0, 0.2, 0.8), new Vec3(0, 0.2, 2.4), 0.7))
                .freeFrom(40).motion(new Motion(46).dash(18, 23, 0.25, 0.05).plant(23, 46)).tracking(9, 17).blend(2).build());
        s.add(Move.builder("vk_thrust", Kind.ATTACK, 38).armor(0, 26)
                .hit(18, 22, HitSpec.of(5).reaction(Reaction.KNOCKBACK).stun(18, 10).push(1.4).hitstop(6).shake(0.6).guard(45).poise(60)
                        .fx("blade_cleave", "boss_cut").build(), blade)
                .freeFrom(32).motion(new Motion(38).dash(16, 23, 1.15, 0.2).plant(24, 38)).tracking(10, 15).blend(2).build());
        s.add(Move.builder("vk_sweep", Kind.ATTACK, 42).armor(0, 26)
                .hit(18, 22, HitSpec.of(5).reaction(Reaction.KNOCKBACK).stun(18, 10).push(1.3).lift(0.3).hitstop(6).radial().shake(0.6)
                        .guard(45).poise(60).fx("blade_sweep", "boss_cut").build(), blade, HitShape.sphere(0, 0.5, 0.4, 1.6))
                .freeFrom(36).motion(new Motion(42).plant(0, 42)).tracking(8, 14).blend(2).build());
        s.add(Move.builder("vk_leap", Kind.ATTACK, 58).armor(0, 40).iframes(14, 30)
                .hit(32, 34, HitSpec.of(6).reaction(Reaction.LAUNCH).stun(24, 14).push(1.0).lift(0.7).hitstop(8).radial().shake(1.0)
                        .guard(60).poise(90).fx("vk_quake", "boss_slam").build(), HitShape.sphere(0, 0.2, 0.8, 2.0))
                .freeFrom(50).motion(new Motion(58).rise(13, 1.05).dash(13, 31, 0.55, 0.3).gravity(1.3).plant(33, 58))
                .tracking(14, 30).blend(2).build());
        s.add(Move.builder("vk_wave", Kind.ATTACK, 52).armor(0, 34)
                .hit(24, 26, HitSpec.of(5).reaction(Reaction.LAUNCH).stun(20, 12).push(0.6).lift(0.6).hitstop(6).shake(0.8).guard(50)
                        .poise(70).fx("vk_wave", "boss_slam").build(), blade, HitShape.area(new Vec3(0, 0.2, 1.0), new Vec3(0, 0.2, 9.0), 0.45))
                .freeFrom(44).motion(new Motion(52).plant(0, 52)).tracking(8, 21).blend(2).build());
        s.add(Move.builder("vk_oath", Kind.ATTACK, 74).armor(0, 60)
                .hit(38, 40, HitSpec.of(12).reaction(Reaction.LAUNCH).stun(32, 20).push(1.6).lift(0.9).hitstop(12).shake(1.2).guard(200).poise(200)
                        .unblockable().unparryable().fx("vk_oath_impact", "boss_oath").build(),
                        blade, HitShape.area(new Vec3(0, 0.2, 0.6), new Vec3(0, 0.2, 5.0), 1.1))
                .freeFrom(64).motion(new Motion(74).plant(0, 74)).tracking(6, 35).blend(2).build());
        s.add(Move.builder("vk_roar", Kind.ATTACK, 52).iframes(0, 52).armor(0, 52)
                .hit(16, 18, HitSpec.of(2).reaction(Reaction.KNOCKBACK).stun(14, 8).push(2.2).lift(0.3).hitstop(4).radial().shake(1.0)
                        .guard(0).poise(0).unblockable().unparryable().fx("vk_roar", "boss_slam").build(), HitShape.sphere(0, 0.8, 0, 2.6))
                .freeFrom(48).motion(new Motion(52).plant(0, 52)).blend(2).build());
        s.validate();
        return s;
    }

    private static Move tripleMove(String id, int len, int wind, String fx) {
        int[] at = tripleHits(wind);
        HitShape blade = blade();
        Move.Builder b = Move.builder(id, Kind.ATTACK, len).armor(0, at[2] + 2);
        b.hit(at[0] - 1, at[0] + 1, HitSpec.of(4).reaction(Reaction.STAGGER).stun(14, 9).push(0.5).hitstop(5).shake(0.45).guard(30).poise(40)
                .fx(fx, "boss_cut").build(), blade);
        b.hit(at[1] - 1, at[1] + 1, HitSpec.of(4).reaction(Reaction.STAGGER).stun(14, 9).push(0.5).lift(0.15).hitstop(5).shake(0.45).guard(30)
                .poise(40).fx(fx, "boss_cut").build(), blade);
        b.hit(at[2] - 1, at[2] + 1, HitSpec.of(6).reaction(Reaction.KNOCKDOWN).stun(22, 12).push(0.8).hitstop(8).shake(0.8).guard(50)
                .poise(70).fx("vk_slam", "boss_slam").build(), blade, HitShape.area(new Vec3(0, 0.2, 0.8), new Vec3(0, 0.2, 2.2), 0.6));
        b.motion(new Motion(len).dash(at[0] - 3, at[0] + 1, 0.3, 0.05).dash(at[1] - 3, at[1] + 1, 0.3, 0.05)
                .dash(at[2] - 3, at[2] + 1, 0.4, 0.05).plant(at[2] + 2, len));
        return b.freeFrom(len - 8).tracking(8, at[2] - 3).blend(2).build();
    }

    /** Heavy poise: light strings chip him; a heavy string or a parried strike staggers him. */
    public static Rules rules() {
        Rules r = Rules.mob(260);
        r.poiseRegenDelay = 90;
        return r;
    }

    public static BossBrain brain(long seed) {
        List<Attack> attacks = List.of(
                new Attack("vk_triple", 0, 7.5, 60, 3, P1 | P2, Tell.STRIKE, 34),
                new Attack("vk_triple_fast", 0, 7.5, 50, 3, P3, Tell.STRIKE, 26),
                new Attack("vk_overhead", 0, 6.5, 90, 2, ALL, Tell.HEAVY, 36),
                new Attack("vk_thrust", 5, 14, 110, 2, ALL, Tell.STRIKE, 30),
                new Attack("vk_sweep", 0, 6, 120, 1.6, ALL, Tell.HEAVY, 32),
                new Attack("vk_leap", 8, 22, 160, 2.5, P2 | P3, Tell.AREA, 40),
                new Attack("vk_wave", 4, 18, 140, 2, P2 | P3, Tell.AREA, 34),
                new Attack("vk_oath", 0, 9, 260, 3, P3, Tell.UNBLOCKABLE, 50));
        return new BossBrain(attacks, new String[]{null, "vk_roar", "vk_roar"}, 4.5, 0.85, 1, seed);
    }
}
