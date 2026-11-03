package dev.veyrath.mod.arena;

import dev.veyrath.mod.combat.CombatServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Training dummy: takes every hit and reaction (it can be launched and juggled), never fights back,
 * never dies. It measures what it takes: damage per hit, per string and per second, shown above it.
 * A string ends after {@link #STRING_GAP} ticks without a hit. Knocked away, it returns to its spot
 * once it has been left alone for a moment.
 */
public class TrainingDummyEntity extends PathfinderMob {
    /** Health pool large enough that no single hit can kill it; it heals back every tick. */
    static final float POOL = 1000;
    static final int STRING_GAP = 40, RETURN_AFTER = 40;

    private Vec3 home;
    private long stringStart = -1, lastHit = -1000;
    private double stringDamage, lastAmount;
    private int stringHits;

    public TrainingDummyEntity(EntityType<? extends TrainingDummyEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, POOL)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    public void setHome(Vec3 pos) { home = pos; }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        float lost = getMaxHealth() - getHealth();
        if (lost > 1e-3f) {
            record(lost, now);
            setHealth(getMaxHealth());
        }
        if (home == null) home = position();
        if (now % 5 == 0) showStats(now);
        resetFallDistance();
        boolean resting = !CombatServer.stunned(this) && onGround() && now - lastHit > RETURN_AFTER;
        if (resting && position().distanceToSqr(home) > 1.5) {
            teleportTo(home.x, home.y, home.z);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private void record(double amount, long now) {
        if (now - lastHit > STRING_GAP) {
            stringStart = now;
            stringDamage = 0;
            stringHits = 0;
        }
        lastHit = now;
        lastAmount = amount;
        stringDamage += amount;
        stringHits++;
    }

    /** Damage per second of the current or last string, over at least one second. */
    double dps() {
        if (stringStart < 0) return 0;
        double seconds = Math.max(1, (lastHit - stringStart + 1) / 20.0);
        return stringDamage / seconds;
    }

    private void showStats(long now) {
        Component name;
        if (stringStart < 0) {
            name = Component.translatable("entity.veyrath.training_dummy");
        } else {
            name = Component.literal(String.format(Locale.ROOT, "%s%.1f  ·  %d hits %.1f  ·  %.1f DPS",
                    now - lastHit <= STRING_GAP ? "▶ " : "", lastAmount, stringHits, stringDamage, dps()));
        }
        setCustomName(name);
        setCustomNameVisible(true);
    }
}
