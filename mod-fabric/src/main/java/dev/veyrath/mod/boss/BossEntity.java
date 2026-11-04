package dev.veyrath.mod.boss;

import dev.veyrath.combat.ai.BossBrain;
import dev.veyrath.combat.ai.Brain;
import dev.veyrath.mod.combat.CombatNet;
import dev.veyrath.mod.combat.CombatServer;
import dev.veyrath.mod.combat.CombatSounds;
import dev.veyrath.mod.combat.MindedBody;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * The boss framework's body. A boss sleeps at its mark until a player comes within
 * {@link #wakeRange()}, then announces itself (name card, roar) and fights through its phases,
 * which follow its health ({@link #phaseAt}). Its boss bar asks for boss music and shows to every
 * player in {@link #barRange()}. With nobody left in range for ten seconds it resets: full
 * health, back to sleep at its mark. It has no vanilla AI: CombatServer drives its fighter with
 * its {@link BossBrain}, and every attack's tell is shown through {@link #tell}.
 */
public abstract class BossEntity extends PathfinderMob implements MindedBody {
    static final String AWAKE_TAG = "veyrath.boss.awake";
    /** The phase for clients (-1 asleep): their effects and presentation follow it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SHOWN_PHASE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(BossEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private ServerBossEvent bar;
    private final Set<ServerPlayer> watching = new HashSet<>();
    private int alone;
    private int phase = -1;
    private Vec3 home;

    protected BossEntity(EntityType<? extends BossEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SHOWN_PHASE, -1);
    }

    /** Phase as clients see it: -1 asleep, else 0-based. */
    public int shownPhase() { return entityData.get(SHOWN_PHASE); }

    public abstract Component bossName();

    public abstract Component epithet();

    protected abstract BossEvent.BossBarColor barColor();

    /** Phase (0-based) for a fraction of health left. */
    protected int phaseAt(float health) {
        return health > 0.6f ? 0 : health > 0.25f ? 1 : 2;
    }

    protected double wakeRange() { return 16; }

    protected double barRange() { return 64; }

    /** Shows an attack's tell (effect and sound) as it starts. */
    protected abstract void tell(BossBrain.Attack a);

    /** A phase begins (after the first): the boss's own presentation. */
    protected void phaseStarted(int phase) {}

    public boolean awake() { return entityTags().contains(AWAKE_TAG); }

    public void setHome(Vec3 v) { home = v; }

    @Override
    public final Brain newBrain(long seed) {
        BossBrain b = createBrain(seed);
        b.listener(new BossBrain.Listener() {
            @Override
            public void begin(BossBrain.Attack a) { tell(a); }
        });
        return b;
    }

    protected abstract BossBrain createBrain(long seed);

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (home == null) home = BossArenas.homeNear(position());
        CombatServer.ensureMinded(this);
        if (!awake()) {
            CombatServer.setMindActive(this, false);
            ServerPlayer near = level.getNearestPlayer(this, wakeRange()) instanceof ServerPlayer sp && !sp.isSpectator() ? sp : null;
            if (near != null) awaken(level);
            return;
        }
        updateBar(level);
        if (entityData.get(SHOWN_PHASE) != Math.max(0, phase)) entityData.set(SHOWN_PHASE, Math.max(0, phase));
        int p = phaseAt(getHealth() / getMaxHealth());
        if (p != phase) {
            boolean first = phase < 0;
            phase = p;
            if (CombatServer.brainOf(this) instanceof BossBrain b) b.setPhase(p);
            if (!first) phaseStarted(p);
        }
        if (watching.isEmpty()) {
            if (++alone > 200) reset();
        } else {
            alone = 0;
        }
    }

    private void awaken(ServerLevel level) {
        addTag(AWAKE_TAG);
        CombatServer.setMindActive(this, true);
        phase = -1;
        alone = 0;
        var intro = new CombatNet.BossIntroS2C(getId(), bossName().getString(), epithet().getString());
        for (ServerPlayer p : level.players()) {
            if (p.distanceTo(this) < barRange()) ServerPlayNetworking.send(p, intro);
        }
        level.playSound(null, getX(), getY() + 2, getZ(), CombatSounds.event("boss_awaken"), SoundSource.HOSTILE, 2.0f, 1.0f);
        CombatServer.vfx(this, "boss_awaken", getX(), getY(), getZ());
    }

    private void updateBar(ServerLevel level) {
        if (bar == null) {
            bar = new ServerBossEvent(java.util.UUID.randomUUID(), bossName(), barColor(), BossEvent.BossBarOverlay.PROGRESS);
            bar.setPlayBossMusic(true);
        }
        bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0f, 1f));
        Set<ServerPlayer> now = new HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceTo(this) < barRange()) now.add(p);
        }
        for (ServerPlayer p : Set.copyOf(watching)) {
            if (!now.contains(p)) {
                bar.removePlayer(p);
                watching.remove(p);
            }
        }
        for (ServerPlayer p : now) {
            if (watching.add(p)) bar.addPlayer(p);
        }
    }

    /** Nobody left to fight: full health, back to its mark, asleep. */
    public void reset() {
        removeTag(AWAKE_TAG);
        entityData.set(SHOWN_PHASE, -1);
        clearBar();
        setHealth(getMaxHealth());
        CombatServer.detach(this);
        phase = -1;
        alone = 0;
        if (home != null) {
            teleportTo(home.x, home.y, home.z);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Back on its mark (it fell off its arena). */
    public void backHome() {
        Vec3 h = home != null ? home : position();
        teleportTo(h.x, h.y, h.z);
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
    }

    private void clearBar() {
        if (bar != null) bar.removeAllPlayers();
        watching.clear();
    }

    @Override
    public void die(DamageSource source) {
        clearBar();
        if (level() instanceof ServerLevel level) {
            CombatServer.vfx(this, "boss_death", getX(), getY(), getZ());
            level.playSound(null, getX(), getY() + 2, getZ(), CombatSounds.event("boss_death"), SoundSource.HOSTILE, 2.0f, 1.0f);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        clearBar();
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    /** Bosses do not die to the room: no fall, drowning, suffocation or crowding damage. */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource s) {
        if (s.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || s.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || s.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || s.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)) return true;
        return super.isInvulnerableTo(level, s);
    }

    /** Position of a point on the boss: up = fraction of its height, forward in blocks. */
    protected Vec3 at(double up, double forward) {
        double r = Math.toRadians(getYRot());
        return new Vec3(getX() - Math.sin(r) * forward, getY() + getBbHeight() * up, getZ() + Math.cos(r) * forward);
    }
}
