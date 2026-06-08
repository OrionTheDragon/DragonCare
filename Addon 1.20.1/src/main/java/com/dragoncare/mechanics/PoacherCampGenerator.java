package com.dragoncare.mechanics;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
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

        EntityType<? extends EntityDragonBase> entityType = null;
        if ("fire".equalsIgnoreCase(dragonTypeStr)) {
            entityType = (EntityType<? extends EntityDragonBase>) Registries.ENTITY_TYPE.get(Identifier.of("iceandfire", "fire_dragon"));
        } else if ("ice".equalsIgnoreCase(dragonTypeStr)) {
            entityType = (EntityType<? extends EntityDragonBase>) Registries.ENTITY_TYPE.get(Identifier.of("iceandfire", "ice_dragon"));
        } else if ("lightning".equalsIgnoreCase(dragonTypeStr)) {
            entityType = (EntityType<? extends EntityDragonBase>) Registries.ENTITY_TYPE.get(Identifier.of("iceandfire", "lightning_dragon"));
        }

        if (entityType != null) {
            EntityDragonBase baby = entityType.create(world);
            if (baby != null) {
                baby.setGender(world.getRandom().nextBoolean());
                baby.growDragon(25 + world.getRandom().nextInt(24)); // Stage 2
                
                baby.setAgingDisabled(true);
                if (com.dragoncare.config.AddonConfig.WILD_DRAGONS_AGE.get()) {
                    baby.setAgingDisabled(false);
                }
                
                baby.setHealth(baby.getMaxHealth());
                java.util.List<com.iafenvoy.iceandfire.data.DragonColor> colors = com.iafenvoy.iceandfire.data.DragonColor.getColorsByType(baby.dragonType); baby.setVariant(colors.get(world.getRandom().nextInt(colors.size())).name());
                baby.updatePositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360, 0);
                baby.setHunger(10); // Very hungry, starving

                world.spawnEntity(baby);
            }
        }
    }
}



