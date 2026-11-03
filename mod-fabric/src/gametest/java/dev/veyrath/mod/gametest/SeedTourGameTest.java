package dev.veyrath.mod.gametest;

import dev.veyrath.engine.debug.Locator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
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
    private static final List<String> TARGETS = List.of("summit", "rift", "rift_rim", "crown", "throne", "aethercrown", "elderbough", "cinderwake", "waterfall", "elder_tree", "arch");
    /** Ticks to let chunks around a teleport target generate and render. */
    private static final int SETTLE_TICKS = 200;
    private static final int VIEW_TICKS = 60;
    private static final int LIFT = 40;
    private static final int RENDER_DISTANCE = 24;

    @Override
    public void runTest(ClientGameTestContext context) {
        String setup = System.getProperty("veyrath.tourSetup", "vanilla");
        for (String s : System.getProperty("veyrath.tourSeeds", "1,2,3").split(",")) tour(context, s.trim(), setup);
    }

    private static void tour(ClientGameTestContext context, String seed, String setup) {
        long t0 = System.nanoTime();
        TestWorldSave save;
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
            context.runOnClient(mc -> mc.options.renderDistance().set(RENDER_DISTANCE));
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
        for (List<WorldCreationUiState.WorldTypeEntry> list : List.of(ui.getNormalPresetList(), ui.getAltPresetList())) {
            for (WorldCreationUiState.WorldTypeEntry e : list) {
                if (e.preset().is(PRESET)) return e;
            }
        }
        throw new AssertionError("World preset veyrath:veyrath is not offered on the world creation screen");
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
    }

    private static void shot(ClientGameTestContext context, String name, String setup) {
        LOG.info("screenshot {}", context.takeScreenshot(
                TestScreenshotOptions.of("seed" + name + "_" + setup).withSize(1920, 1080)));
    }
}
