package dev.veyrath.mod.arena;

import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Proving Grounds entity types: the sparring Duelist and the training dummy (player-sized humanoids). */
public final class ArenaEntities {
    private ArenaEntities() {}

    public static final EntityType<DuelistEntity> DUELIST = register("duelist",
            EntityType.Builder.<DuelistEntity>of(DuelistEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f)
                    .clientTrackingRange(10).updateInterval(1));
    public static final EntityType<TrainingDummyEntity> TRAINING_DUMMY = register("training_dummy",
            EntityType.Builder.<TrainingDummyEntity>of(TrainingDummyEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f)
                    .clientTrackingRange(10).updateInterval(1));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, VeyrathMod.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    /** Registers the types (class initialisation) and their default attributes. */
    public static void register() {
        FabricDefaultAttributeRegistry.register(DUELIST, DuelistEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TRAINING_DUMMY, TrainingDummyEntity.createAttributes());
    }
}
