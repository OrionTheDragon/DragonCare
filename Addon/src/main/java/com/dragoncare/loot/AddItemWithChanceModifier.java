package com.dragoncare.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.context.LootContextParameters;

/**
 * Глобальный модификатор лута: с шансом {@code chance} добавляет в дроп предмет
 * {@code item} в количестве [{@code min}, {@code max}], НО только если ID текущей
 * лут-таблицы перечислен в {@code lootTables}.
 *
 * <p>Зачем список таблиц внутри модификатора, а не {@code LootTableIdCondition} в JSON:
 * чтобы один JSON-файл мог покрыть все 16 типов деревенских сундуков без дублирования.</p>
 */
public class AddItemWithChanceModifier extends LootModifier {

    /** Codec для Item через Identifier — устойчив к разнице имён getCodec()/byNameCodec(). */
    private static final Codec<Item> ITEM_BY_ID = Identifier.CODEC.xmap(
            id -> Registries.ITEM.get(id),
            item -> Registries.ITEM.getId(item)
    );

    public static final MapCodec<AddItemWithChanceModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            LootModifier.codecStart(inst).and(inst.group(
                    Codec.list(Identifier.CODEC).fieldOf("loot_tables").forGetter((AddItemWithChanceModifier m) -> m.lootTables),
                    ITEM_BY_ID.fieldOf("item").forGetter((AddItemWithChanceModifier m) -> m.item),
                    Codec.INT.fieldOf("min").forGetter((AddItemWithChanceModifier m) -> m.min),
                    Codec.INT.fieldOf("max").forGetter((AddItemWithChanceModifier m) -> m.max),
                    Codec.DOUBLE.fieldOf("chance").forGetter((AddItemWithChanceModifier m) -> m.chance),
                    Codec.DOUBLE.optionalFieldOf("step", 0.0).forGetter((AddItemWithChanceModifier m) -> m.step),
                    Codec.DOUBLE.optionalFieldOf("max_chance", 1.0).forGetter((AddItemWithChanceModifier m) -> m.maxChance)
            )).apply(inst, AddItemWithChanceModifier::new));

    private final List<Identifier> lootTables;
    private final Item item;
    private final int min;
    private final int max;
    private final double chance;
    private final double step;
    private final double maxChance;
    
    private final Map<UUID, Integer> pityCounters = new ConcurrentHashMap<>();

    public AddItemWithChanceModifier(LootCondition[] conditionsIn,
                                     List<Identifier> lootTables,
                                     Item item,
                                     int min,
                                     int max,
                                     double chance,
                                     double step,
                                     double maxChance) {
        super(conditionsIn);
        this.lootTables = lootTables;
        this.item = item;
        this.min = min;
        this.max = max;
        this.chance = chance;
        this.step = step;
        this.maxChance = maxChance;
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Identifier currentTable = context.getQueriedLootTableId();
        boolean matches = false;
        if (currentTable != null) {
            if (lootTables.contains(currentTable)) {
                matches = true;
            } else if (currentTable.getPath().contains("village") && (currentTable.getPath().contains("chest") || currentTable.getPath().contains("house"))) {
                matches = true;
            }
        }
        
        if (!matches) {
            return generatedLoot;
        }

        PlayerEntity player = null;
        if (context.hasParameter(LootContextParameters.THIS_ENTITY)) {
            Entity entity = context.get(LootContextParameters.THIS_ENTITY);
            if (entity instanceof PlayerEntity pe) {
                player = pe;
            }
        }

        double currentChance = chance;
        UUID playerId = player != null ? player.getUuid() : null;
        int pity = 0;

        if (playerId != null && step > 0) {
            pity = pityCounters.getOrDefault(playerId, 0);
            currentChance = Math.min(chance + (pity * step), maxChance);
        }

        double roll = context.getRandom().nextDouble();
        boolean success = roll < currentChance;

        if (!success) {
            if (playerId != null && step > 0) {
                // Bound the pity map. A LootModifier instance lives for the
                // whole server session and has no player-logout hook, so on an
                // extreme distinct-player count just reset it — pity is a minor
                // luck mechanic and an occasional wipe is harmless.
                if (pityCounters.size() > 4096) pityCounters.clear();
                pityCounters.put(playerId, pityCounters.getOrDefault(playerId, 0) + 1);
            }
            return generatedLoot;
        }

        if (playerId != null && step > 0) {
            pityCounters.put(playerId, 0);
        }

        if (item == null || item == Items.AIR) {
            return generatedLoot;
        }
        int lo = Math.max(1, Math.min(min, max));
        int hi = Math.max(lo, max);
        int count = (lo == hi) ? lo : context.getRandom().nextBetween(lo, hi);
        generatedLoot.add(new ItemStack(item, count));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
