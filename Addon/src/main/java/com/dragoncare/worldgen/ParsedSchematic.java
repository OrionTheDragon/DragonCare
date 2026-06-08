package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * Parsed Sponge .schem v2/v3 schematic ready for placement.
 *
 * <p>Stores the palette of resolved {@link BlockState}s, a flat W*H*L index array
 * pointing into that palette, and a list of block-entity NBT payloads keyed by
 * their relative position. Placement walks the volume and writes blocks via
 * {@link ServerWorld#setBlockState}, then injects block entity NBT for any
 * matching positions. Rotation around the Y-axis is supported (0/90/180/270).</p>
 */
public final class ParsedSchematic {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/Schematic");

    /** Block flags for {@link ServerWorld#setBlockState}: notify clients + skip block updates. */
    private static final int PLACE_FLAGS = Block.NOTIFY_LISTENERS | Block.NO_REDRAW | Block.FORCE_STATE;

    public final int width;
    public final int height;
    public final int length;
    /** Linearised palette indices in (y,z,x) order to match Sponge .schem layout. */
    public final int[] data;
    /** Resolved block states, indexed by palette id. */
    public final BlockState[] palette;
    public final List<BlockEntityEntry> blockEntities;
    public final List<EntityEntry> entities;

    public ParsedSchematic(int width, int height, int length,
                           int[] data, BlockState[] palette,
                           List<BlockEntityEntry> blockEntities,
                           List<EntityEntry> entities) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.data = data;
        this.palette = palette;
        this.blockEntities = blockEntities;
        this.entities = entities;
    }

    /** A single block-entity entry recovered from the schematic. */
    public record BlockEntityEntry(BlockPos pos, NbtCompound data) {}

    /** A single entity entry (item frames, etc) recovered from the schematic. */
    public record EntityEntry(double x, double y, double z, NbtCompound data) {}

    /** Schematic index lookup: linear offset for (x, y, z) in Sponge layout. */
    public int indexAt(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    /** Block state at (x, y, z), or {@link Blocks#AIR} when index is missing. */
    public BlockState stateAt(int x, int y, int z) {
        int paletteId = data[indexAt(x, y, z)];
        if (paletteId < 0 || paletteId >= palette.length) return Blocks.AIR.getDefaultState();
        BlockState st = palette[paletteId];
        return st != null ? st : Blocks.AIR.getDefaultState();
    }

    private byte getNumericByte(net.minecraft.nbt.NbtCompound tag, String key) {
        if (!tag.contains(key)) return 0;
        net.minecraft.nbt.NbtElement elem = tag.get(key);
        if (elem instanceof net.minecraft.nbt.AbstractNbtNumber num) {
            return num.byteValue();
        }
        return 0;
    }

    /**
     * Place the schematic into the world with the origin at {@code origin}.
     * Air blocks in the schematic are skipped (we do not erase existing terrain).
     *
     * @param world      target world
     * @param origin     world position of the (0, 0, 0) schematic corner after rotation
     * @param rotation   Y-axis rotation
     * @param random     RNG for processors / random states (unused for now, kept for API parity)
     * @return total world-aligned bounding box of placed blocks, or {@code null} if nothing was placed
     */
    public BlockBox placeInWorld(ServerWorld world, BlockPos origin, BlockRotation rotation, Random random) {
        return placeInWorld(world, origin, rotation, random, null);
    }

    /**
     * Same as {@link #placeInWorld(ServerWorld, BlockPos, BlockRotation, Random)}, but
     * runs {@code processor} on each block-entity NBT before it's applied — useful for
     * substituting placeholder signs / chest loot tables / etc. at generation time.
     */
    public BlockBox placeInWorld(ServerWorld world, BlockPos origin, BlockRotation rotation,
                                 Random random, MarkerProcessor processor) {
        long t0 = System.nanoTime();
        LOG.debug("[PLACE-BEGIN] origin={} rotation={} schemSize=W{} H{} L{} paletteSize={} blockEntities={} entities={} processor={} dim={}",
                origin, rotation, width, height, length, palette.length,
                blockEntities.size(), entities.size(),
                processor != null ? processor.getClass().getSimpleName() : "null",
                world.getRegistryKey().getValue());

        BlockMirror mirror = BlockMirror.NONE;
        int placed = 0;
        int airSkipped = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        BlockPos.Mutable worldPos = new BlockPos.Mutable();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockState state = stateAt(x, y, z);
                    if (state.isAir()) { airSkipped++; continue; }

                    BlockState rotated = state.rotate(rotation).mirror(mirror);
                    rotateLocal(worldPos, x, y, z, rotation);
                    worldPos.move(origin.getX(), origin.getY(), origin.getZ());

                    boolean ok = world.setBlockState(worldPos, rotated, PLACE_FLAGS);
                    if (!ok) {
                        LOG.warn("[PLACE-BLOCK-FAIL] setBlockState=false at {} block={} (chunkLoaded={})",
                                worldPos, rotated.getBlock().getName().getString(),
                                world.getChunkManager().isChunkLoaded(worldPos.getX() >> 4, worldPos.getZ() >> 4));
                    }
                    placed++;

                    if (worldPos.getX() < minX) minX = worldPos.getX();
                    if (worldPos.getY() < minY) minY = worldPos.getY();
                    if (worldPos.getZ() < minZ) minZ = worldPos.getZ();
                    if (worldPos.getX() > maxX) maxX = worldPos.getX();
                    if (worldPos.getY() > maxY) maxY = worldPos.getY();
                    if (worldPos.getZ() > maxZ) maxZ = worldPos.getZ();
                }
            }
        }

        long tBlocksDone = System.nanoTime();
        BlockBox bbox = (placed == 0) ? null : BlockBox.create(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        LOG.debug("[PLACE-BLOCKS-DONE] placed={} airSkipped={} bbox={} blockTimeMs={}",
                placed, airSkipped, bbox, (tBlocksDone - t0) / 1_000_000);

        // Apply block entity NBT after all blocks are placed
        int beApplied = 0, beMissing = 0, beReadFail = 0, beProcReplaced = 0;
        for (int beIdx = 0; beIdx < blockEntities.size(); beIdx++) {
            BlockEntityEntry be = blockEntities.get(beIdx);
            BlockPos rel = be.pos();
            rotateLocal(worldPos, rel.getX(), rel.getY(), rel.getZ(), rotation);
            worldPos.move(origin.getX(), origin.getY(), origin.getZ());
            String beIdInNbt = be.data().contains("id") ? be.data().getString("id") : "?";
            BlockEntity entity = world.getBlockEntity(worldPos);
            if (entity == null) {
                BlockState atPos = world.getBlockState(worldPos);
                LOG.warn("[BE-MISSING] idx={} relPos={} worldPos={} nbtId={} actualBlock={} (no BlockEntity at target — block isn't a tile-entity-bearing block)",
                        beIdx, rel, worldPos, beIdInNbt, atPos.getBlock().getName().getString());
                beMissing++;
                continue;
            }
            NbtCompound copy = be.data().copy();
            copy.putInt("x", worldPos.getX());
            copy.putInt("y", worldPos.getY());
            copy.putInt("z", worldPos.getZ());
            if (processor != null) {
                try {
                    NbtCompound replaced = processor.process(world, copy, worldPos, random);
                    if (replaced != null) {
                        copy = replaced;
                        beProcReplaced++;
                    }
                } catch (Throwable t) {
                    LOG.warn("[BE-PROC-FAIL] idx={} worldPos={} nbtId={} err={}", beIdx, worldPos, beIdInNbt, t.toString());
                }
            }
            try {
                entity.read(copy, world.getRegistryManager());
                beApplied++;
                LOG.debug("[BE-APPLY] idx={} worldPos={} type={} nbtId={} keys={}",
                        beIdx, worldPos, entity.getType(), beIdInNbt, copy.getKeys());
            } catch (Throwable t) {
                LOG.warn("[BE-READ-FAIL] idx={} worldPos={} nbtId={} type={} err={}",
                        beIdx, worldPos, beIdInNbt, entity.getType(), t.toString());
                beReadFail++;
            }
        }
        long tBeDone = System.nanoTime();
        LOG.debug("[PLACE-BE-DONE] beTotal={} applied={} missing={} readFail={} procReplaced={} beTimeMs={}",
                blockEntities.size(), beApplied, beMissing, beReadFail, beProcReplaced, (tBeDone - tBlocksDone) / 1_000_000);

        // Spawn entities
        int entitiesSpawned = 0;
        for (EntityEntry entry : entities) {
            NbtCompound tag = entry.data().copy();

            // === DIAGNOSTIC: dump SOURCE NBT so we can see exactly what schema we're dealing with ===
            String srcId = tag.contains("id") ? tag.getString("id") : "?";
            String srcTile = (tag.contains("TileX") && tag.contains("TileY") && tag.contains("TileZ"))
                    ? "(" + tag.getInt("TileX") + "," + tag.getInt("TileY") + "," + tag.getInt("TileZ") + ")"
                    : "none";
            String srcFacing = tag.contains("Facing") ? ("Facing=" + getNumericByte(tag, "Facing"))
                    : tag.contains("facing") ? ("facing=" + getNumericByte(tag, "facing"))
                    : "none";
            LOG.debug("[FRAME-SRC] id={} schemPos=({},{},{}) Tile={} {} schemSize=W{} H{} L{} rotation={} origin={}",
                    srcId, entry.x(), entry.y(), entry.z(),
                    srcTile, srcFacing,
                    width, height, length, rotation, origin);

            // Rotate local coordinates.
            // IMPORTANT: entities use continuous double coords [0.0 .. size],
            // unlike block indices [0 .. size-1]. Use width/length, NOT width-1/length-1.
            double lx = entry.x();
            double ly = entry.y();
            double lz = entry.z();
            double rx, rz;
            
            switch (rotation) {
                case CLOCKWISE_90 -> {
                    rx = length - lz;
                    rz = lx;
                }
                case CLOCKWISE_180 -> {
                    rx = width - lx;
                    rz = length - lz;
                }
                case COUNTERCLOCKWISE_90 -> {
                    rx = lz;
                    rz = width - lx;
                }
                default -> {
                    rx = lx;
                    rz = lz;
                }
            }
            
            double worldX = origin.getX() + rx;
            double worldY = origin.getY() + ly;
            double worldZ = origin.getZ() + rz;
            
            // Rotate orientation for item frames / paintings.
            //
            // 1.21 uses "facing" (lowercase) on AbstractDecorationEntity. Older
            // schematics may still ship "Facing" (capital). Read whichever exists,
            // rotate it, write BOTH back so vanilla reader picks whichever it expects.
            // Default = NORTH(2) if the entity has no facing at all (mob/etc.).
            byte origFacing = -1;
            if (tag.contains("Facing")) origFacing = getNumericByte(tag, "Facing");
            else if (tag.contains("facing")) origFacing = getNumericByte(tag, "facing");

            byte rotatedFacing;
            if (origFacing >= 0) {
                rotatedFacing = (byte) rotateFacing(origFacing, rotation);
                if (tag.contains("Facing")) tag.putByte("Facing", rotatedFacing);
                if (tag.contains("facing")) tag.putByte("facing", rotatedFacing);
            } else {
                rotatedFacing = 2; // unused for non-hanging entities, but keep formula safe
            }

            // Adjust ItemRotation for floor/ceiling frames (4 rotation steps per 90° turn).
            if (origFacing == 0 || origFacing == 1) {
                if (tag.contains("ItemRotation")) {
                    byte itemRot = getNumericByte(tag, "ItemRotation");
                    int rotSteps = switch (rotation) {
                        case CLOCKWISE_90 -> 2;
                        case CLOCKWISE_180 -> 4;
                        case COUNTERCLOCKWISE_90 -> 6;
                        default -> 0;
                    };
                    tag.putByte("ItemRotation", (byte) ((itemRot + rotSteps) % 8));
                }
            }

            // Rotate entity yaw (stored in Rotation list: [yaw, pitch])
            if (tag.contains("Rotation", NbtElement.LIST_TYPE)) {
                net.minecraft.nbt.NbtList rotList = tag.getList("Rotation", NbtElement.FLOAT_TYPE);
                if (rotList.size() >= 2) {
                    float yaw = rotList.getFloat(0);
                    float pitch = rotList.getFloat(1);
                    yaw = (yaw + rotationDegrees(rotation)) % 360.0f;
                    net.minecraft.nbt.NbtList newRot = new net.minecraft.nbt.NbtList();
                    newRot.add(net.minecraft.nbt.NbtFloat.of(yaw));
                    newRot.add(net.minecraft.nbt.NbtFloat.of(pitch));
                    tag.put("Rotation", newRot);
                }
            }

            if (tag.contains("DragonYaw")) {
                float dyaw = tag.getFloat("DragonYaw");
                tag.putFloat("DragonYaw", (dyaw + rotationDegrees(rotation)) % 360.0f);
            }

            // Clean schematic wrapper tags
            tag.remove("Id");
            tag.remove("UUID");

            // ===== Block-attached entities (Item Frames / Paintings / Glow Item Frames) =====
            //
            // Vanilla 1.21 AbstractDecorationEntity:
            //   attachedBlockPos = TileX/Y/Z (read from NBT)
            //   Pos              = attachedBlockPos + 0.5 - facing.offset * 0.46875
            //   readCustomDataFromNbt() validates |Pos - TileXYZ| < 16; on failure
            //   the entity is silently rejected and never spawned.
            //
            // We MUST therefore always write BOTH a consistent TileX/Y/Z and a Pos
            // that sits within ~0.5 of it. Two cases for source NBT:
            //
            //  (A) NBT contains TileX/Y/Z (vanilla-style): trust them as local
            //      schem coords, rotate discretely like block positions.
            //  (B) NBT has only Pos/facing (minimal payload): derive TileX/Y/Z
            //      from rotated continuous Pos via floor(Pos + facing*0.46875).
            //
            // BOTH branches always write final TileX/Y/Z + pixel-perfect Pos.
            if (origFacing >= 0) {
                double stepX = 0, stepY = 0, stepZ = 0;
                switch (rotatedFacing) {
                    case 0 -> stepY = -1;
                    case 1 -> stepY = 1;
                    case 2 -> stepZ = -1;
                    case 3 -> stepZ = 1;
                    case 4 -> stepX = -1;
                    case 5 -> stepX = 1;
                }

                // ── How is the hanging entity's position stored? ──
                //
                // CRITICAL: some exporters — incl. WorldEdit for item frames —
                // write the Sponge-level entity "Pos" as the INTEGER tile cell
                // (floor of the real entity Pos), NOT the continuous position.
                // A genuine continuous item-frame Pos always carries a
                // fractional part on EVERY axis (0.5 / 0.03125 / 0.96875), so
                // an all-integer Pos unambiguously means "tile coordinates".
                //
                // Why this matters: a tile cell is a BLOCK index and must be
                // rotated with the block formula ((dim-1) - c); a continuous
                // coord rotates with (dim - c). Rotating a tile cell with the
                // continuous formula shifts every frame in a rotated structure
                // by exactly one block — the "frames displaced in rotated
                // guilds" bug (rotation NONE was unaffected, which is why
                // command-spawned guilds looked fine).
                boolean spongePosIsTile =
                        entry.x() == Math.floor(entry.x())
                     && entry.y() == Math.floor(entry.y())
                     && entry.z() == Math.floor(entry.z());

                // Stale-absolute-coords guard for the NBT TileX/Y/Z path:
                // accept NBT TileXYZ as local schem coords only when inside
                // [-1 .. dim] (1-block slack for edge-face frames).
                boolean rawHasTileXYZ = tag.contains("TileX") && tag.contains("TileY") && tag.contains("TileZ");
                int rawTx = rawHasTileXYZ ? tag.getInt("TileX") : 0;
                int rawTy = rawHasTileXYZ ? tag.getInt("TileY") : 0;
                int rawTz = rawHasTileXYZ ? tag.getInt("TileZ") : 0;
                boolean tileLooksLocal = rawHasTileXYZ
                        && rawTx >= -1 && rawTx <= width
                        && rawTy >= -1 && rawTy <= height
                        && rawTz >= -1 && rawTz <= length;

                int worldTx, worldTy, worldTz;
                String rotMode;

                if (spongePosIsTile) {
                    // (T) Sponge "Pos" IS the local tile cell — rotate it as a
                    //     block index. Correct path for WorldEdit item-frame
                    //     exports (and the fix for the displacement bug).
                    int[] rot = rotateTileLocal((int) entry.x(), (int) entry.z(), rotation);
                    worldTx = origin.getX() + rot[0];
                    worldTy = origin.getY() + (int) entry.y();
                    worldTz = origin.getZ() + rot[1];
                    rotMode = "SpongePos-tile";
                } else if (tileLooksLocal) {
                    // (A) NBT TileX/Y/Z are local schem coords; rotate discretely.
                    int[] rot = rotateTileLocal(rawTx, rawTz, rotation);
                    worldTx = origin.getX() + rot[0];
                    worldTy = origin.getY() + rawTy;
                    worldTz = origin.getZ() + rot[1];
                    rotMode = "TileXYZ-local";
                } else {
                    // (B) Continuous-Pos fallback: TileXYZ = floor(rotated Pos).
                    worldTx = (int) Math.floor(worldX);
                    worldTy = (int) Math.floor(worldY);
                    worldTz = (int) Math.floor(worldZ);
                    rotMode = rawHasTileXYZ ? "TileXYZ-rejected-Pos-fallback" : "Pos-fallback";
                }

                tag.putInt("TileX", worldTx);
                tag.putInt("TileY", worldTy);
                tag.putInt("TileZ", worldTz);

                // Pixel-perfect Pos from final TileXYZ + facing.
                worldX = worldTx + 0.5 - stepX * 0.46875;
                worldY = worldTy + 0.5 - stepY * 0.46875;
                worldZ = worldTz + 0.5 - stepZ * 0.46875;

                LOG.debug("[FRAME-ROT] mode={} spongePos=({},{},{}) rawTile=({},{},{}) origFacing={} rotFacing={} -> Tile({},{},{}) Pos({},{},{})",
                        rotMode, entry.x(), entry.y(), entry.z(),
                        rawTx, rawTy, rawTz,
                        origFacing, rotatedFacing,
                        worldTx, worldTy, worldTz, worldX, worldY, worldZ);
            }

            // Note: keeping frames attached to slabs/stairs/leaves/etc. is handled
            // at runtime by FrameKeeperEvents (cancels the tick in which vanilla
            // would discard a decoration that fails canStayAttached()), without
            // touching the original wall block and without breaking player
            // interaction (Fixed=true would).

            final double finalWorldX = worldX;
            final double finalWorldY = worldY;
            final double finalWorldZ = worldZ;

            // Update Pos tag so readNbt puts the entity at the correct coords 
            // BEFORE readCustomDataFromNbt validates TileX/Y/Z.
            net.minecraft.nbt.NbtList posList = new net.minecraft.nbt.NbtList();
            posList.add(net.minecraft.nbt.NbtDouble.of(finalWorldX));
            posList.add(net.minecraft.nbt.NbtDouble.of(finalWorldY));
            posList.add(net.minecraft.nbt.NbtDouble.of(finalWorldZ));
            tag.put("Pos", posList);

            String entityId = tag.contains("id") ? tag.getString("id") : "unknown";

            // ===== PRE-SPAWN WALL CHECK + ANCHOR =====
            //
            // Vanilla validates a block-attached entity against the block one
            // step OPPOSITE its facing — the wall BEHIND the frame — NOT the
            // frame's own cell (TileXYZ, which is naturally air). Earlier
            // diagnostics checked TileXYZ itself and so always reported
            // "wallBlock=air", which was misleading.
            //
            // attachCell  = TileXYZ                       (frame's own cell)
            // realWallPos = TileXYZ - facing.offset       (block vanilla checks)
            //
            // If the real wall is empty, vanilla's tick() discards the frame
            // within ~100 ticks. Norse Hall anchors a lot of invisible display
            // frames on air on purpose, so we drop an invisible STRUCTURE_VOID
            // marker behind those frames — invisible AND collision-free, so it
            // never gets in the player's way. structure_void is NOT solid, so
            // it does not by itself satisfy canStayAttached(); keeping those
            // frames alive is FrameKeeperEvents' job (it cancels the tick in
            // which vanilla would discard them). Frames on a real (solid) wall,
            // or on a present-but-non-solid block (slab/anvil/leaves), are left
            // untouched — FrameKeeperEvents covers the non-solid case too.
            if (origFacing >= 0 && tag.contains("TileX") && tag.contains("TileY") && tag.contains("TileZ")) {
                int tx = tag.getInt("TileX"), ty = tag.getInt("TileY"), tz = tag.getInt("TileZ");
                BlockPos attachCell = new BlockPos(tx, ty, tz);

                int foX = 0, foY = 0, foZ = 0;
                switch (rotatedFacing) {
                    case 0 -> foY = -1; // down
                    case 1 -> foY = 1;  // up
                    case 2 -> foZ = -1; // north
                    case 3 -> foZ = 1;  // south
                    case 4 -> foX = -1; // west
                    case 5 -> foX = 1;  // east
                }
                BlockPos realWallPos = new BlockPos(tx - foX, ty - foY, tz - foZ);
                BlockState wallState = world.getBlockState(realWallPos);
                boolean wallSolid = !wallState.isAir() && wallState.isSolidBlock(world, realWallPos);

                if (wallState.isAir()) {
                    // Invisible, collision-free marker. Does NOT make
                    // canStayAttached() true (structure_void isn't solid) —
                    // FrameKeeperEvents keeps the frame alive at runtime.
                    world.setBlockState(realWallPos, Blocks.STRUCTURE_VOID.getDefaultState(), PLACE_FLAGS);
                    wallState = world.getBlockState(realWallPos);
                    LOG.debug("[FRAME-ANCHOR] placed STRUCTURE_VOID marker at {} for '{}' attachCell={} facing={} (kept alive by FrameKeeperEvents)",
                            realWallPos, entityId, attachCell, rotatedFacing);
                }

                LOG.debug("[FRAME-WALLCHECK] '{}' attachCell={} facing={} realWallPos={} wallBlock={} wallSolid={} chunkLoaded={}",
                        entityId, attachCell, rotatedFacing, realWallPos,
                        wallState.getBlock().getName().getString(), wallSolid,
                        world.getChunkManager().isChunkLoaded(realWallPos.getX() >> 4, realWallPos.getZ() >> 4));
            }

            LOG.debug("[ENTITY-SPAWN] Attempting '{}' Pos=({},{},{}) Tile=({},{},{}) finalKeys={}",
                    entityId, finalWorldX, finalWorldY, finalWorldZ,
                    tag.contains("TileX") ? tag.getInt("TileX") : "-",
                    tag.contains("TileY") ? tag.getInt("TileY") : "-",
                    tag.contains("TileZ") ? tag.getInt("TileZ") : "-",
                    tag.getKeys());

            final byte capturedOrigFacing = origFacing;
            try {
                var result = net.minecraft.entity.EntityType.loadEntityWithPassengers(tag, world, e -> {
                    if (!(e instanceof net.minecraft.entity.decoration.AbstractDecorationEntity)) {
                        e.refreshPositionAndAngles(finalWorldX, finalWorldY, finalWorldZ, e.getYaw(), e.getPitch());
                    }
                    boolean spawned = world.spawnEntity(e);
                    java.util.UUID uuid = e.getUuid();
                    LOG.debug("[ENTITY-SPAWN] '{}' type={} spawned={} uuid={} FINAL Pos=({},{},{}) blockPos={} alive={} removed={} removalReason={}",
                            entityId, e.getType().getUntranslatedName(), spawned, uuid,
                            e.getX(), e.getY(), e.getZ(), e.getBlockPos(),
                            e.isAlive(), e.isRemoved(),
                            e.getRemovalReason() == null ? "n/a" : e.getRemovalReason().toString());

                    // For decoration entities (frames/paintings): immediately re-fetch from
                    // the world's entity manager and check whether the spawn actually stuck.
                    // If the entity was rejected by vanilla validation (canStayAttached, etc),
                    // getEntity will return null even though spawnEntity returned true.
                    if (spawned && e instanceof net.minecraft.entity.decoration.AbstractDecorationEntity dec) {
                        net.minecraft.entity.Entity fetched = world.getEntity(uuid);
                        LOG.debug("[FRAME-POSTSPAWN] '{}' uuid={} fetchedFromWorld={} dec.alive={} dec.removed={} attachedPos={} facing={}",
                                entityId, uuid, fetched != null,
                                dec.isAlive(), dec.isRemoved(),
                                dec.getAttachedBlockPos(),
                                dec.getHorizontalFacing());
                    }

                    // Structure-specific post-spawn customisation (e.g. the guild
                    // randomises armour-stand equipment). No-op for a null processor.
                    if (spawned && processor != null) {
                        try {
                            processor.processEntity(world, e, random);
                        } catch (Throwable t) {
                            LOG.warn("[ENTITY-PROCESS-FAIL] '{}': {}", entityId, t.toString());
                        }
                    }
                    return e;
                });
                if (result != null) {
                    entitiesSpawned++;
                } else {
                    LOG.warn("[ENTITY-SPAWN] loadEntityWithPassengers returned null for '{}' origFacing={} — id not found in registry OR vanilla validation failed (TileXYZ vs Pos distance check, canStayAttached, etc).",
                            entityId, capturedOrigFacing);
                }
            } catch (Throwable t) {
                LOG.warn("[ENTITY-SPAWN-EXCEPTION] '{}' at ({},{},{}): {}", entityId, worldX, worldY, worldZ, t.toString(), t);
            }
        }

        long tEntDone = System.nanoTime();
        LOG.debug("[PLACE-ENTITIES-DONE] entSpawned={}/{} entTimeMs={}",
                entitiesSpawned, entities.size(), (tEntDone - tBeDone) / 1_000_000);

        if (placed == 0) {
            LOG.warn("[PLACE-END] origin={} returned NULL — placed=0 blocks (schematic empty OR all-air OR all setBlockState calls failed)", origin);
            return null;
        }
        LOG.debug("[PLACE-END] origin={} totalTimeMs={} bbox={} placedBlocks={} beApplied={} entSpawned={}/{}",
                origin, (tEntDone - t0) / 1_000_000, bbox, placed, beApplied, entitiesSpawned, entities.size());
        return bbox;
    }

    /** Rotate (x, y, z) inside the schematic bounds around the Y-axis. */
    private void rotateLocal(BlockPos.Mutable out, int x, int y, int z, BlockRotation rotation) {
        switch (rotation) {
            case NONE                -> out.set(x, y, z);
            case CLOCKWISE_90        -> out.set((length - 1) - z, y, x);
            case CLOCKWISE_180       -> out.set((width - 1) - x, y, (length - 1) - z);
            case COUNTERCLOCKWISE_90 -> out.set(z, y, (width - 1) - x);
        }
    }

    /**
     * Rotate a LOCAL block/tile cell (lx, lz) around the Y axis. Returns
     * {@code {rotatedX, rotatedZ}}. Uses the discrete BLOCK formula
     * ({@code (dim-1) - c}) — must NOT be used for continuous coordinates,
     * which rotate with {@code dim - c} instead.
     */
    private int[] rotateTileLocal(int lx, int lz, BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90        -> new int[]{(length - 1) - lz, lx};
            case CLOCKWISE_180       -> new int[]{(width - 1) - lx, (length - 1) - lz};
            case COUNTERCLOCKWISE_90 -> new int[]{lz, (width - 1) - lx};
            default                  -> new int[]{lx, lz};
        };
    }

    /** World-aligned size after rotation around Y. */
    public int rotatedWidth(BlockRotation rotation) {
        return (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) ? length : width;
    }

    public int rotatedLength(BlockRotation rotation) {
        return (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) ? width : length;
    }

    private int rotateFacing(int facing, BlockRotation rotation) {
        if (facing < 2) return facing; // Up/Down stay same
        return switch (rotation) {
            case CLOCKWISE_90 -> switch (facing) {
                case 2 -> 5; // N -> E
                case 5 -> 3; // E -> S
                case 3 -> 4; // S -> W
                case 4 -> 2; // W -> N
                default -> facing;
            };
            case CLOCKWISE_180 -> switch (facing) {
                case 2 -> 3; // N -> S
                case 3 -> 2; // S -> N
                case 4 -> 5; // W -> E
                case 5 -> 4; // E -> W
                default -> facing;
            };
            case COUNTERCLOCKWISE_90 -> switch (facing) {
                case 2 -> 4; // N -> W
                case 4 -> 3; // W -> S
                case 3 -> 5; // S -> E
                case 5 -> 2; // E -> N
                default -> facing;
            };
            default -> facing;
        };
    }

    private static float rotationDegrees(BlockRotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 90f;
            case CLOCKWISE_180 -> 180f;
            case COUNTERCLOCKWISE_90 -> 270f;
            default -> 0f;
        };
    }

}
