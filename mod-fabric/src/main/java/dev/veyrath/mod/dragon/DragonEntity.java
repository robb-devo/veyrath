package dev.veyrath.mod.dragon;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Vaelith, VEYRATH's dragon. Its body is a generated skinned mesh drawn by the client
 * (client.dragon); here it is a large, persistent creature with a movement state the client
 * animates. Flight, riding and calling are added work package by work package
 * (docs/CHAPTER_05_PLAN.md).
 */
public class DragonEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
    /** A state forced from outside (tests, the clip gallery); null = derived from movement. */
    private DragonState forced;

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
    }

    public DragonState state() { return DragonState.of(entityData.get(STATE)); }

    protected void setState(DragonState s) {
        if (state() != s) entityData.set(STATE, s.ordinal());
    }

    /** Forces a state (tests); null hands it back to movement. */
    public void force(DragonState s) {
        forced = s;
        if (s != null) setState(s);
    }

    public double horizontalSpeed() { return getDeltaMovement().horizontalDistance(); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (forced != null) {
            setState(forced);
            return;
        }
        double h = horizontalSpeed();
        setState(!onGround() ? DragonState.GLIDE : h > 0.2 ? DragonState.RUN : h > 0.03 ? DragonState.WALK : DragonState.IDLE);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
}
