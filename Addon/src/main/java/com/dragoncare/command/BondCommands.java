package com.dragoncare.command;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.taming.BondManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Brigadier commands for the bond / affection system.
 *
 * <ul>
 *   <li>{@code /dragonbond add <amount>} — admin (cheats / op level 2): bumps the bond
 *       points of the dragon the player is looking at, ignoring feeding caps and
 *       per-tick dedup.</li>
 *   <li>{@code /dragonbond set <points>} — admin: overwrites bond points.</li>
 *   <li>{@code /attachmentinfo} — open to everyone, prints the static bonus table for
 *       all bond levels and (if a dragon is in crosshair) that dragon's current level.</li>
 * </ul>
 */
public final class BondCommands {

    private static final SimpleCommandExceptionType NOT_LOOKING =
            new SimpleCommandExceptionType(Text.translatable("commands.dragoncare.bond.no_dragon"));

    private static final double LOOK_DISTANCE = 64.0;

    private BondCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Admin command: requires permission level 2 (= cheats enabled in singleplayer / op).
        dispatcher.register(
                CommandManager.literal("dragonbond")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> doAdd(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("points", IntegerArgumentType.integer(0))
                                        .executes(ctx -> doSet(ctx, IntegerArgumentType.getInteger(ctx, "points")))))
        );

        // Info command: open to everyone.
        dispatcher.register(
                CommandManager.literal("attachmentinfo")
                        .executes(BondCommands::doInfo)
        );
    }

    // ---------- /dragonbond add ----------

    private static int doAdd(CommandContext<ServerCommandSource> ctx, int amount) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        DragonBaseEntity dragon = getLookedAtDragon(player);
        if (dragon == null) throw NOT_LOOKING.create();

        int newPoints = BondManager.addPointsAdmin(server, dragon, amount);
        int level = BondManager.getLevel(newPoints);
        ctx.getSource().sendFeedback(() ->
                Text.translatable("commands.dragoncare.bond.added", amount, newPoints, level)
                        .formatted(Formatting.GREEN), true);
        return newPoints;
    }

    // ---------- /dragonbond set ----------

    private static int doSet(CommandContext<ServerCommandSource> ctx, int points) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        DragonBaseEntity dragon = getLookedAtDragon(player);
        if (dragon == null) throw NOT_LOOKING.create();

        int newPoints = BondManager.setPointsAdmin(server, dragon, points);
        int level = BondManager.getLevel(newPoints);
        ctx.getSource().sendFeedback(() ->
                Text.translatable("commands.dragoncare.bond.set", newPoints, level)
                        .formatted(Formatting.GREEN), true);
        return newPoints;
    }

    // ---------- /attachmentinfo ----------

    private static int doInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.translatable("commands.dragoncare.bond.info.header")
                .formatted(Formatting.GOLD, Formatting.BOLD), false);

        for (int lvl = 0; lvl <= BondManager.MAX_LEVEL; lvl++) {
            String key = "commands.dragoncare.bond.info.level_" + lvl;
            Formatting color = colorForLevel(lvl);
            src.sendFeedback(() -> Text.translatable(key).formatted(color), false);
        }

        // Optional: if the caller is a player and looking at a dragon, show its current level.
        if (src.getEntity() instanceof ServerPlayerEntity player && player.getServer() != null) {
            DragonBaseEntity dragon = getLookedAtDragon(player);
            if (dragon != null) {
                int points = BondManager.getPoints(player.getServer(), dragon.getUuid());
                int level = BondManager.getLevel(points);
                int needed = BondManager.needForNextLevel(points);
                int progress = BondManager.progressInLevel(points);
                Text line;
                if (level >= BondManager.MAX_LEVEL) {
                    line = Text.translatable("commands.dragoncare.bond.info.current_max", level, points);
                } else {
                    line = Text.translatable("commands.dragoncare.bond.info.current", level, progress, needed, points);
                }
                src.sendFeedback(() -> line.copy().formatted(Formatting.AQUA), false);

                var maxHealthAttr = dragon.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
                if (maxHealthAttr != null) {
                    double baseVal = maxHealthAttr.getBaseValue();
                    double totalVal = maxHealthAttr.getValue();
                    src.sendFeedback(() -> Text.literal("Base Max HP: " + baseVal + " | Total Max HP: " + totalVal).formatted(Formatting.GRAY), false);
                    for (var mod : maxHealthAttr.getModifiers()) {
                        src.sendFeedback(() -> Text.literal(" - Modifier: " + mod.id() + " | Value: " + mod.value() + " | Op: " + mod.operation()).formatted(Formatting.GRAY), false);
                    }
                }
            }
        }
        return 1;
    }

    // ---------- helpers ----------

    private static Formatting colorForLevel(int level) {
        return switch (level) {
            case 0 -> Formatting.GRAY;
            case 1 -> Formatting.WHITE;
            case 2 -> Formatting.GREEN;
            case 3 -> Formatting.AQUA;
            case 4 -> Formatting.LIGHT_PURPLE;
            case 5 -> Formatting.GOLD;
            default -> Formatting.WHITE;
        };
    }

    /** Raycast from the player's eye to find a {@link DragonBaseEntity} in their crosshair. */
    public static DragonBaseEntity getLookedAtDragon(ServerPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).multiply(LOOK_DISTANCE);
        Vec3d end = eye.add(look);
        Box box = player.getBoundingBox().stretch(look).expand(2.0);
        EntityHitResult hit = ProjectileUtil.raycast(
                player, eye, end, box,
                e -> e instanceof DragonBaseEntity && e.isAlive(),
                LOOK_DISTANCE * LOOK_DISTANCE
        );
        if (hit != null && hit.getEntity() instanceof DragonBaseEntity d) return d;
        return null;
    }
}
