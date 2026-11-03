package dev.veyrath.preview;

import dev.veyrath.engine.WorldSpec;
import dev.veyrath.engine.atlas.AtlasBuilder;
import dev.veyrath.engine.atlas.RealmAtlas;
import dev.veyrath.engine.region.Archetype;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Command line entry point.
 *
 * <pre>
 *   atlas  [--seed S] [--res N] [--out DIR]   realm atlas maps + timings
 *   view   [--seed S] [--out DIR] [--width W]   perspective renders of landmark viewpoints
 *   bench  [--seed S]                           engine benchmark (realm build, chunk composition)
 *   audit  [--seed S] [--per N]                 world-quality audit (unstable water/lava, ranges)
 *   voxel  [--seed S] [--biome B] [--size N]    block-level render of composed chunks (trees, water)
 *   light  [--seed S] [--at crown|crown0.6|x,z] [--half N]  approximate light under floating islands
 * </pre>
 */
public final class PreviewMain {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage: atlas|view|bench|audit [--seed S] [--res N] [--out DIR] [--per N]");
            return;
        }
        String cmd = args[0];
        long seed = Long.parseLong(opt(args, "--seed", "1"));
        int res = Integer.parseInt(opt(args, "--res", "1024"));
        Path out = Path.of(opt(args, "--out", "preview-out"));
        Files.createDirectories(out);
        WorldSpec spec = WorldSpec.defaults(seed).withAtlasResolution(res);
        switch (cmd) {
            case "atlas" -> atlas(spec, out);
            case "view" -> Views.render(spec, out, Integer.parseInt(opt(args, "--width", "1600")));
            case "voxel" -> VoxelView.render(spec, out, opt(args, "--biome", "elderwood"), Integer.parseInt(opt(args, "--size", "256")),
                    Integer.parseInt(opt(args, "--width", "1280")),
                    Double.parseDouble(opt(args, "--dist", "0.42")), Double.parseDouble(opt(args, "--elev", "0.32")), opt(args, "--at", null));
            case "bench" -> {
                try { Bench.run(spec); } catch (Exception e) { throw new RuntimeException(e); }
            }
            case "audit" -> Audit.run(spec, Integer.parseInt(opt(args, "--per", "6")));
            case "riftprobe" -> RiftProbe.run(spec);
            case "falls" -> FallList.run(spec);
            case "light" -> LightProbe.run(spec, opt(args, "--at", "crown"), Integer.parseInt(opt(args, "--half", "3")));
            case "caves" -> CaveProfile.run(spec);
            case "poses" -> PoseSheet.render(out.resolve("combat"));
            default -> System.out.println("unknown command " + cmd);
        }
    }

    static RealmAtlas buildAtlas(WorldSpec spec) {
        long start = System.nanoTime();
        RealmAtlas a = AtlasBuilder.build(spec, 0, 0, (name, ms) -> System.out.printf("  %-14s %6d ms%n", name, ms));
        System.out.printf("atlas seed=%d res=%d built in %d ms%n", spec.seed(), spec.atlasResolution(), (System.nanoTime() - start) / 1_000_000);
        return a;
    }

    private static void atlas(WorldSpec spec, Path out) throws IOException {
        RealmAtlas a = buildAtlas(spec);
        String base = "seed" + spec.seed();
        ImageIO.write(AtlasMaps.relief(a), "png", new File(out.toFile(), base + "_relief.png"));
        ImageIO.write(AtlasMaps.regions(a), "png", new File(out.toFile(), base + "_regions.png"));
        report(a);
    }

    static void report(RealmAtlas a) {
        int land = 0, lake = 0;
        double maxH = -1e9;
        Map<Archetype, Integer> counts = new EnumMap<>(Archetype.class);
        for (int c = 0; c < a.n * a.n; c++) {
            if (a.continent[c] > 0) {
                land++;
                maxH = Math.max(maxH, a.elevation[c]);
                counts.merge(Archetype.byId(a.archetype[c]), 1, Integer::sum);
                if (!Float.isNaN(a.water[c])) lake++;
            }
        }
        double cellKm2 = a.cell * a.cell / 1e6;
        System.out.printf("land %.0f km2, lakes %.1f km2, highest ground y=%.0f, provinces %d, rivers %d reaches, spawn (%d, %d)%n",
                land * cellKm2, lake * cellKm2, maxH, a.provinces.size(), a.rivers.reaches().size(), a.spawnX, a.spawnZ);
        System.out.printf("highest ground before summit ceiling y=%.0f, cells above knee %d%n", a.rawPeak, a.cellsAboveKnee);
        counts.forEach((k, v) -> System.out.printf("  %-24s %6.0f km2%n", k.displayName, v * cellKm2));
    }

    static String opt(String[] args, String name, String def) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(name)) return args[i + 1];
        return def;
    }
}
