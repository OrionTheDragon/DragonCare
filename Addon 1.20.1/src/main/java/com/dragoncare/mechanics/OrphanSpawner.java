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

@SuppressWarnings({"deprecation", "unchecked"})
public class OrphanSpawner {

    public static void tick(ServerWorld world) {
        if (com.dragoncare.config.AddonConfig.DISABLE_WILD_BABY_SPAWNS.get()) return;
        if (!world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)) return;

        // Process every 200 ticks (10 seconds)
        if (world.getTime() % 200 != 0) return;

        Random random = world.getRandom();

        var players = world.getPlayers();
        if (players.isEmpty()) return;

        // One world-wide roll prevents multiplayer servers from multiplying the rate.
        // 0.165% is approximately pink-sheep rarity with a ~1% relative increase.
        if (random.nextDouble() >= 0.00165D) return;
        ServerPlayerEntity player = players.get(random.nextInt(players.size()));

        // Pick a random surface position around the player (radius 32-64 blocks)
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = 32 + random.nextInt(33);
        int rx = player.getBlockX() + (int)(Math.cos(angle) * dist);
        int rz = player.getBlockZ() + (int)(Math.sin(angle) * dist);

        // Only newly explored chunks qualify. Inhabited time is persistent, so revisiting
        // or reloading a chunk does not make it eligible again.
        var chunk = world.getChunkManager().getChunk(
                rx >> 4, rz >> 4, net.minecraft.world.chunk.ChunkStatus.FULL, false);
        if (chunk == null || chunk.getInhabitedTime() > 1200L) return;

        BlockPos targetPos = world.getTopPosition(Heightmap.Type.WORLD_SURFACE, new BlockPos(rx, 0, rz));

        // Ensure it's not a fluid or dangerous block, and not deep underground
        if (targetPos.getY() < world.getSeaLevel()) return;
        if (!world.getBlockState(targetPos.down()).isSolidBlock(world, targetPos.down())) return;

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



