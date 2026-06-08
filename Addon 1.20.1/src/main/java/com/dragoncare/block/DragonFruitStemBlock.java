package com.dragoncare.block;

import com.dragoncare.config.AddonConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StemBlock;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Кастомный стебель драконьего фрукта.
 * Скорость роста стебля и появления плода настраивается в конфиге.
 * По умолчанию оба значения = 50% (в 2 раза медленнее ванильного арбуза/тыквы).
 */
public class DragonFruitStemBlock extends StemBlock {

    public DragonFruitStemBlock(net.minecraft.block.GourdBlock gourdBlock, java.util.function.Supplier<Item> pickBlockItem, Settings settings) {
        super(gourdBlock, pickBlockItem, settings);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int age = state.get(AGE);

        if (age < MAX_AGE) {
            // Стебель ещё растёт: применяем шанс роста
            int chance = AddonConfig.DRAGON_FRUIT_STEM_GROWTH_CHANCE.get();
            if (chance >= 100 || random.nextInt(100) < chance) {
                super.randomTick(state, world, pos, random);
            }
        } else {
            // Стебель полностью вырос: применяем шанс появления фрукта
            int chance = AddonConfig.DRAGON_FRUIT_SPAWN_CHANCE.get();
            if (chance >= 100 || random.nextInt(100) < chance) {
                super.randomTick(state, world, pos, random);
            }
        }
    }
}


