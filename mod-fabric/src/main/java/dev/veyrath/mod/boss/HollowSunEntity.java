package dev.veyrath.mod.boss;

import dev.veyrath.combat.ai.BossBrain;
import dev.veyrath.combat.content.HollowSun;
import dev.veyrath.combat.fighter.Fighter;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.move.HitSpec;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.combat.move.Reaction;
import dev.veyrath.mod.combat.CombatServer;
import dev.veyrath.mod.combat.CombatSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Hollow Sun's body (combat content: dev.veyrath.combat.content.HollowSun). It floats over
 * the centre of the Sunless Crown, drifting round it, and fires each cast's timeline itself: the
 * move it is in and that move's tick say what happens now. Its hits are {@link CombatServer#blast}s,
 * so the player blocks, parries or dodges them like blows. After the gravity well and the nova it
 * sinks to the floor, exhausted, for five seconds: then it can be struck in melee and takes more.
 */
public class HollowSunEntity extends BossEntity {
    private static final MoveSet MOVES = HollowSun.moveSet();
    static final double HOVER = 2.2, LOW = 0.3;
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> TIRED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(HollowSunEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private String castMove = "";
    private final List<Vec3> marks = new ArrayList<>();
    private final Set<Integer> hit = new HashSet<>();
    private Vec3 aimDir = new Vec3(0, 0, 1);
    private int exhausted;
    private double orbit;

    public HollowSunEntity(EntityType<? extends HollowSunEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HollowSun.HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.FOLLOW_RANGE, 80);
    }

    @Override public Component bossName() { return Component.translatable("veyrath.boss.hollow_sun"); }

    @Override public Component epithet() { return Component.translatable("veyrath.boss.hollow_sun.epithet"); }

    @Override protected BossEvent.BossBarColor barColor() { return BossEvent.BossBarColor.YELLOW; }

    @Override public MoveSet combatMoves() { return MOVES; }

    @Override public Rules combatRules() { return HollowSun.rules(); }

    @Override protected BossBrain createBrain(long seed) { return HollowSun.brain(seed); }

    @Override public String idleClip() { return ""; }

    @Override public double fightRange() { return 64; }

    @Override protected double wakeRange() { return 18; }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(TIRED, false);
    }

    /** Exhausted: low and open for a moment (clients dim its rings). */
    public boolean exhausted() { return level().isClientSide() ? entityData.get(TIRED) : exhausted > 0; }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel level) {
            hover(level);
            if (awake()) runCast(level);
        }
        super.tick();
        setNoGravity(true);
    }

    /** Floats over the arena centre, drifting slowly round it; sinks while exhausted. */
    private void hover(ServerLevel level) {
        Vec3 c = BossArenas.Arena.SUNLESS_CROWN.bossMark();
        if (exhausted > 0) exhausted--;
        if (entityData.get(TIRED) != exhausted > 0) entityData.set(TIRED, exhausted > 0);
        orbit += awake() ? 0.006 : 0.002;
        double r = awake() ? 3.5 : 0;
        double y = c.y + (exhausted > 0 ? LOW : HOVER) + Math.sin(tickCount * 0.05) * 0.25;
        Vec3 want = new Vec3(c.x + Math.cos(orbit) * r, y, c.z + Math.sin(orbit) * r);
        Vec3 d = want.subtract(position());
        double k = exhausted > 0 ? 0.08 : 0.04;
        setDeltaMovement(d.scale(k));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Exhausted, it is open: blows bite harder.
        return super.hurtServer(level, source, exhausted > 0 ? amount * 1.6f : amount);
    }

    private ServerPlayer target(ServerLevel level) {
        return level.getNearestPlayer(this, 64) instanceof ServerPlayer sp && !sp.isSpectator() ? sp : null;
    }

    private Vec3 core() { return position().add(0, getBbHeight() * 0.5, 0); }

    private double floorY() { return BossArenas.Arena.SUNLESS_CROWN.bossMark().y; }

    private void runCast(ServerLevel level) {
        Fighter f = CombatServer.fighterOf(this);
        String move = f == null || f.move() == null ? "" : f.move().id;
        int t = f == null ? 0 : f.moveTick();
        if (!move.equals(castMove)) {
            castMove = move;
            marks.clear();
            hit.clear();
            ServerPlayer p = target(level);
            if (p != null) {
                Vec3 to = p.position().subtract(position());
                aimDir = new Vec3(to.x, 0, to.z).lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : new Vec3(to.x, 0, to.z).normalize();
            }
        }
        if (move.isEmpty()) return;
        ServerPlayer p = target(level);
        switch (move) {
            case "hs_beams" -> beams(level, t);
            case "hs_starfall" -> starfall(level, t == 1 ? 0 : -1, p, 5, 30);
            case "hs_volley" -> volley(level, t, p);
            case "hs_gravity" -> gravity(level, t);
            case "hs_nova" -> nova(level, t);
            case "hs_shatter" -> {
                if (t == 10) BossArenas.shatter(level, BossArenas.Arena.SUNLESS_CROWN, this);
            }
            case "hs_flare" -> {
                if (t == 1) {
                    Vec3 c = core();
                    CombatServer.vfx(this, "hs_flare", c.x, c.y, c.z);
                }
                if (t >= 10 && t <= 40 && t % 10 == 0) starfall(level, 0, p, 3, 0);
            }
            default -> {}
        }
    }

    /** Two beams sweep the floor in a fan from the core, crossing as they go. */
    private void beams(ServerLevel level, int t) {
        if (t < 18 || t > 52 || t % 2 != 0) return;
        double k = (t - 18) / 34.0;
        for (int side = -1; side <= 1; side += 2) {
            double ang = Math.toRadians(side * (60 - 120 * k));
            Vec3 dir = new Vec3(aimDir.x * Math.cos(ang) - aimDir.z * Math.sin(ang), 0, aimDir.x * Math.sin(ang) + aimDir.z * Math.cos(ang));
            Vec3 end = new Vec3(getX() + dir.x * 18, floorY(), getZ() + dir.z * 18);
            CombatServer.vfx(this, "hs_beam", end.x, end.y, end.z);
            HitSpec spec = HitSpec.of(4).reaction(Reaction.KNOCKBACK).stun(14, 8).push(0.8).hitstop(4).shake(0.4).guard(30).poise(40)
                    .fx("impact_heavy", "hit_heavy").build();
            for (int i = 2; i <= 18; i += 2) {
                Vec3 at = new Vec3(getX() + dir.x * i, floorY() + 0.9, getZ() + dir.z * i);
                CombatServer.blast(this, at, 1.0, spec, false, hit);
            }
        }
        if (t == 18) level.playSound(null, getX(), getY(), getZ(), CombatSounds.event("boss_fire"), SoundSource.HOSTILE, 2.0f, 1.4f);
    }

    /** Marks the ground (the player's feet and around), then the stars come down there. */
    private void starfall(ServerLevel level, int t, ServerPlayer p, int count, int delay) {
        if (t == 0 && p != null) {
            marks.clear();
            marks.add(new Vec3(p.getX(), floorY(), p.getZ()));
            for (int i = 1; i < count; i++) {
                double a = getRandom().nextDouble() * Math.PI * 2, d = 3 + getRandom().nextDouble() * 5;
                marks.add(new Vec3(p.getX() + Math.cos(a) * d, floorY(), p.getZ() + Math.sin(a) * d));
            }
            for (Vec3 m : marks) CombatServer.vfx(this, "tell_area", m.x, m.y + 0.05, m.z);
            if (delay == 0) delay = 26;
            List<Vec3> due = List.copyOf(marks);
            int when = tickCount + delay;
            pendingStars.add(new Pending(when, due));
        }
    }

    private record Pending(int at, List<Vec3> where) {}

    private final List<Pending> pendingStars = new ArrayList<>();

    @Override
    public void baseTick() {
        super.baseTick();
        if (!(level() instanceof ServerLevel level) || pendingStars.isEmpty()) return;
        List<Pending> now = new ArrayList<>();
        pendingStars.removeIf(pd -> {
            if (pd.at > tickCount) return false;
            now.add(pd);
            return true;
        });
        HitSpec spec = HitSpec.of(5).reaction(Reaction.LAUNCH).stun(18, 10).push(0.6).lift(0.6).hitstop(6).radial().shake(0.6).guard(40).poise(50)
                .fx("impact_heavy", "boss_slam").build();
        for (Pending pd : now) {
            Set<Integer> once = new HashSet<>();
            for (Vec3 m : pd.where) {
                CombatServer.vfx(this, "hs_star", m.x, m.y, m.z);
                CombatServer.blast(this, m.add(0, 0.8, 0), 2.8, spec, false, once);
            }
            level.playSound(null, pd.where.getFirst().x, pd.where.getFirst().y, pd.where.getFirst().z, CombatSounds.event("boss_slam"), SoundSource.HOSTILE, 2.0f, 1.1f);
        }
    }

    /** Six shards at the player, each aimed where they will be. */
    private void volley(ServerLevel level, int t, ServerPlayer p) {
        if (p == null || t < 10 || t > 40 || (t - 10) % 6 != 0) return;
        Vec3 lead = p.position().add(p.getDeltaMovement().scale(8));
        Vec3 c = core();
        CombatServer.vfx(this, "hs_shard", lead.x, lead.y + 0.8, lead.z);
        level.playSound(null, c.x, c.y, c.z, CombatSounds.event("tell_strike"), SoundSource.HOSTILE, 1.2f, 1.6f);
        int arrive = tickCount + 8;
        HitSpec spec = HitSpec.of(3).reaction(Reaction.FLINCH).stun(10, 6).push(0.3).hitstop(3).shake(0.25).guard(18).poise(20)
                .fx("impact_light", "hit_light").build();
        shards.add(new Shot(arrive, lead.add(0, 0.8, 0), spec));
    }

    private record Shot(int at, Vec3 where, HitSpec spec) {}

    private final List<Shot> shards = new ArrayList<>();

    /** The well: players are drawn to the core's foot for three seconds, then it implodes. */
    private void gravity(ServerLevel level, int t) {
        Vec3 foot = new Vec3(getX(), floorY(), getZ());
        if (t == 2) CombatServer.vfx(this, "hs_well", foot.x, foot.y, foot.z);
        if (t > 6 && t < 70) {
            if (t % 12 == 0) CombatServer.vfx(this, "hs_well", foot.x, foot.y, foot.z);
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.distanceToSqr(foot) > 24 * 24) continue;
                Vec3 to = foot.subtract(p.position());
                double d = Math.max(1, to.horizontalDistance());
                Vec3 pull = new Vec3(to.x / d, 0, to.z / d).scale(0.11);
                p.push(pull.x, 0, pull.z);
                ((dev.veyrath.mod.mixin.EntityInvoker) p).veyrath$markHurt();
            }
        }
        if (t == 74) {
            CombatServer.vfx(this, "hs_implode", foot.x, foot.y, foot.z);
            HitSpec spec = HitSpec.of(8).reaction(Reaction.LAUNCH).stun(24, 14).push(1.4).lift(0.9).hitstop(8).radial().shake(1.0).guard(60)
                    .poise(80).fx("impact_heavy", "boss_slam").build();
            CombatServer.blast(this, foot.add(0, 1, 0), 6.0, spec, false, hit);
            exhausted = 100;
        }
    }

    /** It gathers itself for five seconds; then light fills the Crown. Only the monoliths' shadow is safe. */
    private void nova(ServerLevel level, int t) {
        Vec3 c = core();
        if (t == 1) CombatServer.vfx(this, "hs_nova_charge", c.x, c.y, c.z);
        if (t > 1 && t < 96 && t % 20 == 0) {
            CombatServer.vfx(this, "hs_nova_charge", c.x, c.y, c.z);
            level.playSound(null, c.x, c.y, c.z, CombatSounds.event("tell_unblockable"), SoundSource.HOSTILE, 2.0f, 0.6f + t / 200f);
        }
        if (t == 100) {
            CombatServer.vfx(this, "hs_nova", c.x, c.y, c.z);
            level.playSound(null, c.x, c.y, c.z, CombatSounds.event("ultimate"), SoundSource.HOSTILE, 3.0f, 0.6f);
            level.playSound(null, c.x, c.y, c.z, CombatSounds.event("boss_slam"), SoundSource.HOSTILE, 3.0f, 0.7f);
            HitSpec spec = HitSpec.of(14).reaction(Reaction.LAUNCH).stun(30, 18).push(1.8).lift(0.8).hitstop(10).radial().shake(1.2).guard(200)
                    .poise(200).unblockable().unparryable().fx("impact_heavy", "hit_charged").build();
            CombatServer.blast(this, c, 40, spec, true, hit);
            exhausted = 120;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel) || shards.isEmpty()) return;
        List<Shot> due = new ArrayList<>();
        shards.removeIf(s -> {
            if (s.at > tickCount) return false;
            due.add(s);
            return true;
        });
        for (Shot s : due) {
            CombatServer.vfx(this, "hs_shard_hit", s.where.x, s.where.y, s.where.z);
            CombatServer.blast(this, s.where, 1.4, s.spec, false, null);
        }
    }

    @Override
    protected void tell(BossBrain.Attack a) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = core();
        switch (a.tell()) {
            case STRIKE -> CombatServer.vfx(this, "tell_strike", c.x, c.y, c.z);
            case HEAVY -> CombatServer.vfx(this, "vk_tell_heavy", c.x, c.y, c.z);
            case UNBLOCKABLE -> CombatServer.vfx(this, "vk_tell_oath", c.x, c.y, c.z);
            case AREA -> CombatServer.vfx(this, "hs_gather", c.x, c.y, c.z);
        }
        level.playSound(null, c.x, c.y, c.z, CombatSounds.event(a.tell() == BossBrain.Tell.UNBLOCKABLE ? "tell_unblockable" : "tell_heavy"),
                SoundSource.HOSTILE, 1.8f, 0.7f);
    }

    @Override
    protected void phaseStarted(int phase) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = core();
        CombatServer.vfx(this, phase == 1 ? "hs_flare" : "hs_flare_red", c.x, c.y, c.z);
        level.playSound(null, c.x, c.y, c.z, CombatSounds.event("boss_roar"), SoundSource.HOSTILE, 2.5f, phase == 1 ? 1.3f : 1.1f);
    }

    @Override
    public void reset() {
        super.reset();
        exhausted = 0;
        pendingStars.clear();
        shards.clear();
        if (level() instanceof ServerLevel level) BossArenas.restore(level, BossArenas.Arena.SUNLESS_CROWN);
    }
}
