package dev.veyrath.mod.client.audio;

import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.client.combat.LocalCombat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * VEYRATH's own score. Vanilla's music manager never plays (see MusicManagerMixin); this picks a
 * mood every tick and crossfades between our streamed tracks (assets/veyrath/sounds/music, built
 * by tools/audio/build_music.py, all CC0) on the MUSIC channel, so the vanilla music slider still
 * sets the volume.
 *
 * <p>Moods, highest wins: BOSS (a boss bar that asks for music), COMBAT (a hit landed on or by the
 * player in the last 10 s, an arena round, or a locked target in stance), TENSION (a hostile within
 * 18 blocks), EXPLORE. Explore plays like an open-world score: one piece, then a quiet gap of
 * 40-110 s, then another, never the same twice in a row. Combat and boss loop while they last and
 * fade out slowly; winning a fight (the last thing the player hit is dead) plays a victory sting.
 * MENU plays the choir on the title screen.
 */
public final class VeyrathMusic {
    private VeyrathMusic() {}

    public enum Mood { MENU, EXPLORE, TENSION, COMBAT, BOSS, VICTORY }

    static final Map<Mood, List<String>> POOLS = new EnumMap<>(Mood.class);
    static {
        POOLS.put(Mood.MENU, List.of("fantasy_choir_2", "fantasy_choir_1", "fantasy_choir_3"));
        POOLS.put(Mood.EXPLORE, List.of("the_hope", "new_sunrise", "at_home", "once_upon_a_time", "fantasy_choir_1", "fantasy_choir_2", "fantasy_choir_3"));
        POOLS.put(Mood.TENSION, List.of("evil_approach", "ancient_power"));
        POOLS.put(Mood.COMBAT, List.of("battle_a", "battle_b", "determined_pursuit", "battlegrounds", "prepare_to_fight"));
        POOLS.put(Mood.BOSS, List.of("epic_boss_battle", "dramatic_boss", "sinister_boss"));
        POOLS.put(Mood.VICTORY, List.of("victory", "legend_will_rise"));
    }

    /** Per-mood loudness on top of the -18 LUFS master, so calm pieces sit under the fights. */
    private static float baseGain(Mood m) {
        return switch (m) {
            case MENU -> 0.85f;
            case EXPLORE -> 0.62f;
            case TENSION -> 0.7f;
            case COMBAT, BOSS -> 0.9f;
            case VICTORY -> 0.85f;
        };
    }

    static final int COMBAT_HOLD = 200, TENSION_HOLD = 160, TENSION_RANGE = 18;

    private static Track current;
    private static final List<Track> fading = new ArrayList<>();
    private static final Map<Mood, Deque<String>> recent = new EnumMap<>(Mood.class);
    private static Mood mood;
    private static long tick, lastCombat = -100000, lastTension = -100000;
    /** Tick at which the next explore piece may start (the quiet gap). */
    private static long nextExplore;
    private static int lastOpponent = -1;
    private static long lastOpponentHit;
    private static boolean victoryPending;
    private static final RandomSource RNG = RandomSource.create();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(VeyrathMusic::tick);
    }

    /** A combat hit involving the local player (from the hit packet). */
    public static void combatHit(int opponent, boolean byMe) {
        lastCombat = tick;
        if (byMe) {
            lastOpponent = opponent;
            lastOpponentHit = tick;
        }
    }

    public static Mood mood() { return mood; }

    public static String playing() { return current == null ? "" : current.trackId; }

    private static void tick(Minecraft mc) {
        tick++;
        Mood want = desired(mc);
        fading.removeIf(t -> t.isStopped() || !mc.getSoundManager().isActive(t) && t.everActive);
        if (current != null) {
            boolean ended = current.everActive && !mc.getSoundManager().isActive(current) || current.isStopped();
            if (current.everActive || mc.getSoundManager().isActive(current)) current.everActive = true;
            if (ended) {
                if (current.mood == Mood.VICTORY) victoryPending = false;
                if (current.mood == Mood.EXPLORE || current.mood == Mood.MENU || current.mood == Mood.VICTORY)
                    nextExplore = tick + 20L * (40 + RNG.nextInt(71));
                current = null;
            }
        }
        if (want != mood) {
            Mood was = mood;
            mood = want;
            if (current != null && current.mood != want) {
                // Into a fight fast, out of it slowly; a victory cuts the fight a little quicker.
                float out = switch (want) {
                    case COMBAT, BOSS -> 1.2f;
                    case VICTORY -> 1.5f;
                    default -> 4.5f;
                };
                current.fadeOut(out);
                fading.add(current);
                current = null;
            }
            // Arriving in the world (the loading screen stops every sound) the score starts soon;
            // after a fight or a tense stretch, a short breath first.
            if (want == Mood.EXPLORE) nextExplore = was == null || was == Mood.MENU ? tick + 60 : Math.max(nextExplore, tick + 20 * 8);
            if (want == Mood.MENU) nextExplore = tick + 20;
        }
        if (current == null) {
            boolean calm = want == Mood.EXPLORE || want == Mood.MENU;
            if (!calm || tick >= nextExplore) start(mc, want);
        }
    }

    private static Mood desired(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            victoryPending = false;
            lastOpponent = -1;
            return Mood.MENU;
        }
        var p = mc.player;
        var bars = mc.gui.hud.getBossOverlay();
        if (bars.shouldPlayMusic()) return Mood.BOSS;
        if (ArenaWatch.active(mc)) lastCombat = tick;
        Entity lock = LocalCombat.active() ? LocalCombat.lockTarget() : null;
        if (lock instanceof LivingEntity le && le.isAlive() && le.distanceTo(p) < 16) lastCombat = tick;
        if (p.hurtTime > 0 && hostileNear(mc, 10)) lastCombat = tick;
        boolean inCombat = tick - lastCombat < COMBAT_HOLD;
        // Won: the last thing we hit died within moments of our last blow, and nothing else is fighting us.
        if (lastOpponent >= 0 && tick - lastOpponentHit < 120) {
            var e = mc.level.getEntity(lastOpponent);
            if (e == null || e instanceof LivingEntity le && le.isDeadOrDying()) {
                lastOpponent = -1;
                if (!hostileNear(mc, 14)) {
                    victoryPending = true;
                    lastCombat = -100000;
                    inCombat = false;
                }
            }
        }
        if (victoryPending) return Mood.VICTORY;
        if (inCombat) return Mood.COMBAT;
        if (tick % 20 == 0 && hostileNear(mc, TENSION_RANGE)) lastTension = tick;
        if (tick - lastTension < TENSION_HOLD) return Mood.TENSION;
        return Mood.EXPLORE;
    }

    private static boolean hostileNear(Minecraft mc, double r) {
        var p = mc.player;
        return !mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e instanceof Enemy && e.isAlive() && e != p).isEmpty();
    }

    private static void start(Minecraft mc, Mood m) {
        List<String> pool = POOLS.get(m);
        Deque<String> rec = recent.computeIfAbsent(m, k -> new ArrayDeque<>());
        List<String> fresh = new ArrayList<>(pool);
        fresh.removeAll(rec);
        if (fresh.isEmpty()) {
            rec.clear();
            fresh = new ArrayList<>(pool);
            if (current != null) fresh.remove(current.trackId);
        }
        String id = fresh.get(RNG.nextInt(fresh.size()));
        rec.addLast(id);
        while (rec.size() > Math.max(1, pool.size() / 2)) rec.removeFirst();
        boolean loop = m == Mood.COMBAT || m == Mood.BOSS || m == Mood.TENSION;
        float in = switch (m) {
            case COMBAT, BOSS -> 0.6f;
            case VICTORY -> 0.2f;
            case TENSION -> 3f;
            default -> 2.5f;
        };
        current = new Track(id, m, baseGain(m), loop, in);
        mc.getSoundManager().play(current);
    }

    private static final class Track extends AbstractTickableSoundInstance {
        final String trackId;
        final Mood mood;
        final float base;
        float gain, step, target = 1;
        boolean everActive;

        Track(String id, Mood mood, float base, boolean loop, float fadeInSeconds) {
            super(SoundEvent.createVariableRangeEvent(VeyrathMod.id("music.track." + id)), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.trackId = id;
            this.mood = mood;
            this.base = base;
            this.looping = loop;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.step = 1f / Math.max(1, fadeInSeconds * 20);
            this.gain = 0.0001f;
            this.volume = base * gain;
        }

        void fadeOut(float seconds) {
            target = 0;
            step = 1f / Math.max(1, seconds * 20);
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public void tick() {
            gain += Math.signum(target - gain) * Math.min(step, Math.abs(target - gain));
            // Equal-power curve so the crossfade does not dip in the middle.
            volume = base * (float) Math.sin(gain * Math.PI / 2);
            if (target == 0 && gain <= 0.0005f) stop();
        }
    }

    private static final class ArenaWatch {
        /** An arena round shows its notched red bar; we treat any visible boss bar as a fight. */
        static boolean active(Minecraft mc) {
            return !((dev.veyrath.mod.client.mixin.BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).veyrath$events().isEmpty();
        }
    }

}
