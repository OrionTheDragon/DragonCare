package com.dragoncare.loot;

import com.mojang.serialization.Codec;
import com.dragoncare.DragonCare;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Регистрация Global Loot Modifier serializer'ов.
 * Сами modifier'ы описываются JSON-файлами в {@code data/dragoncare/forge/loot_modifiers/}.
 */
public final class ModLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, DragonCare.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_ITEM_WITH_CHANCE =
            SERIALIZERS.register("add_item_with_chance", () -> AddItemWithChanceModifier.CODEC);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_GUILD_MAP =
            SERIALIZERS.register("add_guild_map", () -> AddGuildMapModifier.CODEC);

    private ModLootModifiers() {}
}


