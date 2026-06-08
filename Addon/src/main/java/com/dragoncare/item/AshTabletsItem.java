package com.dragoncare.item;

import com.dragoncare.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

public class AshTabletsItem extends Item {
    public AshTabletsItem(Settings settings) {
        super(settings.maxDamage(16));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }

        if (!world.isClient()) {
            user.addStatusEffect(new StatusEffectInstance(ModEffects.MYSTERIOUS_TABLETS, 5 * 60 * 20, 0));
            user.getItemCooldownManager().set(this, (5 * 60 + 30) * 20); // 5 min 30 sec
            
            // Play our custom sound
            world.playSound(null, user.getX(), user.getY(), user.getZ(), 
                    com.dragoncare.sound.ModSounds.USING_PILL.get(), 
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);

            if (!user.isCreative()) {
                stack.damage(1, user, LivingEntity.getSlotForHand(hand));
            }
        }
        
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.dragoncare.ash_tablets.desc").formatted(net.minecraft.util.Formatting.GRAY));
    }
}
