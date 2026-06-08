package com.dragoncare.worldgen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

public class BiomeMaterialHelper {

    /**
     * Determines the appropriate surface block for a given biome.
     * Used to ensure structures blend with their surroundings (sand in deserts, etc).
     */
    public static BlockState getTopBlockForBiome(RegistryEntry<Biome> biome) {
        if (biome.getKey().isPresent()) {
            String path = biome.getKey().get().getValue().getPath().toLowerCase();
            if (path.contains("desert") || path.contains("beach") || path.contains("sand")) {
                return Blocks.SAND.getDefaultState();
            }
            if (path.contains("snowy") || path.contains("ice")) {
                return Blocks.SNOW_BLOCK.getDefaultState();
            }
            if (path.contains("taiga") || path.contains("old_growth")) {
                return Blocks.PODZOL.getDefaultState();
            }
            if (path.contains("mushroom")) {
                return Blocks.MYCELIUM.getDefaultState();
            }
            if (path.contains("badlands") || path.contains("mesa")) {
                return Blocks.RED_SAND.getDefaultState();
            }
        }
        return Blocks.GRASS_BLOCK.getDefaultState();
    }
}


