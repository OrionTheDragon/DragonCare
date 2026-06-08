package com.dragoncare.item;

import net.minecraft.block.Block;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
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
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("item.dragoncare.dragon_fruit_seeds.desc_0").formatted(Formatting.GRAY));
    }
}
