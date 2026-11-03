package dev.veyrath.mod.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.veyrath.combat.ai.Style;
import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.combat.CombatServer;
import dev.veyrath.mod.combat.CombatSounds;
import dev.veyrath.mod.combat.SlowMotion;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Proving Grounds: a floating stone arena in its own void dimension
 * ({@code data/veyrath/dimension/proving_grounds.json}), so training never touches the player's
 * world. Built by code the first time anyone enters. Holds the sparring rounds against the
 * Duelist: a countdown, the fight, VICTORY or DEFEAT, then the next round from the marks.
 * Nobody dies here: a lethal hit ends the round instead, and a fall off the edge brings you back.
 */
public final class ProvingGrounds {
    private ProvingGrounds() {}

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, VeyrathMod.id("proving_grounds"));
    /** Feet height on the arena floor; the floor blocks are one below. */
    static final int FLOOR = 100;
    static final int RADIUS = 20;
    static final Vec3 PLAYER_MARK = new Vec3(0.5, FLOOR, -8.5), DUELIST_MARK = new Vec3(0.5, FLOOR, 9.5);
    /** Below this height an arena body is caught and put back. */
    static final int RESCUE_Y = FLOOR - 30;
    static final int COUNTDOWN_TICKS = 60, OVER_TICKS = 80;

    /** Where a player came from, kept on the player (saved with the world) until they leave. */
    record Return(ResourceKey<Level> level, Vec3 pos, float yaw, float pitch) {
        static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("level").forGetter(Return::level),
                Vec3.CODEC.fieldOf("pos").forGetter(Return::pos),
                Codec.FLOAT.fieldOf("yaw").forGetter(Return::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(Return::pitch)).apply(i, Return::new));
    }

    static final AttachmentType<Return> RETURN = AttachmentRegistry.create(VeyrathMod.id("arena_return"),
            b -> b.persistent(Return.CODEC).copyOnDeath());

    enum Phase { COUNTDOWN, FIGHT, OVER }

    static final class Round {
        final UUID player, duelist;
        final Style style;
        final ServerBossEvent bar;
        Phase phase = Phase.COUNTDOWN;
        int timer, number = 1, wins, losses;

        Round(UUID player, UUID duelist, Style style, ServerBossEvent bar) {
            this.player = player;
            this.duelist = duelist;
            this.style = style;
            this.bar = bar;
        }
    }

    /** Singleplayer first: one round at a time. */
    private static Round round;

    /** Mod init: registers the return-point attachment (class initialisation). */
    public static void init() {
        VeyrathMod.LOG.debug("Proving Grounds attachment {}", RETURN);
    }

    // ------------------------------------------------------------------ commands

    public static int enter(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        ServerLevel arena = server == null ? null : server.getLevel(LEVEL);
        if (arena == null) {
            p.sendSystemMessage(Component.translatable("veyrath.arena.missing"));
            return 0;
        }
        if (!built(arena)) build(arena);
        if (p.level() != arena) {
            p.setAttached(RETURN, new Return(p.level().dimension(), p.position(), p.getYRot(), p.getXRot()));
            p.sendSystemMessage(Component.translatable("veyrath.arena.welcome"));
        }
        teleport(p, arena, PLAYER_MARK, 0, 5);
        return 1;
    }

    public static int leave(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        if (server == null) return 0;
        if (round != null && round.player.equals(p.getUUID())) clearRound(server);
        Return r = p.getAttached(RETURN);
        p.removeAttached(RETURN);
        ServerLevel to = r == null ? null : server.getLevel(r.level());
        if (to == null || to.dimension().equals(LEVEL)) {
            to = server.overworld();
            BlockPos top = to.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.ZERO);
            teleport(p, to, Vec3.atBottomCenterOf(top), p.getYRot(), 0);
        } else {
            teleport(p, to, r.pos(), r.yaw(), r.pitch());
        }
        return 1;
    }

    public static int duelist(ServerPlayer p, Style style) {
        if (enter(p) == 0) return 0;
        MinecraftServer server = p.level().getServer();
        ServerLevel arena = server.getLevel(LEVEL);
        clearRound(server);
        DuelistEntity d = new DuelistEntity(ArenaEntities.DUELIST, arena);
        d.setStyle(style);
        place(d, DUELIST_MARK, 180);
        arena.addFreshEntity(d);
        CombatServer.attachDuelist(d, style, arena.getRandom().nextLong());
        CombatServer.setDuelistActive(d, false);
        ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("veyrath.arena.bar", styleName(style)),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.addPlayer(p);
        round = new Round(p.getUUID(), d.getUUID(), style, bar);
        return 1;
    }

    public static int dummy(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return 0;
        double yawR = Math.toRadians(p.getYRot());
        Vec3 at = new Vec3(Math.floor(p.getX() - Math.sin(yawR) * 3) + 0.5, p.getY(), Math.floor(p.getZ() + Math.cos(yawR) * 3) + 0.5);
        TrainingDummyEntity d = new TrainingDummyEntity(ArenaEntities.TRAINING_DUMMY, level);
        place(d, at, p.getYRot() + 180);
        d.setHome(at);
        level.addFreshEntity(d);
        return 1;
    }

    /** Ends the round, removes every Duelist and dummy in the arena and rebuilds the floor. */
    public static int reset(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        ServerLevel arena = server == null ? null : server.getLevel(LEVEL);
        if (arena == null) return enter(p);
        clearRound(server);
        List<Entity> gone = new ArrayList<>();
        for (Entity e : arena.getAllEntities()) if (e instanceof DuelistEntity || e instanceof TrainingDummyEntity) gone.add(e);
        gone.forEach(Entity::discard);
        build(arena);
        return enter(p);
    }

    // ------------------------------------------------------------------ rounds

    public static void tick(MinecraftServer server) {
        ServerLevel arena = server.getLevel(LEVEL);
        if (arena == null) return;
        if (server.getTickCount() % 4 == 0) rescueFallen(arena);
        if (round == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(round.player);
        Entity e = arena.getEntity(round.duelist);
        if (p == null || p.level() != arena || !(e instanceof DuelistEntity d) || !d.isAlive()) {
            clearRound(server);
            return;
        }
        round.bar.setProgress(Math.clamp(d.getHealth() / d.getMaxHealth(), 0f, 1f));
        round.timer++;
        switch (round.phase) {
            case COUNTDOWN -> {
                Component sub = Component.translatable("veyrath.arena.round", round.number, styleName(round.style));
                if (round.timer == 1) title(p, Component.literal("3"), sub);
                else if (round.timer == 21) title(p, Component.literal("2"), sub);
                else if (round.timer == 41) title(p, Component.literal("1"), sub);
                else if (round.timer >= COUNTDOWN_TICKS) {
                    title(p, Component.translatable("veyrath.arena.fight").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), Component.empty());
                    arena.playSound(null, p.getX(), p.getY(), p.getZ(), CombatSounds.ev(SoundEvents.BELL_BLOCK), SoundSource.PLAYERS, 1.0f, 1.2f);
                    round.phase = Phase.FIGHT;
                    round.timer = 0;
                    CombatServer.setDuelistActive(d, true);
                }
            }
            case FIGHT -> {
                if (!CombatServer.inStance(p) && round.timer % 40 == 1) {
                    p.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("veyrath.arena.stance_hint")));
                }
            }
            case OVER -> {
                if (round.timer >= OVER_TICKS) nextRound(arena, p, d);
            }
        }
    }

    /** ALLOW_DEATH: nobody dies in the Proving Grounds; a lethal hit in a round decides it. */
    public static boolean allowDeath(LivingEntity e, DamageSource source, float amount) {
        if (!(e.level() instanceof ServerLevel level) || !level.dimension().equals(LEVEL)) return true;
        if (e instanceof ServerPlayer p) {
            p.setHealth(p.getMaxHealth());
            if (round != null && round.player.equals(p.getUUID()) && round.phase == Phase.FIGHT) roundOver(level, p, false);
            return false;
        }
        if (e instanceof DuelistEntity d) {
            d.setHealth(d.getMaxHealth());
            if (round != null && round.duelist.equals(d.getUUID()) && round.phase == Phase.FIGHT) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(round.player);
                if (p != null) roundOver(level, p, true);
            }
            return false;
        }
        return true;
    }

    private static void roundOver(ServerLevel arena, ServerPlayer p, boolean won) {
        round.phase = Phase.OVER;
        round.timer = 0;
        if (won) round.wins++;
        else round.losses++;
        if (arena.getEntity(round.duelist) instanceof DuelistEntity d) CombatServer.setDuelistActive(d, false);
        Component t = won ? Component.translatable("veyrath.arena.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : Component.translatable("veyrath.arena.defeat").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        title(p, t, Component.translatable("veyrath.arena.score", round.wins, round.losses));
        // The finishing blow plays out in slow motion.
        if (won) SlowMotion.trigger(arena.getServer(), 8, 16);
    }

    private static void nextRound(ServerLevel arena, ServerPlayer p, DuelistEntity d) {
        round.phase = Phase.COUNTDOWN;
        round.timer = 0;
        round.number++;
        p.setHealth(p.getMaxHealth());
        teleport(p, arena, PLAYER_MARK, 0, 5);
        d.setHealth(d.getMaxHealth());
        d.teleportTo(DUELIST_MARK.x, DUELIST_MARK.y, DUELIST_MARK.z);
        face(d, 180);
        d.setDeltaMovement(Vec3.ZERO);
        CombatServer.resetDuelist(d);
        CombatServer.setDuelistActive(d, false);
    }

    private static void clearRound(MinecraftServer server) {
        if (round == null) return;
        round.bar.removeAllPlayers();
        ServerLevel arena = server.getLevel(LEVEL);
        Entity d = arena == null ? null : arena.getEntity(round.duelist);
        if (d != null) d.discard();
        round = null;
    }

    /** "round 2 COUNTDOWN 1-0", or "none" (game tests). */
    public static String roundInfo() {
        return round == null ? "none" : "round " + round.number + " " + round.phase + " " + round.wins + "-" + round.losses;
    }

    public static void clear(MinecraftServer server) {
        if (round != null) round.bar.removeAllPlayers();
        round = null;
    }

    private static void rescueFallen(ServerLevel arena) {
        for (ServerPlayer p : List.copyOf(arena.players())) {
            if (p.getY() < RESCUE_Y) teleport(p, arena, PLAYER_MARK, 0, 5);
        }
        List<Entity> fallen = new ArrayList<>();
        for (Entity e : arena.getAllEntities()) {
            if (e.getY() < RESCUE_Y && (e instanceof DuelistEntity || e instanceof TrainingDummyEntity)) fallen.add(e);
        }
        for (Entity e : fallen) {
            e.teleportTo(DUELIST_MARK.x, DUELIST_MARK.y, DUELIST_MARK.z);
            e.setDeltaMovement(Vec3.ZERO);
            e.resetFallDistance();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Component styleName(Style s) { return Component.translatable("veyrath.style." + s.id); }

    private static void title(ServerPlayer p, Component title, Component sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 4));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    static void teleport(ServerPlayer p, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        p.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
    }

    private static void place(LivingEntity e, Vec3 pos, float yaw) {
        e.setPos(pos.x, pos.y, pos.z);
        face(e, yaw);
    }

    private static void face(LivingEntity e, float yaw) {
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
    }

    // ------------------------------------------------------------------ the arena

    static boolean built(ServerLevel l) {
        return l.getBlockState(new BlockPos(0, FLOOR - 1, 0)).is(Blocks.CHISELED_QUARTZ_BLOCK);
    }

    /**
     * A disc of radius 20 at y 99: a quartz centre in a ring of froglights, eight tuff spokes to an
     * andesite ring with lights at the spoke ends, smooth stone, a deepslate border and a waist-high
     * parapet, eight basalt pillars with lanterns, and a jagged rock underside so it floats.
     */
    static void build(ServerLevel l) {
        int edge = RADIUS + 2;
        for (int x = -edge; x <= edge; x++) {
            for (int z = -edge; z <= edge; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > RADIUS + 1.7) continue;
                int depth = (int) Math.round(3 + (RADIUS + 1.7 - r) * 0.55 + hash(x, 0, z) * 2.5);
                for (int k = 2; k <= depth; k++) {
                    BlockState s = k == 2 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : k >= depth - 1 ? Blocks.DEEPSLATE.defaultBlockState() : stone(x, FLOOR - k, z);
                    set(l, x, FLOOR - k, z, s);
                }
                if (r > RADIUS + 0.5) {
                    set(l, x, FLOOR - 1, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                    set(l, x, FLOOR, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                    set(l, x, FLOOR + 1, z, Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());
                    continue;
                }
                set(l, x, FLOOR - 1, z, floor(x, z, r));
                for (int y = FLOOR; y <= FLOOR + 8; y++) set(l, x, y, z, Blocks.AIR.defaultBlockState());
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int px = (int) Math.round(Math.cos(a) * 18), pz = (int) Math.round(Math.sin(a) * 18);
            for (int y = FLOOR; y <= FLOOR + 3; y++) set(l, px, y, pz, Blocks.POLISHED_BASALT.defaultBlockState());
            set(l, px, FLOOR + 4, pz, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            set(l, px, FLOOR + 5, pz, Blocks.LANTERN.defaultBlockState());
        }
    }

    private static BlockState floor(int x, int z, double r) {
        if (r < 1) return Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
        if (r < 3) return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        if (r < 4) return Blocks.OCHRE_FROGLIGHT.defaultBlockState();
        double ang = Math.toDegrees(Math.atan2(z, x));
        double m = ((ang % 45) + 45) % 45;
        boolean spoke = r * Math.sin(Math.toRadians(Math.min(m, 45 - m))) < 0.55;
        if (r < 10) return spoke ? Blocks.POLISHED_TUFF.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
        if (r < 11) return spoke ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
        if (r < 19) return Blocks.SMOOTH_STONE.defaultBlockState();
        return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    }

    private static BlockState stone(int x, int y, int z) {
        double h = hash(x, y, z);
        if (h < 0.6) return Blocks.STONE.defaultBlockState();
        if (h < 0.85) return Blocks.ANDESITE.defaultBlockState();
        return Blocks.TUFF.defaultBlockState();
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
        l.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
    }

    /** Deterministic value in [0, 1) per block. */
    private static double hash(int x, int y, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
