package com.dragoncare.command;

import com.dragoncare.network.WoundDebugPayload;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WoundDebugCommands {
    private static final SimpleCommandExceptionType NOT_LOOKING =
            new SimpleCommandExceptionType(Text.translatable("commands.dragoncare.wounds.no_dragon"));

    private WoundDebugCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("dragonwounds")
                .requires(WoundDebugCommands::canUse)
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(context -> setFixed(
                                        context,
                                        IntegerArgumentType.getInteger(context, "percent")
                                ))))
                .then(CommandManager.literal("animate")
                        .executes(WoundDebugCommands::animate))
                .then(CommandManager.literal("reset")
                        .executes(WoundDebugCommands::reset)));
    }

    private static boolean canUse(ServerCommandSource source) {
        return source.getEntity() instanceof ServerPlayerEntity player
                && (source.hasPermissionLevel(2) || player.isCreative());
    }

    private static int setFixed(CommandContext<ServerCommandSource> context, int percent)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        DragonBaseEntity dragon = requireTarget(player);
        PacketDistributor.sendToPlayer(
                player,
                new WoundDebugPayload(dragon.getUuid(), WoundDebugPayload.MODE_FIXED, percent)
        );
        context.getSource().sendFeedback(
                () -> Text.translatable("commands.dragoncare.wounds.set", percent).formatted(Formatting.GREEN),
                false
        );
        return 1;
    }

    private static int animate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        DragonBaseEntity dragon = requireTarget(player);
        PacketDistributor.sendToPlayer(
                player,
                new WoundDebugPayload(dragon.getUuid(), WoundDebugPayload.MODE_ANIMATE, 0)
        );
        context.getSource().sendFeedback(
                () -> Text.translatable("commands.dragoncare.wounds.animate").formatted(Formatting.GREEN),
                false
        );
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        DragonBaseEntity dragon = requireTarget(player);
        PacketDistributor.sendToPlayer(
                player,
                new WoundDebugPayload(dragon.getUuid(), WoundDebugPayload.MODE_RESET, 0)
        );
        context.getSource().sendFeedback(
                () -> Text.translatable("commands.dragoncare.wounds.reset").formatted(Formatting.YELLOW),
                false
        );
        return 1;
    }

    private static DragonBaseEntity requireTarget(ServerPlayerEntity player) throws CommandSyntaxException {
        DragonBaseEntity dragon = BondCommands.getLookedAtDragon(player);
        if (dragon == null) {
            throw NOT_LOOKING.create();
        }
        return dragon;
    }
}
