package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class DragonBrushItem extends Item {

    public DragonBrushItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.World world, java.util.List<net.minecraft.text.Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("item.dragoncare.dragon_brush.desc_0").formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof EntityDragonBase dragon)) {
            return ActionResult.PASS;
        }
        if (dragon.isModelDead()) {
            return ActionResult.PASS;
        }
        if (!dragon.isTamed()) {
            return ActionResult.PASS;
        }

        World world = user.getWorld();
        int dirtLevel;
        if (world.isClient()) {
            dirtLevel = com.dragoncare.client.ClientDirtCache.get(dragon.getUuid());
        } else {
            dirtLevel = com.dragoncare.mechanics.DragonDirtManager.getDirtLevel(world.getServer(), dragon.getUuid());
        }

        if (dirtLevel == 0) {
            if (!world.isClient()) {
                user.sendMessage(Text.translatable("message.dragoncare.dragon_brush.already_clean").formatted(Formatting.YELLOW), true);
            }
            return ActionResult.SUCCESS;
        }

        if (world.isClient()) {
            if (com.dragoncare.config.AddonConfig.DIRT_EASY_MINIGAME.get() && dirtLevel == 1) {
                com.dragoncare.network.ModNetwork.INSTANCE.sendToServer(new com.dragoncare.network.DragonBrushCleanPayload(dragon.getUuid(), 0, 0));
                user.playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
                user.sendMessage(Text.translatable("gui.dragoncare.dragon_brush.success").formatted(Formatting.GREEN), true);
            } else {
                com.dragoncare.client.DragonBrushClient.openScreen(dragon, dirtLevel);
            }
        } else {
            if (!user.isCreative()) {
                stack.damage(1, user, (p) -> p.sendToolBreakStatus(hand));
            }
            user.getItemCooldownManager().set(this, 20);
        }

        return ActionResult.SUCCESS;
    }
}



