package com.dragoncare.loot;

import com.mojang.serialization.MapCodec;
import com.dragoncare.DragonCare;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Регистрация Global Loot Modifier serializer'ов.
 * Сами modifier'ы описываются JSON-файлами в {@code data/dragoncare/neoforge/loot_modifiers/}.
 */
public final class ModLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, DragonCare.MOD_ID);

    public static final Supplier<MapCodec<AddItemWithChanceModifier>> ADD_ITEM_WITH_CHANCE =
            SERIALIZERS.register("add_item_with_chance", () -> AddItemWithChanceModifier.CODEC);

    public static final Supplier<MapCodec<AddGuildMapModifier>> ADD_GUILD_MAP =
            SERIALIZERS.register("add_guild_map", () -> AddGuildMapModifier.CODEC);

    private ModLootModifiers() {}
}
