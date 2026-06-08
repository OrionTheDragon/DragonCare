package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for the WorldEdit / Sponge Schematic file format (versions 2 and 3).
 *
 * <p>The file is a gzipped NBT. v2 stores Palette / BlockData / BlockEntities at the
 * root of the Schematic compound; v3 wraps them in a "Blocks" sub-compound. Both
 * use the same palette format (block state strings indexed by varint).</p>
 *
 * <p>Block state strings are parsed via {@link BlockArgumentParser} against the
 * block lookup obtained from a {@link RegistryWrapper.WrapperLookup}. Unknown
 * blocks are silently replaced with air.</p>
 */
public final class SpongeSchemReader {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/SchemReader");

    private SpongeSchemReader() {}

    public static ParsedSchematic read(InputStream rawStream, RegistryWrapper<Block> blockLookup) throws IOException {
        // Sponge .schem is a gzipped NBT file with a named root compound.
        // NbtIo.readCompressed handles the gzip wrapper and returns the root compound directly.
        NbtCompound root = NbtIo.readCompressed(rawStream, NbtSizeTracker.ofUnlimitedBytes());

        // Root is { Schematic: {...} } in v3 (and most WE exports for v2 too).
        boolean hasSchematicKey = root.contains("Schematic", NbtElement.COMPOUND_TYPE);
        NbtCompound schem = hasSchematicKey ? root.getCompound("Schematic") : root;

        LOG.debug("[DIAG-PARSE] rootKeys={} hasSchematicKey={} schemKeys={}",
                root.getKeys(), hasSchematicKey, schem.getKeys());

        int version = schem.contains("Version") ? schem.getInt("Version") : 2;

        int width  = schem.getShort("Width") & 0xFFFF;
        int height = schem.getShort("Height") & 0xFFFF;
        int length = schem.getShort("Length") & 0xFFFF;

        LOG.debug("[DIAG-PARSE] version={} W={} H={} L={} containsWidth={} widthTagType={}",
                version, width, height, length,
                schem.contains("Width"),
                // try multiple type IDs: 2=Short, 3=Int
                (schem.contains("Width", NbtElement.SHORT_TYPE) ? "Short"
                        : schem.contains("Width", NbtElement.INT_TYPE) ? "Int"
                        : "OTHER"));

        NbtCompound paletteHolder;
        byte[] blockData;
        NbtList beList;
        NbtList entList;

        if (version >= 3) {
            NbtCompound blocks = schem.getCompound("Blocks");
            paletteHolder = blocks.getCompound("Palette");
            blockData = blocks.getByteArray("Data");
            beList = blocks.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
            entList = schem.getList("Entities", NbtElement.COMPOUND_TYPE);
        } else {
            paletteHolder = schem.getCompound("Palette");
            blockData = schem.getByteArray("BlockData");
            beList = schem.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
            entList = schem.getList("Entities", NbtElement.COMPOUND_TYPE);
        }

        // Build palette: indices may be sparse — find max.
        int paletteSize = 0;
        for (String key : paletteHolder.getKeys()) {
            int id = paletteHolder.getInt(key);
            if (id + 1 > paletteSize) paletteSize = id + 1;
        }
        BlockState[] palette = new BlockState[paletteSize];
        for (String key : paletteHolder.getKeys()) {
            int id = paletteHolder.getInt(key);
            palette[id] = parseBlockState(key, blockLookup);
        }

        // Decode varint stream into a flat index array of length width*height*length.
        int volume = width * height * length;
        int[] data = decodeVarints(blockData, volume);

        // Block entities.
        List<ParsedSchematic.BlockEntityEntry> entities = new ArrayList<>(beList.size());
        for (int i = 0; i < beList.size(); i++) {
            NbtCompound entry = beList.getCompound(i);
            int[] pos = entry.getIntArray("Pos");
            if (pos.length != 3) continue;
            NbtCompound beData;
            if (entry.contains("Data", NbtElement.COMPOUND_TYPE)) {
                // v3: nested Data compound holds the actual BE NBT.
                beData = entry.getCompound("Data").copy();
            } else {
                // v2: entry itself is the BE NBT (with Id/Pos at the same level).
                beData = entry.copy();
                beData.remove("Pos");
            }
            // Normalise to vanilla NBT key "id" (lowercase).
            if (entry.contains("Id")) {
                beData.putString("id", entry.getString("Id"));
            }
            entities.add(new ParsedSchematic.BlockEntityEntry(new BlockPos(pos[0], pos[1], pos[2]), beData));
        }

        // Entities (Item frames, dragon skulls, etc).
        List<ParsedSchematic.EntityEntry> entityEntries = new ArrayList<>(entList.size());
        for (int i = 0; i < entList.size(); i++) {
            NbtCompound entry = entList.getCompound(i);
            NbtList posList = entry.getList("Pos", NbtElement.DOUBLE_TYPE);
            if (posList.size() != 3) continue;

            double ex = posList.getDouble(0);
            double ey = posList.getDouble(1);
            double ez = posList.getDouble(2);

            NbtCompound entData;
            if (entry.contains("Data", NbtElement.COMPOUND_TYPE)) {
                // v3: actual entity NBT is inside the "Data" sub-compound.
                entData = entry.getCompound("Data").copy();
            } else {
                // v2: entity NBT is at the root level alongside Id/Pos.
                entData = entry.copy();
                entData.remove("Pos");
            }

            // Normalise Id to lowercase "id" for vanilla EntityType.loadEntityWithPassengers
            if (entry.contains("Id")) {
                entData.putString("id", entry.getString("Id"));
            }

            LOG.debug("[DIAG-ENTITY] Parsed entity #{}: id={} keys={}", i,
                    entData.contains("id") ? entData.getString("id") : "?",
                    entData.getKeys());

            entityEntries.add(new ParsedSchematic.EntityEntry(ex, ey, ez, entData));
        }

        LOG.debug("[DIAG-PARSE] DONE W={} H={} L={} palette={} dataLen={} blockEntities={} entities={}",
                width, height, length, paletteSize, blockData.length, entities.size(), entityEntries.size());

        return new ParsedSchematic(width, height, length, data, palette, entities, entityEntries);
    }

    /** Decode a sequence of unsigned LEB128 varints into a fixed-size int array. */
    private static int[] decodeVarints(byte[] data, int expected) {
        int[] out = new int[expected];
        int pos = 0;
        int idx = 0;
        while (pos < data.length && idx < expected) {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                if (pos >= data.length) {
                    LOG.warn("Truncated varint stream at byte {} (expected {} entries, got {})", pos, expected, idx);
                    return out;
                }
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            out[idx++] = value;
        }
        if (idx < expected) {
            LOG.warn("Schematic BlockData under-filled: {} of {} entries decoded", idx, expected);
        }
        return out;
    }

    /** Parse a block state string like {@code "minecraft:oak_stairs[facing=north,half=top]"}. */
    private static BlockState parseBlockState(String text, RegistryWrapper<Block> blockLookup) {
        try {
            BlockArgumentParser.BlockResult result = BlockArgumentParser.block(blockLookup, text, false);
            return result.blockState();
        } catch (CommandSyntaxException e) {
            LOG.warn("Unknown block state '{}', replacing with air", text);
            return Blocks.AIR.getDefaultState();
        }
    }
}
