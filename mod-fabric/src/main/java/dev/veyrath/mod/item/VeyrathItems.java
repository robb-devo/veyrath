package dev.veyrath.mod.item;

import dev.veyrath.combat.content.Weapons;
import dev.veyrath.mod.VeyrathMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VEYRATH's items and its own creative tab. Every item we add goes into {@link #ALL}, so it shows
 * up in the tab without commands. The weapons are items too: hold one and enter stance, and you
 * fight with it (keys 5-9 still swap in stance).
 */
public final class VeyrathItems {
    private VeyrathItems() {}

    public static final List<Item> ALL = new ArrayList<>();
    private static final Map<Item, String> WEAPON_OF = new HashMap<>();

    public static Item OATHCLEAVER, WREN_AND_THORN, SKYREACH, VESPER_CODEX;
    public static ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, VeyrathMod.id("veyrath"));

    public static void register() {
        OATHCLEAVER = weapon("oathcleaver", Weapons.GREATSWORD);
        WREN_AND_THORN = weapon("wren_and_thorn", Weapons.TWIN_BLADES);
        SKYREACH = weapon("skyreach", Weapons.GLAIVE);
        VESPER_CODEX = weapon("vesper_codex", Weapons.GRIMOIRE);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.veyrath"))
                .icon(() -> new ItemStack(OATHCLEAVER))
                .displayItems((params, out) -> ALL.forEach(out::accept))
                .build());
    }

    private static Item weapon(String name, Weapons.Weapon w) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VeyrathMod.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)));
        ALL.add(item);
        WEAPON_OF.put(item, w.id());
        return item;
    }

    /** The combat weapon id an item stands for, or null. */
    public static String weaponOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : WEAPON_OF.get(stack.getItem());
    }
}
