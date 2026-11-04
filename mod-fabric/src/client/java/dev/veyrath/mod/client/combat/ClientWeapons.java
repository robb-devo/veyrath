package dev.veyrath.mod.client.combat;

import dev.veyrath.combat.content.Weapons;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Which weapon each fighter has drawn, on this client: the local player's from its own fighter,
 * everyone else's from the server's {@code weapon} messages. Weapons are drawn only in stance.
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
        if (e == mc.player) {
            if (!LocalCombat.active()) return null;
            String id = Weapons.of(LocalCombat.fighter().set).id();
            return id.equals(Weapons.UNARMED.id()) ? null : id;
        }
        return REMOTE.get(e.getId());
    }
}
