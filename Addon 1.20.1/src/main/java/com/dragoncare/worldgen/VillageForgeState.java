package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-world persistent record of which village structures have already been
 * decorated with a ruined dragon forge.
 *
 * <p>The key is the chunk position of the village structure start (which is
 * stable for the lifetime of a structure, even when many of its chunks load
 * separately). Two villages with the same start chunk cannot exist, so the
 * record can never collide with itself.</p>
 */
public final class VillageForgeState extends PersistentState {

    private static final String NAME = DragonCare.MOD_ID + "_village_forges";

    /** Encoded as long-array of {@link ChunkPos#toLong()} entries. */
    private final Set<Long> processed = new HashSet<>();

    public VillageForgeState() {}

    private VillageForgeState(Set<Long> initial) {
        this.processed.addAll(initial);
    }

    public boolean markProcessed(ChunkPos startChunk) {
        if (processed.add(startChunk.toLong())) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean isProcessed(ChunkPos startChunk) {
        return processed.contains(startChunk.toLong());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        long[] arr = new long[processed.size()];
        int i = 0;
        for (Long v : processed) arr[i++] = v;
        nbt.putLongArray("Processed", arr);
        return nbt;
    }

    public static VillageForgeState fromNbt(NbtCompound nbt) {
        long[] arr = nbt.getLongArray("Processed");
        Set<Long> set = new HashSet<>(arr.length);
        for (long v : arr) set.add(v);
        return new VillageForgeState(set);
    }

    public static VillageForgeState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(VillageForgeState::fromNbt, VillageForgeState::new, NAME);
    }
}


