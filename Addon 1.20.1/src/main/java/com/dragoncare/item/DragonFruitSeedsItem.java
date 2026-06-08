package com.dragoncare.item;

import net.minecraft.block.Block;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Семечки драконьего фрукта. Сажаются на вспаханную землю как тыква/арбуз.
 */
public class DragonFruitSeedsItem extends AliasedBlockItem {

    public DragonFruitSeedsItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.World world, java.util.List<net.minecraft.text.Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("item.dragoncare.dragon_fruit_seeds.desc_0").formatted(Formatting.GRAY));
    }
}


