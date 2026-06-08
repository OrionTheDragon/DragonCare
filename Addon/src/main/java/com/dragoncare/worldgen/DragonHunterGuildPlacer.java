package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import com.dragoncare.worldgen.BiomeMaterialHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Random;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Spawns the extremely rare Dragon Hunter Guild.
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class DragonHunterGuildPlacer {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/GuildPlacer");

    /** 64×64 chunk region (~1024×1024 blocks). Larger regions = fewer rolls per
     *  explored area, which keeps the server tick budget reasonable during travel.
     *  Subsequent-guild placement is anchored at the trigger chunk, so larger regions
     *  don't reduce the chance of meeting the player's path. */
    private static final int REGION_SIZE = 64;
    /** Base spawn chance for normal (non-first) guilds. With REGION_SIZE=64 this
     *  matches the historical density while keeping pity ramping responsive. */
    private static final double BASE_SPAWN_CHANCE = 0.05;
    /** Every this many missed regions, the chance doubles. */
    private static final int PITY_INTERVAL = 3;
    /** Hard cap on PENDING entries — drops new rolls (without marking missed) when
     *  the queue is already saturated. Prevents stalls when the player elytra-flies
     *  across many regions in a few seconds. */
    private static final int PENDING_CAP = 64;

    private static final int MIN_DISTANCE = 2048;
    private static final int MAX_TERRAIN_VARIANCE = 6;
    private static final int MAX_GAP_DEPTH = 5;
    private static final int MAX_RETRIES = 1000;
    private static final int PLACEMENTS_PER_TICK = 1;

    // ===== Subsequent-guild jitter retries =====
    /** Subsequent-guild placement is tried this many times with small jitter before
     *  the region is counted as "missed" — so a bad spot doesn't waste a whole region. */
    private static final int SUBSEQUENT_JITTER_RETRIES = 3;
    /** Max jitter offset (in blocks) for retries around the trigger anchor. */
    private static final int SUBSEQUENT_JITTER_RADIUS = 96;

    private static final ConcurrentLinkedQueue<PendingCandidate> PENDING = new ConcurrentLinkedQueue<>();
    /** Active build jobs being streamed across ticks. Filled by {@link #attemptPlacement}
     *  once a candidate passes terrain validation; drained by {@link #onServerTick}. */
    private static final ConcurrentLinkedQueue<GuildBuildJob> BUILD_JOBS = new ConcurrentLinkedQueue<>();

    /** Number of columns of {@code prepareAndSmoothTerrain} PASS 1 (vegetation clear)
     *  processed per server tick. Each column does a top-Y lookup plus a downward scan
     *  of ~15 blocks — light per column, but the full guild footprint has ~5000+ columns,
     *  so without streaming this is a ~50 ms spike. Raised from 256 → 1024 so blending
     *  on huge mountains doesn't visibly chunk through in-game. */
    private static final int PASS1_COLS_PER_TICK = 1024;
    /** Same as above for PASS 2 (terrain blend / build up / carve down). PASS 2 writes
     *  many setBlockState calls per column, so the budget is smaller. Raised from
     *  96 → 384 — the heavier writes still stay under one tick's slice. */
    private static final int PASS2_COLS_PER_TICK = 384;

    private record PendingCandidate(ServerWorld world, int blockX, int blockZ, long rngSeed,
                                     int retries, boolean isForced, int jitterLeft) {}

    /** Phases of a streamed guild build. */
    private enum Phase { PASS1, PASS2, PLACE, FINALIZE, DONE }

    /**
     * In-progress guild build, paused between ticks.
     *
     * <p>Carries the full context of a placement (world, origin, rotation, schematic,
     * dominant biome material) plus a cursor (cur_x, cur_z) tracking how far along
     * the current phase has advanced. Each tick the placer processes up to
     * {@link #PASS1_COLS_PER_TICK} / {@link #PASS2_COLS_PER_TICK} columns and yields.</p>
     *
     * <p>Why this is safe: all reads / writes still happen on the server tick thread —
     * we only spread the *cost* across ticks. Chunks the schematic touches were verified
     * loaded at enqueue time; if a chunk unloads mid-stream the per-column code falls
     * back to no-op writes (setBlockState on an unloaded chunk is harmless).</p>
     */
    private static final class GuildBuildJob {
        final ServerWorld world;
        final BlockPos origin;
        final BlockRotation rotation;
        final ParsedSchematic schem;
        final long rngSeed;
        final BlockState topBlock;
        final BlockState fillBlock;
        final int rotW, rotL;
        final int hillRadius;
        final int footMinX, footMinZ, footMaxX, footMaxZ;
        final int baseY, hillTopY, floor;
        final int trackerX, trackerZ; // original (blockX, blockZ) for state.addGuildPlacement
        final boolean isForced;

        Phase phase = Phase.PASS1;
        /** Cursor X for the current phase (PASS1 or PASS2). */
        int curX;
        /** Cursor Z for the current phase. */
        int curZ;

        GuildBuildJob(ServerWorld world, BlockPos origin, BlockRotation rotation,
                      ParsedSchematic schem, long rngSeed,
                      BlockState topBlock, BlockState fillBlock,
                      int rotW, int rotL, int hillRadius,
                      int trackerX, int trackerZ, boolean isForced) {
            this.world = world;
            this.origin = origin;
            this.rotation = rotation;
            this.schem = schem;
            this.rngSeed = rngSeed;
            this.topBlock = topBlock;
            this.fillBlock = fillBlock;
            this.rotW = rotW;
            this.rotL = rotL;
            this.hillRadius = hillRadius;
            this.footMinX = origin.getX();
            this.footMinZ = origin.getZ();
            this.footMaxX = origin.getX() + rotW - 1;
            this.footMaxZ = origin.getZ() + rotL - 1;
            this.baseY = origin.getY();
            this.hillTopY = baseY + 2; // FOUNDATION_HEIGHT
            this.floor = Math.max(world.getBottomY(), baseY - 40);
            this.trackerX = trackerX;
            this.trackerZ = trackerZ;
            this.isForced = isForced;
            // Initialise cursor to the top-left corner of the blend zone.
            this.curX = footMinX - hillRadius;
            this.curZ = footMinZ - hillRadius;
        }
    }

    private DragonHunterGuildPlacer() {}

    /** Clears all transient static state. Called on server stop to prevent
     *  stale references to a dead ServerWorld leaking into the next session. */
    public static void clearStatic() {
        PENDING.clear();
        BUILD_JOBS.clear();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerWorld world)) return;
        // Guilds are an Overworld-only feature — skip the Nether/End/custom dimensions.
        if (!world.getRegistryKey().equals(World.OVERWORLD)) return;

        DragonHunterState state = DragonHunterState.get(world);

        Chunk chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        int regionX = Math.floorDiv(chunkPos.x, REGION_SIZE);
        int regionZ = Math.floorDiv(chunkPos.z, REGION_SIZE);
        long regionKey = packRegion(regionX, regionZ);

        if (state.isGuildRegionProcessed(regionKey)) return;
        state.markGuildRegionProcessed(regionKey);

        Random regionRng = new Random(world.getSeed()
                ^ ((long) regionX * 1234567L + (long) regionZ * 7654321L) ^ 8888L);

        int missed = state.getMissedRegions();
        
        double chance = 0.0;
        boolean isForced = false;
        
        if (missed >= 5) { // 5 regions passed ~ 5000 blocks
            chance = 1.0;
            isForced = true;
        } else if (missed == 4) {
            chance = 0.5;
        } else if (missed == 3) {
            chance = 0.33;
        }

        if (regionRng.nextDouble() < chance) {
            // Drop the roll if the worker queue is already saturated. Doesn't mark
            // the region as missed — we just defer; another nearby region will pick
            // it up later. Prevents lag spikes during fast travel.
            if (PENDING.size() >= PENDING_CAP) return;

            int blockX = (chunkPos.x << 4) + 8;
            int blockZ = (chunkPos.z << 4) + 8;
            PENDING.add(new PendingCandidate(world, blockX, blockZ, regionRng.nextLong(),
                    0, isForced, SUBSEQUENT_JITTER_RETRIES));
            LOG.info("Guild candidate enqueued at {},{} (chance={}%, forced={})",
                    blockX, blockZ, String.format("%.1f", chance * 100), isForced);
        } else {
            state.incrementMissedRegions();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {

        // Drain at most one streamed build job per tick (each phase tick is the
        // heavy part — we never want to run two phases of two different builds
        // in the same tick, that would defeat the smoothing entirely).
        GuildBuildJob job = BUILD_JOBS.peek();
        if (job != null) {
            advanceBuildJob(job);
            if (job.phase == Phase.DONE) {
                BUILD_JOBS.poll();
                // NOTE: addGuildPlacement() is now called at job creation time
                // (in attemptPlacement, just before BUILD_JOBS.add) to close the
                // race window between candidates. No need to record again here.
                LOG.info("PLACED Dragon Hunter Guild at {} (forced={}, dominant={})",
                        job.origin, job.isForced, job.topBlock.getBlock().getName().getString());
            }
            // If a build is in flight we still drain the small candidate queue —
            // candidates only validate + enqueue (cheap), they don't write blocks.
        }

        // Run multiple times per tick to prevent the queue from clogging up
        for (int i = 0; i < 5; i++) {
            PendingCandidate c = PENDING.poll();
            if (c == null) return;

            int result = attemptPlacement(c);
            if (result == 0) {
                // Chunk not loaded — re-queue for later.
                int maxRetriesAllowed = 60; // 60 ticks ~ 3 seconds
                if (c.retries < maxRetriesAllowed) {
                    PENDING.add(new PendingCandidate(c.world, c.blockX, c.blockZ, c.rngSeed,
                            c.retries + 1, c.isForced, c.jitterLeft));
                }
            } else if (result == -1) {
                // Terrain rejected.
                if (c.jitterLeft > 0) {
                    // Jitter around the trigger chunk and try again.
                    Random jitterRng = new Random(c.rngSeed ^ (long) c.jitterLeft * 999983L);
                    int dx = jitterRng.nextInt(SUBSEQUENT_JITTER_RADIUS * 2 + 1) - SUBSEQUENT_JITTER_RADIUS;
                    int dz = jitterRng.nextInt(SUBSEQUENT_JITTER_RADIUS * 2 + 1) - SUBSEQUENT_JITTER_RADIUS;
                    PENDING.add(new PendingCandidate(c.world, c.blockX + dx, c.blockZ + dz,
                            jitterRng.nextLong(), 0, c.isForced, c.jitterLeft - 1));
                } else {
                    // Out of jitter retries — count this region as missed (pity ticks up).
                    DragonHunterState.get(c.world).incrementMissedRegions();
                }
            }
            // result == 1 — done (placed or permanently skipped); no further action.
        }
    }

    public static void forceSpawn(ServerWorld world, BlockPos pos) {
        ParsedSchematic schem = AddonSchematics.getSchematic(AddonSchematics.GUILD_FILE);
        if (schem == null) {
            LOG.warn("Guild schematic is null!");
            return;
        }
        
        Random rng = new Random();
        // Force NONE rotation for testing entity placement
        BlockRotation rotation = BlockRotation.NONE;
        LOG.info("FORCE SPAWN: schematic W={} H={} L={}, entities={}, blockEntities={}",
                schem.width, schem.height, schem.length, schem.entities.size(), schem.blockEntities.size());
        
        RegistryEntry<Biome> biome = world.getBiome(pos);
        BlockState dominantTop = BiomeMaterialHelper.getTopBlockForBiome(biome);
        BlockState dominantFill = Blocks.DIRT.getDefaultState();
        if (dominantTop.isOf(Blocks.SAND)) {
            dominantFill = Blocks.SANDSTONE.getDefaultState();
        } else if (dominantTop.isOf(Blocks.RED_SAND)) {
            dominantFill = Blocks.RED_SANDSTONE.getDefaultState();
        } else if (dominantTop.isOf(Blocks.SNOW_BLOCK)) {
            dominantFill = Blocks.PACKED_ICE.getDefaultState();
        } else if (dominantTop.isOf(Blocks.STONE)) {
            dominantFill = Blocks.STONE.getDefaultState();
        }

        prepareAndSmoothTerrain(world, pos, schem.rotatedWidth(rotation), schem.rotatedLength(rotation), rng, dominantTop, dominantFill);
        schem.placeInWorld(world, pos, rotation, rng, new DragonHunterMarkers(true));
        
        finalizeFootprintSurface(world, pos, schem.rotatedWidth(rotation), schem.rotatedLength(rotation), dominantTop, dominantFill, rng.nextLong());
        LOG.info("FORCE PLACED Guild at {} with rotation {}", pos, rotation);
    }

    /**
     * Attempt to place the guild.
     * @return 0 = chunk not loaded (retry later), 1 = done (placed or permanently rejected),
     *        -1 = terrain rejected (eligible for reroll)
     */
    private static int attemptPlacement(PendingCandidate c) {
        ServerWorld world = c.world;
        ServerChunkManager scm = world.getChunkManager();

        DragonHunterState state = DragonHunterState.get(world);

        // We load AT MOST ONE chunk synchronously per tick to avoid freezing.
        if (scm.getChunk(c.blockX >> 4, c.blockZ >> 4, ChunkStatus.FULL, false) == null) {
            return 0;
        }

        if (!c.isForced && !isValidBiome(world, c.blockX, c.blockZ)) {
            LOG.debug("Rejecting Guild at {},{} — Invalid Biome", c.blockX, c.blockZ);
            return -1;
        }

        if (state.isGuildNearby(c.blockX, c.blockZ, MIN_DISTANCE)) return 1;

        ParsedSchematic schem = AddonSchematics.getSchematic(AddonSchematics.GUILD_FILE);
        if (schem == null) return 1;

        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, c.blockX, c.blockZ) - 1;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int y = walkDownToGround(world, c.blockX, surfaceY, c.blockZ, cursor);
        if (y <= world.getBottomY() || y >= world.getTopY() - schem.height - 10) return -1;
        cursor.set(c.blockX, y, c.blockZ);
        if (!c.isForced && !world.getFluidState(cursor).isEmpty()) return -1;

        int footprint = Math.max(schem.width, schem.length);
        for (int sx = c.blockX - 16; sx <= c.blockX + footprint + 16; sx += 16) {
            for (int sz = c.blockZ - 16; sz <= c.blockZ + footprint + 16; sz += 16) {
                if (scm.getChunk(sx >> 4, sz >> 4, ChunkStatus.FULL, false) == null) {
                    return 0;
                }
            }
        }

        // 16-point terrain check
        int[] results = sampleGround(world, c.blockX, c.blockZ, footprint);
        if (results == null) return 0;
 
        boolean forceAnyTerrain = c.isForced;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < 16; i++) {
            int gy = results[i];
            int surfaceAtPoint = results[i + 16];
            
            if (gy == Integer.MIN_VALUE) {
                if (!forceAnyTerrain) {
                    LOG.debug("Rejecting Guild at {},{} — Fluid at probe {}", c.blockX, c.blockZ, i);
                    return -1;
                }
                // If forcing, pretend the water surface is the ground so it builds an island up to water level.
                gy = surfaceAtPoint;
            }
            
            if (gy < min) min = gy;
            if (gy > max) max = gy;
            
            int maxGapAllowed = forceAnyTerrain ? 10 : MAX_GAP_DEPTH;
            if (!forceAnyTerrain && (surfaceAtPoint - gy > maxGapAllowed)) {
                LOG.debug("Rejecting Guild at {},{} — Gap depth {} > {} at probe {}", c.blockX, c.blockZ, surfaceAtPoint - gy, maxGapAllowed, i);
                return -1;
            }
        }

        int maxVarianceAllowed = forceAnyTerrain ? 10 : MAX_TERRAIN_VARIANCE;
        if (!forceAnyTerrain && (max - min > maxVarianceAllowed)) {
            LOG.debug("Rejecting Guild at {},{} — Variance {} > {}", c.blockX, c.blockZ, max - min, maxVarianceAllowed);
            return -1;
        }

        if (forceAnyTerrain) {
            y = min + 1;
        }

        Random rng = new Random(c.rngSeed);
        BlockPos origin = new BlockPos(c.blockX, y - 1, c.blockZ);
        BlockRotation rotation = BlockRotation.random(net.minecraft.util.math.random.Random.create(rng.nextLong()));
        
        // ===== DETERMINE DOMINANT SURFACE MATERIAL BY SAMPLING 16 ACTUAL BLOCKS =====
        // Reads the real top block at each of the 16 probe points (using the surface
        // Y already computed by sampleGround in results[i+16]) and classifies it into
        // a tier. The tier with the most votes wins. This replaces the previous
        // biome-name match which gave wrong results (e.g. picked PODZOL for any
        // "taiga"-named biome even though the ground at the spawn site was grass).
        BlockState dominantTop  = Blocks.GRASS_BLOCK.getDefaultState();
        BlockState dominantFill = Blocks.DIRT.getDefaultState();

        int grassCount = 0;     // grass / dirt / moss / mud
        int sandCount = 0;      // sand / sandstone
        int redSandCount = 0;   // red sand / red sandstone / terracotta
        int snowCount = 0;      // snow / ice / powder snow
        int stoneCount = 0;     // stone / gravel / andesite / etc.
        int podzolCount = 0;    // podzol / coarse dirt / rooted dirt
        int myceliumCount = 0;  // mycelium

        int step = footprint / 3;
        int hi = footprint - 1;
        BlockPos.Mutable probe = new BlockPos.Mutable();
        for (int i = 0; i < 16; i++) {
            int px = c.blockX + (i % 4) * step;
            int pz = c.blockZ + (i / 4) * step;
            if (i % 4 == 3) px = c.blockX + hi;
            if (i / 4 == 3) pz = c.blockZ + hi;

            // results[i+16] is the natural surface Y at this probe (set by sampleGround).
            // Fall back to a heightmap lookup if the probe was marked as water.
            int sy = results[i + 16];
            if (sy == Integer.MIN_VALUE - 1 || sy <= world.getBottomY()) {
                sy = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, px, pz) - 1;
            }
            BlockState surfaceBlock = world.getBlockState(probe.set(px, sy, pz));
            // If the top block is snow LAYER (1-block-tall) prefer the block under it
            // so a thin dusting on grass votes for grass, not snow.
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
            } else if (surfaceBlock.isOf(Blocks.GRASS_BLOCK) || surfaceBlock.isOf(Blocks.DIRT)
                    || surfaceBlock.isOf(Blocks.DIRT_PATH) || surfaceBlock.isOf(Blocks.MUD)
                    || surfaceBlock.isOf(Blocks.MUDDY_MANGROVE_ROOTS) || surfaceBlock.isOf(Blocks.MOSS_BLOCK)) {
                grassCount++;
            } else {
                // Unknown block (e.g. snow layer that wasn't replaced, leaves, etc.) —
                // count it as grass so we don't paint exotic foundations under regular forest.
                grassCount++;
            }
        }

        int maxCount = grassCount;
        if (sandCount > maxCount) {
            maxCount = sandCount;
            dominantTop  = Blocks.SAND.getDefaultState();
            dominantFill = Blocks.SANDSTONE.getDefaultState();
        }
        if (redSandCount > maxCount) {
            maxCount = redSandCount;
            dominantTop  = Blocks.RED_SAND.getDefaultState();
            dominantFill = Blocks.RED_SANDSTONE.getDefaultState();
        }
        if (snowCount > maxCount) {
            maxCount = snowCount;
            dominantTop  = Blocks.SNOW_BLOCK.getDefaultState();
            dominantFill = Blocks.PACKED_ICE.getDefaultState();
        }
        if (stoneCount > maxCount) {
            maxCount = stoneCount;
            dominantTop  = Blocks.STONE.getDefaultState();
            dominantFill = Blocks.STONE.getDefaultState();
        }
        if (podzolCount > maxCount) {
            maxCount = podzolCount;
            dominantTop  = Blocks.PODZOL.getDefaultState();
            dominantFill = Blocks.DIRT.getDefaultState();
        }
        if (myceliumCount > maxCount) {
            maxCount = myceliumCount;
            dominantTop  = Blocks.MYCELIUM.getDefaultState();
            dominantFill = Blocks.DIRT.getDefaultState();
        }
        
        // Hand off to the streamed-build pipeline instead of doing all the work
        // (vegetation clear + terrain blend + schematic placement + finalize) inline.
        // The job carries enough state to be resumed across ticks, which spreads
        // the ~600 ms guild placement spike over ~10 ticks (≈ 60 ms each).
        final int hillRadius = 16 + rng.nextInt(5); // matches old HILL_RADIUS roll
        // Reserve the spot in state IMMEDIATELY so any candidate processed
        // before this job reaches its FINALIZE phase sees the placement and
        // gets rejected by the MIN_DISTANCE check.
        //
        // Without this reservation there is a window of several dozen ticks
        // (streaming PASS1 + PASS2 + PLACE + FINALIZE) during which a
        // neighbouring candidate can sneak past isGuildNearby() because the
        // current build has not yet called addGuildPlacement() — that race
        // produced two guilds spawning side-by-side on a beach.
        state.addGuildPlacement(c.blockX, c.blockZ);
        BUILD_JOBS.add(new GuildBuildJob(
                world, origin, rotation, schem, c.rngSeed,
                dominantTop, dominantFill,
                schem.rotatedWidth(rotation), schem.rotatedLength(rotation),
                hillRadius,
                c.blockX, c.blockZ, c.isForced));
        LOG.info("ENQUEUED Dragon Hunter Guild build at {} (forced={}, dominant={}, hillRadius={})",
                origin, c.isForced, dominantTop.getBlock().getName().getString(), hillRadius);
        return 1;
    }

    /**
     * Advance one phase of a streamed guild build. Processes at most
     * {@link #PASS1_COLS_PER_TICK} / {@link #PASS2_COLS_PER_TICK} columns
     * for the corresponding phase, then yields. PLACE and FINALIZE phases
     * still run in one tick — schematic placement is monolithic.
     */
    private static void advanceBuildJob(GuildBuildJob job) {
        Phase prevPhase = job.phase;
        long t0 = System.nanoTime();
        switch (job.phase) {
            case PASS1 -> tickPass1(job);
            case PASS2 -> tickPass2(job);
            case PLACE -> tickPlace(job);
            case FINALIZE -> tickFinalize(job);
            case DONE -> {}
        }
        if (job.phase != prevPhase) {
            LOG.debug("[BUILD-JOB] origin={} forced={} phase {} -> {} (phaseTookMs={})",
                    job.origin, job.isForced, prevPhase, job.phase, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    private static void tickPass1(GuildBuildJob job) {
        int processed = 0;
        int maxX = job.footMaxX + job.hillRadius;
        int maxZ = job.footMaxZ + job.hillRadius;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        while (processed < PASS1_COLS_PER_TICK && job.curX <= maxX) {
            clearVegetationColumn(job.world, job.curX, job.curZ, job.baseY, cursor);
            processed++;
            job.curZ++;
            if (job.curZ > maxZ) {
                job.curZ = job.footMinZ - job.hillRadius;
                job.curX++;
            }
        }
        if (job.curX > maxX) {
            // PASS 1 finished — reset cursor and advance phase.
            job.phase = Phase.PASS2;
            job.curX = job.footMinX - job.hillRadius;
            job.curZ = job.footMinZ - job.hillRadius;
        }
    }

    private static void tickPass2(GuildBuildJob job) {
        int processed = 0;
        int maxX = job.footMaxX + job.hillRadius;
        int maxZ = job.footMaxZ + job.hillRadius;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        while (processed < PASS2_COLS_PER_TICK && job.curX <= maxX) {
            blendColumn(job, job.curX, job.curZ, cursor);
            processed++;
            job.curZ++;
            if (job.curZ > maxZ) {
                job.curZ = job.footMinZ - job.hillRadius;
                job.curX++;
            }
        }
        if (job.curX > maxX) {
            job.phase = Phase.PLACE;
        }
    }

    private static void tickPlace(GuildBuildJob job) {
        // Schematic placement is monolithic — the schematic walks its own
        // volume internally and we cannot resume it half-way. Done in one tick.
        LOG.debug("[STREAM-PLACE-BEGIN] origin={} rotation={} forced={} schemSize=W{} H{} L{} entities={} blockEntities={}",
                job.origin, job.rotation, job.isForced,
                job.schem.width, job.schem.height, job.schem.length,
                job.schem.entities.size(), job.schem.blockEntities.size());
        long t0 = System.nanoTime();
        Random rng = new Random(job.rngSeed);
        // Burn one nextLong to match the RNG sequence the inline code used to
        // consume for BlockRotation.random — keeps marker processor seeds
        // identical to the old behaviour.
        rng.nextLong();
        try {
            job.schem.placeInWorld(job.world, job.origin, job.rotation, rng, new DragonHunterMarkers(true));
        } catch (Throwable t) {
            LOG.error("[STREAM-PLACE-EXCEPTION] origin={} forced={} err={}", job.origin, job.isForced, t.toString(), t);
        }
        LOG.debug("[STREAM-PLACE-END] origin={} forced={} totalMs={}",
                job.origin, job.isForced, (System.nanoTime() - t0) / 1_000_000);
        job.phase = Phase.FINALIZE;
    }

    private static void tickFinalize(GuildBuildJob job) {
        LOG.debug("[STREAM-FINALIZE-BEGIN] origin={} forced={} rotW={} rotL={} top={} fill={}",
                job.origin, job.isForced, job.rotW, job.rotL,
                job.topBlock.getBlock().getName().getString(),
                job.fillBlock.getBlock().getName().getString());
        long t0 = System.nanoTime();
        try {
            finalizeFootprintSurface(job.world, job.origin, job.rotW, job.rotL, job.topBlock, job.fillBlock, job.rngSeed);
        } catch (Throwable t) {
            LOG.error("[STREAM-FINALIZE-EXCEPTION] origin={} err={}", job.origin, t.toString(), t);
        }
        LOG.debug("[STREAM-FINALIZE-END] origin={} forced={} ms={}",
                job.origin, job.isForced, (System.nanoTime() - t0) / 1_000_000);
        job.phase = Phase.DONE;
    }

    /** Single-column body of PASS 1 — clears vegetation between schematic base
     *  and the natural surface. Extracted so it can be called incrementally by
     *  {@link #tickPass1(GuildBuildJob)}. */
    private static void clearVegetationColumn(ServerWorld world, int x, int z, int baseY, BlockPos.Mutable cursor) {
        int surfaceTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
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

    /** Single-column body of PASS 2 — builds up valleys / carves down mountains
     *  with a quintic smoothstep blend. Extracted from {@link #prepareAndSmoothTerrain}. */
    private static void blendColumn(GuildBuildJob job, int x, int z, BlockPos.Mutable cursor) {
        int distX = Math.max(0, Math.max(job.footMinX - x, x - job.footMaxX));
        int distZ = Math.max(0, Math.max(job.footMinZ - z, z - job.footMaxZ));
        int dist = Math.max(distX, distZ);

        int surfaceY = job.world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
        int groundY = walkDownThroughFluid(job.world, x, surfaceY, z, cursor);
        if (groundY <= job.world.getBottomY()) return;

        cursor.set(x, groundY, z);
        BlockState naturalSurface = job.world.getBlockState(cursor);
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

        int targetY;
        if (dist == 0) {
            targetY = job.baseY - 1;
        } else {
            double t = Math.min((double) dist / job.hillRadius, 1.0);
            double s = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            targetY = (int) Math.round(job.hillTopY * (1.0 - s) + groundY * s);
        }
        if (targetY < job.floor) return;

        if (targetY >= groundY) {
            for (int y = groundY + 1; y <= targetY; y++) {
                cursor.set(x, y, z);
                job.world.setBlockState(cursor, fillBlock, 50);
            }
            if (dist > 0) {
                cursor.set(x, targetY, z);
                job.world.setBlockState(cursor, topBlock, 50);
                decorateGrassTop(job.world, job.rngSeed, x, targetY, z, topBlock, cursor);
            }
            for (int y = targetY + 1; y <= surfaceY + 5; y++) {
                cursor.set(x, y, z);
                BlockState st = job.world.getBlockState(cursor);
                if (!st.isAir() && st.getFluidState().isEmpty()) {
                    job.world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                }
            }
        } else {
            for (int y = surfaceY + 3; y > targetY; y--) {
                cursor.set(x, y, z);
                BlockState toClear = job.world.getBlockState(cursor);
                if (!toClear.isAir() && toClear.getFluidState().isEmpty()) {
                    job.world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                }
            }
            if (dist > 0) {
                cursor.set(x, targetY, z);
                job.world.setBlockState(cursor, topBlock, 50);
                decorateGrassTop(job.world, job.rngSeed, x, targetY, z, topBlock, cursor);
            }
            for (int y = targetY - 1; y >= targetY - 4; y--) {
                cursor.set(x, y, z);
                BlockState below = job.world.getBlockState(cursor);
                if (below.isAir() || below.isReplaceable()) {
                    job.world.setBlockState(cursor, fillBlock, 50);
                }
            }
        }
    }

    /**
     * If the topBlock is grass, sprinkles short grass / flowers on top of it.
     * Deterministic per (seed, x, z) so the same world seed gives the same
     * decoration. Cheap: one hash + at most one extra setBlockState per call.
     *
     * <p>Density tuned to roughly vanilla plains: ~12% short grass plus ~3%
     * scattered flowers, ~85% bare grass. Player-perceived as natural
     * groundcover, not a dense flower bed.</p>
     */
    private static void decorateGrassTop(ServerWorld world, long rngSeed, int x, int y, int z,
                                          BlockState topBlock, BlockPos.Mutable cursor) {
        if (!topBlock.isOf(Blocks.GRASS_BLOCK)) return;
        cursor.set(x, y + 1, z);
        if (!world.getBlockState(cursor).isAir()) return;

        // SplitMix64-style hash for cheap, well-distributed per-column randomness.
        long h = rngSeed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xBF58476D1CE4E5B9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        int roll = (int) (h & 0xFF); // 0..255

        BlockState decoration;
        if      (roll <  30) decoration = Blocks.SHORT_GRASS.getDefaultState();  // 11.7%
        else if (roll <  34) decoration = Blocks.DANDELION.getDefaultState();    //  1.6%
        else if (roll <  37) decoration = Blocks.POPPY.getDefaultState();        //  1.2%
        else if (roll <  38) decoration = Blocks.OXEYE_DAISY.getDefaultState();  //  0.4%
        else if (roll <  39) decoration = Blocks.CORNFLOWER.getDefaultState();   //  0.4%
        else if (roll <  40) decoration = Blocks.AZURE_BLUET.getDefaultState();  //  0.4%
        else                  return;                                             // ~84% bare grass

        world.setBlockState(cursor, decoration, 50);
    }

    /**
     * Prepares the terrain around the structure:
     * 1. Clears trees and vegetation in the full blending zone
     * 2. Flattens footprint to structure base
     * 3. Blends terrain in BOTH directions — builds hills up from valleys
     *    AND carves slopes down from mountains — using quintic smoothstep
     *
     * <p>Kept around for the {@link #forceSpawn} debug path, which still runs
     * synchronously. Live placements go through the streamed {@link GuildBuildJob}
     * pipeline instead.</p>
     */
    private static void prepareAndSmoothTerrain(ServerWorld world, BlockPos origin, int rotW, int rotL, Random rng, BlockState dominantTop, BlockState dominantFill) {
        // How many blocks the blending extends beyond the footprint
        // Beautiful compact blend radius for guilds (16 to 20 blocks)
        final int HILL_RADIUS = 16 + rng.nextInt(5);
        // The building's foundation is 3 blocks tall inside the schematic (y=0,1,2).
        // The blend target at the building edge is 2nd layer of the foundation.
        final int FOUNDATION_HEIGHT = 2;
        
        int footMinX = origin.getX();
        int footMinZ = origin.getZ();
        int footMaxX = origin.getX() + rotW - 1;
        int footMaxZ = origin.getZ() + rotL - 1;
        int baseY = origin.getY();
        // The blend target at the building edge
        int hillTopY = baseY + FOUNDATION_HEIGHT;
        
        int floor = Math.max(world.getBottomY(), baseY - 40);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        
        // ===== PASS 1: Clear vegetation in the full blending zone =====
        // Extend to HILL_RADIUS so trees on slopes above the structure are also removed
        for (int x = footMinX - HILL_RADIUS; x <= footMaxX + HILL_RADIUS; x++) {
            for (int z = footMinZ - HILL_RADIUS; z <= footMaxZ + HILL_RADIUS; z++) {
                int surfaceTopY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
                int scanMinY = Math.min(baseY, surfaceTopY - 15);
                for (int y = surfaceTopY; y >= scanMinY; y--) {
                    cursor.set(x, y, z);
                    BlockState st = world.getBlockState(cursor);
                    // SKIP AIR AND WATER
                    if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                    
                    if (TerrainHelper.isTreeOrVegetation(st)) {
                        world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                    }
                }
            }
        }
        
        // ===== PASS 2: Blend terrain — both upward (valley) and downward (mountain) =====
        for (int x = footMinX - HILL_RADIUS; x <= footMaxX + HILL_RADIUS; x++) {
            for (int z = footMinZ - HILL_RADIUS; z <= footMaxZ + HILL_RADIUS; z++) {
                // Chebyshev distance from the footprint edge (0 = inside, >0 = outside)
                int distX = Math.max(0, Math.max(footMinX - x, x - footMaxX));
                int distZ = Math.max(0, Math.max(footMinZ - z, z - footMaxZ));
                int dist = Math.max(distX, distZ);
                
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
                int groundY = walkDownThroughFluid(world, x, surfaceY, z, cursor);
                if (groundY <= world.getBottomY()) continue;

                // Determine biome surface & fill blocks
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

                int targetY;
                if (dist == 0) {
                    // Inside footprint — flat at 1 below schematic origin
                    targetY = baseY - 1;
                } else {
                    // Outside footprint — smooth blend between hillTopY and groundY.
                    // Works symmetrically: if ground < hillTop → builds up (hill),
                    //                      if ground > hillTop → carves down (slope).
                    double t = Math.min((double) dist / HILL_RADIUS, 1.0);
                    // Quintic smoothstep: 6t⁵ - 15t⁴ + 10t³
                    double s = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
                    targetY = (int) Math.round(hillTopY * (1.0 - s) + groundY * s);
                }
                
                if (targetY < floor) continue;
                
                if (targetY >= groundY) {
                    // BUILD UP: terrain is lower than target — fill upward
                    for (int y = groundY + 1; y <= targetY; y++) {
                        cursor.set(x, y, z);
                        world.setBlockState(cursor, fillBlock, 50);
                    }
                    // Surface block on top (only outside the footprint)
                    if (dist > 0) {
                        cursor.set(x, targetY, z);
                        world.setBlockState(cursor, topBlock, 50);
                    }
                    // Clear debris above
                    for (int y = targetY + 1; y <= surfaceY + 5; y++) {
                        cursor.set(x, y, z);
                        BlockState st = world.getBlockState(cursor);
                        if (!st.isAir() && st.getFluidState().isEmpty()) {
                            world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                        }
                    }
                } else {
                    // CARVE DOWN: terrain is higher than target — smooth slope downward
                    // Remove everything above the target surface
                    for (int y = surfaceY + 3; y > targetY; y--) {
                        cursor.set(x, y, z);
                        BlockState toClear = world.getBlockState(cursor);
                        // NEVER replace water/lava with air
                        if (!toClear.isAir() && toClear.getFluidState().isEmpty()) {
                            world.setBlockState(cursor, Blocks.AIR.getDefaultState(), 50);
                        }
                    }
                    // Place biome-appropriate surface on top of the carved slope
                    if (dist > 0) {
                        cursor.set(x, targetY, z);
                        world.setBlockState(cursor, topBlock, 50);
                    }
                    // Ensure solid fill under the new surface (no floating surface blocks)
                    for (int y = targetY - 1; y >= targetY - 4; y--) {
                        cursor.set(x, y, z);
                        BlockState below = world.getBlockState(cursor);
                        if (below.isAir() || below.isReplaceable()) {
                            world.setBlockState(cursor, fillBlock, 50);
                        }
                    }
                }
            }
        }

    }

    /**
     * Final pass over the footprint to find any grass/dirt blocks (often baked into the schematic)
     * and replace them with the actual biome surface block. Run AFTER schematic placement.
     *
     * <p>Additionally sprinkles {@link #decorateGrassTop} on any exposed grass cell
     * (with air above) so the building's foundation perimeter and any open
     * courtyards / paths naturally grow over with sparse vanilla-like
     * grass/flowers.</p>
     */
    private static void finalizeFootprintSurface(ServerWorld world, BlockPos origin, int rotW, int rotL,
                                                  BlockState topBlock, BlockState fillBlock, long rngSeed) {
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
                // Scan the base level foundation layers (y=0, y=1, y=2 relative to baseY)
                for (int y = baseY; y <= baseY + 2; y++) {
                    cursor.set(x, y, z);
                    BlockState current = world.getBlockState(cursor);

                    // If it's grass/lawn, path, podzol, or mycelium from the schematic, swap it with the dominant top block!
                    if (current.isOf(Blocks.GRASS_BLOCK) || current.isOf(Blocks.DIRT_PATH)
                            || current.isOf(Blocks.MYCELIUM) || current.isOf(Blocks.PODZOL)) {
                        world.setBlockState(cursor, topBlock, 50);
                        if (topBlock.isOf(Blocks.GRASS_BLOCK)) highestReplacedY = y;
                    }
                    // If it's the under-dirt layer, swap it with the dominant fill block (sandstone for sand, ice for snow, etc.)
                    // Except for the y=baseY+2 layer, which is the surface lawn, so we want the topBlock there!
                    else if (current.isOf(Blocks.DIRT) || current.isOf(Blocks.COARSE_DIRT)) {
                        boolean isTopLayer = (y == baseY + 2);
                        world.setBlockState(cursor, isTopLayer ? topBlock : fillBlock, 50);
                        if (isTopLayer && topBlock.isOf(Blocks.GRASS_BLOCK)) highestReplacedY = y;
                    }
                }
                // Try to decorate the highest grass cell we placed (foundation grow-over).
                if (highestReplacedY != Integer.MIN_VALUE) {
                    decorateGrassTop(world, rngSeed, x, highestReplacedY, z, topBlock, decoCursor);
                }
            }
        }
    }

    private static int[] sampleGround(ServerWorld world, int x, int z, int size) {
        int hi = size - 1;
        int step = size / 3;
        int[] out = new int[32]; // 0-15: ground Y, 16-31: surface Y
        
        ServerChunkManager scm = world.getChunkManager();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int i = 0; i < 16; i++) {
            int px = x + (i % 4) * step;
            int pz = z + (i / 4) * step;
            if (i % 4 == 3) px = x + hi; // clamp edge
            if (i / 4 == 3) pz = z + hi; // clamp edge

            if (scm.getChunk(px >> 4, pz >> 4, ChunkStatus.FULL, false) == null) return null;
            
            int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, px, pz);
            int floorMap = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, px, pz);
            
            // If surface is higher than ocean floor, there is liquid (water/lava)
            if (surface > floorMap) {
                out[i] = Integer.MIN_VALUE; // MARK AS WATER
            } else {
                int gy = walkDownThroughFluid(world, px, surface - 1, pz, cursor);
                out[i] = gy;
            }
            out[i + 16] = surface - 1;
        }
        return out;
    }

    private static boolean isValidBiome(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
        RegistryEntry<Biome> biomeEntry = world.getBiome(new BlockPos(x, y, z));
        Optional<RegistryKey<Biome>> keyOpt = biomeEntry.getKey();
        if (keyOpt.isEmpty()) return false;
        
        String path = keyOpt.get().getValue().getPath().toLowerCase();
        // Allow ANY biome except water ones
        return !path.contains("ocean") && !path.contains("river") && !path.contains("beach");
    }

    private static int walkDownToGround(ServerWorld world, int x, int yStart, int z, BlockPos.Mutable cursor) {
        cursor.set(x, yStart, z);
        int floor = Math.max(world.getBottomY(), yStart - 64);
        while (cursor.getY() > floor) {
            BlockState st = world.getBlockState(cursor);
            if (!st.isAir() && !TerrainHelper.isWalkThroughBlock(st)) {
                return cursor.getY();
            }
            cursor.move(0, -1, 0);
        }
        return yStart;
    }

    private static int walkDownThroughFluid(ServerWorld world, int x, int yStart, int z, BlockPos.Mutable cursor) {
        cursor.set(x, yStart, z);
        int floor = Math.max(world.getBottomY(), yStart - 64);
        while (cursor.getY() > floor) {
            BlockState st = world.getBlockState(cursor);
            if (!st.isAir()
                    && !TerrainHelper.isWalkThroughBlock(st)
                    && st.getFluidState().isEmpty()) {
                return cursor.getY();
            }
            cursor.move(0, -1, 0);
        }
        return yStart;
    }

    private static long packRegion(int x, int z) {
        return (((long) x) & 0xFFFFFFFFL) | (((long) z) << 32);
    }
}
