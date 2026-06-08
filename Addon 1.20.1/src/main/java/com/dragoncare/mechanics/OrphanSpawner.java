package com.dragoncare.mechanics;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.iceandfire.registry.IafEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

public class OrphanSpawner {

    public static void tick(ServerWorld world) {
        if (com.dragoncare.config.AddonConfig.DISABLE_WILD_BABY_SPAWNS.get()) return;

        // Process every 200 ticks (10 seconds)
        if (world.getTime() % 200 != 0) return;

        Random random = world.getRandom();

        for (ServerPlayerEntity player : world.getPlayers()) {
            // Very rare chance: 0.5% per 10 seconds. Roughly 1 spawn every 33 minutes of walking.
            if (random.nextDouble() > 0.005) continue;

            // Pick a random surface position around the player (radius 32-64 blocks)
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 32 + random.nextInt(33);
            int rx = player.getBlockX() + (int)(Math.cos(angle) * dist);
            int rz = player.getBlockZ() + (int)(Math.sin(angle) * dist);

            BlockPos targetPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(rx, 0, rz));

            // Ensure it's not a fluid or dangerous block, and not deep underground
            if (targetPos.getY() < world.getSeaLevel()) continue;
            if (!world.getBlockState(targetPos.down()).isSolidBlock(world, targetPos.down())) continue;

            RegistryEntry<Biome> biome = world.getBiome(targetPos);
            EntityType<? extends EntityDragonBase> entityType = null;

            if (biome.isIn(BiomeTags.IS_JUNGLE) || biome.isIn(BiomeTags.IS_SAVANNA)) {
                entityType = (EntityType<? extends EntityDragonBase>) Registries.ENTITY_TYPE.get(Identifier.of("iceandfire", "lightning_dragon"));
            } else if (biome.isIn(BiomeTags.IS_TAIGA) && biome.value().getTemperature() < 0.15F) {
                entityType = (EntityType<? extends EntityDragonBase>) Registries.ENTITY_TYPE.get(Identifier.of("iceandfire", "ice_dragon"));
            } else if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_BADLANDS)) {
                entityType = (EntityType<? extends EntityDragonBase>) Registries.ENTITY_TYPE.get(Identifier.of("iceandfire", "fire_dragon"));
            }

            if (entityType != null) {
                EntityDragonBase baby = entityType.create(world);
                if (baby != null) {
                    baby.setGender(random.nextBoolean());
                    
                    // 50/50 for Stage 1 or 2
                    if (random.nextBoolean()) {
                        baby.growDragon(1 + random.nextInt(24));
                    } else {
                        baby.growDragon(25 + random.nextInt(24));
                    }
                    
                    baby.setAgingDisabled(true);
                    if (com.dragoncare.config.AddonConfig.WILD_DRAGONS_AGE.get()) {
                        baby.setAgingDisabled(false);
                    }
                    
                    baby.setHealth(baby.getMaxHealth());
                    java.util.List<com.iafenvoy.iceandfire.data.DragonColor> colors = com.iafenvoy.iceandfire.data.DragonColor.getColorsByType(baby.dragonType); baby.setVariant(colors.get(random.nextInt(colors.size())).name());
                    baby.updatePositionAndAngles(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5, random.nextFloat() * 360, 0);
                    baby.setHunger(30); // A bit hungry, makes them look lost

                    world.spawnEntity(baby);
                }
            }
        }
    }
}



