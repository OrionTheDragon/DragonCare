package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listens to chunk loads and, the first time a village structure's "start" chunk
 * becomes available, places one ruined dragon forge schematic next to the village.
 *
 * <p>Detection is tag-based: we look for {@code #minecraft:village} which is the
 * conventional tag for villages used by vanilla AND modded village-like
 * structures. This means the placer fires for vanilla villages, Repurposed Structures,
 * Towns & Towers, etc., as long as they participate in the standard village tag.</p>
 *
 * <p>One forge per village (keyed by the village's start ChunkPos), persisted in
 * {@link VillageForgeState} so that re-loading the chunk later does not re-place.</p>
 *
 * <p>Placement is deferred to {@link ServerTickEvent.Post}. Doing the work
 * inside {@link ChunkEvent.Load} would re-enter the chunk-load pipeline via
 * {@code world.getChunk(...)} and deadlock the server (ticks freeze, world
 * save hangs). The chunk-load handler only enqueues a candidate; the tick
 * handler verifies surrounding chunks with a non-blocking query and then
 * places the schematic on the tick thread.</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class VillageForgePlacer {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/VillageForge");

    /** The conventional vanilla tag for villages — modded villages typically join it. */
    private static final TagKey<Structure> VILLAGE_TAG =
            TagKey.of(RegistryKeys.STRUCTURE, Identifier.of("minecraft", "village"));

    /** Distance from village center where the forge will land (random within the band). */
    private static final int MIN_OFFSET = 30;
    private static final int MAX_OFFSET = 95;
    /**
     * Maximum allowed Y-spread between the four footprint corners. Above this
     * we reject the candidate so the forge never straddles a cliff or sits
     * with half its floor in mid-air on a steep slope.
     */
    private static final int MAX_TERRAIN_VARIANCE = 5;
    /** Number of candidate positions tried before giving up on a village. */
    private static final int PLACE_ATTEMPTS = 24;

    /** Placements processed per server tick — schematic placement is heavy. */
    private static final int PLACEMENTS_PER_TICK = 1;
    /** Cap on retries when surrounding chunks are not yet loaded (~200 ticks ≈ 10s). */
    private static final int MAX_RETRIES = 200;

    /**
     * Chunks scanned for village starts per server tick — scanning is lightweight
     * (a non-blocking chunk fetch + a small map iteration), so this can be high
     * to keep up with the spawn-area load burst on first world entry without
     * letting {@link #PENDING_SCANS} grow into the thousands.
     */
    private static final int SCANS_PER_TICK = 64;
    /**
     * Cap on retries while a chunk has not yet reached {@link ChunkStatus#FULL}.
     * Generously sized because newly generated chunks can take a few ticks to
     * promote, especially under load.
     */
    private static final int MAX_SCAN_RETRIES = 400;

    /** Deferred placement candidates, drained on {@link ServerTickEvent.Post}. */
    private static final ConcurrentLinkedQueue<PendingForge> PENDING = new ConcurrentLinkedQueue<>();

    /**
     * Chunks awaiting inspection for village structure starts. NeoForge fires
     * {@link ChunkEvent.Load} before the chunk reaches {@link ChunkStatus#FULL}
     * (see the event's own Javadoc), and at that point {@code structureStarts}
     * may not yet be populated. We must defer the scan to a later tick when
     * the chunk is reliably {@code FULL}.
     */
    private static final ConcurrentLinkedQueue<PendingScan> PENDING_SCANS = new ConcurrentLinkedQueue<>();

    /**
     * In-memory set of chunk-long keys we have already enqueued for scanning
     * (or for which scanning has completed). Prevents the same chunk from
     * being re-queued endlessly while it sits in the scan queue waiting for FULL.
     */
    private static final Set<Long> SCAN_INFLIGHT = ConcurrentHashMap.newKeySet();

    /**
     * In-memory set of village start chunk longs that are currently queued
     * or being retried. Prevents the same village from being queued multiple
     * times when several of its chunks fire {@link ChunkEvent.Load} in close
     * succession. Cleared when a candidate is finalized (placed, rejected, or
     * gave up after MAX_RETRIES so a later reload can retry).
     */
    private static final Set<Long> INFLIGHT = ConcurrentHashMap.newKeySet();

    /**
     * @param bbMinX/MinZ/MaxX/MaxZ horizontal bounding box of the village
     *     start (expanded by a safety margin in {@link #attemptPlacement}).
     *     Used to reject candidate positions that would land on top of
     *     a village building's roof (where the WORLD_SURFACE_WG heightmap
     *     points at the roof rather than the ground).
     */
    private record PendingForge(ServerWorld world, ChunkPos villageStart, int centerX, int centerZ,
                                int bbMinX, int bbMinZ, int bbMaxX, int bbMaxZ,
                                long rngSeed, int retries) {}

    private record PendingScan(ServerWorld world, ChunkPos pos, int retries) {}

    // =================== DIAGNOSTIC COUNTERS ===================
    /** Fires every chunk load handled by this placer. */
    private static final AtomicLong DIAG_CHUNK_EVENTS = new AtomicLong();
    /** Chunks that successfully reached FULL and got scanned. */
    private static final AtomicLong DIAG_SCAN_OK = new AtomicLong();
    /** Scans where chunk was not FULL yet (these get retried). */
    private static final AtomicLong DIAG_SCAN_NOT_FULL = new AtomicLong();
    /** Scans where the chunk's structureStarts was empty (no structures here). */
    private static final AtomicLong DIAG_SCAN_NO_STRUCTURES = new AtomicLong();
    /** Total non-village structures seen in scanned chunks. */
    private static final AtomicLong DIAG_NON_VILLAGE = new AtomicLong();
    /** Total village structures detected. */
    private static final AtomicLong DIAG_VILLAGE_DETECTED = new AtomicLong();
    /** Villages skipped: already persisted as processed. */
    private static final AtomicLong DIAG_VILLAGE_SKIP_PROCESSED = new AtomicLong();
    /** Villages skipped: already in INFLIGHT (another chunk load already queued). */
    private static final AtomicLong DIAG_VILLAGE_SKIP_INFLIGHT = new AtomicLong();
    /** Villages successfully queued into PENDING. */
    private static final AtomicLong DIAG_VILLAGE_QUEUED = new AtomicLong();
    /** attemptPlacement invocations. */
    private static final AtomicLong DIAG_PLACE_ATTEMPTS = new AtomicLong();
    /** Placements that succeeded. */
    private static final AtomicLong DIAG_PLACE_OK = new AtomicLong();
    /** Placements that gave up because rollForge returned null. */
    private static final AtomicLong DIAG_PLACE_NO_SCHEM = new AtomicLong();
    /** Placements that gave up because every Y check failed (terrain issue). */
    private static final AtomicLong DIAG_PLACE_NO_VALID_Y = new AtomicLong();
    /** Placements rejected because terrain corners varied too much. */
    private static final AtomicLong DIAG_PLACE_BAD_VARIANCE = new AtomicLong();
    /** Placements that re-queued because chunks weren't ready. */
    private static final AtomicLong DIAG_PLACE_CHUNKS_PENDING = new AtomicLong();
    /** Placements abandoned after MAX_RETRIES. */
    private static final AtomicLong DIAG_PLACE_GAVE_UP = new AtomicLong();
    /** Tick counter — used to throttle periodic diagnostic dumps. */
    private static long diagTickCounter = 0;
    /** Last logged values, to suppress dumps when nothing changed. */
    private static long lastDumpChunkEvents = -1;
    private static long lastDumpVillagesDetected = -1;
    private static long lastDumpPlaceOk = -1;
    // ===========================================================

    private VillageForgePlacer() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerWorld world)) return;
        ChunkPos pos = event.getChunk().getPos();
        long n = DIAG_CHUNK_EVENTS.incrementAndGet();
        if (n == 1) {
            LOG.info("[DIAG] First chunk-load event received: world={} chunk={} isNewChunk={}",
                    world.getRegistryKey().getValue(), pos, event.isNewChunk());
        }
        // Defer structure-start inspection: ChunkEvent.Load can fire before the
        // chunk reaches ChunkStatus.FULL, when structureStarts may still be
        // empty. The tick handler will re-fetch the chunk through the chunk
        // manager (non-blocking, FULL-only) and inspect its starts then.
        if (SCAN_INFLIGHT.add(pos.toLong())) {
            PENDING_SCANS.add(new PendingScan(world, pos, 0));
        }
    }

    /**
     * Look at one queued chunk's structure starts. Returns true when the
     * chunk has been inspected (whether or not a village was found), false
     * if the chunk is not yet FULL and we should retry next tick.
     */
    private static boolean attemptScan(PendingScan scan) {
        ServerWorld world = scan.world;
        ChunkPos pos = scan.pos;

        // Non-blocking FULL check — if the chunk isn't promoted yet, retry later.
        Chunk fullChunk = world.getChunkManager().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
        if (fullChunk == null) {
            DIAG_SCAN_NOT_FULL.incrementAndGet();
            return false;
        }

        DIAG_SCAN_OK.incrementAndGet();

        Map<Structure, StructureStart> starts = fullChunk.getStructureStarts();
        if (starts.isEmpty()) {
            DIAG_SCAN_NO_STRUCTURES.incrementAndGet();
            return true;
        }

        Registry<Structure> registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
            Structure structure = entry.getKey();
            StructureStart start = entry.getValue();
            if (!start.hasChildren()) continue;
            if (!isVillage(registry, structure)) {
                long n = DIAG_NON_VILLAGE.incrementAndGet();
                if (n <= 5) {
                    Identifier id = registry.getId(structure);
                    LOG.debug("[DIAG] Non-village structure in chunk {}: {}", pos,
                            id != null ? id : "<unknown>");
                }
                continue;
            }

            long vd = DIAG_VILLAGE_DETECTED.incrementAndGet();
            Identifier id = registry.getId(structure);
            // Dedup by the village's actual start chunk — any of its chunks
            // loading first should be able to trigger placement. This is
            // important for fast-flying players who may load an edge chunk
            // of the village before the start chunk itself.
            ChunkPos startPos = start.getPos();
            if (vd <= 10) {
                LOG.debug("[DIAG] Village detected #{} type={} loadedChunk={} startChunk={}",
                        vd, id != null ? id : "<unknown>", pos, startPos);
            }

            VillageForgeState state = VillageForgeState.get(world);
            if (state.isProcessed(startPos)) {
                long sp = DIAG_VILLAGE_SKIP_PROCESSED.incrementAndGet();
                if (sp <= 5) LOG.debug("[DIAG] Skip (processed) startChunk={}", startPos);
                continue;
            }

            // Skip if already pending — another chunk load already queued this village.
            if (!INFLIGHT.add(startPos.toLong())) {
                long si = DIAG_VILLAGE_SKIP_INFLIGHT.incrementAndGet();
                if (si <= 5) LOG.debug("[DIAG] Skip (inflight) startChunk={}", startPos);
                continue;
            }

            // Deterministic per-village RNG so re-rolls (during testing) match.
            long baseSeed = world.getSeed() ^ ((long) startPos.x * 341873128712L + (long) startPos.z * 132897987541L);

            BlockBox bb = start.getBoundingBox();
            Vec3i center = bb.getCenter();
            PENDING.add(new PendingForge(world, startPos, center.getX(), center.getZ(),
                    bb.getMinX(), bb.getMinZ(), bb.getMaxX(), bb.getMaxZ(),
                    baseSeed, 0));
            long q = DIAG_VILLAGE_QUEUED.incrementAndGet();
            LOG.debug("[DIAG] Queued village forge #{} startChunk={} center=({},{}) bb={} pendingSize={}",
                    q, startPos, center.getX(), center.getZ(), bb, PENDING.size());
        }
        return true;
    }

    /**
     * Drain pending forge placements on the server tick thread, where heavy
     * work (heightmap, schematic placement, BE writes) is safe without
     * re-entering the chunk-load pipeline.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 1) Drain scan queue: inspect chunks that have reached FULL for village starts.
        for (int i = 0; i < SCANS_PER_TICK; i++) {
            PendingScan scan = PENDING_SCANS.poll();
            if (scan == null) break;
            if (attemptScan(scan)) {
                // Inspection complete — even if no village was found, we never re-scan
                // this chunk. SCAN_INFLIGHT stays set to avoid re-queueing on future
                // load events of the same chunk (in-memory only, reset on server restart
                // which is fine because we'd re-scan freshly).
            } else if (scan.retries < MAX_SCAN_RETRIES) {
                PENDING_SCANS.add(new PendingScan(scan.world, scan.pos, scan.retries + 1));
            } else {
                // Chunk never reached FULL within the retry window. Drop SCAN_INFLIGHT
                // so a later reload of this chunk can try again.
                SCAN_INFLIGHT.remove(scan.pos.toLong());
                LOG.warn("[DIAG] Scan gave up on chunk {} after {} retries (never reached FULL)",
                        scan.pos, MAX_SCAN_RETRIES);
            }
        }

        // 2) Drain placement queue: actually build the forge structures.
        for (int i = 0; i < PLACEMENTS_PER_TICK; i++) {
            PendingForge c = PENDING.poll();
            if (c == null) break;
            if (attemptPlacement(c)) {
                // Handled — either placed or definitively rejected (no schematic,
                // or every attempt failed Y check). Mark processed so future
                // chunk reloads of this village don't reprocess it.
                VillageForgeState.get(c.world).markProcessed(c.villageStart);
                INFLIGHT.remove(c.villageStart.toLong());
            } else if (c.retries < MAX_RETRIES) {
                PENDING.add(new PendingForge(c.world, c.villageStart, c.centerX, c.centerZ,
                        c.bbMinX, c.bbMinZ, c.bbMaxX, c.bbMaxZ,
                        c.rngSeed, c.retries + 1));
            } else {
                // Chunks never came ready — the player may have flown past.
                // Do NOT mark processed; remove from INFLIGHT so the next time
                // a chunk of this village loads we can re-queue and try again.
                INFLIGHT.remove(c.villageStart.toLong());
                DIAG_PLACE_GAVE_UP.incrementAndGet();
                LOG.warn("[DIAG] Placement gave up after {} retries: village start {} (chunks never loaded)",
                        MAX_RETRIES, c.villageStart);
            }
        }

        // 3) Periodic counter dump — once per second (20 ticks), only if anything changed.
        diagTickCounter++;
        if ((diagTickCounter % 20L) == 0L) {
            long ce = DIAG_CHUNK_EVENTS.get();
            long vd = DIAG_VILLAGE_DETECTED.get();
            long pok = DIAG_PLACE_OK.get();
            if (ce != lastDumpChunkEvents || vd != lastDumpVillagesDetected || pok != lastDumpPlaceOk) {
                lastDumpChunkEvents = ce;
                lastDumpVillagesDetected = vd;
                lastDumpPlaceOk = pok;
                LOG.info("[DIAG-STATS] chunkEvts={} scanOK={} scanRetry={} scanEmpty={} nonVillage={} " +
                                "villagesSeen={} skipProc={} skipInfl={} queued={} " +
                                "placeAttempts={} placeOK={} placeNoSchem={} placeNoY={} placeChunksPending={} placeGaveUp={} " +
                                "qScan={} qPlace={}",
                        ce, DIAG_SCAN_OK.get(), DIAG_SCAN_NOT_FULL.get(), DIAG_SCAN_NO_STRUCTURES.get(),
                        DIAG_NON_VILLAGE.get(), vd, DIAG_VILLAGE_SKIP_PROCESSED.get(),
                        DIAG_VILLAGE_SKIP_INFLIGHT.get(), DIAG_VILLAGE_QUEUED.get(),
                        DIAG_PLACE_ATTEMPTS.get(), pok, DIAG_PLACE_NO_SCHEM.get(),
                        DIAG_PLACE_NO_VALID_Y.get(), DIAG_PLACE_CHUNKS_PENDING.get(), DIAG_PLACE_GAVE_UP.get(),
                        PENDING_SCANS.size(), PENDING.size());
            }
        }
    }

    /**
     * @return true if the candidate is fully handled (placed or definitively rejected);
     *         false if surrounding chunks are not yet loaded and the candidate should be re-queued.
     */
    private static boolean attemptPlacement(PendingForge c) {
        DIAG_PLACE_ATTEMPTS.incrementAndGet();
        ServerWorld world = c.world;
        Random rng = new Random(c.rngSeed);
        ServerChunkManager scm = world.getChunkManager();

        ParsedSchematic schem = AddonSchematics.rollForge(rng);
        if (schem == null) {
            long n = DIAG_PLACE_NO_SCHEM.incrementAndGet();
            if (n <= 5) {
                LOG.warn("[DIAG] rollForge returned NULL for village start {} — schematics not loaded? " +
                        "BASIC={} DRAIN={} HEART={}", c.villageStart,
                        AddonSchematics.forgeCount(AddonSchematics.Category.BASIC),
                        AddonSchematics.forgeCount(AddonSchematics.Category.DRAIN),
                        AddonSchematics.forgeCount(AddonSchematics.Category.HEART));
            }
            return true;
        }

        int cx = c.centerX;
        int cz = c.centerZ;

        int rejectAnchorMissing = 0;
        int rejectBadY = 0;
        int rejectBboxMissing = 0;
        int rejectInsideVillage = 0;
        int rejectBadVariance = 0;
        int firstBadY = Integer.MIN_VALUE;

        // Inflated village bbox — a forge whose footprint (origin + schem.width/length)
        // overlaps this region likely lands on a roof and would float, so we skip it.
        // 6-block safety margin around the village's reported bounding box.
        final int villageMargin = 6;
        int vMinX = c.bbMinX - villageMargin;
        int vMinZ = c.bbMinZ - villageMargin;
        int vMaxX = c.bbMaxX + villageMargin;
        int vMaxZ = c.bbMaxZ + villageMargin;

        // Try a handful of candidate positions; pick the first that lands on solid ground
        // AND has its bounding-box chunks already loaded.
        boolean anyChunkPending = false;
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            int dist = MIN_OFFSET + rng.nextInt(MAX_OFFSET - MIN_OFFSET + 1);
            int x = cx + (int) Math.round(Math.cos(angle) * dist);
            int z = cz + (int) Math.round(Math.sin(angle) * dist);

            // Reject candidates whose footprint overlaps the village's own bbox.
            // The whole point is to land OUTSIDE houses so the WORLD_SURFACE_WG
            // heightmap gives us actual terrain, not a roof.
            int footMaxX = x + schem.width;
            int footMaxZ = z + schem.length;
            boolean overlaps = footMaxX >= vMinX && x <= vMaxX
                    && footMaxZ >= vMinZ && z <= vMaxZ;
            if (overlaps) {
                rejectInsideVillage++;
                continue;
            }

            // Anchor chunk must be loaded for heightmap to be meaningful.
            if (scm.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null) {
                anyChunkPending = true;
                rejectAnchorMissing++;
                continue;
            }

            // WORLD_SURFACE_WG includes tree canopies. Walk down through logs /
            // leaves / saplings / snow until we hit real ground — otherwise a
            // village in a forest would land the forge on top of a tree.
            int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
            BlockPos.Mutable cursor = new BlockPos.Mutable();
            int y = walkDownToGround(world, x, surfaceY, z, cursor);
            // Reject only true void chunks (surface at/below absolute world bottom) and ceiling clips.
            // A small fixed margin (e.g. bottomY+5) breaks superflat where the surface sits ~4 blocks above bedrock.
            if (y <= world.getBottomY() || y >= world.getTopY() - schem.height - 5) {
                if (firstBadY == Integer.MIN_VALUE) firstBadY = y;
                rejectBadY++;
                continue;
            }
            // Reject anchors that sit on water / lava — never place a forge on a fluid surface.
            cursor.set(x, y, z);
            if (!world.getFluidState(cursor).isEmpty()) {
                rejectBadVariance++;
                continue;
            }

            // Non-blocking check that every chunk the schematic touches is loaded.
            // Iterate by CHUNK coordinates — stepping block coords by 16 from an
            // unaligned origin can skip the chunk the far edge falls into.
            int rotW = schem.width;
            int rotL = schem.length;
            boolean allLoaded = true;
            int minChkX = x >> 4, maxChkX = (x + rotW) >> 4;
            int minChkZ = z >> 4, maxChkZ = (z + rotL) >> 4;
            for (int chkX = minChkX; chkX <= maxChkX && allLoaded; chkX++) {
                for (int chkZ = minChkZ; chkZ <= maxChkZ && allLoaded; chkZ++) {
                    if (scm.getChunk(chkX, chkZ, ChunkStatus.FULL, false) == null) {
                        allLoaded = false;
                    }
                }
            }
            if (!allLoaded) {
                anyChunkPending = true;
                rejectBboxMissing++;
                continue;
            }

            // Surface sampling: walk down at 9 footprint probes. Reject if the
            // ground levels differ by more than MAX_TERRAIN_VARIANCE, or if any
            // probe lands on water / lava (encoded as Integer.MIN_VALUE).
            int[] corners = sampleCorners(world, x, z, Math.max(rotW, rotL));
            if (corners != null) {
                int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                boolean hasFluid = false;
                for (int v : corners) {
                    if (v == Integer.MIN_VALUE) { hasFluid = true; break; }
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
                if (hasFluid || max - min > MAX_TERRAIN_VARIANCE) {
                    rejectBadVariance++;
                    DIAG_PLACE_BAD_VARIANCE.incrementAndGet();
                    continue;
                }
            }

            BlockPos origin = new BlockPos(x, y, z);
            net.minecraft.util.BlockRotation rotation = net.minecraft.util.BlockRotation.random(
                    net.minecraft.util.math.random.Random.create(rng.nextLong()));
            schem.placeInWorld(world, origin, rotation, rng);
            // Foundation fill — pour dirt under the entire footprint to cover any
            // residual sub-block dip that the variance check tolerated.
            fillFoundationUnder(world, origin, schem.rotatedWidth(rotation), schem.rotatedLength(rotation), schem.height);
            DIAG_PLACE_OK.incrementAndGet();
            LOG.info("[DIAG] PLACED ruined dragon forge at {} dims={}x{}x{} (village start {}, retries={})",
                    origin, schem.width, schem.height, schem.length, c.villageStart, c.retries);
            return true;
        }

        // No attempt succeeded. If some attempts were blocked purely on chunk loading,
        // re-queue so they can be retried once chunks come in. Otherwise give up.
        if (anyChunkPending) {
            long n = DIAG_PLACE_CHUNKS_PENDING.incrementAndGet();
            if (n <= 5 || c.retries % 50 == 0) {
                LOG.debug("[DIAG] Defer placement for {} (retry {}): rejectAnchor={} rejectBadY={} rejectBbox={} rejectInsideVillage={} rejectVariance={} firstBadY={}",
                        c.villageStart, c.retries, rejectAnchorMissing, rejectBadY, rejectBboxMissing, rejectInsideVillage, rejectBadVariance, firstBadY);
            }
            return false;
        }
        long n = DIAG_PLACE_NO_VALID_Y.incrementAndGet();
        if (n <= 10) {
            LOG.warn("[DIAG] No valid Y for forge near village {} after {} attempts: " +
                            "world bottomY={} topY={} schemH={} firstBadY={} rejectInsideVillage={} rejectVariance={} cx={} cz={}",
                    c.villageStart, PLACE_ATTEMPTS, world.getBottomY(), world.getTopY(), schem.height, firstBadY, rejectInsideVillage, rejectBadVariance, cx, cz);
        }
        return true;
    }

    /**
     * Scan downward from {@code yStart} past tree/grass/snow blocks until the
     * first solid non-tree block. Bounded by a 64-block scan depth so void
     * columns don't drag the loop to world bottom.
     */
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

    /**
     * Sample the walked-down ground Y at 9 strategic points across a square
     * footprint: 4 corners + 4 edge midpoints + 1 center. Catches cliff edges
     * that cut through the middle of the footprint — a 4-corner sample would
     * miss those when the corners happen to share a plateau.
     * Returns null if any sample falls in a chunk that is not yet fully loaded.
     */
    private static int[] sampleCorners(ServerWorld world, int x, int z, int size) {
        int mid = size / 2;
        int hi  = size - 1;
        // 9 points: 4 corners, 4 edge midpoints, 1 center.
        int[] dx = {0, hi, 0,  hi, mid, 0,   hi,  mid, mid};
        int[] dz = {0, 0,  hi, hi, 0,   mid, mid, hi,  mid};
        int[] out = new int[dx.length];
        ServerChunkManager scm = world.getChunkManager();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int i = 0; i < dx.length; i++) {
            int px = x + dx[i];
            int pz = z + dz[i];
            if (scm.getChunk(px >> 4, pz >> 4, ChunkStatus.FULL, false) == null) return null;
            int top = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, px, pz) - 1;
            int gy = walkDownToGround(world, px, top, pz, cursor);
            // Encode "this probe landed on water/lava" as Integer.MIN_VALUE so the
            // caller can reject the candidate.
            cursor.set(px, gy, pz);
            out[i] = world.getFluidState(cursor).isEmpty() ? gy : Integer.MIN_VALUE;
        }
        return out;
    }

    /**
     * Determine the appropriate foundation block based on the biome.
     */
    private static BlockState getBiomeFoundation(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        if (biomeEntry.getKey().isPresent()) {
            String path = biomeEntry.getKey().get().getValue().getPath().toLowerCase();
            if (path.contains("desert")) return Blocks.SAND.getDefaultState();
            if (path.contains("badlands") || path.contains("mesa")) return Blocks.RED_SAND.getDefaultState();
            if (path.contains("snow") || path.contains("ice") || path.contains("frozen") || path.contains("glacier")) return Blocks.SNOW_BLOCK.getDefaultState();
        }
        return Blocks.DIRT.getDefaultState();
    }

    /**
     * Pour foundation straight down under every column of the rotated footprint
     * starting at {@code origin.y - 1}, until each column meets a solid
     * non-tree block. Bounded depth keeps this cheap on cliff edges.
     * Also replaces dirt/grass blocks within the schematic's height to match the biome.
     */
    private static void fillFoundationUnder(ServerWorld world, BlockPos origin, int rotW, int rotL, int schemHeight) {
        BlockState fill = getBiomeFoundation(world, origin);
        boolean replaceSchemBlocks = !fill.isOf(Blocks.DIRT);

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int floor = Math.max(world.getBottomY(), origin.getY() - 64);
        for (int dx = 0; dx < rotW; dx++) {
            for (int dz = 0; dz < rotL; dz++) {
                if (replaceSchemBlocks) {
                    for (int dy = 0; dy < schemHeight; dy++) {
                        cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        BlockState st = world.getBlockState(cursor);
                        if (st.isOf(Blocks.DIRT) || st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.DIRT_PATH) || 
                            st.isOf(Blocks.PODZOL) || st.isOf(Blocks.COARSE_DIRT) || st.isOf(Blocks.ROOTED_DIRT)) {
                            world.setBlockState(cursor, fill, 50);
                        }
                    }
                }

                cursor.set(origin.getX() + dx, origin.getY() - 1, origin.getZ() + dz);
                while (cursor.getY() > floor) {
                    BlockState st = world.getBlockState(cursor);
                    if (!st.isAir() && !TerrainHelper.isWalkThroughBlock(st)) break;
                    world.setBlockState(cursor, fill, 50);
                    cursor.move(0, -1, 0);
                }
            }
        }
    }

    private static boolean isVillage(Registry<Structure> registry, Structure structure) {
        return registry.getEntryList(VILLAGE_TAG)
                .map(list -> {
                    for (RegistryEntry<Structure> e : list) {
                        if (e.value() == structure) return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    public static void clearCache() { SCAN_INFLIGHT.clear(); INFLIGHT.clear(); }
}
