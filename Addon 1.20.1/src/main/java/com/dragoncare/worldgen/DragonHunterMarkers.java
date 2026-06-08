package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces dragon-hunter placeholder signs with localised text components.
 *
 * <h2>Supported markers</h2>
 * <ul>
 *   <li>{@code [d_young_stock<N>]} — wyrmling-sale advertisement (4 price + 2 sold-out layouts)</li>
 *   <li>{@code [d_meal_heart<N>]} — dragon-flesh/heart stall (4 price + 2 sold-out layouts)</li>
 *   <li>{@code [cost_dragon<N>]} — companion sign to {@code d_young_stock}; reuses the per-camp colour</li>
 *   <li>{@code [d_hunter]} — "dragon hunter" wall label</li>
 * </ul>
 *
 * <p>The dragon colour is rolled once per camp (per {@code DragonHunterMarkers}
 * instance) and shared by every {@code d_young_stock} and {@code cost_dragon}
 * sign inside the same structure, so adjacent advert signs always describe the
 * same dragon.</p>
 *
 * <h2>Dragon colour and price</h2>
 * <p>A random colour is picked from a fixed pool of 12 dragon scale colours,
 * each abbreviated to a 2-letter Russian code in the sign text. Prices are
 * banded by rarity:</p>
 * <ul>
 *   <li>Common (red/grey/emerald-green): 150–200</li>
 *   <li>Coloured (blue/white/black/electric): 350–500</li>
 *   <li>Noble metallic / gem (copper/silver/amethyst/sapphire/bronze): 750–999</li>
 * </ul>
 *
 * <h2>Localisation</h2>
 * <p>Lines are emitted as {@code Text.translatable} components. Translation
 * files in {@code assets/dragoncare/lang/} cover Russian ({@code ru_ru},
 * {@code uk_ua}) and English ({@code en_us}). Other locales fall back to English
 * via vanilla's standard mechanism.</p>
 */
public final class DragonHunterMarkers implements MarkerProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/HunterMarkers");

    private static final String LANG_NS = DragonCare.MOD_ID;

    /** Single regex captures every supported marker. Trailing digits are optional —
     *  the suffix index is ignored by the generator (kept only for legacy schematics
     *  that still encode it). */
    private static final Pattern MARKER = Pattern.compile(
            "\\[(d_hunter|d_young_stock\\d*|d_meal_heart\\d*|cost_dragon\\d*)]");

    /** 1% chance (within the 8% sold-out roll) for the dark
     *  "ВСЕ ДРАКОНЧИКИ УТИЛИЗИРОВАННЫ…" variant. */
    private static final double DARK_SOLD_OUT_CHANCE = 0.01;

    /** Probability that a stock sign rolls a sold-out layout instead of an ad layout. */
    private static final double SOLD_OUT_CHANCE = 0.08;

    /** Layout indices used for the 4 advert variants on {@code d_young_stock}. */
    private static final int[] AD_LAYOUTS = {0, 1, 3, 5};

    /** {@code d_meal_heart} in-stock layouts (rolled independently, not paired). */
    private static final int[] MEAL_PRICE_LAYOUTS = {0, 2, 4, 5};
    /** {@code d_meal_heart} sold-out layouts (rolled independently, not paired). */
    private static final int[] MEAL_SOLD_OUT_LAYOUTS = {1, 3};

    /** {@code cost_dragon} layouts that display the dragon colour as a full word
     *  (matches the user spec: "пишет его полным словом"). The age-based layout 3
     *  is intentionally excluded. */
    private static final int[] COST_COLOUR_LAYOUTS = {0, 1, 5};

    // Boundary-free distance limit to pair a young_stock and cost_dragon sign
    private static final int MAX_PAIRING_DISTANCE = 4;

    /** Dragon colour catalogue. Each colour has its own translation key. */
    private enum DragonColor {
        RED   ("red",      PriceTier.COMMON),
        EMER  ("emerald",  PriceTier.COMMON),
        GREY  ("grey",     PriceTier.COMMON),
        BLUE  ("blue",     PriceTier.COLOURED),
        WHITE ("white",    PriceTier.COLOURED),
        ELEC  ("electric", PriceTier.COLOURED),
        BLACK ("black",    PriceTier.COLOURED),
        COPPER("copper",   PriceTier.NOBLE),
        SILVER("silver",   PriceTier.NOBLE),
        AMETH ("amethyst", PriceTier.NOBLE),
        SAPPH ("sapphire", PriceTier.NOBLE),
        BRONZE("bronze",   PriceTier.NOBLE);

        final String translationKey; // resolved via "dragoncare.color.<key>"
        final PriceTier tier;
        DragonColor(String key, PriceTier tier) { this.translationKey = key; this.tier = tier; }
    }

    private enum PriceTier {
        COMMON  (150, 200),
        COLOURED(350, 500),
        NOBLE   (750, 999);
        final int min, max;
        PriceTier(int min, int max) { this.min = min; this.max = max; }
        int roll(Random r) { return min + r.nextInt(max - min + 1); }
    }

    /** Marker for an argument that should serialise as a nested {@code translate} component. */
    private record TranslatableArg(String key) {}

    /** Loot table assigned to the FIRST chest inside a dragon-hunter camp. Contains the lore-book pool. */
    private static final String LOOT_TABLE_ID = DragonCare.MOD_ID + ":chest/dragon_hunter";
    /** Loot table for subsequent chests — identical to {@link #LOOT_TABLE_ID} minus the lore book. */
    private static final String LOOT_TABLE_ID_NO_BOOK = DragonCare.MOD_ID + ":chest/dragon_hunter_no_book";
    /**
     * Loot table for the guild's special "Dragon_Heart" chest (a double chest
     * with both halves carrying the marker {@code CustomName}). Only the first
     * half to be processed receives the table; the second half is wiped, so
     * the combined contents come from a single roll and match the spec totals.
     */
    private static final String LOOT_TABLE_ID_DRAGON_HEART = DragonCare.MOD_ID + ":chest/dragon_heart";

    /**
     * Loot table for the Guild's barrels and its second (unnamed) double chest.
     * Mixes Ice & Fire trophies (sea-serpent scales, cyclops eye, gorgon head,
     * dragon blood/flesh, dragonsteel, deathworm tongue, amphithere feather,
     * hippogryph talon, wither shard, blindfold, charred blocks, burnt torches)
     * with vanilla junk (rotten flesh, sticks, string, etc.).
     */
    private static final String LOOT_TABLE_ID_GUILD_JUNK = DragonCare.MOD_ID + ":chest/guild_junk";

    /**
     * {@code true} when this marker processor was created for the
     * {@code DragonHunterGuildPlacer}, enabling the guild-specific
     * barrel-and-junk-chest behaviour.
     */
    private final boolean isGuild;

    public DragonHunterMarkers() { this(false); }

    public DragonHunterMarkers(boolean isGuild) { this.isGuild = isGuild; }

    /**
     * Per-structure flag. Each placed camp creates a fresh {@code DragonHunterMarkers}
     * instance, so this stays scoped to one structure and is set when the first chest
     * is processed.
     */
    private boolean bookChestAssigned;

    /**
     * Per-structure flag for the Dragon_Heart double chest. Set when the first
     * half of the double chest is given the loot table; the second half then
     * gets emptied so totals stay within the configured pool ranges.
     */
    private boolean dragonHeartChestAssigned;

    /**
     * Per-structure flag for the Guild's second (unnamed) double chest. Only
     * the first half rolls {@link #LOOT_TABLE_ID_GUILD_JUNK}; subsequent halves
     * are emptied so a double chest does not double the listed amounts.
     */
    private boolean guildJunkChestAssigned;

    /**
     * Tracks the number of generated bounty sheets of each type (0-3) in this structure.
     * Prevents spawning too many of the exact same contract.
     */
    private final int[] paperCounts = new int[4];

    /**
     * Per-structure shared dragon colour. Lazy-initialised on first sign that
     * needs it (either {@code d_young_stock} or {@code cost_dragon}) so all
     * adverts in the same camp describe the same dragon.
     */
    private DragonColor sharedColor;

    /**
     * Per-camp seed for deterministic per-cell outcomes. Lazy-initialised on the
     * first call to {@link #ensureCampSeed(Random)} from whichever sign is
     * processed first.
     */
    private Long campSeed;

    /**
     * Cache of rolled outcomes per sign position. Both the {@code d_young_stock}
     * and its paired {@code cost_dragon} look up nearby positions and share
     * the same outcome, regardless of which is processed first.
     */
    private final Map<BlockPos, CellOutcome> rolledOutcomes = new HashMap<>();

    /**
     * Deterministic rolled state shared by an adjacent {@code d_young_stock} +
     * {@code cost_dragon} pair (and any other markers that happen to fall in
     * the same spatial cell).
     */
    private record CellOutcome(boolean soldOut, boolean dark, int adLayoutIdx,
                                int costLayoutIdx, int price, long seed) {}

    @Override
    public NbtCompound process(ServerWorld world, NbtCompound data, BlockPos worldPos, Random random) {
        if (!data.contains("id")) return data;
        String beId = data.getString("id");

        // Chests inside the camp: attach the dragon-hunter loot table.
        // Works automatically with Lootr — it intercepts vanilla LootTable on first open.
        if (beId.equals("minecraft:chest") || beId.equals("minecraft:trapped_chest")
                || beId.endsWith(":chest") || beId.endsWith("_chest")) {
            
            // Special handling for the Guild's heart chest. The schematic stores
            // two adjacent chest halves with CustomName="Dragon_Heart"; each half
            // is its own block entity. To keep the combined contents within the
            // configured pool ranges, only the first half receives the loot table;
            // the second half is wiped to a plain empty chest.
            if (data.contains("CustomName", 8)) { // 8 = String
                String name = data.getString("CustomName");
                if (name.contains("Dragon_Heart")) {
                    if (!dragonHeartChestAssigned) {
                        data.putString("LootTable", LOOT_TABLE_ID_DRAGON_HEART + "_1");
                        data.putLong("LootTableSeed", random.nextLong());
                        dragonHeartChestAssigned = true;
                    } else {
                        data.putString("LootTable", LOOT_TABLE_ID_DRAGON_HEART + "_2");
                        data.putLong("LootTableSeed", random.nextLong());
                    }
                    // Drop any baked-in items so the table (or emptiness) is the sole source.
                    if (data.contains("Items")) data.remove("Items");
                    // Tag this position so the open-event listener can identify it later.
                    DragonHunterState.get(world).markHunterChest(worldPos);
                    return data;
                }
            }

            // Guild: the second (unnamed) chest is also a double chest. To avoid
            // doubling the listed amounts, only the first half rolls the junk
            // table and the second half is emptied.
            if (isGuild) {
                if (!guildJunkChestAssigned) {
                    data.putString("LootTable", LOOT_TABLE_ID_GUILD_JUNK + "_1");
                    data.putLong("LootTableSeed", random.nextLong());
                    guildJunkChestAssigned = true;
                } else {
                    data.putString("LootTable", LOOT_TABLE_ID_GUILD_JUNK + "_2");
                    data.putLong("LootTableSeed", random.nextLong());
                }
                if (data.contains("Items")) data.remove("Items");
                DragonHunterState.get(world).markHunterChest(worldPos);
                return data;
            }

            // Camp: exactly one chest per structure carries the lore-book pool. The first
            String table;
            if (!bookChestAssigned) {
                table = LOOT_TABLE_ID;
                bookChestAssigned = true;
            } else {
                table = LOOT_TABLE_ID_NO_BOOK;
            }
            data.putString("LootTable", table);
            data.putLong("LootTableSeed", random.nextLong());
            // Drop any baked-in items so the table is the sole source.
            if (data.contains("Items")) data.remove("Items");
            // Tag this position so the open-event listener can identify it later.
            DragonHunterState.get(world).markHunterChest(worldPos);
            return data;
        }

        // Guild barrels share the same junk loot table as the second chest.
        // Each barrel rolls independently and is tagged for the listener.
        if (isGuild && beId.equals("minecraft:barrel")) {
            data.putString("LootTable", LOOT_TABLE_ID_GUILD_JUNK);
            data.putLong("LootTableSeed", random.nextLong());
            if (data.contains("Items")) data.remove("Items");
            DragonHunterState.get(world).markHunterChest(worldPos);
            return data;
        }

        if (!beId.endsWith("sign") && !beId.endsWith("hanging_sign")
                && !beId.endsWith("wall_sign") && !beId.endsWith("wall_hanging_sign")) {
            return data;
        }

        // Detect which side of the sign holds the marker: front_text or back_text.
        // Text must be written ONLY to that side — the other side stays untouched.
        String markerText;
        boolean markerOnFront;
        String frontText = readSideMessage(data, "front_text");
        String backText  = readSideMessage(data, "back_text");
        if (frontText != null && MARKER.matcher(frontText).find()) {
            markerText   = frontText;
            markerOnFront = true;
        } else if (backText != null && MARKER.matcher(backText).find()) {
            markerText   = backText;
            markerOnFront = false;
        } else {
            return data; // no marker on either side
        }
        Matcher m = MARKER.matcher(markerText);
        m.find(); // already confirmed above

        String inner = m.group(1);
        String[] lines;
        boolean soldOut = false;
        if (inner.equals("d_hunter")) {
            lines = buildHunterLines();
        } else if (inner.startsWith("d_young_stock")) {
            // Cell-paired roll: the adjacent cost_dragon (same spatial cell) will
            // pick the SAME outcome out of this map and stay consistent.
            CellOutcome outcome = outcomeFor(worldPos, random);
            if (outcome.soldOut) {
                soldOut = true;
                String prefix = outcome.dark ? "sold_out_dark" : "sold_out";
                lines = new String[]{
                        translatable(key(prefix + ".line1"), null),
                        translatable(key(prefix + ".line2"), null),
                        translatable(key(prefix + ".line3"), null),
                        translatable(key(prefix + ".line4"), null)
                };
                if (outcome.dark) {
                    try {
                        spawnDragonSkull(world, worldPos, new Random(outcome.seed));
                    } catch (Throwable t) {
                        LOG.debug("Skull spawn failed at {}: {}", worldPos, t.toString());
                    }
                }
            } else {
                lines = buildAdLines(outcome.adLayoutIdx, ensureSharedColor(random), outcome.price);
            }
        } else if (inner.startsWith("d_meal_heart")) {
            // d_meal_heart is independent (not paired with anything) — keep the
            // per-sign random roll, ignoring the suffix index.
            Random local = new Random(worldPos.hashCode() ^ random.nextLong());
            int idx;
            if (local.nextDouble() < SOLD_OUT_CHANCE) {
                idx = MEAL_SOLD_OUT_LAYOUTS[local.nextInt(MEAL_SOLD_OUT_LAYOUTS.length)];
                soldOut = true;
            } else {
                idx = MEAL_PRICE_LAYOUTS[local.nextInt(MEAL_PRICE_LAYOUTS.length)];
            }
            lines = buildMealHeartLines(idx);
        } else if (inner.startsWith("cost_dragon")) {
            // Look up the cell outcome that the paired d_young_stock either
            // already produced or will produce. Same cell == same outcome.
            CellOutcome outcome = outcomeFor(worldPos, random);
            if (outcome.soldOut) {
                // Paired d_young_stock is sold out (normal or dark) — emit a
                // blank cost_dragon so the stall reads consistently.
                lines = new String[]{"\"\"", "\"\"", "\"\"", "\"\""};
            } else {
                lines = buildCostColourLines(outcome.costLayoutIdx, ensureSharedColor(random));
            }
        } else {
            return data;
        }

        // Write to the correct side only; clear the opposite side.
        writeSignLines(data, lines, markerOnFront, soldOut);
        return data;
    }

    // ===================== Guild armour-stand randomisation =====================

    /**
     * Post-spawn entity hook. In the GUILD only, gives every armour stand a
     * randomised, mismatched set of armour (see {@link #randomizeArmorStand}).
     * Camps are deliberately left untouched.
     */
    @Override
    public void processEntity(ServerWorld world, Entity entity, Random random) {
        if (!isGuild) return;
        if (entity instanceof ArmorStandEntity stand) {
            randomizeArmorStand(world, stand, random);
        } else if (entity instanceof net.minecraft.entity.decoration.ItemFrameEntity frame) {
            if (!frame.isInvisible()) {
                randomizeItemFrame(world, frame, random);
            } else {
                randomizeInvisibleItemFrame(world, frame, random);
            }
        }
    }

    private void randomizeInvisibleItemFrame(ServerWorld world, net.minecraft.entity.decoration.ItemFrameEntity frame, Random rng) {
        if (rng.nextDouble() < 0.25) {
            frame.setHeldItemStack(ItemStack.EMPTY, false);
            return;
        }

        int paperType;
        int attempts = 0;
        do {
            paperType = rng.nextInt(4);
            attempts++;
        } while (paperCounts[paperType] >= 2 && attempts < 15);

        if (paperCounts[paperType] >= 2) {
            // All types exhausted or we got very unlucky, leave frame empty.
            frame.setHeldItemStack(ItemStack.EMPTY, false);
            return;
        }
        
        paperCounts[paperType]++;

        ItemStack stack = new ItemStack(com.dragoncare.item.ModItems.BURNT_SHEET.get());
        
        // Set the custom data for the GUI content
        NbtCompound customData = new NbtCompound();
        customData.putString("translate", "book.dragoncare.bounty." + paperType);
        stack.setNbt(customData);
        
        // Set the custom name to match the bounty title
        stack.setCustomName(net.minecraft.text.Text.translatable("book.dragoncare.bounty.title." + paperType).formatted(net.minecraft.util.Formatting.GOLD));
        
        frame.setHeldItemStack(stack, false);
    }

    private static final Item[] JUNK_ITEMS = {
            Items.TORCH, Items.BONE, Items.BONE_MEAL, Items.ROTTEN_FLESH, Items.STICK, Items.FLINT
    };

    private static final String[] DRAGONSTEEL_SWORDS = {
            "dragonsteel_fire_sword",
            "dragonsteel_ice_sword",
            "dragonsteel_lightning_sword"
    };

    private static final List<net.minecraft.enchantment.Enchantment> SWORD_ENCHANTS = List.of(
            Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.FIRE_ASPECT,
            Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.SWEEPING, Enchantments.UNBREAKING);

    private static void randomizeItemFrame(ServerWorld world, net.minecraft.entity.decoration.ItemFrameEntity frame, Random rng) {
        double r = rng.nextDouble();
        ItemStack stack = ItemStack.EMPTY;

        if (r < 0.30) {
            stack = new ItemStack(JUNK_ITEMS[rng.nextInt(JUNK_ITEMS.length)]);
        } else if (r < 0.65) {
            stack = new ItemStack(Items.STONE_SWORD);
        } else if (r < 0.85) {
            stack = new ItemStack(Items.IRON_SWORD);
        } else if (r < 0.95) {
            stack = new ItemStack(Items.DIAMOND_SWORD);
        } else if (r < 0.99) {
            stack = new ItemStack(Items.NETHERITE_SWORD);
        } else {
            String path = DRAGONSTEEL_SWORDS[rng.nextInt(DRAGONSTEEL_SWORDS.length)];
            Item dragonsteelItem = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("iceandfire", path));
            if (dragonsteelItem != Items.AIR) {
                stack = new ItemStack(dragonsteelItem);
            } else {
                stack = new ItemStack(Items.NETHERITE_SWORD);
            }
        }

        if (r >= 0.30 && rng.nextDouble() < 0.20) {
            try {
                Registry<Enchantment> reg = world.getRegistryManager()
                        .getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
                if (reg != null) {
                    int count = 1 + rng.nextInt(2);
                    for (int i = 0; i < count; i++) {
                        net.minecraft.enchantment.Enchantment key = SWORD_ENCHANTS.get(rng.nextInt(SWORD_ENCHANTS.size()));
                        int maxLevel = Math.max(1, key.getMaxLevel());
                        stack.addEnchantment(key, 1 + rng.nextInt(maxLevel));
                    }
                }
            } catch (Throwable t) {
                LOG.debug("Guild sword enchant failed: {}", t.toString());
            }
        }

        frame.setHeldItemStack(stack, false);
    }

    /** [material][slot] — slot order: 0 helmet, 1 chestplate, 2 leggings, 3 boots. */
    private static final Item[][] GUILD_ARMOR = {
            { Items.LEATHER_HELMET,   Items.LEATHER_CHESTPLATE,   Items.LEATHER_LEGGINGS,   Items.LEATHER_BOOTS },
            { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
            { Items.IRON_HELMET,      Items.IRON_CHESTPLATE,      Items.IRON_LEGGINGS,      Items.IRON_BOOTS },
            { Items.DIAMOND_HELMET,   Items.DIAMOND_CHESTPLATE,   Items.DIAMOND_LEGGINGS,   Items.DIAMOND_BOOTS },
            { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS },
    };

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Vanilla enchantments valid on every armour piece. */
    private static final List<net.minecraft.enchantment.Enchantment> ARMOR_ENCHANTS = List.of(
            Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION,
            Enchantments.PROJECTILE_PROTECTION, Enchantments.THORNS, Enchantments.UNBREAKING);

    /**
     * Rolls an independent, mismatched armour set for one stand. Per slot:
     * 20% empty, 30% leather, 20% chainmail, 15% iron, 13% diamond, 2% netherite.
     * Every placed piece additionally has a 20% chance to be enchanted.
     */
    private static void randomizeArmorStand(ServerWorld world, ArmorStandEntity stand, Random rng) {
        for (int slot = 0; slot < ARMOR_SLOTS.length; slot++) {
            double r = rng.nextDouble();
            int material;
            if      (r < 0.20) material = -1; // 20% empty
            else if (r < 0.50) material = 0;  // 30% leather
            else if (r < 0.70) material = 1;  // 20% chainmail
            else if (r < 0.85) material = 2;  // 15% iron
            else if (r < 0.98) material = 3;  // 13% diamond
            else               material = 4; //  2% netherite

            if (material < 0) {
                stand.equipStack(ARMOR_SLOTS[slot], ItemStack.EMPTY);
                continue;
            }
            ItemStack piece = new ItemStack(GUILD_ARMOR[material][slot]);
            if (rng.nextInt(100) < 20) {
                applyRandomEnchantments(world, piece, rng);
            }
            stand.equipStack(ARMOR_SLOTS[slot], piece);
        }
    }

    /** Apply 1-2 random vanilla armour enchantments at a random valid level. */
    private static void applyRandomEnchantments(ServerWorld world, ItemStack stack, Random rng) {
        try {
            Registry<Enchantment> reg = world.getRegistryManager()
                    .getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
            if (reg == null) return;
            int count = 1 + rng.nextInt(2);
            for (int i = 0; i < count; i++) {
                net.minecraft.enchantment.Enchantment key = ARMOR_ENCHANTS.get(rng.nextInt(ARMOR_ENCHANTS.size()));
                int maxLevel = Math.max(1, key.getMaxLevel());
                stack.addEnchantment(key, 1 + rng.nextInt(maxLevel));
            }
        } catch (Throwable t) {
            // Enchantment registry shape differs — skip; the armour still spawns.
            LOG.debug("Guild armour enchant failed: {}", t.toString());
        }
    }

    /** Lazily roll a per-camp dragon colour shared across all advert signs. */
    private DragonColor ensureSharedColor(Random rng) {
        if (sharedColor == null) {
            sharedColor = DragonColor.values()[rng.nextInt(DragonColor.values().length)];
        }
        return sharedColor;
    }

    /** Lazily seed the per-camp deterministic RNG used by {@link #outcomeFor}. */
    private long ensureCampSeed(Random rng) {
        if (campSeed == null) {
            campSeed = rng.nextLong();
        }
        return campSeed;
    }

    /**
     * Look up — or roll once and cache — the outcome for {@code pos}.
     * If there's an existing outcome within 4 blocks (Manhattan distance),
     * we reuse it so {@code d_young_stock} and adjacent {@code cost_dragon}
     * always match perfectly without grid boundary errors.
     */
    private CellOutcome outcomeFor(BlockPos pos, Random sharedRng) {
        for (Map.Entry<BlockPos, CellOutcome> entry : rolledOutcomes.entrySet()) {
            BlockPos cachedPos = entry.getKey();
            int dist = Math.abs(pos.getX() - cachedPos.getX())
                     + Math.abs(pos.getY() - cachedPos.getY())
                     + Math.abs(pos.getZ() - cachedPos.getZ());
            if (dist <= MAX_PAIRING_DISTANCE) {
                return entry.getValue();
            }
        }

        // Deterministic hash based on this block position to vary the random roll
        long posHash = (long) pos.getX() * 3129871L ^ (long) pos.getZ() * 116123897L ^ (long) pos.getY();
        // Mix the hash to properly avalanche entropy into the top bits for java.util.Random
        posHash = (posHash ^ (posHash >>> 30)) * 0xBF58476D1CE4E5B9L;
        posHash = (posHash ^ (posHash >>> 27)) * 0x94D049BB133111EBL;
        posHash ^= posHash >>> 31;
        long seed = ensureCampSeed(sharedRng) ^ posHash;
        Random rng = new Random(seed);
        boolean soldOut = rng.nextDouble() < SOLD_OUT_CHANCE;
        boolean dark = soldOut && rng.nextDouble() < DARK_SOLD_OUT_CHANCE;
        int adLayoutIdx = AD_LAYOUTS[rng.nextInt(AD_LAYOUTS.length)];
        int costLayoutIdx = COST_COLOUR_LAYOUTS[rng.nextInt(COST_COLOUR_LAYOUTS.length)];

        // Price uses the shared per-camp colour's tier (rolled deterministically).
        DragonColor colour = ensureSharedColor(sharedRng);
        int price = colour.tier.roll(rng);

        CellOutcome o = new CellOutcome(soldOut, dark, adLayoutIdx, costLayoutIdx, price, seed);
        rolledOutcomes.put(pos, o);
        return o;
    }

    /** Find the first non-empty line in the given side's messages and return its raw text. */
    private static String readSideMessage(NbtCompound data, String side) {
        if (!data.contains(side, NbtElement.COMPOUND_TYPE)) return null;
        NbtCompound sideData = data.getCompound(side);
        if (!sideData.contains("messages", NbtElement.LIST_TYPE)) return null;
        NbtList msgs = sideData.getList("messages", NbtElement.STRING_TYPE);
        for (int i = 0; i < msgs.size(); i++) {
            String raw = msgs.getString(i);
            String text = stripJsonQuotes(raw);
            if (!text.isEmpty()) return text;
        }
        return null;
    }

    /** Vanilla stores sign lines as JSON. A bare string {@code "abc"} parses as plain text "abc". */
    private static String stripJsonQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String[] buildHunterLines() {
        return new String[]{
                translatable(key("d_hunter.line1"), null),
                translatable(key("d_hunter.line2"), null),
                translatable(key("d_hunter.line3"), null),
                "\"\""
        };
    }

    /**
     * Build 4 advert sign lines for a {@code d_young_stock} marker. The layout
     * index, colour and price are all decided ahead of time by the cell-paired
     * outcome — this method just stamps them into the right translation keys.
     */
    private static String[] buildAdLines(int idx, DragonColor colour, int price) {
        TranslatableArg colourArg = new TranslatableArg(LANG_NS + ".color." + colour.translationKey);
        switch (idx) {
            case 0: return new String[]{
                    translatable(key("stock.0.line1"), null),
                    translatable(key("stock.0.line2"), null),
                    translatable(key("stock.0.line3"), new Object[]{colourArg}),
                    translatable(key("stock.0.line4"), new Object[]{price})
            };
            case 1: return new String[]{
                    translatable(key("stock.1.line1"), null),
                    translatable(key("stock.1.line2"), null),
                    translatable(key("stock.1.line3"), new Object[]{colourArg}),
                    translatable(key("stock.1.line4"), new Object[]{price})
            };
            case 3: return new String[]{
                    translatable(key("stock.3.line1"), null),
                    translatable(key("stock.3.line2"), null),
                    translatable(key("stock.3.line3"), null),
                    translatable(key("stock.3.line4"), new Object[]{price})
            };
            case 5: return new String[]{
                    translatable(key("stock.5.line1"), new Object[]{colourArg}),
                    translatable(key("stock.5.line2"), null),
                    translatable(key("stock.5.line3"), null),
                    translatable(key("stock.5.line4"), new Object[]{price})
            };
            default:
                // Should never happen — AD_LAYOUTS is closed over {0,1,3,5}.
                return new String[]{"\"\"", "\"\"", "\"\"", "\"\""};
        }
    }

    /**
     * Build 4 sign lines for the {@code d_meal_heart<idx>} marker.
     * Indices 0/2/4/5 = in-stock messages; 1/3 = "sold out" messages.
     * All text is static — no parameters needed.
     */
    private static String[] buildMealHeartLines(int idx) {
        return new String[]{
                translatable(key("meal_heart." + idx + ".line1"), null),
                translatable(key("meal_heart." + idx + ".line2"), null),
                translatable(key("meal_heart." + idx + ".line3"), null),
                translatable(key("meal_heart." + idx + ".line4"), null)
        };
    }

    /**
     * Build 4 sign lines for a {@code cost_dragon} marker. Only the
     * colour-based layouts (0, 1, 5) are produced — the cost_dragon sign always
     * shows the dragon colour as a full word (per user spec). The sold-out
     * variant is emitted directly in {@link #process} as four blank lines and
     * does NOT go through this method.
     */
    private static String[] buildCostColourLines(int idx, DragonColor colour) {
        TranslatableArg colourArg = new TranslatableArg(LANG_NS + ".color.full." + colour.translationKey);
        switch (idx) {
            case 0: return new String[]{
                    translatable(key("cost_dragon.0.line1"), new Object[]{colourArg}),
                    translatable(key("cost_dragon.0.line2"), null),
                    translatable(key("cost_dragon.0.line3"), null),
                    translatable(key("cost_dragon.0.line4"), null)
            };
            case 1: return new String[]{
                    translatable(key("cost_dragon.1.line1"), new Object[]{colourArg}),
                    translatable(key("cost_dragon.1.line2"), null),
                    translatable(key("cost_dragon.1.line3"), null),
                    translatable(key("cost_dragon.1.line4"), null)
            };
            case 5: return new String[]{
                    translatable(key("cost_dragon.5.line1"), new Object[]{colourArg}),
                    translatable(key("cost_dragon.5.line2"), null),
                    translatable(key("cost_dragon.5.line3"), null),
                    translatable(key("cost_dragon.5.line4"), null)
            };
            default:
                return new String[]{"\"\"", "\"\"", "\"\"", "\"\""};
        }
    }

    /** Dragon skull entity types — random pick on dark sold-out signs. */
    private static final String[] SKULL_TYPES = {"fire", "ice", "lightning"};

    /**
     * Spawn a baby dragon skull on the ground to the right of the sign (as seen by a reader).
     * "Right" is computed from the sign's facing direction.
     */
    private static void spawnDragonSkull(ServerWorld world, BlockPos signPos, Random rng) {
        BlockState signState = world.getBlockState(signPos);
        Direction facing;
        if (signState.contains(Properties.HORIZONTAL_FACING)) {
            facing = signState.get(Properties.HORIZONTAL_FACING);
        } else if (signState.contains(Properties.ROTATION)) {
            int rot = signState.get(Properties.ROTATION);
            // Standing sign rotation: 0 = south, increases clockwise (when viewed from above).
            // Map 0..15 → south/sw/w/nw/n/ne/e/se using nearest horizontal direction.
            float yaw = rot * 22.5f;
            facing = Direction.fromRotation(yaw);
        } else {
            facing = Direction.NORTH;
        }
        // Right side of the sign when reading it = facing.rotateYCounterclockwise()
        Direction right = facing.rotateYCounterclockwise();
        BlockPos skullPos = signPos.offset(right);

        String type = SKULL_TYPES[rng.nextInt(SKULL_TYPES.length)];

        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "iceandfire:dragon_skull");
        nbt.putString("Type", type);
        nbt.putInt("DragonAge", 20);
        nbt.putInt("Stage", 1);
        // Face the skull the same way the sign faces, so it looks "out" toward the reader.
        float skullYaw = facing.asRotation();
        nbt.putFloat("DragonYaw", skullYaw);

        EntityType.loadEntityWithPassengers(nbt, world, entity -> {
            entity.refreshPositionAndAngles(
                    skullPos.getX() + 0.5,
                    skullPos.getY(),
                    skullPos.getZ() + 0.5,
                    skullYaw, 0f);
            world.spawnEntity(entity);
            return entity;
        });
        LOG.debug("Spawned dark sold-out skull ({}) at {} right of sign {}", type, skullPos, signPos);
    }

    private static String key(String suffix) {
        return LANG_NS + ".sign." + suffix;
    }

    /**
     * Produce the JSON serialisation of a {@code translate} text component.
     * Numbers are written bare; strings are JSON-escaped and quoted.
     */
    private static String translatable(String translationKey, Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append('{').append("\"translate\":\"").append(escape(translationKey)).append('"');
        if (args != null && args.length > 0) {
            sb.append(",\"with\":[");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(',');
                Object v = args[i];
                if (v instanceof Number) {
                    sb.append(v);
                } else if (v instanceof TranslatableArg ta) {
                    sb.append("{\"translate\":\"").append(escape(ta.key())).append("\"}");
                } else {
                    sb.append('"').append(escape(String.valueOf(v))).append('"');
                }
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Write sign lines to the side that contained the marker.
     * The opposite side is explicitly cleared to empty (blank sign).
     * If soldOut is true the opposite side is also cleared regardless.
     *
     * @param markerOnFront true = marker was on front_text, false = back_text
     * @param soldOut       true = this sign is in a sold-out state; clear opposite side
     */
    private static void writeSignLines(NbtCompound data, String[] lines,
                                       boolean markerOnFront, boolean soldOut) {
        // Build the filled side NBT
        NbtCompound filled = new NbtCompound();
        NbtList msgs = new NbtList();
        for (int i = 0; i < 4; i++) {
            String content = (i < lines.length && lines[i] != null) ? lines[i] : "\"\"";
            msgs.add(NbtString.of(content));
        }
        filled.put("messages", msgs);

        // Preserve color/glow from original side
        String markerSide = markerOnFront ? "front_text" : "back_text";
        String otherSide  = markerOnFront ? "back_text"  : "front_text";
        if (data.contains(markerSide, NbtElement.COMPOUND_TYPE)) {
            NbtCompound orig = data.getCompound(markerSide);
            if (orig.contains("color")) filled.put("color", orig.get("color").copy());
            if (orig.contains("has_glowing_text")) filled.put("has_glowing_text", orig.get("has_glowing_text").copy());
        }

        // Build the empty side NBT
        NbtCompound empty = new NbtCompound();
        NbtList emptyMsgs = new NbtList();
        for (int i = 0; i < 4; i++) emptyMsgs.add(NbtString.of("\"\""));
        empty.put("messages", emptyMsgs);

        data.put(markerSide, filled);
        data.put(otherSide,  empty);
    }
}


