package com.dragoncare.taming;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.iceandfire.entity.EntityFireDragon;
import com.iafenvoy.iceandfire.entity.EntityIceDragon;
import com.iafenvoy.iceandfire.entity.EntityLightningDragon;
import com.dragoncare.advancement.AchievementGranter;
import com.dragoncare.taming.TamingState.FriendshipData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * High-level driver for befriending and taming dragons.
 *
 * <p>Persistent friendship state lives in {@link TamingState}. Active taming
 * sessions (boss-bar countdown) are kept in a transient static map вЂ” they're
 * cleared on server restart by design (the friendship is preserved, the player
 * just needs to feed the dragon again to start a new session).
 */
public final class DragonTamingManager {

    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    /** playerUuid -> active taming session (one per player). */
    private static final Map<UUID, TamingSession> ACTIVE = new HashMap<>();

    private DragonTamingManager() {}

    // =========================================================================
    //  Queries
    // =========================================================================

    public static boolean isBefriended(MinecraftServer server, UUID dragonId, UUID playerId) {
        FriendshipData fd = TamingState.get(server).peek(dragonId, playerId);
        return fd != null && fd.active;
    }

    public static boolean hasActiveSession(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    public static boolean isBeingTamed(UUID dragonId) {
        for (TamingSession s : ACTIVE.values()) {
            if (s.dragonId.equals(dragonId)) return true;
        }
        return false;
    }

    // =========================================================================
    //  Public API: programmatic manipulation
    // =========================================================================

    /** Automatically befriends a dragon to a player without needing a meal. */
    public static void forceBefriend(MinecraftServer server, EntityDragonBase dragon, PlayerEntity player) {
        if (server == null || dragon.isTamed()) return;
        TamingState state = TamingState.get(server);
        FriendshipData fd = state.getOrCreate(dragon.getUuid(), player.getUuid());
        if (!fd.active) {
            fd.active = true;
            fd.feedingsDone = 0;
            state.markDirty();
            if (dragon.getTarget() == player) {
                dragon.setTarget(null);
            }
            // Send visual feedback to let the player know the baby calmed down
            player.sendMessage(
                    Text.translatable("message.dragoncare.taming.befriended")
                            .formatted(Formatting.GREEN, Formatting.BOLD),
                    true
            );
        }
    }

    // =========================================================================
    //  Public API: feeding
    // =========================================================================

    /**
     * Handles a feeding action. Sends the appropriate feedback message to the
     * player and starts a taming session if the dragon was already befriended.
     *
     * @return {@code true} if the meal item should be consumed.
     */
    public static boolean feedDragon(ServerPlayerEntity player, EntityDragonBase dragon) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        UUID dragonId = dragon.getUuid();
        UUID playerId = player.getUuid();

        // Block if this player is already taming any dragon
        if (hasActiveSession(playerId)) {
            sendActionBar(player, "message.dragoncare.taming.busy", Formatting.RED);
            return false;
        }
        // Block if any other player is taming this dragon
        if (isBeingTamed(dragonId)) {
            sendActionBar(player, "message.dragoncare.taming.other_busy", Formatting.RED);
            return false;
        }

        TamingState state = TamingState.get(server);
        FriendshipData fd = state.getOrCreate(dragonId, playerId);
        long currentTick = server.getOverworld().getTime();

        if (fd.grudgeUntilTick > currentTick) {
            dragon.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 0.8F);
            player.sendMessage(
                    Text.translatable("message.dragoncare.taming.grudge", formatTimer(fd.grudgeUntilTick - currentTick))
                            .formatted(Formatting.DARK_RED, Formatting.BOLD),
                    false
            );
            return false; // Meal not consumed
        }

        dragon.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 1.0F);

        if (fd.active) {
            // Initialize duration from config if first time
            if (fd.tamingDurationTicks < 0) {
                fd.tamingDurationTicks = com.dragoncare.config.AddonConfig.TAMING_DURATION_SECONDS.get() * 20L;
                state.markDirty();
            }
            
            // Already befriended в†’ second-style feeding starts the taming timer.
            startSession(player, dragon, fd.tamingDurationTicks);
            AchievementGranter.grant(player, AchievementGranter.TAMING_STARTED);
            player.sendMessage(
                    Text.translatable("message.dragoncare.taming.started",
                                    formatTimer(fd.tamingDurationTicks))
                            .formatted(Formatting.GOLD),
                    false
            );
            return true;
        }

        // Not yet befriended вЂ” accumulate feedings toward the requirement
        fd.feedingsDone++;
        if (fd.feedingsDone >= fd.feedingsNeeded) {
            fd.active = true;
            fd.feedingsDone = 0;
            state.markDirty();

            if (dragon.getTarget() == player) {
                dragon.setTarget(null);
            }
            AchievementGranter.grant(player, AchievementGranter.DRAGON_BEFRIENDED);
            player.sendMessage(
                    Text.translatable("message.dragoncare.taming.befriended")
                            .formatted(Formatting.GREEN, Formatting.BOLD),
                    false
            );
        } else {
            state.markDirty();
            player.sendMessage(
                    Text.translatable("message.dragoncare.taming.progress",
                                    fd.feedingsDone, fd.feedingsNeeded)
                            .formatted(Formatting.YELLOW),
                    false
            );
        }
        return true;
    }

    // =========================================================================
    //  Public API: betrayal (player hits dragon)
    // =========================================================================

    /**
     * Called whenever a player damages a dragon. If they had any relationship
     * (passive or active taming), it gets reset with penalties, unless the damage
     * is within the forgiveness threshold.
     */
    public static void onPlayerHitDragon(ServerPlayerEntity player, EntityDragonBase dragon, float damage) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        UUID dragonId = dragon.getUuid();
        UUID playerId = player.getUuid();

        TamingSession session = ACTIVE.get(playerId);
        boolean wasTaming = session != null && session.dragonId.equals(dragonId);

        TamingState state = TamingState.get(server);
        FriendshipData fd = state.peek(dragonId, playerId);

        if (!wasTaming && (fd == null || !fd.active)) {
            // No relationship to break.
            return;
        }

        // Ensure entry exists for the penalty
        if (fd == null) {
            fd = state.getOrCreate(dragonId, playerId);
        }

        long currentTick = server.getOverworld().getTime();

        if (wasTaming) {
            if (damage >= 10.0f) {
                // Instantly break trust
            } else if (damage >= 3.0f) {
                if (!session.mediumDamageForgiven) {
                    session.mediumDamageForgiven = true;
                    return; // Forgiven
                }
            } else {
                // Small damage (< 3.0f)
                session.smallDamageTicks.removeIf(t -> currentTick - t > 2400); // 2 mins
                session.smallDamageTicks.add(currentTick);
                if (session.smallDamageTicks.size() < 5) {
                    return; // Forgiven
                }
            }

            // Cancel the active session and multiply the next attempt's duration.
            cancelSessionInternal(playerId);
            double multiplier = com.dragoncare.config.AddonConfig.TAMING_PENALTY_MULTIPLIER.get();
            long newDuration = (long) (fd.tamingDurationTicks * multiplier);
            long maxDuration = 20L * 60L * 60L * 24L; // 24h cap
            fd.tamingDurationTicks = Math.min(newDuration, maxDuration);
            fd.active = false;
            fd.feedingsNeeded = Math.max(2, fd.feedingsNeeded);
            fd.feedingsDone = 0;

            player.sendMessage(
                    Text.translatable("message.dragoncare.taming.hit_during_taming",
                                    formatTimer(fd.tamingDurationTicks))
                            .formatted(Formatting.RED, Formatting.BOLD),
                    false
            );
        } else {
            // Was just befriended (passive) -> break trust if cumulative damage > 3 within 3 mins.
            fd.recentDamage.removeIf(r -> currentTick - r.tick > 3600); // 3 mins
            fd.recentDamage.add(new TamingState.DamageRecord(damage, currentTick));
            
            double totalDamage = 0;
            for (TamingState.DamageRecord r : fd.recentDamage) {
                totalDamage += r.amount;
            }
            
            if (totalDamage <= 3.0) {
                return; // Forgiven
            }

            fd.active = false;
            fd.feedingsNeeded = Math.max(2, fd.feedingsNeeded);
            fd.feedingsDone = 0;

            player.sendMessage(
                    Text.translatable("message.dragoncare.taming.hit_befriended")
                            .formatted(Formatting.RED, Formatting.BOLD),
                    false
            );
        }

        // Apply Grudge Logic
        if (fd.grudgeUntilTick > currentTick) {
            // Hit during cooldown: add 1 minute (1200 ticks)
            fd.grudgeUntilTick += 1200L;
        } else {
            fd.betrayalCount++;
            if (fd.betrayalCount > 1) {
                // Second betrayal: 10 mins (12000 ticks)
                // Subsequent: +5 mins (6000 ticks) each
                long basePenalty = 12000L; 
                long extraPenalty = (fd.betrayalCount - 2) * 6000L;
                fd.grudgeUntilTick = currentTick + basePenalty + extraPenalty;
            }
        }

        state.markDirty();

        // Make the dragon retaliate immediately
        dragon.setTarget(player);
    }

    // =========================================================================
    //  Sessions
    // =========================================================================

    private static void startSession(ServerPlayerEntity player, EntityDragonBase dragon, long durationTicks) {
        cancelSessionInternal(player.getUuid());

        ServerBossBar bar = new ServerBossBar(
                buildBarTitle(dragon.getBlockPos(), durationTicks),
                BossBar.Color.YELLOW,
                BossBar.Style.NOTCHED_10
        );
        bar.setPercent(0.0F);
        bar.addPlayer(player);

        TamingSession session = new TamingSession(
                dragon.getUuid(),
                player.getUuid(),
                player.getWorld().getTime(),
                durationTicks,
                bar
        );
        ACTIVE.put(player.getUuid(), session);
    }

    private static void cancelSessionInternal(UUID playerId) {
        TamingSession s = ACTIVE.remove(playerId);
        if (s != null) {
            s.bar.setVisible(false);
            s.bar.clearPlayers();
        }
    }

    // =========================================================================
    //  Per-tick driver
    // =========================================================================

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, TamingSession>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            TamingSession s = it.next().getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(s.playerId);
            if (player == null) {
                s.bar.clearPlayers();
                it.remove();
                continue;
            }

            EntityDragonBase dragon = findDragon(server, s.dragonId);
            if (dragon == null || dragon.isModelDead() || dragon.isRemoved()) {
                player.sendMessage(
                        Text.translatable("message.dragoncare.taming.lost").formatted(Formatting.RED),
                        false
                );
                s.bar.clearPlayers();
                it.remove();
                continue;
            }

            if (player.getWorld() != dragon.getWorld()
                    || player.squaredDistanceTo(dragon) > MAX_DISTANCE_SQ) {
                player.sendMessage(
                        Text.translatable("message.dragoncare.taming.too_far").formatted(Formatting.RED),
                        false
                );
                s.bar.clearPlayers();
                it.remove();
                continue;
            }

            long elapsed = player.getWorld().getTime() - s.startTick;
            long remaining = Math.max(0L, s.durationTicks - elapsed);
            float pct = Math.min(1.0F, (float) elapsed / (float) s.durationTicks);

            s.bar.setPercent(pct);
            s.bar.setName(buildBarTitle(dragon.getBlockPos(), remaining));

            if (elapsed >= s.durationTicks) {
                completeTaming(server, player, dragon);
                s.bar.setName(Text.translatable("message.dragoncare.taming.success")
                        .formatted(Formatting.GREEN, Formatting.BOLD));
                s.bar.setPercent(1.0F);
                s.bar.clearPlayers();
                it.remove();
            }
        }
    }

    private static void completeTaming(MinecraftServer server, ServerPlayerEntity player, EntityDragonBase dragon) {
        dragon.setTamed(true);
        dragon.setOwner(player);
        dragon.setHunger(dragon.getHunger() + 20);
        dragon.heal(Math.min(dragon.getHealth(), dragon.getMaxHealth() / 2.0F));
        dragon.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        // Friendship entry no longer needed once the dragon is officially tamed
        TamingState.get(server).remove(dragon.getUuid(), player.getUuid());
        grantTamedAdvancement(player, dragon);

        // Grant color-specific taming criterion for Dracomania
        com.iafenvoy.iceandfire.data.DragonColor color = com.iafenvoy.iceandfire.data.DragonColor.getById(dragon.getVariant());
        if (color != null) {
            AchievementGranter.grantCriterion(player, AchievementGranter.DRACOMANIA, color.name().toLowerCase());
        }

        player.sendMessage(
                Text.translatable("message.dragoncare.taming.tamed")
                        .formatted(Formatting.GOLD, Formatting.BOLD),
                false
        );
    }

    private static void grantTamedAdvancement(ServerPlayerEntity player, EntityDragonBase dragon) {
        if (dragon instanceof EntityFireDragon) {
            AchievementGranter.grant(player, AchievementGranter.TAMED_FIRE_DRAGON);
        } else if (dragon instanceof EntityIceDragon) {
            AchievementGranter.grant(player, AchievementGranter.TAMED_ICE_DRAGON);
        } else if (dragon instanceof EntityLightningDragon) {
            AchievementGranter.grant(player, AchievementGranter.TAMED_LIGHTNING_DRAGON);
        }
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static Text buildBarTitle(BlockPos pos, long remainingTicks) {
        return Text.translatable(
                "message.dragoncare.taming.bar",
                pos.getX(), pos.getY(), pos.getZ(), formatTimer(remainingTicks)
        );
    }

    private static String formatTimer(long ticks) {
        int totalSeconds = (int) (ticks / 20L);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static EntityDragonBase findDragon(MinecraftServer server, UUID dragonId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity e = world.getEntity(dragonId);
            if (e instanceof EntityDragonBase dragon) return dragon;
        }
        return null;
    }

    private static void sendActionBar(PlayerEntity player, String key, Formatting formatting) {
        player.sendMessage(Text.translatable(key).formatted(formatting), true);
    }

    private static class TamingSession {
        final UUID dragonId;
        final UUID playerId;
        final long startTick;
        final long durationTicks;
        final ServerBossBar bar;
        boolean mediumDamageForgiven = false;
        final java.util.List<Long> smallDamageTicks = new java.util.ArrayList<>();

        TamingSession(UUID dragonId, UUID playerId, long startTick, long durationTicks, ServerBossBar bar) {
            this.dragonId = dragonId;
            this.playerId = playerId;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.bar = bar;
        }
    }

    public static void clearCache() { ACTIVE.clear(); }
}



