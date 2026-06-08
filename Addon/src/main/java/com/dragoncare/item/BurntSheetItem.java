package com.dragoncare.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Burnt sheet/scroll. Right-click opens a fullscreen paper-styled GUI that
 * displays pre-generated text. Cannot be crafted; content is read-only and
 * intended to be supplied via item data (later — currently shows a placeholder
 * translation key).
 */
public class BurntSheetItem extends Item {

    public BurntSheetItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.text.Text> tooltip, net.minecraft.item.tooltip.TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(net.minecraft.text.Text.translatable("item.dragoncare.burnt_sheet.desc_0").formatted(net.minecraft.util.Formatting.GRAY));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            // Indirection via a client-only class so the server JVM never resolves
            // MinecraftClient / Screen references.
            com.dragoncare.client.BurntSheetClient.openScreen(stack);
        } else {
            // Quiet rustle on the server for ambience; harmless if client lacks sound.
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6F, 1.0F);
        }

        return TypedActionResult.success(stack, world.isClient());
    }
}
