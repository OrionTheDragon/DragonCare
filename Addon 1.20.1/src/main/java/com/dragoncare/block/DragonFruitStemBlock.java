package com.dragoncare.block;

import com.dragoncare.DragonCare;
import com.dragoncare.config.AddonConfig;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.GourdBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.function.Supplier;

/**
 * Кастомный стебель драконьего фрукта.
 * Скорость роста стебля и появления плода настраивается в конфиге.
 * По умолчанию оба значения = 50% (в 2 раза медленнее ванильного арбуза/тыквы).
 */
public class DragonFruitStemBlock extends StemBlock {
    public static final TagKey<Block> DRAGON_FRUIT_GROWABLE_ON = TagKey.of(
            RegistryKeys.BLOCK,
            Identifier.of(DragonCare.MOD_ID, "dragon_fruit_growable_on")
    );

    private final GourdBlock gourdBlock;

    public DragonFruitStemBlock(GourdBlock gourdBlock, Supplier<Item> pickBlockItem, Settings settings) {
        super(gourdBlock, pickBlockItem, settings);
        this.gourdBlock = gourdBlock;
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
                trySpawnFruit(state, world, pos, random);
            }
        }
    }

    private void trySpawnFruit(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        Direction direction = Direction.Type.HORIZONTAL.random(random);
        BlockPos fruitPos = pos.offset(direction);
        BlockPos soilPos = fruitPos.down();

        if (!world.isAir(fruitPos) || !canDragonFruitGrowOn(world.getBlockState(soilPos))) {
            return;
        }

        world.setBlockState(fruitPos, gourdBlock.getDefaultState());
        world.setBlockState(pos, gourdBlock.getAttachedStem().getDefaultState().with(AttachedStemBlock.FACING, direction));
    }

    private static boolean canDragonFruitGrowOn(BlockState soil) {
        return soil.isIn(DRAGON_FRUIT_GROWABLE_ON)
                || soil.isIn(BlockTags.DIRT)
                || soil.isOf(Blocks.FARMLAND)
                || soil.isOf(Blocks.MUD);
    }
}


