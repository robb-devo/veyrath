package dev.veyrath.mod.combat;

import dev.veyrath.combat.fighter.Controller;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.FighterEvents;
import dev.veyrath.combat.fighter.Outcome;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.hit.Box;
import dev.veyrath.combat.hit.HitDetector;
import dev.veyrath.combat.hit.HitResolver;
import dev.veyrath.combat.input.Action;
import dev.veyrath.combat.input.Controls;
import dev.veyrath.combat.math.Vec3;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.Move;
import dev.veyrath.combat.move.Reaction;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.mixin.EntityInvoker;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server side of combat. Every fighting entity has an {@link Entry}: players in combat stance get a
 * follower copy of their client's fighter (moves start only through checked requests), and any
 * mob or player that is struck gets a fighter for its reactions. Each server tick the fighters
 * advance, mob bodies take hit velocity and hit-stop, then the active hit windows are swept
 * against nearby entities and the outcomes applied and broadcast.
 */
public final class CombatServer {
    private CombatServer() {}

    /** Ticks a request may wait for the server copy to catch up (it trails the client by the latency). */
    static final int RETRY_TICKS = 3;
    /** A mob entry with nothing going on is dropped after this many ticks. */
    static final int IDLE_DROP = 200;

    static final Identifier GRAVITY_ID = VeyrathMod.id("combat_gravity");
    static final Identifier STEP_ID = VeyrathMod.id("stance_step");

    static final class Pending {
        final int seq;
        final Action action;
        final String move;
        final Controls controls;
        final float yaw;
        final boolean release;
        final double level;
        int age;

        Pending(int seq, Action action, String move, Controls controls, float yaw, boolean release, double level) {
            this.seq = seq; this.action = action; this.move = move; this.controls = controls; this.yaw = yaw;
            this.release = release; this.level = level;
        }
    }

    static final class Entry {
        final LivingEntity entity;
        final Fighter fighter;
        final boolean player;
        boolean stance, blockHeld, heavyHeld;
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        boolean airCombat;
        /** Clip last broadcast for this fighter ("" = none). */
        String shownClip = "";
        /** Mobs: the facing held while reacting (toward the last attacker). */
        float facing;
        double appliedGravity = 1;
        int idle, stateTimer;

        Entry(LivingEntity entity, Fighter fighter, boolean player) {
            this.entity = entity;
            this.fighter = fighter;
            this.player = player;
        }
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final List<Runnable> AFTER_TICK = new ArrayList<>();
    private static boolean applyingDamage;

    // Session statistics for /veyrath combat stats (acceptance: zero rejected moves).
    private static int accepted, acceptedAfterRetry, rejected;
    private static final EnumMap<Outcome.Result, Integer> RESULTS = new EnumMap<>(Outcome.Result.class);

    // ------------------------------------------------------------------ queries

    /** True while a mob should not think: reacting or frozen by hit-stop (MobMixin). */
    public static boolean stunned(LivingEntity e) {
        Entry en = ENTRIES.get(e.getId());
        if (en == null || en.entity != e) return false;
        Fighter f = en.fighter;
        return f.phase() == Fighter.Phase.REACTION || f.inHitstop() || f.frozen();
    }

    public static boolean inStance(ServerPlayer p) {
        Entry en = ENTRIES.get(p.getId());
        return en != null && en.entity == p && en.stance;
    }

    // ------------------------------------------------------------------ entries

    static Entry entryFor(LivingEntity e) {
        Entry en = ENTRIES.get(e.getId());
        if (en != null && en.entity == e) return en;
        boolean player = e instanceof ServerPlayer;
        Fighter f = new Fighter(e.getId(), player ? CombatContent.PLAYER : CombatContent.MOB,
                player ? Rules.player() : Rules.mob(poiseFor(e)));
        f.yaw = e.getYRot();
        Entry created = new Entry(e, f, player);
        created.facing = e.getYRot();
        f.events(new FighterEvents() {
            @Override
            public void moveStarted(Fighter fighter, Move m) {
                created.shownClip = m.clip;
                broadcastAnim(created, m.clip, (float) m.blendIn);
            }
        });
        ENTRIES.put(e.getId(), created);
        return created;
    }

    /** Super-armour by size: small mobs react to every hit, big ones shrug off light strings. */
    static double poiseFor(LivingEntity e) {
        double hp = e.getMaxHealth();
        if (hp <= 30) return 0;
        if (hp <= 60) return 25;
        if (hp <= 150) return 60;
        return 200;
    }

    // ------------------------------------------------------------------ client messages

    public static void onStance(ServerPlayer p, boolean on) {
        if (on) {
            Entry e = entryFor(p);
            e.stance = true;
            e.pending.clear();
            setModifier(p, Attributes.STEP_HEIGHT, STEP_ID, 0.45, AttributeModifier.Operation.ADD_VALUE);
            sendState(e, false);
        } else {
            Entry e = ENTRIES.remove(p.getId());
            removeModifier(p, Attributes.STEP_HEIGHT, STEP_ID);
            removeModifier(p, Attributes.GRAVITY, GRAVITY_ID);
            if (e == null) return;
        }
        CombatNet.StanceS2C msg = new CombatNet.StanceS2C(p.getId(), on);
        for (ServerPlayer viewer : PlayerLookup.tracking(p)) ServerPlayNetworking.send(viewer, msg);
    }

    public static void onAction(ServerPlayer p, CombatNet.ActionC2S msg) {
        Entry e = ENTRIES.get(p.getId());
        if (e == null || !e.stance || e.entity != p) return;
        if (msg.action() < 0 || msg.action() >= Action.values().length) return;
        Controls c = new Controls();
        c.moveX = msg.moveX();
        c.moveZ = msg.moveZ();
        c.aimYaw = msg.aimYaw();
        c.blockHeld = (msg.held() & CombatNet.HELD_BLOCK) != 0;
        c.heavyHeld = (msg.held() & CombatNet.HELD_HEAVY) != 0;
        c.sprintHeld = (msg.held() & CombatNet.HELD_SPRINT) != 0;
        c.jumpHeld = (msg.held() & CombatNet.HELD_JUMP) != 0;
        enqueue(e, new Pending(msg.seq(), Action.values()[msg.action()], msg.move().isEmpty() ? null : msg.move(), c, msg.yaw(), false, 0));
    }

    public static void onRelease(ServerPlayer p, CombatNet.ReleaseC2S msg) {
        Entry e = ENTRIES.get(p.getId());
        if (e == null || !e.stance || e.entity != p) return;
        enqueue(e, new Pending(msg.seq(), Action.HEAVY, msg.move(), null, p.getYRot(), true, msg.level()));
    }

    public static void onHeld(ServerPlayer p, CombatNet.HeldC2S msg) {
        Entry e = ENTRIES.get(p.getId());
        if (e == null || e.entity != p) return;
        e.blockHeld = (msg.held() & CombatNet.HELD_BLOCK) != 0;
        e.heavyHeld = (msg.held() & CombatNet.HELD_HEAVY) != 0;
    }

    private static void enqueue(Entry e, Pending pr) {
        // Requests run in order: a later one usually branches from an earlier one.
        if (!e.pending.isEmpty() || !tryRequest(e, pr)) e.pending.add(pr);
    }

    private static boolean tryRequest(Entry e, Pending pr) {
        Fighter f = e.fighter;
        f.yaw = pr.yaw;
        boolean ok = pr.release ? f.requestRelease(pr.move, pr.level) : f.request(pr.action, pr.move, pr.controls);
        if (ok) {
            accepted++;
            if (pr.age > 0) acceptedAfterRetry++;
        }
        return ok;
    }

    private static void processPending(Entry e) {
        Pending head;
        while ((head = e.pending.peek()) != null) {
            if (tryRequest(e, head)) {
                e.pending.poll();
                continue;
            }
            if (++head.age > RETRY_TICKS) {
                Fighter f = e.fighter;
                VeyrathMod.LOG.info("combat: rejected {} {} (seq {}) for {}: server fighter {} {} t={} grounded={}",
                        head.release ? "release" : head.action, head.move, head.seq, e.entity.getName().getString(),
                        f.phase(), f.move() == null ? "-" : f.move().id, f.moveTick(), f.grounded());
                // Everything queued behind it depended on it.
                for (Pending p : e.pending) {
                    rejected++;
                    if (e.entity instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new CombatNet.RejectS2C(p.seq));
                }
                e.pending.clear();
            }
            break;
        }
    }

    // ------------------------------------------------------------------ vanilla damage on stance players

    /**
     * ALLOW_DAMAGE: a vanilla attack (mob melee, projectile) on a player in combat stance goes
     * through the player's fighter first, so dodges, parries and blocks work against vanilla mobs.
     */
    public static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        if (applyingDamage) return true;
        if (!(target instanceof ServerPlayer p)) return true;
        Entry e = ENTRIES.get(p.getId());
        if (e == null || !e.stance || e.entity != p) return true;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        Entity direct = source.getDirectEntity(), causing = source.getEntity();
        Entity from = direct != null ? direct : causing;
        if (from == null || from == p) return true; // falls, fire, drowning: not combat
        net.minecraft.world.phys.Vec3 fp = from.position(), pp = p.position();
        Vec3 dir = new Vec3(pp.x - fp.x, 0, pp.z - fp.z);
        if (dir.lengthSq() < 1e-6) dir = new Vec3(-Math.sin(Math.toRadians(p.getYRot())), 0, Math.cos(Math.toRadians(p.getYRot()))).scale(-1);
        boolean heavy = amount >= 6;
        HitSpec spec = HitSpec.of(amount).reaction(heavy ? Reaction.STAGGER : Reaction.FLINCH).stun(heavy ? 14 : 8, 6).push(0)
                .hitstop(heavy ? 3 : 2).shake(Math.min(0.6, 0.1 + amount * 0.04)).guard(Math.max(8, amount * 4)).poise(10).gauge(0)
                .fx(heavy ? "impact_heavy" : "impact_light", heavy ? "hit_heavy" : "hit_light").build();
        // A melee attacker can be parried into a stagger; a projectile's shooter is not affected.
        Entry attacker = direct == causing && causing instanceof LivingEntity le && !(le instanceof Player) ? entryFor(le) : null;
        e.fighter.yaw = p.getYRot();
        Outcome o = e.fighter.receive(attacker == null ? null : attacker.fighter, spec, 0, dir);
        count(o);
        ServerLevel level = (ServerLevel) p.level();
        net.minecraft.world.phys.Vec3 at = p.position().add(0, p.getBbHeight() * 0.6, 0);
        // Vanilla applies its own knockback on a hit; nothing for the client to add.
        Outcome sent = withVelocities(o, null, null);
        sendHit(level, from, p, sent, at, "", 0, p.getYRot());
        switch (o.result()) {
            case HIT, ARMORED -> {
                playCues(level, null, at, o.sfx());
                return true;
            }
            case BLOCKED, GUARD_BREAK -> {
                playCues(level, null, at, o.sfx());
                float chip = (float) o.damage();
                Entity credit = causing;
                if (chip > 0) AFTER_TICK.add(() -> {
                    if (!p.isAlive()) return;
                    DamageSource src = credit == null ? level.damageSources().generic()
                            : level.damageSources().source(CombatContent.DAMAGE, credit);
                    hurt(level, p, src, chip);
                });
                return false;
            }
            default -> {
                playCues(level, null, at, o.sfx());
                if (o.perfect()) SlowMotion.trigger(level.getServer(), 10, 12);
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tick(MinecraftServer server) {
        SlowMotion.tick(server);
        if (!AFTER_TICK.isEmpty()) {
            List<Runnable> run = new ArrayList<>(AFTER_TICK);
            AFTER_TICK.clear();
            run.forEach(Runnable::run);
        }
        if (ENTRIES.isEmpty()) return;
        ENTRIES.values().removeIf(e -> e.entity.isRemoved() || !e.entity.isAlive());
        List<Entry> all = new ArrayList<>(ENTRIES.values());
        for (Entry e : all) tickEntry(e);
        for (Entry e : all) {
            if (ENTRIES.get(e.entity.getId()) == e && e.fighter.activeWindows().length > 0) detectHits(e);
        }
        ENTRIES.values().removeIf(e -> !e.stance && e.idle > IDLE_DROP);
    }

    private static void tickEntry(Entry e) {
        LivingEntity en = e.entity;
        Fighter f = e.fighter;
        boolean grounded = en.onGround();
        if (e.player) {
            f.yaw = en.getYRot();
            f.tickFollower(grounded, e.blockHeld);
            processPending(e);
            // Air strings and launches would otherwise end in fall damage.
            if (!grounded && (f.phase() == Fighter.Phase.MOVE || f.phase() == Fighter.Phase.REACTION)) e.airCombat = true;
            if (e.airCombat) {
                en.resetFallDistance();
                if (grounded) e.airCombat = false;
            }
            if (e.stance && ++e.stateTimer >= 10) {
                e.stateTimer = 0;
                sendState(e, false);
            }
        } else {
            f.tickFollower(grounded, false);
            if (f.frozen()) {
                en.setDeltaMovement(0, 0, 0);
            } else {
                Vec3 v = f.takeVelocity();
                if (v != null) {
                    double keep = 1 - Math.clamp(en.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1);
                    en.setDeltaMovement(v.x() * keep, v.y() * keep, v.z() * keep);
                    ((EntityInvoker) en).veyrath$markHurt();
                }
                double bounce = f.bounceVelocity();
                if (!Double.isNaN(bounce)) {
                    net.minecraft.world.phys.Vec3 dm = en.getDeltaMovement();
                    en.setDeltaMovement(dm.x, bounce, dm.z);
                    ((EntityInvoker) en).veyrath$markHurt();
                }
            }
            double g = f.frozen() || f.inHitstop() ? 0 : f.gravityScale();
            if (g != e.appliedGravity) {
                if (Math.abs(g - 1) < 1e-9) removeModifier(en, Attributes.GRAVITY, GRAVITY_ID);
                else setModifier(en, Attributes.GRAVITY, GRAVITY_ID, g - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                e.appliedGravity = g;
            }
            if (f.phase() == Fighter.Phase.REACTION) {
                en.resetFallDistance();
                en.setYRot(e.facing);
                en.setYHeadRot(e.facing);
                en.setYBodyRot(e.facing);
                // Position and motion go out every tick while a juggle is in the air.
                if (!grounded) en.push(0, 0, 0);
            }
        }
        if (f.phase() == Fighter.Phase.IDLE && !f.inHitstop() && e.pending.isEmpty()) e.idle++;
        else e.idle = 0;
        if (f.move() == null && !e.shownClip.isEmpty()) {
            e.shownClip = "";
            broadcastAnim(e, "", 3);
        }
    }

    // ------------------------------------------------------------------ hits

    private static void detectHits(Entry e) {
        LivingEntity a = e.entity;
        ServerLevel level = (ServerLevel) a.level();
        Vec3 feet = feet(a);
        AABB search = a.getBoundingBox().inflate(8);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, search, t -> canStrike(a, t));
        if (near.isEmpty()) return;
        List<HitDetector.Target> targets = new ArrayList<>(near.size());
        Map<Integer, LivingEntity> byId = new HashMap<>();
        for (LivingEntity t : near) {
            AABB bb = t.getBoundingBox();
            Entry te = ENTRIES.get(t.getId());
            targets.add(new HitDetector.Target(t.getId(), new Box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ),
                    te == null ? null : te.fighter));
            byId.put(t.getId(), t);
        }
        Move move = e.fighter.move();
        for (HitDetector.Strike s : HitDetector.detect(e.fighter, CombatContent.CLIPS, feet, e.fighter.yaw, a.getScale(), targets)) {
            LivingEntity d = byId.get(s.target().id());
            if (d != null && d.isAlive()) applyStrike(level, e, d, s, move);
        }
    }

    static boolean canStrike(LivingEntity attacker, LivingEntity t) {
        if (t == attacker || !t.isAlive() || t.isSpectator() || !t.isAttackable() || t.getType() == EntityType.ARMOR_STAND) return false;
        return !attacker.isAlliedTo(t) && !t.isAlliedTo(attacker);
    }

    private static void applyStrike(ServerLevel level, Entry ae, LivingEntity d, HitDetector.Strike s, Move move) {
        Entry de = entryFor(d);
        LivingEntity a = ae.entity;
        Vec3 aFeet = feet(a), dFeet = feet(d);
        if (!de.player) de.fighter.yaw = d.getYRot();
        HitSpec spec = ae.fighter.window(s.window()).spec();
        Outcome o = HitResolver.resolve(ae.fighter, s, de.fighter, aFeet, dFeet);
        count(o);

        if (!de.player) {
            // Mobs face their attacker, stop at once for the hit-stop and take the push afterwards.
            float face = (float) Controller.yawTo(dFeet, aFeet);
            de.facing = face;
            de.fighter.yaw = face;
            d.setYRot(face);
            d.setYHeadRot(face);
            d.setYBodyRot(face);
            if (o.velocity() != null) de.fighter.queueVelocity(o.velocity());
            if (de.fighter.inHitstop()) {
                d.setDeltaMovement(0, 0, 0);
                setModifier(d, Attributes.GRAVITY, GRAVITY_ID, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                de.appliedGravity = 0;
                ((EntityInvoker) d).veyrath$markHurt();
            }
        }
        if (!ae.player && o.attackerVelocity() != null) ae.fighter.queueVelocity(o.attackerVelocity());

        if (o.dealsDamage()) hurt(level, d, level.damageSources().source(CombatContent.DAMAGE, a), (float) o.damage());

        Vec3 p = s.point();
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(p.x(), p.y(), p.z());
        // The attacking player's client already played its predicted impact sound.
        playCues(level, a instanceof Player pl ? pl : null, at, o.sfx());

        if (o.perfect() && o.result() == Outcome.Result.DODGED) SlowMotion.trigger(level.getServer(), 10, 12);
        if ("ultimate".equals(spec.sfx()) && o.result() == Outcome.Result.HIT) SlowMotion.trigger(level.getServer(), 6, 14);

        Outcome sent = withVelocities(o, de.player ? o.velocity() : null, ae.player ? o.attackerVelocity() : null);
        sendHit(level, a, d, sent, at, move == null ? "" : move.id, s.window(), de.player ? d.getYRot() : de.facing);
    }

    private static void hurt(ServerLevel level, LivingEntity d, DamageSource src, float amount) {
        applyingDamage = true;
        try {
            d.hurtServer(level, src, amount);
        } finally {
            applyingDamage = false;
        }
    }

    // ------------------------------------------------------------------ sending

    private static void sendHit(ServerLevel level, Entity attacker, LivingEntity target, Outcome o, net.minecraft.world.phys.Vec3 at,
                                String moveId, int window, float defenderYaw) {
        CombatNet.HitS2C msg = new CombatNet.HitS2C(attacker.getId(), target.getId(), o, (float) at.x, (float) at.y, (float) at.z,
                moveId, window, defenderYaw);
        Set<ServerPlayer> to = new HashSet<>(PlayerLookup.tracking(target));
        to.addAll(PlayerLookup.tracking(attacker));
        if (attacker instanceof ServerPlayer sp) to.add(sp);
        if (target instanceof ServerPlayer sp) to.add(sp);
        for (ServerPlayer sp : to) ServerPlayNetworking.send(sp, msg);
    }

    private static void broadcastAnim(Entry e, String clip, float fade) {
        CombatNet.AnimS2C msg = new CombatNet.AnimS2C(e.entity.getId(), clip, fade);
        for (ServerPlayer viewer : PlayerLookup.tracking(e.entity)) ServerPlayNetworking.send(viewer, msg);
    }

    static void sendState(Entry e, boolean refill) {
        if (!(e.entity instanceof ServerPlayer sp)) return;
        Fighter f = e.fighter;
        ServerPlayNetworking.send(sp, new CombatNet.StateS2C((float) f.stamina(), (float) f.aether(), (float) f.gauge(), refill));
    }

    private static void playCues(ServerLevel level, Player except, net.minecraft.world.phys.Vec3 at, String sfx) {
        for (CombatSounds.Cue c : CombatSounds.cues(sfx)) {
            float pitch = c.pitch() * (0.94f + level.getRandom().nextFloat() * 0.12f);
            level.playSound(except, at.x, at.y, at.z, c.sound(), SoundSource.PLAYERS, c.volume(), pitch);
        }
    }

    private static Outcome withVelocities(Outcome o, Vec3 velocity, Vec3 attackerVelocity) {
        return new Outcome(o.result(), o.damage(), o.reaction(), o.hitstun(), o.hitstop(), velocity, attackerVelocity,
                o.counter(), o.perfect(), o.poiseBreak(), o.shake(), o.vfx(), o.sfx());
    }

    // ------------------------------------------------------------------ helpers

    static Vec3 feet(Entity e) {
        net.minecraft.world.phys.Vec3 p = e.position();
        return new Vec3(p.x, p.y, p.z);
    }

    private static void setModifier(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
    }

    private static void removeModifier(LivingEntity e, Holder<Attribute> attr, Identifier id) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    private static void count(Outcome o) {
        RESULTS.merge(o.result(), 1, Integer::sum);
    }

    // ------------------------------------------------------------------ debug and lifecycle

    public static boolean refill(ServerPlayer p) {
        Entry e = ENTRIES.get(p.getId());
        if (e == null || !e.stance) return false;
        e.fighter.refill();
        sendState(e, true);
        return true;
    }

    /** Strikes with this result since the server started (game tests). */
    public static int resultCount(Outcome.Result r) { return RESULTS.getOrDefault(r, 0); }

    public static int rejectedCount() { return rejected; }

    public static String stats() {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "combat: %d inputs accepted (%d after waiting for the server copy), %d rejected; %d fighters tracked",
                accepted, acceptedAfterRetry, rejected, ENTRIES.size()));
        if (!RESULTS.isEmpty()) {
            sb.append("\nstrikes:");
            RESULTS.forEach((r, n) -> sb.append(' ').append(r.name().toLowerCase(Locale.ROOT)).append(' ').append(n));
        }
        return sb.toString();
    }

    public static void remove(ServerPlayer p) {
        Entry e = ENTRIES.get(p.getId());
        if (e != null && e.entity == p) ENTRIES.remove(p.getId());
    }

    public static void clear(MinecraftServer server) {
        ENTRIES.clear();
        AFTER_TICK.clear();
        SlowMotion.reset(server);
        accepted = acceptedAfterRetry = rejected = 0;
        RESULTS.clear();
    }
}
