package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraft.block.Block;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Static registry of all schematics shipped with the addon.
 *
 * <p>Schematics live in {@code /data/dragoncare/schematics/*.schem} inside the
 * mod jar and are decoded once at server start via {@link #loadAll}. Each schematic
 * is filed into a category bucket ({@link Category}) so the village-forge placer
 * can roll a tier (basic / drain / heart) and pick a random variant of any element.</p>
 *
 * <p>This class is intentionally agnostic of NeoForge events: the caller drives the
 * lifecycle (load on server start, clear on server stop).</p>
 */
public final class AddonSchematics {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/Schematics");

    /** Resource directory inside the mod jar. */
    private static final String RES_PREFIX = "/data/" + DragonCare.MOD_ID + "/schematics/";

    public enum Category { BASIC, DRAIN, HEART }

    /** Every ruined-forge file name we ship, grouped by category. */
    private static final Map<Category, List<String>> FORGE_FILES = buildForgeFileTable();

    /** Dragon-hunter camp file names, keyed by wood type. */
    private static final Map<String, String> HUNTER_FILES = buildHunterFileTable();

    public static final String GUILD_FILE = "Dragon_hunters_guild";

    /** Concurrent so parallel loaders can populate it from worker threads. */
    private static final Map<String, ParsedSchematic> LOADED = new ConcurrentHashMap<>();

    private AddonSchematics() {}

    /**
     * Decode every shipped schematic. Safe to call repeatedly: subsequent calls
     * are no-ops once a non-empty schematic set has been loaded.
     *
     * <p>Parsing runs on the common {@link ForkJoinPool} — each .schem is GZIP
     * + NBT, ~50-300 ms apiece on hot CPU, and with ~18 files the wall-clock
     * cost of sequential loading dominated server startup. The work is fully
     * independent (each call to {@link #tryLoad} only touches its own input
     * stream and the supplied registry lookup), and the registry wrapper is
     * read-only once dynamic registries are frozen, so this is safe.</p>
     */
    public static synchronized void loadAll(DynamicRegistryManager registryManager) {
        if (!LOADED.isEmpty()) return;

        Registry<Block> blockRegistry = registryManager.get(RegistryKeys.BLOCK);
        RegistryWrapper<Block> blockLookup = blockRegistry.getReadOnlyWrapper();

        // Gather every file name we need to parse into one flat list.
        List<String> allNames = new ArrayList<>();
        for (List<String> bucket : FORGE_FILES.values()) allNames.addAll(bucket);
        allNames.addAll(HUNTER_FILES.values());
        allNames.add(GUILD_FILE);

        long t0 = System.currentTimeMillis();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        // parallelStream uses the common ForkJoinPool. tryLoad already writes
        // into a ConcurrentHashMap and only reads from the immutable
        // blockLookup, so no extra synchronisation is needed.
        allNames.parallelStream().forEach(name -> {
            if (tryLoad(name, blockLookup)) ok.incrementAndGet();
            else fail.incrementAndGet();
        });

        long ms = System.currentTimeMillis() - t0;
        LOG.info("Loaded {} addon schematics ({} failed) in {} ms (parallel across {} workers)",
                ok.get(), fail.get(), ms, ForkJoinPool.commonPool().getParallelism());
    }

    /** Dragon-hunter camp for a given wood type (or null if not loaded). */
    public static ParsedSchematic dragonHunterFor(String wood) {
        String fileName = HUNTER_FILES.get(wood);
        if (fileName == null) return null;
        return LOADED.get(fileName);
    }

    /** Access a loaded schematic by its file name key. */
    public static ParsedSchematic getSchematic(String name) {
        return LOADED.get(name);
    }

    /** All wood types we ship hunter camps for. */
    public static java.util.Set<String> dragonHunterWoodTypes() {
        return HUNTER_FILES.keySet();
    }

    /** Diagnostic: how many forge schematics are LOADED (i.e. parsed successfully) for a category. */
    public static int forgeCount(Category category) {
        List<String> names = FORGE_FILES.getOrDefault(category, Collections.emptyList());
        int n = 0;
        for (String name : names) if (LOADED.containsKey(name)) n++;
        return n;
    }

    public static synchronized void clear() { LOADED.clear(); }

    private static boolean tryLoad(String name, RegistryWrapper<Block> blockLookup) {
        String resourcePath = RES_PREFIX + name + ".schem";
        try (InputStream in = AddonSchematics.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOG.warn("Schematic resource not found: {}", resourcePath);
                return false;
            }
            ParsedSchematic parsed = SpongeSchemReader.read(in, blockLookup);
            LOADED.put(name, parsed);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to load schematic {}: {}", resourcePath, e.toString());
            return false;
        }
    }

    /** Random schematic from a category, or {@code null} if the bucket is empty. */
    public static ParsedSchematic randomForge(Category category, Random random) {
        List<String> names = FORGE_FILES.getOrDefault(category, Collections.emptyList());
        if (names.isEmpty()) return null;
        List<ParsedSchematic> available = new ArrayList<>(names.size());
        for (String name : names) {
            ParsedSchematic p = LOADED.get(name);
            if (p != null) available.add(p);
        }
        if (available.isEmpty()) return null;
        return available.get(random.nextInt(available.size()));
    }

    /**
     * Roll a forge variant:
     * <ul>
     *   <li>2% — heart variant (heart-only + drain+heart pool merged for rarity)</li>
     *   <li>8% — drain-only variant</li>
     *   <li>otherwise — basic (no drain, no heart)</li>
     * </ul>
     */
    public static ParsedSchematic rollForge(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.02) {
            ParsedSchematic heart = randomForge(Category.HEART, random);
            if (heart != null) return heart;
        }
        if (roll < 0.10) {
            ParsedSchematic drain = randomForge(Category.DRAIN, random);
            if (drain != null) return drain;
        }
        return randomForge(Category.BASIC, random);
    }

    private static Map<Category, List<String>> buildForgeFileTable() {
        Map<Category, List<String>> m = new HashMap<>();
        String[] elements = {"fire", "ice", "lightning"};

        List<String> basic = new ArrayList<>();
        List<String> drain = new ArrayList<>();
        List<String> heart = new ArrayList<>();
        for (String elem : elements) {
            // basic: 3 variants per element
            basic.add("Ruined_dragon_forge_" + elem + "0");
            basic.add("Ruined_dragon_forge_" + elem + "1");
            basic.add("Ruined_dragon_forge_" + elem + "2");
            // drain-only: 2 variants per element
            drain.add("Ruined_dragon_forge_" + elem + "_drain0");
            drain.add("Ruined_dragon_forge_" + elem + "_drain1");
            // heart-only: 1 variant per element
            heart.add("Ruined_dragon_forge_" + elem + "_heart0");
            // drain+heart pooled into the rare "heart" bucket: 2 variants per element
            heart.add("Ruined_dragon_forge_" + elem + "_drain_heart0");
            heart.add("Ruined_dragon_forge_" + elem + "_drain_heart1");
        }
        m.put(Category.BASIC, basic);
        m.put(Category.DRAIN, drain);
        m.put(Category.HEART, heart);
        return m;
    }

    private static Map<String, String> buildHunterFileTable() {
        Map<String, String> m = new HashMap<>();
        m.put("birch",    "dragon_hunter_birch0");
        m.put("oak",      "dragon_hunter_oak0");
        m.put("spruce",   "dragon_hunter_spruce0");
        m.put("dark_oak", "dragon_hunter_dark_oak0");
        m.put("jungle",   "dragon_hunter_jungle0");
        m.put("acacia",   "dragon_hunter_acacia0");
        return m;
    }
}
