package dev.assisr.compasshud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers {@code /compass [on|off|reload]}.
 *
 * <p>Toggling is available to everyone (matching the original default-true
 * {@code compasshud.use}); {@code reload} requires permission level 2 (op),
 * matching the original default-op {@code compasshud.reload}.
 */
public final class CompassCommand {

    private CompassCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CompassHud mod) {
        dispatcher.register(Commands.literal("compass")
                .executes(ctx -> toggle(ctx.getSource(), mod))
                .then(Commands.literal("on").executes(ctx -> setEnabled(ctx.getSource(), mod, true)))
                .then(Commands.literal("off").executes(ctx -> setEnabled(ctx.getSource(), mod, false)))
                .then(Commands.literal("toggle").executes(ctx -> toggle(ctx.getSource(), mod)))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> reload(ctx.getSource(), mod))));
    }

    private static int toggle(CommandSourceStack source, CompassHud mod) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean now = mod.manager().toggle(player);
        feedback(source, now);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, CompassHud mod, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        mod.manager().setEnabled(player, enabled);
        feedback(source, enabled);
        return 1;
    }

    private static void feedback(CommandSourceStack source, boolean enabled) {
        source.sendSuccess(() -> Component.literal("Compass HUD " + (enabled ? "enabled." : "disabled."))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
    }

    private static int reload(CommandSourceStack source, CompassHud mod) {
        if (mod.reload()) {
            source.sendSuccess(() -> Component.literal("CompassHud config reloaded.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        source.sendFailure(Component.literal("CompassHud reload failed; see server log."));
        return 0;
    }
}
