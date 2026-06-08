package com.dragoncare.worldgen;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Hook used by {@link ParsedSchematic#placeInWorld} to mutate block-entity NBT
 * just before it is applied to the world.
 *
 * <p>Typical use: recognise placeholder signs (e.g. {@code [d_hunter]}) and
 * substitute them with generated content, optionally spawning extra entities
 * (e.g. a decorative dragon skull) via {@code world}.</p>
 */
@FunctionalInterface
public interface MarkerProcessor {
    /**
     * Inspect (and possibly mutate) block-entity NBT.
     *
     * @param world        target server world (for side-effects like spawning entities)
     * @param data         a mutable copy of the BE NBT; modify in place
     * @param worldPos     world coordinate of the BE
     * @param random       RNG to use for any random generation
     * @return the NBT to apply (typically the same {@code data} instance)
     */
    NbtCompound process(ServerWorld world, NbtCompound data, BlockPos worldPos, Random random);

    /**
     * Inspect (and possibly mutate) a freshly-spawned entity. Called by
     * {@link ParsedSchematic#placeInWorld} right after each schematic entity
     * is spawned into the world.
     *
     * <p>Default implementation is a no-op — processors that do not care about
     * entities simply ignore it. Keeping a single abstract method ({@link
     * #process}) means this interface stays a valid {@code @FunctionalInterface}.</p>
     *
     * @param world  target server world
     * @param entity the entity that was just spawned
     * @param random RNG to use for any random generation
     */
    default void processEntity(ServerWorld world, Entity entity, Random random) {
        // no-op by default
    }
}
