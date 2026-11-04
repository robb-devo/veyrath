package dev.veyrath.mod.combat;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /veyrath combat stats}: accepted and rejected inputs and strike results this session.
 * {@code /veyrath combat refill}: full stamina, Aether, Unbound gauge and no cooldowns (testing;
 * singleplayer only).
 * {@code /veyrath combat shapes}: shows the server's hit shapes of every active strike as
 * particles, toggled (debugging; singleplayer only).
 */
public final class CombatCommands {
    private CombatCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("veyrath").then(Commands.literal("combat")
                .then(Commands.literal("stats").executes(ctx -> {
                    String s = CombatServer.stats();
                    ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))
                .then(Commands.literal("refill").requires(src -> src.getServer() != null && src.getServer().isSingleplayer()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!CombatServer.refill(p)) {
                        ctx.getSource().sendFailure(Component.literal("Enter combat stance first (Z)."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Stamina, Aether, Unbound gauge and cooldowns refilled."), false);
                    return 1;
                }))
                .then(Commands.literal("shapes").requires(src -> src.getServer() != null && src.getServer().isSingleplayer()).executes(ctx -> {
                    boolean on = CombatServer.toggleShapes();
                    ctx.getSource().sendSuccess(() -> Component.literal(on ? "Hit shapes shown." : "Hit shapes hidden."), false);
                    return 1;
                }))));
    }
}
