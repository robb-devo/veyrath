package dev.veyrath.mod.dragon;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Vaelith, VEYRATH's dragon. Its body is a generated skinned mesh drawn by the client
 * (client.dragon); here it is a large, persistent creature. Movement states, riding and calling
 * are added work package by work package (docs/CHAPTER_05_PLAN.md).
 */
public class DragonEntity extends PathfinderMob {
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
    public boolean removeWhenFarAway(double distance) { return false; }
}
