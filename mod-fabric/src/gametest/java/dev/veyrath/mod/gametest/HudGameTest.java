package dev.veyrath.mod.gametest;

import dev.veyrath.mod.client.audio.VeyrathMusic;
import dev.veyrath.mod.client.combat.CombatClient;
import dev.veyrath.mod.client.combat.LocalCombat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD and music ({@code -Pveyrath.tests=hud}): our HUD out of stance (hotbar with items, hurt
 * vitals, effects, compass with hearth and hostiles), in stance (abilities, Aether and stamina)
 * and with a boss bar, then after a death (the grave on the compass); screenshots {@code hud_*}.
 * Checks the music director: our choir on the title screen, never vanilla music, an explore piece
 * in the world, tension near hostiles, combat once a fight starts.
 */
public final class HudGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("VEYRATH hud test");
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GameTests.selected("hud")) return;
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(40);
        String menu = context.computeOnClient(mc -> VeyrathMusic.mood() + " " + VeyrathMusic.playing());
        LOG.info("title screen music: {}", menu);
        check(menu.startsWith("MENU ") && !menu.endsWith(" "), "the title screen plays our choir (" + menu + ")");
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            var server = sp.getServer();
            server.runCommand("gamerule send_command_feedback false");
            server.runCommand("time set 1000");
            server.runCommand("gamerule advance_time false");
            server.runCommand("difficulty peaceful");
            context.waitTicks(40);
            server.runCommand("give @p minecraft:diamond_sword");
            server.runCommand("give @p minecraft:bread 23");
            server.runCommand("give @p minecraft:torch 64");
            server.runCommand("give @p minecraft:bow[damage=200]");
            server.runCommand("give @p minecraft:iron_pickaxe");
            server.runCommand("item replace entity @p armor.chest with minecraft:iron_chestplate");
            server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
            server.runCommand("xp set @p 14 levels");
            server.runCommand("xp set @p 40 points");
            server.runCommand("effect give @p minecraft:speed 300 1");
            server.runCommand("effect give @p minecraft:regeneration 12 0");
            server.runCommand("execute as @p at @p run setworldspawn ~40 ~ ~-60");
            context.waitTicks(10);
            server.runCommand("damage @p 7 minecraft:generic");
            server.runCommand("execute as @p at @p run tp @p ~ ~ ~ 0 0");
            context.waitTicks(30);
            String music = context.computeOnClient(mc -> VeyrathMusic.mood() + " " + VeyrathMusic.playing());
            String vanilla = context.computeOnClient(mc -> String.valueOf(mc.getMusicManager().getCurrentMusicTranslationKey()));
            LOG.info("world music: {} (vanilla: {})", music, vanilla);
            check(music.startsWith("EXPLORE ") && !music.endsWith(" "), "an explore piece plays in the world (" + music + ")");
            check("null".equals(vanilla), "no vanilla music (" + vanilla + ")");
            shot(context, "hud_01_explore_north");
            server.runCommand("execute as @p at @p run tp @p ~ ~ ~ 135 0");
            context.waitTicks(10);
            shot(context, "hud_02_explore_southeast");

            // Hostiles on the compass (no AI; peaceful would remove them, so normal difficulty now).
            server.runCommand("difficulty normal");
            server.runCommand("execute as @p at @p run summon minecraft:husk ^3 ^ ^9 {NoAI:1b,PersistenceRequired:1b}");
            server.runCommand("execute as @p at @p run summon minecraft:husk ^-7 ^ ^14 {NoAI:1b,PersistenceRequired:1b}");
            server.runCommand("execute as @p at @p run summon minecraft:skeleton ^12 ^ ^5 {NoAI:1b,PersistenceRequired:1b}");
            context.waitTicks(50);
            String tension = context.computeOnClient(mc -> VeyrathMusic.mood().name());
            check("TENSION".equals(tension), "hostiles nearby turn the music tense (" + tension + ")");
            shot(context, "hud_03_hostiles");

            // Stance: abilities, Aether and stamina, the weapon name.
            // The weapon item in hand is the weapon of the stance.
            server.runCommand("item replace entity @p hotbar.0 with veyrath:wren_and_thorn");
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            context.waitTicks(10);
            shot(context, "hud_03b_weapon_item");
            context.getInput().pressKey(CombatClient.STANCE_KEY);
            context.waitTicks(20);
            check(context.computeOnClient(mc -> LocalCombat.active()), "stance is on");
            String drawn = context.computeOnClient(mc -> dev.veyrath.combat.content.Weapons.of(LocalCombat.fighter().set).id());
            check(dev.veyrath.combat.content.Weapons.TWIN_BLADES.id().equals(drawn), "the held Wren and Thorn is drawn in stance (" + drawn + ")");
            context.runOnClient(mc -> VeyrathMusic.combatHit(-1, false));
            context.waitTicks(40);
            String combat = context.computeOnClient(mc -> VeyrathMusic.mood() + " " + VeyrathMusic.playing());
            check(combat.startsWith("COMBAT ") && !combat.endsWith(" "), "a fight turns the music to combat (" + combat + ")");
            shot(context, "hud_04_stance");

            server.runCommand("bossbar add veyrath:test {\"text\":\"The Hollow Sun\"}");
            server.runCommand("bossbar set veyrath:test players @a");
            server.runCommand("bossbar set veyrath:test color yellow");
            server.runCommand("bossbar set veyrath:test value 64");
            context.waitTicks(20);
            shot(context, "hud_05_boss_bar");
            server.runCommand("bossbar remove veyrath:test");
            context.getInput().pressKey(CombatClient.STANCE_KEY);
            context.waitTicks(10);
            server.runCommand("kill @e[type=!minecraft:player]");
            server.runCommand("kill @p");
            context.waitTicks(30);
            context.runOnClient(mc -> {
                if (mc.player != null && mc.player.isDeadOrDying()) mc.player.respawn();
            });
            context.waitTicks(40);
            server.runCommand("execute as @p at @p run tp @p ~ ~ ~ 180 0");
            context.waitTicks(20);
            shot(context, "hud_06_after_death");
        }
        if (!failures.isEmpty()) throw new AssertionError("hud test: " + String.join("; ", failures));
        LOG.info("hud test passed");
    }

    private void check(boolean ok, String what) {
        LOG.info("{} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> {
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.hud.setOverlayMessage(Component.empty(), false);
        });
        context.takeScreenshot(TestScreenshotOptions.of(name));
    }
}
