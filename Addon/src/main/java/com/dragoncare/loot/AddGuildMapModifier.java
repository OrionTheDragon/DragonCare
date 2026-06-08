package com.dragoncare.loot;

import com.dragoncare.worldgen.DragonHunterState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Adds a vanilla filled_map pointing to the first placed Dragon Hunter Guild,
 * with a fixed (not pity-boosted) chance, when the rolled loot table matches
 * one in {@code loot_tables}.
 *
 * <p>The first guild itself is placed on player login by {@code DragonHunterGuildPlacer}
 * via a sunflower-spiral search; until that completes, this modifier silently does
 * nothing (no map appears). Once placed, every matching chest roll gets a
 * {@code chance} probability of including the map.</p>
 */
public class AddGuildMapModifier extends LootModifier {

    public static final MapCodec<AddGuildMapModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            LootModifier.codecStart(inst).and(inst.group(
                    Codec.list(Identifier.CODEC).fieldOf("loot_tables").forGetter((AddGuildMapModifier m) -> m.lootTables),
                    Codec.DOUBLE.fieldOf("chance").forGetter((AddGuildMapModifier m) -> m.chance),
                    Codec.INT.optionalFieldOf("scale", 3).forGetter((AddGuildMapModifier m) -> m.scale)
            )).apply(inst, AddGuildMapModifier::new));

    private final List<Identifier> lootTables;
    private final double chance;
    /** Map scale 0..4. Scale 3 = 1024×1024 blocks covered — enough to find a 3000-block-range guild. */
    private final int scale;

    public AddGuildMapModifier(LootCondition[] conditionsIn,
                                List<Identifier> lootTables,
                                double chance,
                                int scale) {
        super(conditionsIn);
        this.lootTables = lootTables;
        this.chance = chance;
        this.scale = Math.max(0, Math.min(4, scale));
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // TODO: Implement map logic later. Currently disabled.
        return generatedLoot;
        
        /*
        Identifier currentTable = context.getQueriedLootTableId();
        if (currentTable == null) return generatedLoot;
        if (!lootTables.contains(currentTable)) return generatedLoot;

        if (context.getRandom().nextDouble() >= chance) return generatedLoot;

        ServerWorld world = context.getWorld();
        // Use the overworld's state — guild is overworld-only.
        ServerWorld overworld = world.getServer() != null ? world.getServer().getOverworld() : world;
        DragonHunterState state = DragonHunterState.get(overworld);
        BlockPos guildPos = state.getFirstGuildPos();
        if (guildPos == null) return generatedLoot;

        ItemStack map = FilledMapItem.createMap(overworld, guildPos.getX(), guildPos.getZ(),
                (byte) scale, true /* showIcons * /, true /* unlimitedTracking * /);
        // Red-X marker on the guild itself, like buried-treasure maps.
        MapState.addDecorationsNbt(map, guildPos, "+dragoncare_guild", MapDecorationTypes.RED_X);
        // Rename so the player sees what it is.
        map.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.dragoncare.guild_map"));

        generatedLoot.add(map);
        return generatedLoot;
        */
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
