package com.dragoncare.block;

import com.dragoncare.DragonCare;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(DragonCare.MOD_ID);

    // Статические ключи реестра для перекрестных ссылок в конструкторах стеблей
    public static final RegistryKey<Block> DRAGON_FRUIT_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(DragonCare.MOD_ID, "dragon_fruit"));
    
    public static final RegistryKey<Block> STEM_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(DragonCare.MOD_ID, "dragon_fruit_stem"));
    
    public static final RegistryKey<Block> ATTACHED_STEM_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(DragonCare.MOD_ID, "attached_dragon_fruit_stem"));
    
    public static final RegistryKey<Item> SEEDS_KEY =
            RegistryKey.of(RegistryKeys.ITEM, Identifier.of(DragonCare.MOD_ID, "dragon_fruit_seeds"));

    // 1. Блок драконьего фрукта
    public static final DeferredBlock<Block> DRAGON_FRUIT = BLOCKS.register(
            "dragon_fruit",
            () -> new Block(AbstractBlock.Settings.copy(Blocks.MELON))
    );

    // 2. Растущий стебель
    public static final DeferredBlock<DragonFruitStemBlock> DRAGON_FRUIT_STEM = BLOCKS.register(
            "dragon_fruit_stem",
            () -> new DragonFruitStemBlock(
                    DRAGON_FRUIT_KEY,
                    ATTACHED_STEM_KEY,
                    SEEDS_KEY,
                    AbstractBlock.Settings.copy(Blocks.PUMPKIN_STEM)
            )
    );

    // 3. Прикрепленный стебель
    public static final DeferredBlock<AttachedDragonFruitStemBlock> ATTACHED_DRAGON_FRUIT_STEM = BLOCKS.register(
            "attached_dragon_fruit_stem",
            () -> new AttachedDragonFruitStemBlock(
                    STEM_KEY,
                    DRAGON_FRUIT_KEY,
                    SEEDS_KEY,
                    AbstractBlock.Settings.copy(Blocks.ATTACHED_PUMPKIN_STEM)
            )
    );
}
