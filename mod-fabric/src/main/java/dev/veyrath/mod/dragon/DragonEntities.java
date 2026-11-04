package dev.veyrath.mod.dragon;

import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The dragon's entity type. Its box covers the body; wings, neck and tail reach beyond it. */
public final class DragonEntities {
    private DragonEntities() {}

    public static final EntityType<DragonEntity> VAELITH;

    static {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, VeyrathMod.id("vaelith"));
        VAELITH = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<DragonEntity>of(DragonEntity::new, MobCategory.CREATURE).sized(3.2f, 4.2f).eyeHeight(3.8f)
                        .clientTrackingRange(20).updateInterval(1).fireImmune().build(key));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(VAELITH, DragonEntity.createAttributes());
    }
}
