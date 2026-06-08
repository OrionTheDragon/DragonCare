package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-world persistent record of dragon-hunter camp placements.
 *
 * <p>Two kinds of state are tracked:</p>
 * <ul>
 *   <li>{@code processedRegions} — region keys whose candidates have already
 *       been rolled. Prevents re-rolling on chunk re-load.</li>
 *   <li>{@code placements} — flat list of all camps already placed in this
 *       world, keyed by biome id + block coordinates. Used to enforce the
 *       "max 2 per biome instance" cap via a nearby-radius lookup.</li>
 * </ul>
 *
 * <p><b>Spatial index:</b> placements are also bucketed into 512×512-block grid
 * cells keyed by {@code (x>>9, z>>9)}. {@code countNearby} / {@code countAnyNearby}
 * / {@code isGuildNearby} now visit only the 1-4 cells overlapping the query
 * radius instead of scanning the entire list. This turns the per-candidate cost
 * from O(N) into effectively O(1) once enough structures exist — the linear scan
 * was a major hot path during long flights when dozens of candidate rolls landed
 * on the tick thread within a few seconds.</p>
 */
public final class DragonHunterState extends PersistentState {

    private static final String NAME = DragonCare.MOD_ID + "_dragon_hunters";



    /** Grid cell size (2^9 = 512 blocks). Picked so that the largest lookup radius
     *  used by callers (SAME_BIOME_RADIUS = 384) still fits comfortably in 1-2 cells
     *  per axis, while the cell count stays small enough that the grid map never grows
     *  past a few hundred entries even after thousands of placements. */
    private static final int CELL_SHIFT = 9;

    public record Placement(Identifier biomeId, int x, int z) {}

    private final Set<Long> processedRegions = new HashSet<>();
    private final Set<Long> guildProcessedRegions = new HashSet<>();
    private final List<Placement> placements = new ArrayList<>();
    private final List<BlockPos> guildPlacements = new ArrayList<>();
    /** Packed BlockPos of every chest belonging to a dragon-hunter camp. */
    private final Set<Long> hunterChests = new HashSet<>();
    /** How many guild regions were rolled without a guild spawn (pity counter). */
    private int missedRegions = 0;

    // ============ SPATIAL INDICES (rebuilt from placements on load, kept in sync on add) ============
    /** Bucketed view of {@link #placements}: cell key → list of placements within that cell. */
    private final Map<Long, List<Placement>> placementGrid = new HashMap<>();
    /** Bucketed view of {@link #guildPlacements}: cell key → list of guild positions within that cell. */
    private final Map<Long, List<BlockPos>> guildGrid = new HashMap<>();

    private DragonHunterState() {}

    private DragonHunterState(Set<Long> regions, Set<Long> guildRegions, List<Placement> initialPlacements,
                              List<BlockPos> guildInitialPlacements, Set<Long> chests,
                              int missedRegions) {
        this.processedRegions.addAll(regions);
        this.guildProcessedRegions.addAll(guildRegions);
        this.placements.addAll(initialPlacements);
        this.guildPlacements.addAll(guildInitialPlacements);
        this.hunterChests.addAll(chests);
        this.missedRegions = missedRegions;
        rebuildSpatialIndex();
    }

    /** Re-derives {@link #placementGrid} and {@link #guildGrid} from the flat lists.
     *  Called only on load — subsequent inserts update the grids incrementally. */
    private void rebuildSpatialIndex() {
        placementGrid.clear();
        guildGrid.clear();
        for (Placement p : placements) {
            placementGrid.computeIfAbsent(cellKey(p.x, p.z), k -> new ArrayList<>()).add(p);
        }
        for (BlockPos p : guildPlacements) {
            guildGrid.computeIfAbsent(cellKey(p.getX(), p.getZ()), k -> new ArrayList<>()).add(p);
        }
    }

    private static long cellKey(int x, int z) {
        long cx = x >> CELL_SHIFT;
        long cz = z >> CELL_SHIFT;
        return (cx & 0xFFFFFFFFL) | (cz << 32);
    }

    public boolean isRegionProcessed(long regionKey) {
        return processedRegions.contains(regionKey);
    }

    public void markRegionProcessed(long regionKey) {
        if (processedRegions.add(regionKey)) markDirty();
    }

    public boolean isGuildRegionProcessed(long regionKey) {
        return guildProcessedRegions.contains(regionKey);
    }

    public void markGuildRegionProcessed(long regionKey) {
        if (guildProcessedRegions.add(regionKey)) markDirty();
    }

    /**
     * Count nearby camps that match the given biome id, within {@code radius}
     * blocks of {@code (x, z)} (Chebyshev distance, square footprint).
     * <p>Backed by the spatial grid: only the 1-4 cells overlapping the query
     * box are scanned, not the entire placements list.</p>
     */
    public int countNearby(Identifier biomeId, int x, int z, int radius) {
        int count = 0;
        int minCx = (x - radius) >> CELL_SHIFT;
        int maxCx = (x + radius) >> CELL_SHIFT;
        int minCz = (z - radius) >> CELL_SHIFT;
        int maxCz = (z + radius) >> CELL_SHIFT;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long key = ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
                List<Placement> bucket = placementGrid.get(key);
                if (bucket == null) continue;
                for (Placement p : bucket) {
                    if (!p.biomeId.equals(biomeId)) continue;
                    int dx = Math.abs(p.x - x);
                    int dz = Math.abs(p.z - z);
                    if (dx <= radius && dz <= radius) count++;
                }
            }
        }
        return count;
    }

    /**
     * Count ANY nearby camp (biome-agnostic) within {@code radius} blocks of
     * {@code (x, z)} (Chebyshev distance). Used to enforce a hard global
     * minimum distance between camps regardless of biome boundaries.
     */
    public int countAnyNearby(int x, int z, int radius) {
        int count = 0;
        int minCx = (x - radius) >> CELL_SHIFT;
        int maxCx = (x + radius) >> CELL_SHIFT;
        int minCz = (z - radius) >> CELL_SHIFT;
        int maxCz = (z + radius) >> CELL_SHIFT;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long key = ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
                List<Placement> bucket = placementGrid.get(key);
                if (bucket == null) continue;
                for (Placement p : bucket) {
                    int dx = Math.abs(p.x - x);
                    int dz = Math.abs(p.z - z);
                    if (dx <= radius && dz <= radius) count++;
                }
            }
        }
        return count;
    }

    public void addPlacement(Identifier biomeId, int x, int z) {
        Placement p = new Placement(biomeId, x, z);
        placements.add(p);
        placementGrid.computeIfAbsent(cellKey(x, z), k -> new ArrayList<>()).add(p);
        markDirty();
    }

    public boolean isGuildNearby(int x, int z, int radius) {
        int minCx = (x - radius) >> CELL_SHIFT;
        int maxCx = (x + radius) >> CELL_SHIFT;
        int minCz = (z - radius) >> CELL_SHIFT;
        int maxCz = (z + radius) >> CELL_SHIFT;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long key = ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
                List<BlockPos> bucket = guildGrid.get(key);
                if (bucket == null) continue;
                for (BlockPos p : bucket) {
                    if (Math.abs(p.getX() - x) <= radius && Math.abs(p.getZ() - z) <= radius) return true;
                }
            }
        }
        return false;
    }

    public void addGuildPlacement(int x, int z) {
        BlockPos pos = new BlockPos(x, 0, z);
        guildPlacements.add(pos);
        guildGrid.computeIfAbsent(cellKey(x, z), k -> new ArrayList<>()).add(pos);
        missedRegions = 0;
        markDirty();
    }

    public List<BlockPos> getGuildPlacements() {
        return Collections.unmodifiableList(guildPlacements);
    }

    /** First placed guild position, or null if no guild placed yet. Used as compass target. */
    public BlockPos getFirstGuildPos() {
        return guildPlacements.isEmpty() ? null : guildPlacements.get(0);
    }

    public int getMissedRegions() {
        return missedRegions;
    }

    public void incrementMissedRegions() {
        missedRegions++;
        markDirty();
    }

    /** Mark a chest position as belonging to a dragon-hunter camp. */
    public void markHunterChest(BlockPos pos) {
        if (hunterChests.add(pos.asLong())) markDirty();
    }

    public boolean isHunterChest(BlockPos pos) {
        return hunterChests.contains(pos.asLong());
    }

    /** Read-only view of all known hunter chest positions (packed long). */
    public Set<Long> hunterChestPositions() {
        return Collections.unmodifiableSet(hunterChests);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLongArray("ProcessedRegions", processedRegions.stream().mapToLong(l -> l).toArray());
        nbt.putLongArray("GuildProcessedRegions", guildProcessedRegions.stream().mapToLong(l -> l).toArray());

        NbtList list = new NbtList();
        for (Placement p : placements) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Biome", p.biomeId.toString());
            entry.putInt("X", p.x);
            entry.putInt("Z", p.z);
            list.add(entry);
        }
        nbt.put("Placements", list);

        NbtList guildList = new NbtList();
        for (BlockPos p : guildPlacements) {
            NbtCompound entry = new NbtCompound();
            entry.putInt("X", p.getX());
            entry.putInt("Z", p.getZ());
            guildList.add(entry);
        }
        nbt.put("GuildPlacements", guildList);

        nbt.putLongArray("HunterChests", hunterChests.stream().mapToLong(l -> l).toArray());
        nbt.putInt("MissedRegions", missedRegions);
        return nbt;
    }

    public static DragonHunterState fromNbt(NbtCompound nbt) {
        Set<Long> regions = new HashSet<>();
        for (long l : nbt.getLongArray("ProcessedRegions")) regions.add(l);
        Set<Long> guildRegions = new HashSet<>();
        for (long l : nbt.getLongArray("GuildProcessedRegions")) guildRegions.add(l);

        List<Placement> placements = new ArrayList<>();
        NbtList list = nbt.getList("Placements", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            Identifier biome = Identifier.tryParse(entry.getString("Biome"));
            if (biome != null) placements.add(new Placement(biome, entry.getInt("X"), entry.getInt("Z")));
        }

        List<BlockPos> guildPlacements = new ArrayList<>();
        NbtList guildList = nbt.getList("GuildPlacements", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < guildList.size(); i++) {
            NbtCompound entry = guildList.getCompound(i);
            guildPlacements.add(new BlockPos(entry.getInt("X"), 0, entry.getInt("Z")));
        }

        Set<Long> chests = new HashSet<>();
        for (long l : nbt.getLongArray("HunterChests")) chests.add(l);

        int missedRegions = nbt.contains("MissedRegions") ? nbt.getInt("MissedRegions") : 0;

        return new DragonHunterState(regions, guildRegions, placements, guildPlacements, chests,
                missedRegions);
    }

    public static DragonHunterState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(DragonHunterState::fromNbt, DragonHunterState::new, NAME);
    }
}


