package dev.veyrath.mod.dragon;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Vaelith, VEYRATH's dragon. The body is a generated skinned mesh drawn by the client
 * (client.dragon); here are her movement and riding.
 *
 * <p>Riding: use her to climb on (she crouches), sneak to get off. The rider steers: the look
 * direction is where she flies; forward flies on (flapping), letting go glides and slowly loses
 * height, jump flaps upward (and takes off from the ground), sprint dives and gains speed. Near the
 * ground and slow, she lands by herself. On the ground forward walks, sprint runs. Like every
 * player-driven vehicle her movement is computed on the rider's client and sent to the server;
 * the server derives her movement state (the animation) from how she moves.
 */
public class DragonEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);

    /** The local rider's jump and sprint keys (set by the client; never read on the server). */
    public static Supplier<boolean[]> clientKeys = () -> new boolean[2];

    /** Flight tuning, blocks per tick. */
    static final double GLIDE = 0.55, CRUISE = 0.85, DIVE = 1.45, CLIMB = 0.42, TURN = 5.5, WALK = 0.2, RUN = 0.34;

    private DragonState forced;
    /** Who blew the horn for her (she answers only them). */
    private java.util.UUID owner;
    /** What she does when nobody rides her. */
    public enum Mode { STAY, ARRIVE, DEPART }
    private Mode mode = Mode.STAY;
    private int modeTicks;
    /** Ticks she has stood idle; after a while she lies down to rest. */
    private int idleTicks;
    private boolean resting;
    private int takeoff, landing, roar;
    private Vec3 lastPos;

    public DragonEntity(EntityType<? extends DragonEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, DragonState.IDLE.ordinal());
        b.define(FLYING, false);
    }

    public DragonState state() { return DragonState.of(entityData.get(STATE)); }

    public boolean flying() { return entityData.get(FLYING); }

    void setFlying(boolean f) {
        if (flying() != f) entityData.set(FLYING, f);
        setNoGravity(f);
    }

    protected void setState(DragonState s) {
        if (state() != s) entityData.set(STATE, s.ordinal());
    }

    /** Forces a state (tests); null hands it back to movement. */
    public void force(DragonState s) {
        forced = s;
        if (s != null) setState(s);
    }

    public double horizontalSpeed() { return getDeltaMovement().horizontalDistance(); }

    // ------------------------------------------------------------------ riding

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isVehicle() || player.isSecondaryUseActive()) return super.mobInteract(player, hand);
        wake();
        if (!level().isClientSide()) player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty(); }

    /** The saddle: over the shoulders, where the spine meets the neck. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity rider, EntityDimensions dims, float scale) {
        return new Vec3(0, 4.05, 1.2);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        double r = Math.toRadians(getYRot());
        return new Vec3(getX() + Math.cos(r) * 3.5, getY(), getZ() + Math.sin(r) * 3.5);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) { return false; }

    @Override
    public void travel(Vec3 input) {
        LivingEntity rider = getControllingPassenger();
        if (!(rider instanceof Player p) || !isLocalInstanceAuthoritative()) {
            super.travel(input);
            return;
        }
        boolean[] keys = level().isClientSide() ? clientKeys.get() : new boolean[2];
        boolean jump = keys[0], sprint = keys[1];
        float forward = p.zza, strafe = p.xxa;
        if (flying()) fly(p, forward, strafe, jump, sprint);
        else walk(p, forward, strafe, jump, sprint);
    }

    private void walk(Player p, float forward, float strafe, boolean jump, boolean sprint) {
        if (jump && onGround() && takeoff == 0) {
            takeoff = 18;
            setFlying(true);
            setDeltaMovement(getDeltaMovement().x, 0.75, getDeltaMovement().z);
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        turnToward(p.getYRot(), forward != 0 || strafe != 0 ? TURN : TURN * 0.6);
        double speed = forward > 0 ? (sprint ? RUN : WALK) : forward < 0 ? -WALK * 0.5 : 0;
        double r = Math.toRadians(getYRot());
        Vec3 dm = getDeltaMovement();
        double vx = -Math.sin(r) * speed, vz = Math.cos(r) * speed;
        double vy = onGround() ? Math.min(0, dm.y) - 0.08 : dm.y - 0.08;
        setDeltaMovement(dm.x + (vx - dm.x) * 0.3, vy, dm.z + (vz - dm.z) * 0.3);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround() && getDeltaMovement().y < 0) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
    }

    private void fly(Player p, float forward, float strafe, boolean jump, boolean sprint) {
        if (takeoff > 0) takeoff--;
        float pitch = Mth.clamp(p.getXRot(), -55, 65);
        turnToward(p.getYRot(), TURN);
        double yr = Math.toRadians(getYRot()), pr = Math.toRadians(pitch);
        Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3 v = getDeltaMovement();
        double speed = v.length();
        // Diving gains speed, climbing costs it; flapping (forward) holds cruise speed.
        double target = sprint && pitch > 15 ? DIVE : forward > 0 ? CRUISE : GLIDE;
        target += Math.max(0, pitch) / 65 * 0.4 - Math.max(0, -pitch) / 55 * 0.35;
        double newSpeed = speed + (target - speed) * (forward > 0 || sprint ? 0.06 : 0.025);
        Vec3 want = dir.scale(Math.max(0.15, newSpeed));
        Vec3 nv = v.add(want.subtract(v).scale(0.12));
        // Gliding sinks; jump flaps upward.
        if (forward <= 0 && !jump) nv = nv.add(0, -0.025, 0);
        if (jump) nv = new Vec3(nv.x, Math.min(CLIMB, nv.y + 0.09), nv.z);
        if (takeoff > 10) nv = new Vec3(nv.x, Math.max(nv.y, 0.35), nv.z);
        setDeltaMovement(nv);
        move(MoverType.SELF, nv);
        // Touching down: she lands when she meets the ground going slowly enough, or lands hard.
        if (onGround() && takeoff == 0) land();
    }

    private void land() {
        setFlying(false);
        landing = 16;
        landed();
        setDeltaMovement(getDeltaMovement().multiply(0.3, 0, 0.3));
    }

    private void turnToward(float yaw, double max) {
        float d = Mth.wrapDegrees(yaw - getYRot());
        float step = (float) Mth.clamp(d, -max, max);
        setYRot(getYRot() + step);
        yRotO = getYRot() - step;
        setYBodyRot(getYRot());
        setYHeadRot(yaw);
    }

    /** She roars. */
    public void roar() {
        roar = 50;
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.playSound(null, getX(), getY() + 5, getZ(), dev.veyrath.mod.combat.CombatSounds.event("dragon_roar"),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 3.0f, 1.0f);
        }
    }

    /** The thud of touching down (server): sound and dust for everyone near. */
    private void landed() {
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(), dev.veyrath.mod.combat.CombatSounds.event("dragon_land"),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 2.5f, 1.0f);
            dev.veyrath.mod.combat.CombatServer.vfx(this, "dragon_land", getX(), getY(), getZ());
        }
    }

    // ------------------------------------------------------------------ owner, calling, arrival

    public java.util.UUID owner() { return owner; }

    public void setOwner(java.util.UUID u) { owner = u; }

    public Mode mode() { return mode; }

    /** Called with the horn: she comes to the owner (flying in, or walking if she is close). */
    public void call() {
        mode = Mode.ARRIVE;
        modeTicks = 0;
        wake();
    }

    /** Up from resting. */
    public void wake() {
        resting = false;
        idleTicks = 0;
    }

    public boolean resting() { return resting; }

    /** Idle on the ground: her head follows the nearest player in sight; she turns her body if they go round her. */
    private void lookAround() {
        Player p = level().getNearestPlayer(this, 24);
        if (p == null || resting) return;
        Vec3 to = p.getEyePosition().subtract(getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.max(0.5, to.horizontalDistance())));
        float d = Mth.wrapDegrees(yaw - getYHeadRot());
        setYHeadRot(getYHeadRot() + Mth.clamp(d, -4, 4));
        setXRot(getXRot() + Mth.clamp(pitch - getXRot(), -3, 3));
        if (Math.abs(Mth.wrapDegrees(yaw - getYRot())) > 95) {
            float b = Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -2.5f, 2.5f);
            setYRot(getYRot() + b);
            setYBodyRot(getYRot());
        }
    }

    /** Sent away: she takes off and leaves, and is gone once out of sight. */
    public void dismiss() {
        mode = Mode.DEPART;
        modeTicks = 0;
        if (onGround()) {
            setFlying(true);
            takeoff = 18;
            setDeltaMovement(getDeltaMovement().x, 0.75, getDeltaMovement().z);
        }
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (owner != null) out.store("veyrath_owner", net.minecraft.core.UUIDUtil.CODEC, owner);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        owner = in.read("veyrath_owner", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        setPersistenceRequired();
    }

    private Player ownerPlayer() {
        if (owner == null) return null;
        Player p = level().getPlayerByUUID(owner);
        return p != null && p.isAlive() && !p.isSpectator() ? p : null;
    }

    /**
     * Unridden, on the server: answering the horn she flies to a point high above the owner, then
     * descends in a wide approach and lands a few blocks away, facing them; walking if she was
     * already close. Departing she climbs away and is gone after ten seconds.
     */
    private void think() {
        modeTicks++;
        switch (mode) {
            case STAY -> {
                if (flying()) {
                    glideDown();
                    return;
                }
                stand();
                lookAround();
                // A quiet stretch and she lies down; someone coming close rouses her.
                Player near = level().getNearestPlayer(this, 5.5);
                if (resting && near != null) {
                    resting = false;
                    idleTicks = 0;
                } else if (!resting && ++idleTicks > 600 && near == null) {
                    resting = true;
                }
            }
            case DEPART -> {
                double yr = Math.toRadians(getYRot());
                aiFly(new Vec3(getX() - Math.sin(yr) * 60, getY() + 30, getZ() + Math.cos(yr) * 60), true);
                if (modeTicks > 200) discard();
            }
            case ARRIVE -> {
                Player p = ownerPlayer();
                if (p == null) {
                    mode = Mode.STAY;
                    return;
                }
                Vec3 to = p.position().subtract(position());
                double h = to.horizontalDistance();
                if (!flying()) {
                    if (h > 28) {
                        setFlying(true);
                        takeoff = 18;
                        setDeltaMovement(getDeltaMovement().x, 0.75, getDeltaMovement().z);
                    } else if (h > 9) {
                        aiWalk(p.position(), h > 14);
                    } else {
                        stand();
                        turnToward((float) Math.toDegrees(Math.atan2(-to.x, to.z)), 4);
                        mode = Mode.STAY;
                        roar();
                    }
                    return;
                }
                // The landing spot: ten blocks short of the owner, on her line of approach.
                Vec3 flat = new Vec3(to.x, 0, to.z);
                Vec3 spot = h < 1e-3 ? p.position() : p.position().subtract(flat.normalize().scale(10));
                double ground = groundBelow(spot);
                if (h > 40) aiFly(new Vec3(spot.x, Math.max(ground + 18, p.getY() + 12), spot.z), false);
                else {
                    // Descend toward the spot, slowing as she comes in.
                    double alt = getY() - ground;
                    double t = Math.clamp(h / 40, 0, 1);
                    aiFly(new Vec3(spot.x, ground + Math.max(0.5, alt * 0.5 * t), spot.z), false);
                    Vec3 v = getDeltaMovement();
                    double cap = 0.25 + 0.6 * t;
                    if (v.horizontalDistance() > cap) setDeltaMovement(v.x * 0.92, v.y, v.z * 0.92);
                }
                if (onGround() && takeoff == 0) {
                    land();
                    turnToward((float) Math.toDegrees(Math.atan2(-to.x, to.z)), 30);
                    mode = Mode.STAY;
                    roar();
                }
            }
        }
    }

    private double groundBelow(Vec3 at) {
        var pos = net.minecraft.core.BlockPos.containing(at.x, Math.min(at.y + 20, level().getMaxY() - 1), at.z);
        return level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /** Flies toward a point (server-side AI): turns, flaps or glides toward it. */
    private void aiFly(Vec3 target, boolean climbAlways) {
        if (takeoff > 0) takeoff--;
        Vec3 to = target.subtract(position());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        turnToward(yaw, TURN * 0.8);
        double h = to.horizontalDistance();
        double pitch = -Math.toDegrees(Math.atan2(to.y, Math.max(1, h)));
        pitch = Mth.clamp(pitch, -40, 50);
        double yr = Math.toRadians(getYRot()), pr = Math.toRadians(pitch);
        Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        double speed = Math.min(CRUISE, 0.3 + h / 30);
        Vec3 v = getDeltaMovement();
        Vec3 nv = v.add(dir.scale(speed).subtract(v).scale(0.08));
        if (climbAlways || takeoff > 10) nv = new Vec3(nv.x, Math.max(nv.y, 0.3), nv.z);
        setDeltaMovement(nv);
        move(MoverType.SELF, nv);
    }

    private void aiWalk(Vec3 target, boolean run) {
        Vec3 to = target.subtract(position());
        turnToward((float) Math.toDegrees(Math.atan2(-to.x, to.z)), TURN);
        double speed = run ? RUN : WALK;
        double r = Math.toRadians(getYRot());
        Vec3 dm = getDeltaMovement();
        setDeltaMovement(dm.x + (-Math.sin(r) * speed - dm.x) * 0.3, onGround() ? -0.08 : dm.y - 0.08, dm.z + (Math.cos(r) * speed - dm.z) * 0.3);
        move(MoverType.SELF, getDeltaMovement());
    }

    private void stand() {
        Vec3 dm = getDeltaMovement();
        setDeltaMovement(dm.x * 0.6, onGround() ? -0.08 : dm.y - 0.08, dm.z * 0.6);
        move(MoverType.SELF, getDeltaMovement());
    }

    private void glideDown() {
        Vec3 v = getDeltaMovement();
        Vec3 nv = new Vec3(v.x * 0.98, Math.max(-0.35, v.y - 0.02), v.z * 0.98);
        setDeltaMovement(nv);
        move(MoverType.SELF, nv);
        if (onGround()) land();
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide() && getControllingPassenger() == null && forced == null && !isNoAi()) {
            think();
            // Her own movement replaces the mob's; keep the rest of the living tick (effects, damage).
            return;
        }
        super.aiStep();
    }

    // ------------------------------------------------------------------ state for the animation

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (takeoff > 0 && !isLocalInstanceAuthoritative()) takeoff--;
        if (landing > 0) landing--;
        if (roar > 0) roar--;
        Vec3 pos = position();
        Vec3 vel = lastPos == null ? Vec3.ZERO : pos.subtract(lastPos);
        lastPos = pos;
        if (forced != null) {
            setState(forced);
            return;
        }
        // A ridden dragon's flight is on the rider's client; it tells the server whether it flies by
        // leaving the ground.
        if (!onGround() && getControllingPassenger() != null && !flying() && vel.y > 0.1) setFlying(true);
        if (onGround() && flying() && takeoff == 0 && getControllingPassenger() != null) {
            setFlying(false);
            landing = 16;
            landed();
        }
        setState(derive(vel));
    }

    private DragonState derive(Vec3 vel) {
        double h = vel.horizontalDistance();
        if (flying()) {
            if (takeoff > 8) return DragonState.TAKEOFF;
            if (h < 0.18) return DragonState.HOVER;
            if (vel.y < -0.45 && h > 0.6) return DragonState.DIVE;
            if (vel.y > 0.04 || h < 0.5) return DragonState.FLAP;
            return DragonState.GLIDE;
        }
        if (landing > 0) return DragonState.LAND;
        if (roar > 0) return DragonState.ROAR;
        if (isVehicle() && h < 0.02) return DragonState.CROUCH;
        if (resting && !isVehicle()) return DragonState.REST;
        return h > 0.24 ? DragonState.RUN : h > 0.03 ? DragonState.WALK : DragonState.IDLE;
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
}
