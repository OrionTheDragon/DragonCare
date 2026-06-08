package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.taming.DragonTamingManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.List;

public class ExceptionalDragonMealItem extends Item {

    public ExceptionalDragonMealItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("item.dragoncare.exceptional_dragon_meal.desc_0").formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof DragonBaseEntity dragon)) {
            return ActionResult.PASS;
        }
        if (dragon.isModelDead() || dragon.isTamed()) {
            return ActionResult.PASS;
        }
        if (user.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(user instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        boolean consume = DragonTamingManager.feedDragon(serverPlayer, dragon);
        if (consume && !user.isCreative()) {
            stack.decrement(1);
        }
        return ActionResult.SUCCESS;
    }
}
