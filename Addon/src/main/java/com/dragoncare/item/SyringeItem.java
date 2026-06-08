package com.dragoncare.item;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.config.AddonConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyringeItem extends Item {

    // dragonUuid -> per-dragon collection state (window + use count + cooldown)
    private static final Map<UUID, SyringeState> SYRINGE_STATES = new HashMap<>();
    /** A collection window abandoned this long ago is treated as dead and pruned. */
    private static final long STALE_WINDOW_GUARD_TICKS = 20L * 60L * 20L; // 20 minutes

    public SyringeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("item.dragoncare.dragon_blood_syringe.desc_0").formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof DragonBaseEntity dragon)) {
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
        int stage = dragon.getDragonStage();

        // Keep the per-dragon state map bounded — drop entries already stale
        // (cooldown elapsed, or an abandoned collection window long past).
        // Such states are reset on next use anyway, so this changes nothing
        // functionally (server thread only — plain HashMap is safe here).
        SYRINGE_STATES.entrySet().removeIf(e -> {
            SyringeState s = e.getValue();
            return s.cooldownEnd > 0L
                    ? s.cooldownEnd <= now
                    : now - s.windowStart > STALE_WINDOW_GUARD_TICKS;
        });

        StageCfg cfg = configFor(stage);

        // 1) Per-dragon cooldown check (don't consume bottle)
        SyringeState state = SYRINGE_STATES.get(dragonId);
        if (state != null && state.cooldownEnd > now) {
            sendCooldownMessage(user, state.cooldownEnd - now);
            return ActionResult.SUCCESS;
        }

        // 2) Need a glass bottle to actually collect
        int bottleSlot = findGlassBottle(user.getInventory());
        if (bottleSlot == -1) {
            return ActionResult.PASS;
        }

        long windowTicks = AddonConfig.ticksFromSeconds(cfg.window);
        long cooldownTicks = AddonConfig.ticksFromSeconds(cfg.cooldown);
        int maxUses = cfg.uses.get();
        int bloodMin = cfg.bloodMin.get();
        int bloodMax = Math.max(bloodMin, cfg.bloodMax.get());

        // 3) Reset state if absent / cooldown finished / window expired
        boolean reset = state == null
                || (state.cooldownEnd > 0L && state.cooldownEnd <= now)
                || (state.cooldownEnd == 0L && now - state.windowStart >= windowTicks);
        if (reset) {
            state = new SyringeState(now);
            SYRINGE_STATES.put(dragonId, state);
        }

        state.useCount++;
        if (state.useCount >= maxUses) {
            state.cooldownEnd = now + cooldownTicks;
        }

        // 4) Consume one glass bottle
        user.getInventory().getStackInSlot(bottleSlot).decrement(1);

        // 5) Apply damage based on dragon growth stage
        if (stage == 1) {
            dragon.damage(user.getDamageSources().playerAttack(user), 15.0F);
        } else if (stage == 2) {
            dragon.damage(user.getDamageSources().playerAttack(user), 8.0F);
        }
        // Stage 3+ - no damage

        // 6) Give the corresponding dragon blood item(s)
        int amount = (bloodMax == bloodMin) ? bloodMin : world.getRandom().nextBetween(bloodMin, bloodMax);
        ItemStack bloodStack = new ItemStack(dragon.getBloodItem(), amount);
        if (!user.getInventory().insertStack(bloodStack)) {
            user.dropItem(bloodStack, false);
        }

        // Durability damage
        if (!user.isCreative()) {
            stack.damage(1, user, LivingEntity.getSlotForHand(hand));
        }

        // 7) Short item-cooldown to prevent rapid-fire spam
        user.getItemCooldownManager().set(this, 20);
        return ActionResult.SUCCESS;
    }

    private static StageCfg configFor(int stage) {
        switch (stage) {
            case 1:  return new StageCfg(AddonConfig.SYRINGE_S1_USES, AddonConfig.SYRINGE_S1_WIN, AddonConfig.SYRINGE_S1_CD, AddonConfig.SYRINGE_S1_MIN, AddonConfig.SYRINGE_S1_MAX);
            case 2:  return new StageCfg(AddonConfig.SYRINGE_S2_USES, AddonConfig.SYRINGE_S2_WIN, AddonConfig.SYRINGE_S2_CD, AddonConfig.SYRINGE_S2_MIN, AddonConfig.SYRINGE_S2_MAX);
            case 3:  return new StageCfg(AddonConfig.SYRINGE_S3_USES, AddonConfig.SYRINGE_S3_WIN, AddonConfig.SYRINGE_S3_CD, AddonConfig.SYRINGE_S3_MIN, AddonConfig.SYRINGE_S3_MAX);
            case 4:  return new StageCfg(AddonConfig.SYRINGE_S4_USES, AddonConfig.SYRINGE_S4_WIN, AddonConfig.SYRINGE_S4_CD, AddonConfig.SYRINGE_S4_MIN, AddonConfig.SYRINGE_S4_MAX);
            default: return new StageCfg(AddonConfig.SYRINGE_S5_USES, AddonConfig.SYRINGE_S5_WIN, AddonConfig.SYRINGE_S5_CD, AddonConfig.SYRINGE_S5_MIN, AddonConfig.SYRINGE_S5_MAX);
        }
    }

    private static void sendCooldownMessage(PlayerEntity user, long remainingTicks) {
        int totalSeconds = (int) (remainingTicks / 20L);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timer = String.format("%d:%02d", minutes, seconds);
        user.sendMessage(
                Text.translatable("message.dragoncare.dragon_blood_syringe.cooldown", timer).formatted(Formatting.RED),
                true
        );
    }

    private int findGlassBottle(PlayerInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStackInSlot(i).isOf(Items.GLASS_BOTTLE)) {
                return i;
            }
        }
        return -1;
    }

    private static class SyringeState {
        final long windowStart;
        int useCount;
        long cooldownEnd;

        SyringeState(long windowStart) {
            this.windowStart = windowStart;
            this.useCount = 0;
            this.cooldownEnd = 0L;
        }
    }

    private static class StageCfg {
        final ModConfigSpec.IntValue uses;
        final ModConfigSpec.IntValue window;
        final ModConfigSpec.IntValue cooldown;
        final ModConfigSpec.IntValue bloodMin;
        final ModConfigSpec.IntValue bloodMax;

        StageCfg(ModConfigSpec.IntValue uses, ModConfigSpec.IntValue window, ModConfigSpec.IntValue cooldown,
                 ModConfigSpec.IntValue bloodMin, ModConfigSpec.IntValue bloodMax) {
            this.uses = uses;
            this.window = window;
            this.cooldown = cooldown;
            this.bloodMin = bloodMin;
            this.bloodMax = bloodMax;
        }
    }

    public static void clearCache() { SYRINGE_STATES.clear(); }
}
