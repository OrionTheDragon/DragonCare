package com.dragoncare.mechanics;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.registry.IafEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class PoacherCampGenerator {

    /**
     * Placeholder method for Phase 4 (Poacher Camps).
     * Call this from your custom structure logic/command to spawn a trapped baby dragon.
     */
    public static void spawnPoacherCampBaby(ServerWorld world, BlockPos pos, String dragonTypeStr) {
        if (com.dragoncare.config.AddonConfig.DISABLE_WILD_BABY_SPAWNS.get()) return;

        EntityType<? extends DragonBaseEntity> entityType = null;
        if ("fire".equalsIgnoreCase(dragonTypeStr)) {
            entityType = IafEntities.FIRE_DRAGON.get();
        } else if ("ice".equalsIgnoreCase(dragonTypeStr)) {
            entityType = IafEntities.ICE_DRAGON.get();
        } else if ("lightning".equalsIgnoreCase(dragonTypeStr)) {
            entityType = IafEntities.LIGHTNING_DRAGON.get();
        }

        if (entityType != null) {
            DragonBaseEntity baby = entityType.create(world);
            if (baby != null) {
                baby.setGender(world.getRandom().nextBoolean());
                baby.growDragon(25 + world.getRandom().nextInt(24)); // Stage 2
                
                baby.setAgingDisabled(true);
                if (com.dragoncare.config.AddonConfig.WILD_DRAGONS_AGE.get()) {
                    baby.setAgingDisabled(false);
                }
                
                baby.setHealth(baby.getMaxHealth());
                baby.setVariant(com.dragoncare.compat.IafDragonVariants.randomVariant(
                        baby, world.getRandom().nextInt(4)));
                baby.updatePositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360, 0);
                baby.setHunger(10); // Very hungry, starving

                world.spawnEntity(baby);
            }
        }
    }
}
