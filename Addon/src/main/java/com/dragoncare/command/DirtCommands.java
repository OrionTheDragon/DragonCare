package com.dragoncare.command;

import com.dragoncare.mechanics.DragonDirtManager;
import com.dragoncare.mechanics.DragonDirtState;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class DirtCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("dragondirt")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 5))
                                .executes(DirtCommands::setDirtLevel))
        );
    }

    private static int setDirtLevel(CommandContext<ServerCommandSource> context) {
        int level = IntegerArgumentType.getInteger(context, "level");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("Only players can use this command."));
            return 0;
        }

        // Find the dragon the player is looking at
        double reachDistance = 20.0;
        HitResult hit = player.raycast(reachDistance, 1.0F, false);
        
        Entity target = null;
        if (hit.getType() == HitResult.Type.ENTITY) {
            target = ((EntityHitResult) hit).getEntity();
        } else {
            // Manual raycast for entities
            net.minecraft.util.math.Vec3d cameraPos = player.getCameraPosVec(1.0F);
            net.minecraft.util.math.Vec3d rotation = player.getRotationVec(1.0F);
            net.minecraft.util.math.Vec3d end = cameraPos.add(rotation.x * reachDistance, rotation.y * reachDistance, rotation.z * reachDistance);
            net.minecraft.util.math.Box box = player.getBoundingBox().stretch(rotation.multiply(reachDistance)).expand(1.0D, 1.0D, 1.0D);
            
            double closestDist = reachDistance * reachDistance;
            for (Entity e : player.getWorld().getOtherEntities(player, box, e -> e instanceof DragonBaseEntity)) {
                net.minecraft.util.math.Box eBox = e.getBoundingBox().expand(e.getTargetingMargin());
                java.util.Optional<net.minecraft.util.math.Vec3d> intercept = eBox.raycast(cameraPos, end);
                if (intercept.isPresent()) {
                    double dist = cameraPos.squaredDistanceTo(intercept.get());
                    if (dist < closestDist) {
                        closestDist = dist;
                        target = e;
                    }
                }
            }
        }

        if (!(target instanceof DragonBaseEntity dragon)) {
            source.sendError(Text.literal("You must be looking at a dragon to set its dirt level."));
            return 0;
        }

        DragonDirtState state = DragonDirtState.get(source.getServer());
        DragonDirtState.DirtData data = state.getOrCreate(dragon.getUuid());
        
        data.dirtLevel = level;
        data.lastUpdateTick = source.getServer().getOverworld().getTime();
        state.markDirty();
        
        DragonDirtManager.syncToTrackers(dragon, level);
        DragonDirtManager.applyDirtEffects(dragon, level);

        source.sendFeedback(() -> Text.literal("Set dirt level of " + dragon.getName().getString() + " to " + level), true);
        return 1;
    }
}
