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
                                        Style.byId(StringArgumentType.getString(ctx, "style"))))))
                .then(Commands.literal("dummy").executes(ctx -> ProvingGrounds.dummy(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("reset").executes(ctx -> ProvingGrounds.reset(ctx.getSource().getPlayerOrException())))));
    }
}
