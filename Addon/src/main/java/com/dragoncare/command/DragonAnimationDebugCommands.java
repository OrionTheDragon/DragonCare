package com.dragoncare.command;

import com.dragoncare.DragonCare;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.uranus.animation.Animation;
import com.iafenvoy.uranus.animation.IAnimatedEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class DragonAnimationDebugCommands {
    private static final SimpleCommandExceptionType NOT_LOOKING =
            new SimpleCommandExceptionType(Text.translatable("commands.dragoncare.anim.no_dragon"));
    private static final Map<UUID, WatchState> WATCHES = new ConcurrentHashMap<>();

    private DragonAnimationDebugCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("dragonanimdebug")
                .requires(DragonAnimationDebugCommands::canUse)
                .then(CommandManager.literal("watch").executes(DragonAnimationDebugCommands::watch))
                .then(CommandManager.literal("stop").executes(DragonAnimationDebugCommands::stop)));
    }

    public static void tick(MinecraftServer server) {
        WATCHES.entrySet().removeIf(entry -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) return true;

            WatchState watch = entry.getValue();
            DragonBaseEntity dragon = findDragon(server, watch.dragonId);
            if (dragon == null || dragon.isRemoved()) {
                player.sendMessage(Text.translatable("commands.dragoncare.anim.lost").formatted(Formatting.RED), false);
                return true;
            }

            String snapshot = createSnapshot(dragon);
            if (!snapshot.equals(watch.lastSnapshot)) {
                watch.lastSnapshot = snapshot;
                String line = "[DragonAnim " + dragon.getUuid() + "] " + snapshot;
                DragonCare.LOGGER.info(line);
                player.sendMessage(Text.literal(line).formatted(Formatting.GRAY), false);
            }
            return false;
        });
    }

    public static void clearForPlayer(UUID playerId) {
        WATCHES.remove(playerId);
    }

    public static void clear() {
        WATCHES.clear();
    }

    private static int watch(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        DragonBaseEntity dragon = BondCommands.getLookedAtDragon(player);
        if (dragon == null) throw NOT_LOOKING.create();

        WATCHES.put(player.getUuid(), new WatchState(dragon.getUuid()));
        context.getSource().sendFeedback(
                () -> Text.translatable("commands.dragoncare.anim.watch", dragon.getDisplayName())
                        .formatted(Formatting.GREEN),
                false
        );
        return 1;
    }

    private static int stop(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        WATCHES.remove(player.getUuid());
        context.getSource().sendFeedback(
                () -> Text.translatable("commands.dragoncare.anim.stop").formatted(Formatting.YELLOW),
                false
        );
        return 1;
    }

    private static boolean canUse(ServerCommandSource source) {
        return source.getEntity() instanceof ServerPlayerEntity player
                && (source.hasPermissionLevel(2) || player.isCreative());
    }

    private static DragonBaseEntity findDragon(MinecraftServer server, UUID dragonId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(dragonId);
            if (entity instanceof DragonBaseEntity dragon) return dragon;
        }
        return null;
    }

    private static String createSnapshot(DragonBaseEntity dragon) {
        String target = dragon.getTarget() == null
                ? "none"
                : dragon.getTarget().getType() + ":" + dragon.getTarget().getUuid();
        String goals = dragon.goalSelector.getGoals().stream()
                .filter(PrioritizedGoal::isRunning)
                .sorted(Comparator.comparingInt(PrioritizedGoal::getPriority))
                .map(goal -> goal.getPriority() + ":" + goal.getGoal().getClass().getSimpleName()
                        + goal.getGoal().getControls())
                .collect(Collectors.joining(",", "[", "]"));

        return String.format(Locale.ROOT,
                "command=%d sitting=%s sleeping=%s canMove=%s target=%s navIdle=%s "
                        + "pos=%s velocity=(%.3f,%.3f,%.3f) yaw=%.1f headYaw=%.1f bodyYaw=%.1f "
                        + "sitProgress=%.1f sleepProgress=%.1f animation=%s animTick~%d goals=%s",
                dragon.getCommand(),
                dragon.isInSittingPose(),
                dragon.isSleeping(),
                dragon.canMove(),
                target,
                dragon.getNavigation().isIdle(),
                dragon.getBlockPos().toShortString(),
                dragon.getVelocity().x, dragon.getVelocity().y, dragon.getVelocity().z,
                dragon.getYaw(), dragon.headYaw, dragon.bodyYaw,
                dragon.sitProgress, dragon.sleepProgress,
                getAnimationName(dragon.getAnimation()),
                (dragon.getAnimationTick() / 5) * 5,
                goals);
    }

    private static String getAnimationName(Animation animation) {
        if (animation == null || animation == IAnimatedEntity.NO_ANIMATION) return "NONE";
        if (animation == DragonBaseEntity.ANIMATION_SPEAK) return "SPEAK";
        if (animation == DragonBaseEntity.ANIMATION_EAT) return "EAT";
        if (animation == DragonBaseEntity.ANIMATION_BITE) return "BITE";
        if (animation == DragonBaseEntity.ANIMATION_FIRECHARGE) return "FIRECHARGE";
        if (animation == DragonBaseEntity.ANIMATION_SHAKEPREY) return "SHAKEPREY";
        if (animation == DragonBaseEntity.ANIMATION_WINGBLAST) return "WINGBLAST";
        if (animation == DragonBaseEntity.ANIMATION_ROAR) return "ROAR";
        if (animation == DragonBaseEntity.ANIMATION_EPIC_ROAR) return "EPIC_ROAR";
        if (animation == DragonBaseEntity.ANIMATION_TAILWHACK) return "TAILWHACK";
        return "id=" + animation.getID() + "/duration=" + animation.getDuration();
    }

    private static final class WatchState {
        private final UUID dragonId;
        private String lastSnapshot = "";

        private WatchState(UUID dragonId) {
            this.dragonId = dragonId;
        }
    }
}
