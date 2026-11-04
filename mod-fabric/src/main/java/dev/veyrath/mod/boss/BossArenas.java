package dev.veyrath.mod.boss;

import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.arena.ProvingGrounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Boss arenas, in the Proving Grounds' void dimension far from the sparring disc, each built by
 * code the first time anyone enters. Entering sets the same return point as the Proving Grounds
 * (leave with {@code /veyrath arena leave}, or the sigil again). A fall off an arena brings the
 * player back to its entry; a boss that falls is put back on its mark.
 */
public final class BossArenas {
    private BossArenas() {}

    public enum Arena {
        OATH_HALL("oath_hall", 1000, 0, 24);

        final String id;
        final int cx, cz, radius;

        Arena(String id, int cx, int cz, int radius) {
            this.id = id;
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
        }

        Vec3 entry() { return new Vec3(cx + 0.5, FLOOR, cz + radius - 5.5); }

        Vec3 bossMark() { return new Vec3(cx + 0.5, FLOOR, cz - 9.5); }

        AABB bounds() { return new AABB(cx - radius - 4, FLOOR - 40, cz - radius - 4, cx + radius + 5, FLOOR + 40, cz + radius + 5); }

        public static Arena byId(String id) {
            for (Arena a : values()) if (a.id.equals(id)) return a;
            return null;
        }
    }

    static final int FLOOR = 100;

    public static int enter(ServerPlayer p, Arena a) {
        MinecraftServer server = p.level().getServer();
        ServerLevel level = server == null ? null : server.getLevel(ProvingGrounds.LEVEL);
        if (level == null) {
            p.sendSystemMessage(Component.translatable("veyrath.arena.missing"));
            return 0;
        }
        if (!built(level, a)) build(level, a);
        ProvingGrounds.rememberReturn(p);
        ProvingGrounds.teleport(p, level, a.entry(), 180, 0);
        ensureBoss(level, a);
        return 1;
    }

    /** The arena's boss, spawned at its mark if none is alive there. */
    static BossEntity ensureBoss(ServerLevel level, Arena a) {
        List<BossEntity> there = level.getEntitiesOfClass(BossEntity.class, a.bounds(), BossEntity::isAlive);
        if (!there.isEmpty()) return there.getFirst();
        EntityType<? extends BossEntity> type = switch (a) {
            case OATH_HALL -> BossEntities.VAREK;
        };
        BossEntity b = type.create(level, EntitySpawnReason.COMMAND);
        if (b == null) return null;
        Vec3 m = a.bossMark();
        b.snapTo(m.x, m.y, m.z, 0, 0);
        b.setYHeadRot(0);
        b.setYBodyRot(0);
        b.setHome(m);
        level.addFreshEntity(b);
        return b;
    }

    /** The boss mark of the arena around a position, else the position itself. */
    static Vec3 homeNear(Vec3 pos) {
        for (Arena a : Arena.values()) if (a.bounds().contains(pos)) return a.bossMark();
        return pos;
    }

    /** Puts players and bosses that fell off an arena back (called from the Proving Grounds' rescue). */
    public static boolean rescue(ServerPlayer p, ServerLevel level) {
        for (Arena a : Arena.values()) {
            AABB b = a.bounds();
            if (p.getX() >= b.minX && p.getX() <= b.maxX && p.getZ() >= b.minZ && p.getZ() <= b.maxZ) {
                ProvingGrounds.teleport(p, level, a.entry(), 180, 0);
                return true;
            }
        }
        return false;
    }

    static boolean built(ServerLevel l, Arena a) {
        return l.getBlockState(new BlockPos(a.cx, FLOOR - 1, a.cz)).is(Blocks.CHISELED_POLISHED_BLACKSTONE);
    }

    static void build(ServerLevel l, Arena a) {
        switch (a) {
            case OATH_HALL -> buildOathHall(l, a);
        }
        VeyrathMod.LOG.info("Built boss arena {}", a.id);
    }

    /**
     * The Oath Hall: a ruined round hall of dark stone floating in the void. A blackstone sigil at
     * the centre, rings of deepslate tile and cracked brick, twelve pillars (four broken, rubble at
     * their feet), lanterns on chains, the broken throne at the north on three steps.
     */
    private static void buildOathHall(ServerLevel l, Arena a) {
        int R = a.radius, cx = a.cx, cz = a.cz;
        for (int x = -R - 2; x <= R + 2; x++) {
            for (int z = -R - 2; z <= R + 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > R + 1.6) continue;
                int depth = (int) Math.round(3 + (R + 1.6 - r) * 0.7 + hash(x, 0, z) * 3);
                for (int k = 2; k <= depth; k++) {
                    set(l, cx + x, FLOOR - k, cz + z, k >= depth - 1 ? Blocks.BLACKSTONE.defaultBlockState() : rock(x, FLOOR - k, z));
                }
                set(l, cx + x, FLOOR - 1, cz + z, hallFloor(x, z, r, R));
                for (int y = FLOOR; y <= FLOOR + 16; y++) set(l, cx + x, y, cz + z, Blocks.AIR.defaultBlockState());
                if (r > R + 0.4) {
                    // A broken low wall: high where the hash says, gaps elsewhere.
                    int h = hash(x, 7, z) < 0.35 ? 0 : hash(x, 8, z) < 0.6 ? 1 : 2;
                    for (int y = 0; y < h; y++) set(l, cx + x, FLOOR + y, cz + z, hash(x, y, z) < 0.3 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                            : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                }
            }
        }
        for (int k = 0; k < 12; k++) {
            double ang = Math.toRadians(15 + 30 * k);
            int px = (int) Math.round(Math.cos(ang) * (R - 4)), pz = (int) Math.round(Math.sin(ang) * (R - 4));
            boolean broken = k % 3 == 1;
            int height = broken ? 3 + (int) (hash(k, 1, 1) * 4) : 13;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = dx != 0 && dz != 0;
                    for (int y = 0; y < height - (corner ? 1 : 0); y++) {
                        BlockState s = y == 0 || y == height - 1 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                                : corner ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                        if (broken && y == height - 1 && hash(dx, y, dz) < 0.5) continue;
                        set(l, cx + px + dx, FLOOR + y, cz + pz + dz, s);
                    }
                }
            }
            if (broken) {
                for (int i = 0; i < 6; i++) {
                    int rx = px + (int) Math.round((hash(k, i, 3) - 0.5) * 7), rz = pz + (int) Math.round((hash(k, i, 4) - 0.5) * 7);
                    set(l, cx + rx, FLOOR, cz + rz, hash(k, i, 5) < 0.5 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                            : Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
                }
            } else {
                // A lantern on a chain from the pillar's inner face.
                int ix = (int) Math.round(Math.cos(ang) * (R - 6)), iz = (int) Math.round(Math.sin(ang) * (R - 6));
                set(l, cx + ix, FLOOR + 10, cz + iz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                set(l, cx + ix, FLOOR + 9, cz + iz, Blocks.IRON_CHAIN.defaultBlockState());
                set(l, cx + ix, FLOOR + 8, cz + iz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            }
        }
        // The broken throne at the north, on three steps.
        for (int step = 0; step < 3; step++) {
            for (int x = -4 + step; x <= 4 - step; x++) {
                for (int z = -R + 2; z <= -R + 5 - step; z++) {
                    set(l, cx + x, FLOOR + step, cz + z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                }
            }
        }
        for (int y = 3; y <= 7; y++) {
            set(l, cx, FLOOR + y, cz - R + 2, y == 7 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            if (y <= 5) {
                set(l, cx - 1, FLOOR + y, cz - R + 2, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                if (y <= 4) set(l, cx + 1, FLOOR + y, cz - R + 2, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        set(l, cx - 1, FLOOR + 3, cz - R + 3, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
        set(l, cx + 1, FLOOR + 3, cz - R + 3, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        set(l, cx, FLOOR - 1, cz, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
    }

    private static BlockState hallFloor(int x, int z, double r, int R) {
        if (r < 1.2) return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        double ang = Math.toDegrees(Math.atan2(z, x));
        double m = ((ang % 60) + 60) % 60;
        boolean spoke = r * Math.sin(Math.toRadians(Math.min(m, 60 - m))) < 0.6;
        if (r < 4) return spoke ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        if (r < 5) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        double h = hash(x, 1, z);
        if (r < 14) {
            if (spoke) return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            return h < 0.12 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        if (r < 15) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        if (r > R - 0.5) return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        return h < 0.18 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : h < 0.3 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }

    private static BlockState rock(int x, int y, int z) {
        double h = hash(x, y, z);
        return h < 0.55 ? Blocks.DEEPSLATE.defaultBlockState() : h < 0.85 ? Blocks.TUFF.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    private static double hash(int x, int y, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
