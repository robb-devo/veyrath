package dev.veyrath.mod.gametest;

import dev.veyrath.engine.VeyrathWorld;
import dev.veyrath.engine.debug.Locator;
import dev.veyrath.engine.formation.FloatingIslands;
import dev.veyrath.mod.VeyrathRuntime;
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
    private static final List<String> TARGETS = List.of("summit", "rift", "rift_rim", "crown", "throne", "aethercrown", "elderbough", "cinderwake", "waterfall", "elder_tree", "arch", "caldera", "window", "fault", "crown_falls", "spires", "basalt");
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
        if (!GameTests.selected("tour")) return;
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
            String only = System.getProperty("veyrath.tourTargets", "");
            List<String> targets = only.isBlank() ? TARGETS : List.of(only.split(","));
            for (String target : targets) {
                if (target.equals("none")) continue;
                if (!Locator.targets().contains(target)) continue;
                long tt = System.nanoTime();
                sp.getServer().runCommand("execute as @p at @p run veyrath tp " + target);
                context.waitTicks(SETTLE_TICKS);
                LOG.info("seed {}: target {} settled after {} ms", seed, target, (System.nanoTime() - tt) / 1_000_000);
                vista(context, sp, seed, target, setup);
            }
            crownStudy(context, sp, seed, setup);
            for (String biome : only.isBlank() ? BIOME_STOPS : List.<String>of()) {
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
        if (target.equals("basalt")) {
            // Second sample a minute later, to tell a passing realm build from a steady cost.
            context.waitTicks(1200);
            averageFps(context, seed, "basalt_after_60s");
        }
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

    /**
     * The Crown of Aether up close: the Veil (its widest waterfall) from 160 blocks out, the plunge
     * pool looking up, and the underside from the ground right below and from 600 blocks away
     * (Distant Horizons range), at noon and at midnight.
     */
    private static void crownStudy(ClientGameTestContext context, TestSingleplayerContext sp, String seed, String setup) {
        int[] v = sp.getServer().computeOnServer(server -> {
            VeyrathWorld world = VeyrathRuntime.world();
            VeyrathWorld.Realm realm = world.realmAt(world.spawnEngineX(), world.spawnEngineZ());
            FloatingIslands.Island crown = realm.islands().crown();
            if (crown == null) return null;
            var curtains = realm.islands().curtains(crown);
            if (curtains.isEmpty()) return null;
            var strips = curtains.get(0).strips();
            FloatingIslands.Fall f = strips.get(strips.size() / 2);
            ServerLevel level = server.overworld();
            int ox = world.originX(), oz = world.originZ();
            int fx = f.fallX() - ox, fz = f.fallZ() - oz, cx = (int) crown.x() - ox, cz = (int) crown.z() - oz;
            level.getChunk(fx >> 4, fz >> 4);
            level.getChunk(cx >> 4, cz >> 4);
            int bottom = (int) (crown.y() - crown.depth());
            // Heightmaps stop at the island or the falling water, so scan down from under the island.
            int pool = groundBelow(level, fx + 14 * (f.fallX() - f.lipX()), fz + 14 * (f.fallZ() - f.lipZ()), bottom - 2);
            int below = groundBelow(level, cx + (int) crown.radius() / 2, cz, bottom - 2);
            return new int[] {fx, fz, f.fallX() - f.lipX(), f.fallZ() - f.lipZ(), f.sourceY(), pool, cx, cz,
                    bottom, (int) crown.radius(), below};
        });
        if (v == null) {
            LOG.info("seed {}: no Crown curtain in the spawn realm", seed);
            return;
        }
        int fx = v[0], fz = v[1], dx = v[2], dz = v[3], source = v[4], pool = v[5], cx = v[6], cz = v[7], bottom = v[8], radius = v[9];
        LOG.info("seed {}: Veil at {} {} from y{} to ground/pool beside it y{} ({} blocks); Crown centre {} {}, underside about y{}, radius {}",
                seed, fx, fz, source, pool, source - pool, cx, cz, bottom, radius);
        sp.getServer().runCommand("time set noon");
        look(context, sp, fx + 160 * dx, pool + 30, fz + 160 * dz, fx, (source + pool) / 2, fz);
        shot(context, seed + "_crown_falls_veil", setup);
        look(context, sp, fx + 14 * dx, pool + 2, fz + 14 * dz, fx, source, fz);
        shot(context, seed + "_crown_falls_pool_up", setup);
        // Ground below the Crown, a quarter radius in from the rim, looking up at the underside.
        int ux = cx + radius / 2, uy = v[10];
        look(context, sp, ux, uy + 3, cz, cx, bottom, cz);
        shot(context, seed + "_crown_underside_near", setup);
        census(sp, seed, "crown_underside");
        sp.getServer().runCommand("execute as @p at @p run tp @s ~ ~ ~ 0 20");
        context.waitTicks(VIEW_TICKS);
        shot(context, seed + "_crown_ground_below", setup);
        sp.getServer().runCommand("time set midnight");
        look(context, sp, ux, uy + 3, cz, cx, bottom, cz);
        shot(context, seed + "_crown_underside_midnight", setup);
        sp.getServer().runCommand("time set noon");
        look(context, sp, cx + 600, uy + 40, cz, cx, bottom, cz);
        shot(context, seed + "_crown_underside_far", setup);
    }

    private static void setHudHidden(net.minecraft.client.Minecraft mc, boolean hidden) {
        GameTests.setHudHidden(mc, hidden);
    }

    /** First y below {@code fromY} whose block is not air: the ground or water surface under an island. */
    private static int groundBelow(ServerLevel level, int x, int z, int fromY) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, fromY, z);
        while (pos.getY() > level.getMinY() && level.getBlockState(pos).isAir()) pos.move(0, -1, 0);
        return pos.getY() + 1;
    }

    private static void look(ClientGameTestContext context, TestSingleplayerContext sp, int x, int y, int z, int tx, int ty, int tz) {
        sp.getServer().runCommand("execute as @p run tp @s " + x + " " + y + " " + z + " facing " + tx + " " + ty + " " + tz);
        context.waitTicks(SETTLE_TICKS);
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
            int grass = 0, flowers = 0, vines = 0, roots = 0, falling = 0, lichen = 0, loaded = 0;
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
                        else if (st.is(Blocks.GLOW_LICHEN)) lichen++;
                        else if (st.is(Blocks.WATER) && st.getValue(LiquidBlock.LEVEL) == 8) falling++;
                    }
                }
            }
            return String.format("columns %d short_grass %d flowers %d cave_vines %d hanging_roots %d glow_lichen %d falling_water %d",
                    loaded, grass, flowers, vines, roots, lichen, falling);
        });
        LOG.info("seed {}: {} census {}", seed, target, counts);
    }

    /** Samples the client's frames-per-second counter once a second for 10 s, facing south. */
    private static void averageFps(ClientGameTestContext context, String seed, String target) {
        LOG.info("seed {}: {} fps sample start", seed, target);
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
        // Hide HUD and chat (F1 state; the 26.3 Hud keeps it in a private field) for the capture.
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            setHudHidden(mc, true);
        });
        context.waitTicks(2);
        LOG.info("screenshot {}", context.takeScreenshot(
                TestScreenshotOptions.of("seed" + name + "_" + setup).withSize(1920, 1080)));
        context.runOnClient(mc -> setHudHidden(mc, false));
    }
}
