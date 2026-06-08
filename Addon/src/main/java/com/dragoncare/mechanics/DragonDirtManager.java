package com.dragoncare.mechanics;

import com.dragoncare.config.AddonConfig;
import com.dragoncare.network.DragonDirtSyncPayload;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class DragonDirtManager {

    private static final Identifier DIRT_HEALTH_MODIFIER_ID = Identifier.of("dragoncare", "dirt_health_penalty");
    private static final Identifier DIRT_SPEED_MODIFIER_ID = Identifier.of("dragoncare", "dirt_speed_penalty");
    private static final Identifier DIRT_FLY_SPEED_MODIFIER_ID = Identifier.of("dragoncare", "dirt_fly_speed_penalty");
    private static final Identifier CLEAN_HEALTH_BONUS_ID = Identifier.of("dragoncare", "clean_health_bonus");

    /**
     * Ticks the dirtiness logic for a single dragon.
     * Should be called from the dragon's mobTick (server-side only).
     */
    public static void tickDragon(DragonBaseEntity dragon) {
        if (dragon.getWorld().isClient) return;
        MinecraftServer server = dragon.getServer();
        if (server == null) return;

        boolean isTamed = dragon.isTamed();
        if (isTamed && !AddonConfig.DIRT_ENABLED_TAMED.get()) return;
        if (!isTamed && !AddonConfig.DIRT_ENABLED_WILD.get()) return;

        ServerWorld overworld = server.getOverworld();
        long currentTick = overworld.getTime();

        DragonDirtState state = DragonDirtState.get(server);
        DragonDirtState.DirtData data = state.getOrCreate(dragon.getUuid());

        if (data.lastUpdateTick == 0) {
            data.lastUpdateTick = currentTick;
            state.markDirty();
        }

        double speedDays = AddonConfig.DIRT_SPEED_DAYS.get();
        if (speedDays <= 0) speedDays = 1.0;
        long ticksNeeded = (long) (24000L * speedDays);

        if (currentTick - data.lastUpdateTick >= ticksNeeded) {
            if (data.dirtLevel < 5) {
                data.dirtLevel++;
                data.lastUpdateTick = currentTick;
                state.markDirty();
                syncToTrackers(dragon, data.dirtLevel);
                applyDirtEffects(dragon, data.dirtLevel);
            } else {
                data.lastUpdateTick = currentTick;
                state.markDirty();
            }
        } else if (currentTick < data.lastUpdateTick) {
            data.lastUpdateTick = currentTick;
            state.markDirty();
        }
    }

    public static int getDirtLevel(MinecraftServer server, UUID dragonId) {
        DragonDirtState.DirtData data = DragonDirtState.get(server).peek(dragonId);
        return data == null ? 0 : data.dirtLevel;
    }

    public static void setDirtLevel(DragonBaseEntity dragon, int level) {
        if (dragon.getWorld().isClient) return;
        MinecraftServer server = dragon.getServer();
        if (server == null) return;

        DragonDirtState state = DragonDirtState.get(server);
        DragonDirtState.DirtData data = state.getOrCreate(dragon.getUuid());
        if (data.dirtLevel != level) {
            int oldLevel = data.dirtLevel;
            data.dirtLevel = level;
            data.lastUpdateTick = server.getOverworld().getTime();
            state.markDirty();
            syncToTrackers(dragon, level);
            applyDirtEffects(dragon, level);
            
            if (level == 0 && oldLevel > 0) {
                // Immediately heal the 5% bonus if it wasn't active
                dragon.heal(dragon.getMaxHealth() * 0.05f);
            }
        }
    }

    public static void cleanDragon(DragonBaseEntity dragon) {
        setDirtLevel(dragon, 0);
    }

    public static void syncTo(ServerPlayerEntity player, DragonBaseEntity dragon) {
        MinecraftServer server = dragon.getServer();
        if (server == null) return;
        int level = getDirtLevel(server, dragon.getUuid());
        PacketDistributor.sendToPlayer(player, new DragonDirtSyncPayload(dragon.getUuid(), level));
    }

    public static void syncToTrackers(DragonBaseEntity dragon, int level) {
        PacketDistributor.sendToPlayersTrackingEntity(dragon, new DragonDirtSyncPayload(dragon.getUuid(), level));
    }

    public static void applyDirtEffects(DragonBaseEntity dragon, int level) {
        EntityAttributeInstance maxHealth = dragon.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        EntityAttributeInstance moveSpeed = dragon.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        EntityAttributeInstance flySpeed = dragon.getAttributeInstance(EntityAttributes.GENERIC_FLYING_SPEED);

        if (maxHealth != null) {
            maxHealth.removeModifier(DIRT_HEALTH_MODIFIER_ID);
            maxHealth.removeModifier(CLEAN_HEALTH_BONUS_ID);
        }
        if (moveSpeed != null) moveSpeed.removeModifier(DIRT_SPEED_MODIFIER_ID);
        if (flySpeed != null) flySpeed.removeModifier(DIRT_FLY_SPEED_MODIFIER_ID);

        // Not tamed dragons do not get buffs/debuffs from dirt, only tamed ones do.
        // Wait, user said: "Грезняться только прирученные драконы... У прирученных драконов оригинальная текстура = чистые."
        // Let's check if tamed.
        if (!dragon.isTamed()) return;

        if (level == 0) {
            // Clean: +5% HP, invisible speed buff (we apply modifier instead of potion effect for speed if it's passive)
            // User requested: "получают невидимый эффект скорости 1 уровня, и +5% к максимальному ХП"
            if (maxHealth != null) {
                maxHealth.addPersistentModifier(new EntityAttributeModifier(
                        CLEAN_HEALTH_BONUS_ID, 0.05, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            if (moveSpeed != null) {
                moveSpeed.addPersistentModifier(new EntityAttributeModifier(
                        DIRT_SPEED_MODIFIER_ID, 0.20, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                )); // Speed 1 is +20%
            }
        } else if (level >= 1) {
            // d1: Loses the 5% HP bonus (returns to baseline HP), but speed buff remains.
            if (level == 1) {
                if (moveSpeed != null) {
                    moveSpeed.addPersistentModifier(new EntityAttributeModifier(
                            DIRT_SPEED_MODIFIER_ID, 0.20, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
            }
            
            // d2: loses any improvements (which is the baseline, no modifiers needed)

            // d3: -10% ground speed
            if (level == 3 && moveSpeed != null) {
                moveSpeed.addPersistentModifier(new EntityAttributeModifier(
                        DIRT_SPEED_MODIFIER_ID, -0.10, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }

            // d4: -20% ground speed, -10% fly speed
            if (level == 4) {
                if (moveSpeed != null) {
                    moveSpeed.addPersistentModifier(new EntityAttributeModifier(
                            DIRT_SPEED_MODIFIER_ID, -0.20, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
                if (flySpeed != null) {
                    flySpeed.addPersistentModifier(new EntityAttributeModifier(
                            DIRT_FLY_SPEED_MODIFIER_ID, -0.10, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
            }

            // d5: -25% ground speed, -15% fly speed, -5% overall HP
            if (level == 5) {
                if (moveSpeed != null) {
                    moveSpeed.addPersistentModifier(new EntityAttributeModifier(
                            DIRT_SPEED_MODIFIER_ID, -0.25, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
                if (flySpeed != null) {
                    flySpeed.addPersistentModifier(new EntityAttributeModifier(
                            DIRT_FLY_SPEED_MODIFIER_ID, -0.15, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
                if (maxHealth != null) {
                    maxHealth.addPersistentModifier(new EntityAttributeModifier(
                            DIRT_HEALTH_MODIFIER_ID, -0.05, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
            }
        }
    }
}
