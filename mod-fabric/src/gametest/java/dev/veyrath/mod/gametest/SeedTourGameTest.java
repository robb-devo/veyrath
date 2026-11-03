package dev.veyrath.mod.gametest;

import dev.veyrath.engine.debug.Locator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Seed tour ({@code gradlew :mod-fabric:runClientGameTest}): for each seed, creates a
 * singleplayer world with the VEYRATH preset, runs {@code /veyrath info}, teleports to the main
 * landmark targets and screenshots each, then saves, reopens and screenshots once more.
 *
 * <p>Seeds come from the system property {@code veyrath.tourSeeds} (default "1,2,3"); screenshot
 * names end in the setup tag from {@code veyrath.tourSetup} (default "vanilla").
 */
public final class SeedTourGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH tour");
    private static final ResourceKey<net.minecraft.world.level.levelgen.presets.WorldPreset> PRESET =
            ResourceKey.create(Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("veyrath", "veyrath"));
    private static final List<String> TARGETS = List.of("summit", "rift", "rift_rim", "crown", "throne", "aethercrown", "elderbough", "cinderwake", "waterfall", "elder_tree", "arch", "caldera", "window", "fault", "crown_falls", "spires");
    /** Biomes without a locator target, visited via the nearest matching biome cell. */
    private static final List<String> BIOME_STOPS = List.of("veyrath:mossdeep_hollows", "veyrath:ashen_wastes");
    /** Ticks to let chunks around a teleport target generate and render. */
    private static final int SETTLE_TICKS = 200;
    private static final int VIEW_TICKS = 60;
    private static final int LIFT = 40;
    private static final int STILL_TICKS = 600;
    private static final int FPS_SAMPLES = 10;
    private static final int RENDER_DISTANCE = 24;

    @Override
    public void runTest(ClientGameTestContext context) {
        String setup = System.getProperty("veyrath.tourSetup", "vanilla");
        for (String s : System.getProperty("veyrath.tourSeeds", "1,2,3").split(",")) tour(context, s.trim(), setup);
    }

    private static void tour(ClientGameTestContext context, String seed, String setup) {
        long t0 = System.nanoTime();
        TestWorldSave save;
        // Set before the world exists: the integrated server takes its view distance from the
        // client when it starts. Raising it later left a ring with neither chunks nor LOD.
        context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
        try (TestSingleplayerContext sp = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(ui -> {
                    ui.setSeed(seed);
                    ui.setAllowCommands(true);
                    ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    ui.setWorldType(findPreset(ui));
                })
                .create()) {
            long ready = System.nanoTime();
            LOG.info("seed {}: world creation to spawn ready {} ms", seed, (ready - t0) / 1_000_000);
            sp.getServer().runCommand("gamemode spectator @p");
            context.waitTicks(SETTLE_TICKS);
            vista(context, sp, seed, "spawn", setup);
            sp.getServer().runCommand("execute as @p at @p run veyrath info");
            sp.getServer().runCommand("gamerule advance_time false");
            sp.getServer().runCommand("time set noon");
            for (String target : TARGETS) {
                if (!Locator.targets().contains(target)) continue;
                long tt = System.nanoTime();
                sp.getServer().runCommand("execute as @p at @p run veyrath tp " + target);
                context.waitTicks(SETTLE_TICKS);
                LOG.info("seed {}: target {} settled after {} ms", seed, target, (System.nanoTime() - tt) / 1_000_000);
                vista(context, sp, seed, target, setup);
            }
            for (String biome : BIOME_STOPS) {
                if (!toBiome(sp, seed, biome)) continue;
                context.waitTicks(SETTLE_TICKS);
                vista(context, sp, seed, biome.substring(biome.indexOf(':') + 1), setup);
            }
            save = sp.getWorldSave();
        }
        long r0 = System.nanoTime();
        try (TestSingleplayerContext sp = save.open()) {
            LOG.info("seed {}: reopen to ready {} ms", seed, (System.nanoTime() - r0) / 1_000_000);
            context.waitTicks(SETTLE_TICKS);
            shot(context, seed + "_reopen", setup);
        }
    }

    private static WorldCreationUiState.WorldTypeEntry findPreset(WorldCreationUiState ui) {
        // The normal list is the minecraft:normal world preset tag, i.e. what the World Type button
        // cycles through without holding Shift. VEYRATH must be there, not only in the alt list.
        for (WorldCreationUiState.WorldTypeEntry e : ui.getNormalPresetList()) {
            if (e.preset().is(PRESET)) return e;
        }
        boolean alt = ui.getAltPresetList().stream().anyMatch(e -> e.preset().is(PRESET));
        throw new AssertionError(alt
                ? "veyrath:veyrath is only in the alternative world types, not in the minecraft:normal tag"
                : "World preset veyrath:veyrath is not offered on the world creation screen");
    }

    /**
     * Lifts the (spectator) camera above the landmark and shoots the four compass directions,
     * looking slightly down, so the screenshot shows the landscape rather than the nearest wall.
     */
    private static void vista(ClientGameTestContext context, TestSingleplayerContext sp, String seed, String target, String setup) {
        sp.getServer().runCommand("execute as @p at @p run tp @s ~ ~" + LIFT + " ~");
        context.waitTicks(SETTLE_TICKS);
        String[] names = {"s", "w", "n", "e"};
        for (int i = 0; i < 4; i++) {
            sp.getServer().runCommand("execute as @p at @p run tp @s ~ ~ ~ " + (i * 90) + " 18");
            context.waitTicks(VIEW_TICKS);
            shot(context, seed + "_" + target + "_" + names[i], setup);
        }
        averageFps(context, seed, target);
        census(sp, seed, target);
        if (target.equals("spawn")) {
            // Stand still and look west again: does the LOD band between chunks and Distant
            // Horizons fill in once loading has caught up?
            sp.getServer().runCommand("execute as @p at @p run tp @s ~ ~ ~ 90 18");
            context.waitTicks(STILL_TICKS);
            averageFps(context, seed, "spawn_still");
            shot(context, seed + "_spawn_w_still", setup);
        }
    }

    /** Teleports the player to the surface of the nearest cell of {@code biome}; false if none within 6 km. */
    private static boolean toBiome(TestSingleplayerContext sp, String seed, String biome) {
        BlockPos found = sp.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) player.level();
            var hit = level.findClosestBiome3d(h -> h.unwrapKey().map(k -> k.identifier().toString().equals(biome)).orElse(false),
                    player.blockPosition(), 6400, 32, 64);
            if (hit == null) return null;
            BlockPos p = hit.getFirst();
            level.getChunk(p.getX() >> 4, p.getZ() >> 4); // generate it, or the heightmap reads min_y
            return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()), p.getZ());
        });
        LOG.info("seed {}: nearest {} {}", seed, biome, found);
        if (found == null) return false;
        sp.getServer().runCommand("execute as @p run tp @s " + found.getX() + " " + found.getY() + " " + found.getZ());
        return true;
    }

    /**
     * Counts ground cover and water features in the 64x64 columns around the player, from each
     * column's ground (motion-blocking surface, minus a few blocks) to 40 blocks above it.
     */
    private static void census(TestSingleplayerContext sp, String seed, String target) {
        String counts = sp.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) player.level();
            BlockPos c = player.blockPosition();
            int grass = 0, flowers = 0, vines = 0, roots = 0, falling = 0, loaded = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = c.getX() - 32; x < c.getX() + 32; x++) {
                for (int z = c.getZ() - 32; z < c.getZ() + 32; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    loaded++;
                    // Ground to 40 above it, plus the 160 blocks below the player so the undersides
                    // of floating islands (vines, roots, falls) are counted too.
                    int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                    int y0 = Math.min(ground - 4, c.getY() - 160), y1 = Math.max(ground + 40, c.getY());
                    for (int y = y0; y <= y1; y++) {
                        BlockState st = level.getBlockState(pos.set(x, y, z));
                        if (st.is(Blocks.SHORT_GRASS)) grass++;
                        else if (st.is(BlockTags.SMALL_FLOWERS)) flowers++;
                        else if (st.is(Blocks.CAVE_VINES) || st.is(Blocks.CAVE_VINES_PLANT)) vines++;
                        else if (st.is(Blocks.HANGING_ROOTS)) roots++;
                        else if (st.is(Blocks.WATER) && st.getValue(LiquidBlock.LEVEL) == 8) falling++;
                    }
                }
            }
            return String.format("columns %d short_grass %d flowers %d cave_vines %d hanging_roots %d falling_water %d",
                    loaded, grass, flowers, vines, roots, falling);
        });
        LOG.info("seed {}: {} census {}", seed, target, counts);
    }

    /** Samples the client's frames-per-second counter once a second for 10 s, facing south. */
    private static void averageFps(ClientGameTestContext context, String seed, String target) {
        int sum = 0, min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < FPS_SAMPLES; i++) {
            context.waitTicks(20);
            int fps = context.computeOnClient(mc -> mc.getFps());
            sum += fps;
            min = Math.min(min, fps);
            max = Math.max(max, fps);
        }
        LOG.info("seed {}: {} fps avg {} min {} max {} over {} s", seed, target, sum / FPS_SAMPLES, min, max, FPS_SAMPLES);
    }

    private static void shot(ClientGameTestContext context, String name, String setup) {
        LOG.info("screenshot {}", context.takeScreenshot(
                TestScreenshotOptions.of("seed" + name + "_" + setup).withSize(1920, 1080)));
    }
}
