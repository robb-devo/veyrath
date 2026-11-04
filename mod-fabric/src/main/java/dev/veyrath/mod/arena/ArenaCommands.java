package dev.veyrath.mod.arena;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.veyrath.combat.ai.Style;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.List;

/**
 * {@code /veyrath arena}: go to the Proving Grounds. {@code leave}: back to where you were.
 * {@code duelist [apprentice|adept|master]}: spar in rounds against the Duelist (default adept).
 * {@code dummy}: a training dummy three blocks ahead (works in any world). {@code reset}: clear
 * the arena and rebuild its floor. Singleplayer only, like {@code /veyrath combat refill}.
 */
public final class ArenaCommands {
    private ArenaCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("veyrath").then(Commands.literal("arena")
                .requires(src -> src.getServer() != null && src.getServer().isSingleplayer())
                .executes(ctx -> ProvingGrounds.enter(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("leave").executes(ctx -> ProvingGrounds.leave(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("duelist")
                        .executes(ctx -> ProvingGrounds.duelist(ctx.getSource().getPlayerOrException(), Style.adept()))
                        .then(Commands.argument("style", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("apprentice", "adept", "master"), b))
                                .executes(ctx -> ProvingGrounds.duelist(ctx.getSource().getPlayerOrException(),
                                        Style.byId(StringArgumentType.getString(ctx, "style"))))
                                .then(Commands.argument("weapon", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                dev.veyrath.combat.content.Weapons.ALL.stream().map(dev.veyrath.combat.content.Weapons.Weapon::id).toList(), b))
                                        .executes(ctx -> {
                                            var w = dev.veyrath.combat.content.Weapons.byId(StringArgumentType.getString(ctx, "weapon"));
                                            if (w == null) {
                                                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Unknown weapon"));
                                                return 0;
                                            }
                                            return ProvingGrounds.duelist(ctx.getSource().getPlayerOrException(),
                                                    Style.byId(StringArgumentType.getString(ctx, "style")), w);
                                        }))))
                .then(Commands.literal("dummy").executes(ctx -> ProvingGrounds.dummy(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("reset").executes(ctx -> ProvingGrounds.reset(ctx.getSource().getPlayerOrException())))));
        // /veyrath boss <arena>: the same as using its sigil (tests, and players without one).
        dispatcher.register(Commands.literal("veyrath").then(Commands.literal("boss")
                .requires(src -> src.getServer() != null && src.getServer().isSingleplayer())
                .then(Commands.argument("arena", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(dev.veyrath.mod.boss.BossArenas.Arena.values()).map(a -> a.name().toLowerCase(java.util.Locale.ROOT)).toList(), b))
                        .executes(ctx -> {
                            var a = dev.veyrath.mod.boss.BossArenas.Arena.byId(StringArgumentType.getString(ctx, "arena"));
                            if (a == null) {
                                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Unknown arena"));
                                return 0;
                            }
                            return dev.veyrath.mod.boss.BossArenas.enter(ctx.getSource().getPlayerOrException(), a);
                        }))));
    }
}
