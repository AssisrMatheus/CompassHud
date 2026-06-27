package dev.assisr.compasshud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers {@code /compass [on|off|save|list|delete|reload]}.
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
                .then(Commands.literal("save")
                        .executes(ctx -> save(ctx.getSource(), mod, null, null))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> save(ctx.getSource(), mod,
                                                new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                                        ctx.getSource().getPlayerOrException().blockPosition().getY(),
                                                        IntegerArgumentType.getInteger(ctx, "z")),
                                                null))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> save(ctx.getSource(), mod,
                                                        new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                                                ctx.getSource().getPlayerOrException().blockPosition().getY(),
                                                                IntegerArgumentType.getInteger(ctx, "z")),
                                                        StringArgumentType.getString(ctx, "name"))))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> save(ctx.getSource(), mod, null,
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), mod)))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> delete(ctx.getSource(), mod,
                                        IntegerArgumentType.getInteger(ctx, "id")))))
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

    private static int save(CommandSourceStack source, CompassHud mod, BlockPos pos, String label)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos savedPos = pos != null ? pos : player.blockPosition();
        SavedSpotData.SaveResult result = mod.manager().saveSpot(player, savedPos, label);
        if (result.status() == SavedSpotData.SaveResult.Status.CAPACITY_REACHED) {
            source.sendFailure(Component.literal("Saved spot limit reached (" + result.capacity() + "). Delete one first."));
            return 0;
        }

        SavedSpot spot = result.spot();
        if (result.status() == SavedSpotData.SaveResult.Status.DUPLICATE) {
            source.sendSuccess(() -> Component.literal("That exact spot is already saved as #" + spot.displayId()
                    + ": " + spot.displayLabel() + " " + formatPos(spot) + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Saved compass spot #" + spot.displayId() + " ")
                .append(Component.literal(spot.glyph()).withColor(spot.color()))
                .append(Component.literal(" " + spot.displayLabel() + " " + formatPos(spot) + "."))
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int list(CommandSourceStack source, CompassHud mod) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        java.util.List<SavedSpot> spots = mod.manager().savedSpots(player);
        if (spots.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No saved compass spots. Use /compass save [name].")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Saved compass spots:")
                .withStyle(ChatFormatting.AQUA), false);
        for (SavedSpot spot : spots) {
            source.sendSuccess(() -> listLine(spot), false);
        }
        return spots.size();
    }

    private static MutableComponent listLine(SavedSpot spot) {
        return Component.literal(spot.displayId() + ". ")
                .append(Component.literal(spot.glyph()).withColor(spot.color()))
                .append(Component.literal(" " + spot.displayLabel() + " " + formatPos(spot) + " "))
                .append(Component.literal("[delete]").withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/compass delete " + spot.displayId()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Delete saved spot #" + spot.displayId())))));
    }

    private static int delete(CommandSourceStack source, CompassHud mod, int displayId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SavedSpotData.DeleteResult result = mod.manager().deleteSavedSpot(player, displayId);
        if (result.status() == SavedSpotData.DeleteResult.Status.INVALID_INDEX) {
            source.sendFailure(Component.literal("Invalid saved spot id " + displayId
                    + ". Use /compass list to see " + result.size() + " saved spot(s)."));
            return 0;
        }

        SavedSpot spot = result.spot();
        source.sendSuccess(() -> Component.literal("Deleted saved compass spot #" + spot.displayId() + ": "
                + spot.displayLabel() + " " + formatPos(spot) + ".").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static String formatPos(SavedSpot spot) {
        return "[" + spot.dimension().identifier() + " "
                + spot.pos().getX() + " " + spot.pos().getY() + " " + spot.pos().getZ() + "]";
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
