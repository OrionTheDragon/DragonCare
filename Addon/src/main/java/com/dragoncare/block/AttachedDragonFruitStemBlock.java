package com.dragoncare.block;

import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;

/**
 * Кастомный прикрепленный стебель драконьего фрукта, когда плод созрел.
 */
public class AttachedDragonFruitStemBlock extends AttachedStemBlock {

    public AttachedDragonFruitStemBlock(RegistryKey<Block> stemBlock, RegistryKey<Block> gourdBlock, RegistryKey<Item> seedItem, Settings settings) {
        super(stemBlock, gourdBlock, seedItem, settings);
    }
}
