package com.dragoncare.item;

import com.iafenvoy.iceandfire.data.DragonColor;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.config.AddonConfig;
import com.dragoncare.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

public class ScaleShearsItem extends Item {

    // dragonUuid -> world tick when the cooldown ends
    private static final Map<UUID, Long> DRAGON_COOLDOWNS = new HashMap<>();
    // playerUuid -> scolding state for stage-1 attempts within a 1-minute window
    private static final Map<UUID, ScoldingState> SCOLDING = new HashMap<>();

    private static final long ONE_MINUTE_TICKS = 20L * 60L;

    public ScaleShearsItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("item.dragoncare.scale_shears.desc_0").formatted(Formatting.GRAY));
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

        int stage = dragon.getDragonStage();
        long now = world.getTime();

        // Prune expired entries so these static maps stay bounded (server
        // thread only — plain HashMap is safe here).
        DRAGON_COOLDOWNS.entrySet().removeIf(e -> now >= e.getValue());
        SCOLDING.entrySet().removeIf(e -> now - e.getValue().firstTime > ONE_MINUTE_TICKS);

        // Stage 1: scolding messages, no scales, no damage
        if (stage == 1) {
            ScoldingState state = SCOLDING.get(user.getUuid());
            int count;
            if (state == null || now - state.firstTime > ONE_MINUTE_TICKS) {
                count = 1;
                SCOLDING.put(user.getUuid(), new ScoldingState(now, 1));
            } else {
                count = state.count + 1;
                state.count = count;
            }
            String key;
            if (count == 1) {
                key = "message.dragoncare.scale_shears.too_small";
            } else if (count == 2) {
                key = "message.dragoncare.scale_shears.serious";
            } else {
                key = "message.dragoncare.scale_shears.go_away";
            }
            user.sendMessage(Text.translatable(key).formatted(Formatting.YELLOW), true);
            return ActionResult.SUCCESS;
        }

        // Per-dragon cooldown check
        Long cooldownEnd = DRAGON_COOLDOWNS.get(dragon.getUuid());
        if (cooldownEnd != null && now < cooldownEnd) {
            long remainingTicks = cooldownEnd - now;
            int totalSeconds = (int) (remainingTicks / 20L);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String timer = String.format("%d:%02d", minutes, seconds);
            user.sendMessage(
                    Text.translatable("message.dragoncare.scale_shears.cooldown", timer).formatted(Formatting.RED),
                    true
            );
            return ActionResult.SUCCESS;
        }

        // Determine drop range, damage, cooldown by stage (from config)
        ModConfigSpec.IntValue minCfg, maxCfg, cdCfg;
        float damage;
        switch (stage) {
            case 2:
                minCfg = AddonConfig.SHEARS_S2_MIN; maxCfg = AddonConfig.SHEARS_S2_MAX; cdCfg = AddonConfig.SHEARS_S2_CD;
                damage = 30.0F;
                break;
            case 3:
                minCfg = AddonConfig.SHEARS_S3_MIN; maxCfg = AddonConfig.SHEARS_S3_MAX; cdCfg = AddonConfig.SHEARS_S3_CD;
                damage = 25.0F;
                break;
            case 4:
                minCfg = AddonConfig.SHEARS_S4_MIN; maxCfg = AddonConfig.SHEARS_S4_MAX; cdCfg = AddonConfig.SHEARS_S4_CD;
                damage = 20.0F;
                break;
            default: // stage 5+
                minCfg = AddonConfig.SHEARS_S5_MIN; maxCfg = AddonConfig.SHEARS_S5_MAX; cdCfg = AddonConfig.SHEARS_S5_CD;
                damage = 15.0F;
                break;
        }

        DragonColor color = DragonColor.getById(dragon.getVariant());
        if (color == null) {
            return ActionResult.PASS;
        }
        Item scaleItem = color.getScaleItem();
        if (scaleItem == null) {
            return ActionResult.PASS;
        }

        int min = minCfg.get();
        int max = Math.max(min, maxCfg.get());
        int amount = (max == min) ? min : world.getRandom().nextBetween(min, max);

        // Painkiller effect blocks shearing damage but still triggers cooldown
        if (!dragon.hasStatusEffect(ModEffects.PAINKILLING)) {
            dragon.damage(user.getDamageSources().playerAttack(user), damage);
        }

        ItemStack scales = new ItemStack(scaleItem, amount);
        if (!user.getInventory().insertStack(scales)) {
            user.dropItem(scales, false);
        }

        DRAGON_COOLDOWNS.put(dragon.getUuid(), now + AddonConfig.ticksFromSeconds(cdCfg));

        // Durability damage
        if (!user.isCreative()) {
            stack.damage(1, user, LivingEntity.getSlotForHand(hand));
        }

        user.getItemCooldownManager().set(this, 20);
        return ActionResult.SUCCESS;
    }

    private static class ScoldingState {
        final long firstTime;
        int count;

        ScoldingState(long firstTime, int count) {
            this.firstTime = firstTime;
            this.count = count;
        }
    }

    public static void clearCache() { DRAGON_COOLDOWNS.clear(); SCOLDING.clear(); }
}
