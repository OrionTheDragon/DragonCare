package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spawns dragon-hunter camps in matching biomes.
 *
 * <h2>Distribution</h2>
 * <p>The world is divided into 8×8 chunk regions (128×128 blocks). In each
 * region we roll an 80% chance for one candidate. A 128-block region is small
 * enough that two adjacent biome instances rarely share the same region, so
 * each contiguous biome typically gets multiple independent rolls.</p>
 *
 * <h2>Per-biome cap</h2>
 * <p>Before placing, we look up how many camps with the same biome id have
 * been placed within {@link #SAME_BIOME_RADIUS} blocks. If 2 or more already
 * exist, the candidate is skipped. This caps each contiguous biome instance
 * at 2 camps — even if its area would otherwise warrant more.</p>
 *
 * <h2>Deferred placement</h2>
 * <p>{@link ChunkEvent.Load} fires on the chunk-loading pipeline and MUST NOT
 * call {@code world.getChunk(...)} synchronously — doing so re-entrantly
 * requests chunks from the same subsystem that is currently loading the
 * triggering chunk, which deadlocks the server (ticks freeze, world save
 * hangs). We therefore do only lightweight bookkeeping in the chunk-load
 * handler and queue the actual placement attempt; {@link #onServerTick}
 * drains the queue at a small budget per tick and uses a non-blocking
 * chunk check before invoking the schematic.</p>
 *
 * <h2>Wood-to-biome mapping</h2>
 * <ul>
 *   <li>{@code birch}    → biomes whose id contains "birch"</li>
 *   <li>{@code spruce}   → "taiga" / "spruce" / "coniferous"</li>
 *   <li>{@code dark_oak} → "dark_forest" / "dark_oak" / "roofed"</li>
 *   <li>{@code jungle}   → "jungle" / "bamboo"</li>
 *   <li>{@code acacia}   → "savanna" / "acacia"</li>
 *   <li>{@code oak}      → fallback for forests / plains</li>
 * </ul>
 *
 * <p>Path-substring matching means modded biomes following standard naming
 * conventions are picked up automatically.</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class DragonHunterPlacer {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/DragonHunter");

    /** Region side, in chunks. 8 chunks = 128 blocks — narrower than most biomes. */
    private static final int REGION_SIZE = 8;
    private static final double SPAWN_CHANCE = 0.80;
    /** Two camps with the same biome id within this many blocks count as the same biome instance. */
    private static final int SAME_BIOME_RADIUS = 384;
    /** Maximum camps allowed inside one same-biome cluster. */
    private static final int MAX_PER_BIOME_CLUSTER = 2;
    /**
     * Hard global minimum distance between any two camps regardless of biome.
     * Prevents two camps from spawning next to each other when their distinct
     * biome ids (e.g. birch_forest vs old_growth_birch_forest) bypass the
     * same-biome cap. ~256 blocks ≈ 16 chunks.
     */
    private static final int MIN_GLOBAL_DISTANCE = 256;
    /**
     * Maximum allowed Y-spread across the footprint (the difference between the
     * highest and lowest sampled ground level). Camps must sit on relatively
     * flat terrain — above this spread the candidate is rejected so a hut never
     * straddles a slope, perches on a lone bump, or needs a deep foundation
     * pillar that "roots" far down into a dip.
     *
     * <p>Checked against a DENSE grid (see {@link #sampleFootprintHeights}), so
     * a hidden dip between probe points can no longer slip through.</p>
     */
    private static final int MAX_TERRAIN_VARIANCE = 3;
    /** Spacing of the dense terrain-flatness grid, in blocks. */
    private static final int TERRAIN_PROBE_STEP = 2;
    /** Cap on retries when surrounding chunks are not yet loaded (~200 ticks ≈ 10s). */
    private static final int MAX_RETRIES = 200;
    /** Placements processed per server tick — schematic placement is heavy. */
    private static final int PLACEMENTS_PER_TICK = 1;

    /** Deferred placement candidates, drained on {@link ServerTickEvent.Post}. */
    private static final ConcurrentLinkedQueue<PendingCandidate> PENDING = new ConcurrentLinkedQueue<>();

    private record PendingCandidate(ServerWorld world, int blockX, int blockZ, long rngSeed, int retries) {}

    private DragonHunterPlacer() {}

    /** Clears all transient static state. Called on server stop to prevent
     *  stale references to a dead ServerWorld leaking into the next session. */
    public static void clearStatic() {
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerWorld world)) return;
        Chunk chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        int regionX = Math.floorDiv(chunkPos.x, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkPos.z, REGION_SIZE);
        long regionKey = packRegion(regionX, regionZ);

        DragonHunterState state = DragonHunterState.get(world);
        if (state.isRegionProcessed(regionKey)) return;

        // Only the relative (0,0) chunk of each region drives processing.
        int relX = Math.floorMod(chunkPos.x, REGION_SIZE);
        int relZ = Math.floorMod(chunkPos.z, REGION_SIZE);
        if (relX != 0 || relZ != 0) return;

        Random regionRng = new Random(world.getSeed()
                ^ ((long) regionX * 7392845L + (long) regionZ * 938173L));

        // Mark immediately so re-loading the chunk does not re-queue. If the
        // deferred attempt fails entirely (no matching biome, retries exhausted)
        // we simply lose this region — same effective behaviour as before.
        state.markRegionProcessed(regionKey);

        if (regionRng.nextDouble() < SPAWN_CHANCE) {
            int candidateChunkX = regionX * REGION_SIZE + regionRng.nextInt(REGION_SIZE);
            int candidateChunkZ = regionZ * REGION_SIZE + regionRng.nextInt(REGION_SIZE);
            int blockX = (candidateChunkX << 4) + 4 + regionRng.nextInt(8);
            int blockZ = (candidateChunkZ << 4) + 4 + regionRng.nextInt(8);
            // Defer to tick thread; pass an RNG seed for deterministic rotation/markers.
            PENDING.add(new PendingCandidate(world, blockX, blockZ, regionRng.nextLong(), 0));
        }
    }

    /**
     * Drain pending candidates on the server tick thread, where heavy work
     * (biome lookups, schematic placement, BE writes) is safe to perform
     * without re-entering the chunk-load pipeline.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < PLACEMENTS_PER_TICK; i++) {
            PendingCandidate c = PENDING.poll();
            if (c == null) return;
            if (!attemptPlacement(c)) {
                if (c.retries < MAX_RETRIES) {
                    PENDING.add(new PendingCandidate(c.world, c.blockX, c.blockZ, c.rngSeed, c.retries + 1));
                } else {
                    LOG.debug("Giving up dragon hunter candidate at {},{} — chunks never became ready",
                            c.blockX, c.blockZ);
                }
            }
        }
    }

    /**
     * @return true if the candidate is fully handled (placed, skipped or definitively rejected);
     *         false if surrounding chunks are not yet loaded and the candidate should be re-queued.
     */
    private static boolean attemptPlacement(PendingCandidate c) {
        ServerWorld world = c.world;
        ServerChunkManager scm = world.getChunkManager();

        // Anchor chunk must be fully loaded for biome / heightmap to be meaningful.
        if (scm.getChunk(c.blockX >> 4, c.blockZ >> 4, ChunkStatus.FULL, false) == null) {
            return false;
        }

        BiomeMatch match = matchWoodForBiome(world, c.blockX, c.blockZ);
        if (match == null) return true;

        DragonHunterState state = DragonHunterState.get(world);
        if (state.countNearby(match.biomeId, c.blockX, c.blockZ, SAME_BIOME_RADIUS) >= MAX_PER_BIOME_CLUSTER) {
            return true;
        }
        // Hard global minimum distance — no other camp may be within MIN_GLOBAL_DISTANCE
        // blocks regardless of biome. This prevents two camps clustering when neighbouring
        // sub-biomes (e.g. birch_forest + old_growth_birch_forest) each pass their own cap.
        if (state.countAnyNearby(c.blockX, c.blockZ, MIN_GLOBAL_DISTANCE) > 0) {
            return true;
        }

        ParsedSchematic schem = AddonSchematics.dragonHunterFor(match.wood);
        if (schem == null) return true;

        // WORLD_SURFACE_WG points at the top of EVERYTHING — including tree canopies
        // and floating leaf blocks. Without correction the camp ends up sitting on
        // birch leaves instead of on the actual forest floor. Walk down through any
        // logs / leaves / saplings / air until we hit real ground.
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, c.blockX, c.blockZ) - 1;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int y = walkDownToGround(world, c.blockX, surfaceY, c.blockZ, cursor);
        // Accept anything above the absolute world bottom (so superflat worlds with surface ~4 above bedrock still work).
        if (y <= world.getBottomY() || y >= world.getTopY() - schem.height - 5) return true;
        // Reject anchors that sit on water / lava — never place a camp on a fluid surface.
        cursor.set(c.blockX, y, c.blockZ);
        if (!world.getFluidState(cursor).isEmpty()) return true;

        // Pre-rotation footprint: we sample with the un-rotated dims because we
        // pick rotation later. Using the max of width/length keeps the sample
        // covering whatever orientation we end up choosing.
        int footprint = Math.max(schem.width, schem.length);

        // Non-blocking check that every chunk the schematic touches is loaded.
        // If any is missing, re-queue rather than synchronously loading it.
        // Iterate by CHUNK coordinates — stepping block coords by 16 from an
        // unaligned origin makes floor(footprint/16)+1 probes, which can miss
        // the chunk the far edge falls into and let placement hit an unloaded
        // chunk.
        int minCx = c.blockX >> 4;
        int maxCx = (c.blockX + footprint) >> 4;
        int minCz = c.blockZ >> 4;
        int maxCz = (c.blockZ + footprint) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (scm.getChunk(cx, cz, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }

        // Surface sampling — probe a DENSE grid across the whole footprint.
        // Reject if the ground levels vary by more than MAX_TERRAIN_VARIANCE
        // (hut must sit on relatively flat terrain) or if any probe lands on
        // water / lava (encoded as Integer.MIN_VALUE in the sample array).
        int[] heights = sampleFootprintHeights(world, c.blockX, c.blockZ, footprint);
        if (heights == null) {
            // A probe fell in a chunk that is not fully loaded yet — re-queue.
            return false;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        boolean hasFluid = false;
        for (int v : heights) {
            if (v == Integer.MIN_VALUE) { hasFluid = true; break; }
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (hasFluid) {
            LOG.debug("Rejecting dragon hunter at {},{} — water/lava under footprint",
                    c.blockX, c.blockZ);
            return true;
        }
        if (max - min > MAX_TERRAIN_VARIANCE) {
            LOG.debug("Rejecting dragon hunter at {},{} — terrain variance {} > {} (not flat enough)",
                    c.blockX, c.blockZ, max - min, MAX_TERRAIN_VARIANCE);
            return true;
        }

        Random rng = new Random(c.rngSeed);
        BlockPos origin = new BlockPos(c.blockX, y, c.blockZ);
        BlockRotation rotation = BlockRotation.random(
                net.minecraft.util.math.random.Random.create(rng.nextLong()));
        int rotW = schem.rotatedWidth(rotation);
        int rotL = schem.rotatedLength(rotation);
        // Sample the dominant natural surface block BEFORE terrain prep — once
        // prepareAndSmoothTerrain runs, the ground inside the footprint is our
        // own foundation, not the original biome surface. Probes the actual
        // blocks at 16 grid points instead of guessing from the biome name
        // (which gave wrong results, e.g. PODZOL under grass in taiga).
        BlockState dominantTop = sampleDominantSurfaceBlock(world, c.blockX, c.blockZ, Math.max(rotW, rotL));
        // Deterministic decoration seed derived from the candidate seed so
        // sparse grass/flowers pattern is stable on the same world seed.
        long decoSeed = c.rngSeed ^ 0xC2B2AE3D27D4EB4FL;
        // Prepare and smooth terrain BEFORE placing the schematic
        prepareAndSmoothTerrain(world, origin, rotW, rotL, rng, decoSeed);
        schem.placeInWorld(world, origin, rotation, rng, new DragonHunterMarkers());

        // Final surface pass AFTER schematic placement to replace its internal grass floors with the sampled material
        finalizeFootprintSurface(world, origin, rotW, rotL, dominantTop, decoSeed);
        
        state.addPlacement(match.biomeId, c.blockX, c.blockZ);
        LOG.info("Placed dragon hunter ({}) at {} in biome {}", match.wood, origin, match.biomeId);
        return true;
    }

    /**
     * Final pass over the footprint to find any grass/dirt blocks (often baked into the schematic)
     * and replace them with the chosen surface block. Run AFTER schematic placement.
     * The {@code topBlock} is computed before terrain prep via {@link #sampleDominantSurfaceBlock}
     * so it reflects the actual natural ground, not the biome name.
     */
    private static void finalizeFootprintSurface(ServerWorld world, BlockPos origin, int rotW, int rotL,
                                                  BlockState topBlock, long rngSeed) {
        int footMinX = origin.getX();
        int footMinZ = origin.getZ();
        int footMaxX = origin.getX() + rotW - 1;
        int footMaxZ = origin.getZ() + rotL - 1;
        int baseY = origin.getY();

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos.Mutable decoCursor = new BlockPos.Mutable();

        for (int x = footMinX; x <= footMaxX; x++) {
            for (int z = footMinZ; z <= footMaxZ; z++) {
                int highestReplacedY = Integer.MIN_VALUE;
                // Scan a small range around the base level (foundation and floor)
                for (int y = baseY - 1; y <= baseY + 2; y++) {
                    cursor.set(x, y, z);
                    BlockState current = world.getBlockState(cursor);

                    if (current.isOf(Blocks.GRASS_BLOCK) || current.isOf(Blocks.DIRT)
                            || current.isOf(Blocks.DIRT_PATH) || current.isOf(Blocks.MYCELIUM)
                            || current.isOf(Blocks.PODZOL) || current.isOf(Blocks.COARSE_DIRT)) {
                        world.setBlockState(cursor, topBlock, 50);
                        if (topBlock.isOf(Blocks.GRASS_BLOCK)) highestReplacedY = y;
                    }
                }
                // Foundation grow-over: sprinkle vanilla-like sparse grass/flowers
                // on any exposed grass cell within the footprint.
                if (highestReplacedY != Integer.MIN_VALUE) {
                    decorateGrassTop(world, rngSeed, x, highestReplacedY, z, topBlock, decoCursor);
                }
            }
        }
    }

    /**
     * Vanilla-like sparse groundcover decoration (~12% short grass, ~3%
     * flowers, ~85% bare). Deterministic per (seed, x, z). Same code path
     * as the guild placer's helper — keeps both structures visually
     * consistent.
     */
    private static void decorateGrassTop(ServerWorld world, long rngSeed, int x, int y, int z,
                                          BlockState topBlock, BlockPos.Mutable cursor) {
        if (!topBlock.isOf(Blocks.GRASS_BLOCK)) return;
        cursor.set(x, y + 1, z);
        if (!world.getBlockState(cursor).isAir()) return;

        long h = rngSeed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xBF58476D1CE4E5B9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        int roll = (int) (h & 0xFF);

        BlockState decoration;
        if      (roll <  30) decoration = Blocks.GRASS.getDefaultState();  // 11.7%
        else if (roll <  34) decoration = Blocks.DANDELION.getDefaultState();    //  1.6%
        else if (roll <  37) decoration = Blocks.POPPY.getDefaultState();        //  1.2%
        else if (roll <  38) decoration = Blocks.OXEYE_DAISY.getDefaultState();  //  0.4%
        else if (roll <  39) decoration = Blocks.CORNFLOWER.getDefaultState();   //  0.4%
        else if (roll <  40) decoration = Blocks.AZURE_BLUET.getDefaultState();  //  0.4%
        else                  return;                                             // ~84% bare grass

        world.setBlockState(cursor, decoration, 50);
    }

    /**
     * Samples 16 actual surface blocks in a 4x4 grid across the footprint and
     * returns the dominant top block. Replaces the old biome-name match which
     * incorrectly painted PODZOL anywhere biome name contained "taiga", even
     * when the natural ground at the spawn site was plain grass.
     *
     * <p>Must be called BEFORE {@code prepareAndSmoothTerrain} — that pass
     * modifies the ground inside the footprint, so sampling after it would
     * read back our own foundation instead of the natural terrain.</p>
     */
    private static BlockState sampleDominantSurfaceBlock(ServerWorld world, int x, int z, int size) {
        int grassCount = 0, sandCount = 0, redSandCount = 0, snowCount = 0;
        int stoneCount = 0, podzolCount = 0, myceliumCount = 0;

        int step = size / 3;
        int hi = size - 1;
        BlockPos.Mutable probe = new BlockPos.Mutable();
        for (int i = 0; i < 16; i++) {
            int px = x + (i % 4) * step;
            int pz = z + (i / 4) * step;
            if (i % 4 == 3) px = x + hi;
            if (i / 4 == 3) pz = z + hi;

            int sy = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, px, pz) - 1;
            BlockState surfaceBlock = world.getBlockState(probe.set(px, sy, pz));
            // Thin snow layer? Look at the block underneath so a dusting on grass
            // votes for grass, not snow.
            if (surfaceBlock.isOf(Blocks.SNOW)) {
                BlockState under = world.getBlockState(probe.set(px, sy - 1, pz));
                if (!under.isAir()) surfaceBlock = under;
            }

            if (surfaceBlock.isOf(Blocks.SAND) || surfaceBlock.isOf(Blocks.SANDSTONE)
                    || surfaceBlock.isOf(Blocks.SMOOTH_SANDSTONE) || surfaceBlock.isOf(Blocks.SUSPICIOUS_SAND)) {
                sandCount++;
            } else if (surfaceBlock.isOf(Blocks.RED_SAND) || surfaceBlock.isOf(Blocks.RED_SANDSTONE)
                    || surfaceBlock.isOf(Blocks.TERRACOTTA) || surfaceBlock.isOf(Blocks.RED_TERRACOTTA)
                    || surfaceBlock.isOf(Blocks.ORANGE_TERRACOTTA) || surfaceBlock.isOf(Blocks.YELLOW_TERRACOTTA)
                    || surfaceBlock.isOf(Blocks.WHITE_TERRACOTTA) || surfaceBlock.isOf(Blocks.BROWN_TERRACOTTA)
                    || surfaceBlock.isOf(Blocks.LIGHT_GRAY_TERRACOTTA)) {
                redSandCount++;
            } else if (surfaceBlock.isOf(Blocks.SNOW_BLOCK) || surfaceBlock.isOf(Blocks.SNOW)
                    || surfaceBlock.isOf(Blocks.POWDER_SNOW) || surfaceBlock.isOf(Blocks.ICE)
                    || surfaceBlock.isOf(Blocks.PACKED_ICE) || surfaceBlock.isOf(Blocks.BLUE_ICE)
                    || surfaceBlock.isOf(Blocks.FROSTED_ICE)) {
                snowCount++;
            } else if (surfaceBlock.isOf(Blocks.PODZOL) || surfaceBlock.isOf(Blocks.COARSE_DIRT)
                    || surfaceBlock.isOf(Blocks.ROOTED_DIRT)) {
                podzolCount++;
            } else if (surfaceBlock.isOf(Blocks.MYCELIUM)) {
                myceliumCount++;
            } else if (surfaceBlock.isOf(Blocks.STONE) || surfaceBlock.isOf(Blocks.GRAVEL)
                    || surfaceBlock.isOf(Blocks.ANDESITE) || surfaceBlock.isOf(Blocks.DIORITE)
                    || surfaceBlock.isOf(Blocks.GRANITE) || surfaceBlock.isOf(Blocks.DEEPSLATE)
                    || surfaceBlock.isOf(Blocks.TUFF) || surfaceBlock.isOf(Blocks.CALCITE)
                    || surfaceBlock.isOf(Blocks.DRIPSTONE_BLOCK)) {
                stoneCount++;
            } else {
                grassCount++;
            }
        }

        BlockState dominantTop = Blocks.GRASS_BLOCK.getDefaultState();
        int maxCount = grassCount;
        if (sandCount > maxCount)      { maxCount = sandCount;      dominantTop = Blocks.SAND.getDefaultState(); }
        if (redSandCount > maxCount)   { maxCount = redSandCount;   dominantTop = Blocks.RED_SAND.getDefaultState(); }
        if (snowCount > maxCount)      { maxCount = snowCount;      dominantTop = Blocks.SNOW_BLOCK.getDefaultState(); }
        if (stoneCount > maxCount)     { maxCount = stoneCount;     dominantTop = Blocks.STONE.getDefaultState(); }
        if (podzolCount > maxCount)    { maxCount = podzolCount;    dominantTop = Blocks.PODZOL.getDefaultState(); }
        if (myceliumCount > maxCount)  { maxCount = myceliumCount;  dominantTop = Blocks.MYCELIUM.getDefaultState(); }
        return dominantTop;
    }

    /**
     * Sample the walked-down ground Y across the WHOLE square footprint on a
     * dense grid (every {@link #TERRAIN_PROBE_STEP} blocks, plus the far edge).
     * A 13-wide hut footprint yields ~49 probes instead of the old 9, so a
     * cliff edge or a dip between probe points can no longer slip through and
     * leave the hut perched high with a deep foundation pillar.
     *
     * <p>Each entry is the ground Y, or {@link Integer#MIN_VALUE} when that
     * probe landed on water / lava. Returns {@code null} if any probe falls in
     * a chunk that is not yet fully loaded (caller should re-queue).</p>
     */
    private static int getProbeOffset(int index, int count, int size) {
        if (index == count - 1) return size - 1;
        return index * TERRAIN_PROBE_STEP;
    }

    private static int[] sampleFootprintHeights(ServerWorld world, int x, int z, int size) {
        int count = (size - 1) / TERRAIN_PROBE_STEP + 1;
        if ((size - 1) % TERRAIN_PROBE_STEP != 0) {
            count++;
        }

        int[] out = new int[count * count];
        ServerChunkManager scm = world.getChunkManager();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int k = 0;
        for (int ix = 0; ix < count; ix++) {
            int ox = getProbeOffset(ix, count, size);
            for (int iz = 0; iz < count; iz++) {
                int oz = getProbeOffset(iz, count, size);
                int px = x + ox;
                int pz = z + oz;
                if (scm.getChunk(px >> 4, pz >> 4, ChunkStatus.FULL, false) == null) return null;
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, px, pz) - 1;
                int gy = walkDownToGround(world, px, top, pz, cursor);
                // Encode "this probe landed on water/lava" as Integer.MIN_VALUE
                // so the caller can reject the candidate.
                cursor.set(px, gy, pz);
                out[k++] = world.getFluidState(cursor).isEmpty() ? gy : Integer.MIN_VALUE;
            }
        }
        return out;
    }

    /**
     * Prepares terrain around the camp using bidirectional quintic smoothstep blending.
     * Scaled down from the guild placer: smaller radius, thinner foundation.
     * 1. Clears vegetation in the blending zone
     * 2. Flattens the footprint
     * 3. Smooths terrain both UP (from valleys) and DOWN (from hills)
     */
    private static void prepareAndSmoothTerrain(ServerWorld world, BlockPos origin, int rotW, int rotL,
                                                 Random rng, long decoSeed) {
        // Blend radius — how far the slope extends beyond the footprint edges
        // Beautiful compact blend radius (7 to 9 blocks)
        final int BLEND_RADIUS = 7 + rng.nextInt(3);
        // Camps sit directly on the ground — blend target is at baseY level
        final int FOUNDATION_HEIGHT = 0;

        int footMinX = origin.getX();
        int footMinZ = origin.getZ();
        int footMaxX = origin.getX() + rotW - 1;
        int footMaxZ = origin.getZ() + rotL - 1;
        int baseY = origin.getY();
        int blendTopY = baseY + FOUNDATION_HEIGHT;

        int floor = Math.max(world.getBottomY(), baseY - 30);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos.Mutable decoCursor = new BlockPos.Mutable();

        // ===== PASS 1: Clear vegetation in the blending zone =====
        for (int x = footMinX - BLEND_RADIUS; x <= footMaxX + BLEND_RADIUS; x++) {
            for (int z = footMinZ - BLEND_RADIUS; z <= footMaxZ + BLEND_RADIUS; z++) {
                int surfaceTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
                // Only scan from actual surface down to target level + safety margin
                int scanMinY = Math.min(baseY, surfaceTopY - 15);
                for (int y = surfaceTopY; y >= scanMinY; y--) {
                    cursor.set(x, y, z);
                    BlockState st = world.getBlockState(cursor);
                    if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                    
                    if (TerrainHelper.isTreeOrVegetation(st)) {
                        world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                    }
                }
            }
        }

        // ===== PASS 2: Blend exterior terrain only (dist > 0) =====
        // Interior (dist == 0) is left untouched — the schematic places its own floor.
        // CARVING only starts at dist >= 4. At dist 1-3 (right next to the walls),
        // only building up is allowed to avoid the "carved trench" artifact.
        // Reason: at dist=1 the quintic smoothstep value is ~0.026, so targetY≈baseY.
        // If groundY is 3-5 blocks above (normal terrain variance), this would carve
        // a deep trench immediately at the wall — very visible and unnatural.
        final int CARVE_START_DIST = 4; // Minimum dist before carving is permitted
        final int CARVE_MIN_DELTA  = 3; // Only carve if ground is > this many blocks above target

        for (int x = footMinX - BLEND_RADIUS; x <= footMaxX + BLEND_RADIUS; x++) {
            for (int z = footMinZ - BLEND_RADIUS; z <= footMaxZ + BLEND_RADIUS; z++) {
                int distX = Math.max(0, Math.max(footMinX - x, x - footMaxX));
                int distZ = Math.max(0, Math.max(footMinZ - z, z - footMaxZ));
                int dist = Math.max(distX, distZ);

                // Skip interior — the schematic handles its own floor there
                if (dist == 0) continue;

                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
                int groundY = walkDownToGround(world, x, surfaceY, z, cursor);
                if (groundY <= world.getBottomY()) continue;

                // Determine biome-appropriate surface & fill blocks
                cursor.set(x, groundY, z);
                BlockState naturalSurface = world.getBlockState(cursor);
                BlockState topBlock = Blocks.GRASS_BLOCK.getDefaultState();
                BlockState fillBlock = Blocks.DIRT.getDefaultState();

                if (naturalSurface.isOf(Blocks.GRASS_BLOCK) || naturalSurface.isOf(Blocks.PODZOL)
                        || naturalSurface.isOf(Blocks.MYCELIUM) || naturalSurface.isOf(Blocks.DIRT_PATH)) {
                    topBlock = naturalSurface;
                } else if (naturalSurface.isOf(Blocks.SAND) || naturalSurface.isOf(Blocks.RED_SAND)) {
                    topBlock = naturalSurface;
                    fillBlock = naturalSurface;
                } else if (naturalSurface.isOf(Blocks.STONE) || naturalSurface.isOf(Blocks.ANDESITE)
                        || naturalSurface.isOf(Blocks.DIORITE) || naturalSurface.isOf(Blocks.GRANITE)) {
                    topBlock = naturalSurface;
                    fillBlock = Blocks.STONE.getDefaultState();
                } else if (naturalSurface.isOf(Blocks.SNOW_BLOCK)) {
                    topBlock = Blocks.SNOW_BLOCK.getDefaultState();
                }

                // Quintic smoothstep blend (6t⁵ - 15t⁴ + 10t³)
                double t = Math.min((double) dist / BLEND_RADIUS, 1.0);
                double s = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
                int targetY = (int) Math.round(blendTopY * (1.0 - s) + groundY * s);

                if (targetY < floor) continue;

                if (targetY >= groundY) {
                    // BUILD UP: terrain is lower than target — fill upward
                    for (int y = groundY + 1; y <= targetY; y++) {
                        cursor.set(x, y, z);
                        if (world.getBlockState(cursor).getFluidState().isEmpty()) {
                            world.setBlockState(cursor, fillBlock, 50);
                        }
                    }
                    cursor.set(x, targetY, z);
                    if (world.getBlockState(cursor).getFluidState().isEmpty()) {
                        world.setBlockState(cursor, topBlock, 50);
                        decorateGrassTop(world, decoSeed, x, targetY, z, topBlock, decoCursor);
                    }
                    // Clear debris above
                    for (int y = targetY + 1; y <= surfaceY + 5; y++) {
                        cursor.set(x, y, z);
                        BlockState st = world.getBlockState(cursor);
                        if (!st.isAir() && st.getFluidState().isEmpty()) {
                            world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                        }
                    }
                } else if (dist >= CARVE_START_DIST && (groundY - targetY) >= CARVE_MIN_DELTA) {
                    // CARVE DOWN: only for significant height differences far enough from walls
                    for (int y = surfaceY + 3; y > targetY; y--) {
                        cursor.set(x, y, z);
                        BlockState toClear = world.getBlockState(cursor);
                        // NEVER replace water/lava with air
                        if (!toClear.isAir() && toClear.getFluidState().isEmpty()) {
                            world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                        }
                    }
                    cursor.set(x, targetY, z);
                    world.setBlockState(cursor, topBlock, 50);
                    decorateGrassTop(world, decoSeed, x, targetY, z, topBlock, decoCursor);
                    // Ensure solid fill under the carved surface
                    for (int y = targetY - 1; y >= targetY - 3; y--) {
                        cursor.set(x, y, z);
                        BlockState below = world.getBlockState(cursor);
                        if (below.isAir() || below.isReplaceable()) {
                            world.setBlockState(cursor, fillBlock, 50);
                        }
                    }
                }
                // else: dist < CARVE_START_DIST or delta too small — leave terrain natural
            }
        }

        // ===== PASS 3: Fill foundation under the footprint =====
        // Only fills downward — no surface modification — so the schematic floor is untouched.
        BlockState dirtFill = Blocks.DIRT.getDefaultState();
        for (int x = footMinX; x <= footMaxX; x++) {
            for (int z = footMinZ; z <= footMaxZ; z++) {
                cursor.set(x, baseY - 1, z);
                while (cursor.getY() > floor) {
                    if (!world.getFluidState(cursor).isEmpty()) break; // Never fill through water
                    
                    BlockState st = world.getBlockState(cursor);
                    if (!st.isAir() && !TerrainHelper.isWalkThroughBlock(st)
                            && !st.isIn(BlockTags.SNOW)) break;
                    world.setBlockState(cursor, dirtFill, 50);
                    cursor.move(0, -1, 0);
                }
            }
        }
    }

    private record BiomeMatch(String wood, Identifier biomeId) {}

    /**
     * Decide which wood-variant fits the biome at the given (x, z). Returns null
     * if no variant fits (e.g. desert, ocean, end).
     */
    private static BiomeMatch matchWoodForBiome(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
        BlockPos pos = new BlockPos(x, y, z);
        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);

        Optional<RegistryKey<Biome>> keyOpt = biomeEntry.getKey();
        if (keyOpt.isEmpty()) return null;
        Identifier id = keyOpt.get().getValue();
        String path = id.getPath().toLowerCase();
        String namespace = id.getNamespace();

        // Reject obviously incompatible biomes early.
        if (path.contains("ocean") || path.contains("river") || path.contains("desert")
                || path.contains("badlands") || path.contains("mesa") || path.contains("end_")
                || path.contains("nether") || path.contains("the_void")
                || path.contains("beach") || (path.contains("snowy") && !path.contains("taiga")))
            return null;

        // Strict Water Check: verify center and 4 corners are not liquid
        // Footprint is roughly 10x13 for camps.
        int[][] checkOffsets = {{0,0}, {-5,-6}, {5,-6}, {-5,6}, {5,6}};
        for (int[] offset : checkOffsets) {
            int cx = x + offset[0];
            int cz = z + offset[1];
            int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, cx, cz);
            int floorMap = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, cx, cz);
            
            if (surface > floorMap) {
                return null; // FOUND WATER (liquid layer exists) -> CANCEL
            }
        }

        // Camps may only spawn in forested biomes whose name matches the
        // structure's wood material. Plains, sunflower_plains and other
        // non-forest biomes are intentionally excluded.
        // Order matters — most specific first.
        String wood = null;
        if (path.contains("birch") && (path.contains("forest") || path.contains("grove"))) wood = "birch";
        else if (path.contains("dark_forest") || path.contains("dark_oak") || path.contains("roofed")) wood = "dark_oak";
        else if ((path.contains("jungle") || path.contains("bamboo")) && !path.contains("edge")) wood = "jungle";
        else if (path.contains("savanna") || path.contains("acacia")) wood = "acacia";
        else if (path.contains("taiga") || path.contains("snowy_taiga")
                || path.contains("spruce") || path.contains("coniferous")) wood = "spruce";
        else if ((path.contains("forest") || path.contains("oak")) && !path.contains("plain")) wood = "oak";

        // Final guard: never place in pure plains, sunflower plains, meadows etc.
        if (path.contains("plain") || path.contains("meadow") || path.contains("flower_field")) return null;

        return wood != null ? new BiomeMatch(wood, id) : null;
    }

    private static long packRegion(int x, int z) {
        return (((long) x) & 0xFFFFFFFFL) | (((long) z) << 32);
    }

    /**
     * Scan downward from {@code yStart} until the first solid, non-tree block.
     * Blocks treated as "tree material" and stepped through: logs, leaves,
     * saplings, air, snow layers. Returns the Y of the highest non-tree solid
     * block found (which is what the camp should sit on top of).
     *
     * <p>Bounded by a maximum scan depth so a column with no ground at all
     * (extremely rare) does not run the loop to world-bottom for no reason.</p>
     */
    private static int walkDownToGround(ServerWorld world, int x, int yStart, int z, BlockPos.Mutable cursor) {
        cursor.set(x, yStart, z);
        int floor = Math.max(world.getBottomY(), yStart - 64);
        while (cursor.getY() > floor) {
            BlockState st = world.getBlockState(cursor);
            if (!st.isAir()
                    && st.getFluidState().isEmpty() // Pass through water/lava
                    && !TerrainHelper.isWalkThroughBlock(st)) {
                return cursor.getY();
            }
            cursor.move(0, -1, 0);
        }
        return yStart; // fallback — keep original Y if no ground found
    }
}


