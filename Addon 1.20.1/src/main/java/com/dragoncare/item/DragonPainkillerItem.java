package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.dragoncare.config.AddonConfig;
import com.dragoncare.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DragonPainkillerItem extends Item {

    public static final int EFFECT_DURATION_TICKS = 20 * 90;            // 1:30
    private static final long STAGE5_PAIRED_COOLDOWN_TICKS = 20L * 90L; // 1:30
    private static final long STAGE5_PAIR_WINDOW_TICKS = 20L * 60L * 2L;// 2:00

    // dragonUuid -> world tick when cooldown ends
    private static final Map<UUID, Long> DRAGON_COOLDOWNS = new HashMap<>();
    // stage 5 only: dragonUuid -> tick of the FIRST use in the pair (cleared on 2nd or expiry)
    private static final Map<UUID, Long> STAGE5_FIRST_USE = new HashMap<>();

    public DragonPainkillerItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.World world, java.util.List<net.minecraft.text.Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("item.dragoncare.dragon_painkiller.desc_0").formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof EntityDragonBase dragon)) {
            return ActionResult.PASS;
        }
        if (dragon.isModelDead()) {
            return ActionResult.PASS;
        }

        World world = user.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        long now = world.getTime();
        UUID dragonId = dragon.getUuid();

        // Prune expired entries so these static maps stay bounded to dragons
        // with a currently-active cooldown / open pair-window (server thread
        // only вЂ” plain HashMap is safe here).
        DRAGON_COOLDOWNS.entrySet().removeIf(e -> now >= e.getValue());
        STAGE5_FIRST_USE.entrySet().removeIf(e -> now - e.getValue() > STAGE5_PAIR_WINDOW_TICKS);

        // Active cooldown blocks the use
        Long cooldownEnd = DRAGON_COOLDOWNS.get(dragonId);
        if (cooldownEnd != null && now < cooldownEnd) {
            sendOverdose(user, cooldownEnd - now);
            return ActionResult.SUCCESS;
        } else if (cooldownEnd != null) {
            DRAGON_COOLDOWNS.remove(dragonId);
        }

        int stage = dragon.getDragonStage();
        boolean stage5 = stage >= 5;
        boolean setCooldown = true;

        if (stage5) {
            Long firstUse = STAGE5_FIRST_USE.get(dragonId);
            if (firstUse != null && now - firstUse <= STAGE5_PAIR_WINDOW_TICKS) {
                // Second dose in the pair window: trigger the 1:30 cooldown
                STAGE5_FIRST_USE.remove(dragonId);
                DRAGON_COOLDOWNS.put(dragonId, now + STAGE5_PAIRED_COOLDOWN_TICKS);
                setCooldown = false;
            } else {
                // First dose (or expired pair) вЂ” record and skip cooldown
                STAGE5_FIRST_USE.put(dragonId, now);
                setCooldown = false;
            }
        }

        // Apply painkilling effect
        dragon.addStatusEffect(new StatusEffectInstance(ModEffects.PAINKILLING.get(), EFFECT_DURATION_TICKS, 0, false, false, false));

        if (setCooldown) {
            DRAGON_COOLDOWNS.put(dragonId, now + AddonConfig.ticksFromSeconds(AddonConfig.PAINKILLER_CD));
        }

        // Durability damage
        if (!user.isCreative()) {
            stack.damage(1, user, (p) -> p.sendToolBreakStatus(hand));
        }

        user.getItemCooldownManager().set(this, 10);
        return ActionResult.SUCCESS;
    }

    private static void sendOverdose(PlayerEntity user, long remainingTicks) {
        int totalSeconds = (int) (remainingTicks / 20L);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timer = String.format("%d:%02d", minutes, seconds);
        user.sendMessage(
                Text.translatable("message.dragoncare.dragon_painkiller.overdose", timer).formatted(Formatting.RED),
                true
        );
    }

    public static void clearCache() { DRAGON_COOLDOWNS.clear(); STAGE5_FIRST_USE.clear(); }
}



