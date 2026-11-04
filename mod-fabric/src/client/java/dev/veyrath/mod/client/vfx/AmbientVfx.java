package dev.veyrath.mod.client.vfx;

import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.client.vfx.Vfx.Shape;
import dev.veyrath.mod.client.vfx.Vfx.Sprite;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/**
 * Biome atmosphere drawn by VEYRATH's own effects engine (replaces the vanilla ash, spore and
 * end-rod particles the biomes used in Chapter 01): dark ash flecks in the Cinderwake, drifting
 * spores in damp hollows and marshes, rare pollen over sunlit meadows, faint Aether motes on the
 * high meadows and floating isles. Spawned around the camera, only in open air.
 */
public final class AmbientVfx {
    private AmbientVfx() {}

    static final int RADIUS = 20;

    private enum Mood { NONE, ASH, SPORES, POLLEN, MOTES }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> tick(mc));
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        BlockPos at = mc.player.blockPosition();
        Mood mood = mood(mc, at);
        if (mood == Mood.NONE) return;
        RandomSource r = mc.level.getRandom();
        int tries = switch (mood) {
            case ASH -> 6;
            case SPORES -> 4;
            case POLLEN -> 1;
            case MOTES -> 1;
            default -> 0;
        };
        for (int i = 0; i < tries; i++) {
            double x = at.getX() + r.nextGaussian() * RADIUS * 0.5, y = at.getY() + r.nextDouble() * 14 - 3, z = at.getZ() + r.nextGaussian() * RADIUS * 0.5;
            if (!mc.level.getBlockState(BlockPos.containing(x, y, z)).isAir()) continue;
            switch (mood) {
                case ASH -> Vfx.at(x, y, z).vel(0.15 + r.nextGaussian() * 0.1, -0.25 - r.nextDouble() * 0.2, r.nextGaussian() * 0.1)
                        .sprite(Sprite.SHARD).size(0.05, 0.04).rot(r.nextDouble() * 6).spin(r.nextGaussian() * 2).life(5 + r.nextDouble() * 3)
                        .fadeIn(0.8).color(0xC0302A28, 0x00302A28).smoke().spawn();
                case SPORES -> Vfx.at(x, y, z).vel(r.nextGaussian() * 0.05, 0.03 + r.nextDouble() * 0.05, r.nextGaussian() * 0.05)
                        .sprite(Sprite.GLOW).size(0.05, 0.03).life(6 + r.nextDouble() * 4).fadeIn(1.5).color(0x90B8E89A, 0x00B8E89A).spawn();
                case POLLEN -> Vfx.at(x, y, z).vel(0.2 + r.nextGaussian() * 0.05, r.nextGaussian() * 0.03, r.nextGaussian() * 0.05)
                        .sprite(Sprite.GLOW).size(0.04, 0.03).life(6).fadeIn(1.5).color(0xA0FFF0B0, 0x00FFF0B0).spawn();
                case MOTES -> Vfx.at(x, y, z).vel(r.nextGaussian() * 0.04, 0.05 + r.nextDouble() * 0.05, r.nextGaussian() * 0.04)
                        .sprite(Sprite.FLARE).shape(Shape.BILLBOARD).size(0.06, 0.1).rot(r.nextDouble() * 6).life(5).fadeIn(1.2)
                        .color(0xB0A8DCFF, 0x00A8DCFF).spawn();
                default -> { }
            }
        }
    }

    private static Mood mood(Minecraft mc, BlockPos at) {
        var key = mc.level.getBiome(at).unwrapKey();
        if (key.isEmpty() || !key.get().identifier().getNamespace().equals(VeyrathMod.MOD_ID)) return Mood.NONE;
        return switch (key.get().identifier().getPath()) {
            case "ashen_wastes", "cinder_peaks", "embervein_flats", "charwood" -> Mood.ASH;
            case "mossdeep_hollows", "glowmire", "gloamreed_marsh" -> Mood.SPORES;
            case "sunmantle_meadows", "sunglade" -> Mood.POLLEN;
            case "cloudmeadow", "aether_isles" -> Mood.MOTES;
            default -> Mood.NONE;
        };
    }
}
