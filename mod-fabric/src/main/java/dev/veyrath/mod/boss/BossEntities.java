package dev.veyrath.mod.boss;

import dev.veyrath.combat.content.Varek;
import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/** Boss entity types and the sigils that take a player to each boss's arena. */
public final class BossEntities {
    private BossEntities() {}

    /** Player-sized base box; the SCALE attribute makes him a giant (and grows the box with him). */
    public static final EntityType<VarekEntity> VAREK = register("varek",
            EntityType.Builder.<VarekEntity>of(VarekEntity::new, MobCategory.MONSTER).sized(0.7f, 1.9f).eyeHeight(1.65f)
                    .clientTrackingRange(16).updateInterval(1).fireImmune());

    public static Item OATH_SIGIL;

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, VeyrathMod.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(VAREK, VarekEntity.createAttributes());
        VeyrathMod.LOG.debug("Varek scale {}", Varek.SCALE);
    }

    /** Called from VeyrathItems: the sigils go into the VEYRATH tab. */
    public static Item sigil(String name, BossArenas.Arena arena) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VeyrathMod.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Sigil(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC), arena));
    }

    /** Use it to travel to its boss's arena; use it again there to go back. */
    static final class Sigil extends Item {
        final BossArenas.Arena arena;

        Sigil(Properties p, BossArenas.Arena arena) {
            super(p);
            this.arena = arena;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer sp) {
                if (sp.level().dimension().equals(dev.veyrath.mod.arena.ProvingGrounds.LEVEL) && arena.bounds().contains(sp.position())) {
                    dev.veyrath.mod.arena.ProvingGrounds.leave(sp);
                } else {
                    BossArenas.enter(sp, arena);
                    sp.sendSystemMessage(Component.translatable("veyrath.boss.sigil_enter"));
                }
            }
            return InteractionResult.SUCCESS;
        }
}
}
