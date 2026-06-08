package com.dragoncare.block;

import com.dragoncare.DragonCare;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister<net.minecraft.block.Block> BLOCKS =
            DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS, DragonCare.MOD_ID);

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
    public static final RegistryObject<net.minecraft.block.GourdBlock> DRAGON_FRUIT = BLOCKS.register(
            "dragon_fruit",
            () -> new net.minecraft.block.MelonBlock(AbstractBlock.Settings.copy(Blocks.MELON)) {
                @Override
                public net.minecraft.block.StemBlock getStem() {
                    return DRAGON_FRUIT_STEM.get();
                }

                @Override
                public net.minecraft.block.AttachedStemBlock getAttachedStem() {
                    return ATTACHED_DRAGON_FRUIT_STEM.get();
                }
            }
    );

    // 2. Растущий стебель
    public static final RegistryObject<DragonFruitStemBlock> DRAGON_FRUIT_STEM = BLOCKS.register(
            "dragon_fruit_stem",
            () -> new DragonFruitStemBlock(
                    DRAGON_FRUIT.get(),
                    () -> com.dragoncare.item.ModItems.DRAGON_FRUIT_SEEDS.get(),
                    AbstractBlock.Settings.copy(Blocks.PUMPKIN_STEM)
            )
    );

    // 3. Прикрепленный стебель
    public static final RegistryObject<AttachedDragonFruitStemBlock> ATTACHED_DRAGON_FRUIT_STEM = BLOCKS.register(
            "attached_dragon_fruit_stem",
            () -> new AttachedDragonFruitStemBlock(
                    DRAGON_FRUIT.get(),
                    () -> com.dragoncare.item.ModItems.DRAGON_FRUIT_SEEDS.get(),
                    AbstractBlock.Settings.copy(Blocks.ATTACHED_PUMPKIN_STEM)
            )
    );
}


