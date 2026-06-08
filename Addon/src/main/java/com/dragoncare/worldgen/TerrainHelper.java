package com.dragoncare.worldgen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;

/**
 * Shared block-classification helpers used by both {@link DragonHunterPlacer}
 * and {@link DragonHunterGuildPlacer} to avoid duplicating the same long
 * chains of {@code isIn(LOGS) || isIn(LEAVES) || ...} in six places.
 */
public final class TerrainHelper {

    private TerrainHelper() {}

    /**
     * Returns {@code true} if the given block state is a tree trunk, foliage,
     * or ground-cover vegetation that should be cleared before placing a
     * structure (PASS 1) or stepped-through when scanning for solid ground.
     *
     * <p>This intentionally does NOT include fluids or air — those are handled
     * separately in each caller's scan loop.</p>
     */
    public static boolean isTreeOrVegetation(BlockState st) {
        return st.isIn(BlockTags.LOGS)
                || st.isIn(BlockTags.LEAVES)
                || st.isIn(BlockTags.SAPLINGS)
                || st.isIn(BlockTags.FLOWERS)
                || st.isIn(BlockTags.TALL_FLOWERS)
                || st.isOf(Blocks.VINE)
                || st.isOf(Blocks.BAMBOO)
                || st.isOf(Blocks.COCOA)
                || st.isOf(Blocks.SNOW)
                || st.isOf(Blocks.TALL_GRASS)
                || st.isOf(Blocks.SHORT_GRASS)
                || st.isOf(Blocks.FERN)
                || st.isOf(Blocks.LARGE_FERN)
                || st.isOf(Blocks.DEAD_BUSH)
                || st.isOf(Blocks.SWEET_BERRY_BUSH)
                || st.isOf(Blocks.BROWN_MUSHROOM_BLOCK)
                || st.isOf(Blocks.RED_MUSHROOM_BLOCK)
                || st.isOf(Blocks.MUSHROOM_STEM)
                || st.isOf(Blocks.BROWN_MUSHROOM)
                || st.isOf(Blocks.RED_MUSHROOM);
    }

    /**
     * Like {@link #isTreeOrVegetation(BlockState)} but also considers
     * replaceable blocks and wool as "walk-through" material.
     * Used by {@code walkDownToGround} to find the first real solid surface.
     */
    public static boolean isWalkThroughBlock(BlockState st) {
        return st.isReplaceable()
                || st.isIn(BlockTags.WOOL)
                || isTreeOrVegetation(st);
    }
}
