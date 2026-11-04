package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.content.Weapons;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Which weapon each fighter has drawn, on this client: the local player's from its own fighter,
 * everyone else's from the server's {@code weapon} messages. Out of stance, a weapon item in the
 * main hand is shown as its model too.
 */
public final class ClientWeapons {
    private ClientWeapons() {}

    private static final Int2ObjectOpenHashMap<String> REMOTE = new Int2ObjectOpenHashMap<>();

    static void set(int entity, String weapon) {
        if (weapon == null || weapon.equals(Weapons.UNARMED.id())) REMOTE.remove(entity);
        else REMOTE.put(entity, weapon);
    }

    static void clear() { REMOTE.clear(); }

    /** The weapon id to draw on this entity, or null. */
    public static String drawn(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player && LocalCombat.active()) {
            String id = Weapons.of(LocalCombat.fighter().set).id();
            return id.equals(Weapons.UNARMED.id()) ? null : id;
        }
        if (e instanceof dev.veyrath.mod.boss.VarekEntity) return Weapons.GREATSWORD.id();
        String remote = e == mc.player ? null : REMOTE.get(e.getId());
        if (remote != null) return remote;
        return e instanceof net.minecraft.world.entity.LivingEntity le ? dev.veyrath.mod.item.VeyrathItems.weaponOf(le.getMainHandItem()) : null;
    }
}
