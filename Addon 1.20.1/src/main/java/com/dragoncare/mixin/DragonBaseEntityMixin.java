package com.dragoncare.mixin;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.dragoncare.config.AddonConfig;
import com.dragoncare.mechanics.DragonDirtManager;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.dragoncare.mechanics.ai.DragonSleepPreventer;

/**
 * Overrides the dragon's max HP after CE's own {@code updateAttributes} runs.
 * The override mirrors CE's age-based formula (HP = minHealth + healthStep * age, with minHealth = max * 0.04),
 * but uses the addon-config value as the adult ceiling, allowing values above 1024
 * (the vanilla cap is removed by {@link ClampedEntityAttributeMixin}).
 */
@Mixin(EntityDragonBase.class)
public abstract class DragonBaseEntityMixin implements DragonSleepPreventer {

    @Inject(method = "updateAttributes", at = @At("TAIL"), remap = false)
    private void dragoncare$applyHealthOverride(CallbackInfo ci) {
        EntityDragonBase self = (EntityDragonBase) (Object) this;
        try {
            int configMax = AddonConfig.DRAGON_MAX_HEALTH.get();
            if (configMax <= 0) return;

            EntityAttributeInstance instance = self.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (instance == null) return;

            int age = Math.max(0, Math.min(self.getAgeInDays(), 125));
            double minHealth = configMax * 0.04D;
            double healthStep = (configMax - minHealth) / 125.0D;
            double scaled = Math.round(minHealth + healthStep * age);
            if (scaled < 1.0D) scaled = 1.0D;

            instance.setBaseValue(scaled);
        } catch (Throwable ignored) {
            // Config might not be loaded yet during early world setup вЂ” skip silently
        }
    }

    private int dragoncare$tickCounter = 0;
    private int dragoncare$fleeSleepCooldown = 0;

    @Override
    public void dragoncare$setSleepCooldown(int ticks) {
        this.dragoncare$fleeSleepCooldown = ticks;
    }

    @Override
    public int dragoncare$getSleepCooldown() {
        return this.dragoncare$fleeSleepCooldown;
    }

    @Inject(method = "setInSittingPose", at = @At("HEAD"), cancellable = true)
    private void dragoncare$preventSleeping(boolean sleeping, CallbackInfo ci) {
        if (sleeping && this.dragoncare$fleeSleepCooldown > 0) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void dragoncare$onTick(CallbackInfo ci) {
        EntityDragonBase self = (EntityDragonBase) (Object) this;
        if (!self.getWorld().isClient) {
            if (this.dragoncare$fleeSleepCooldown > 0) {
                this.dragoncare$fleeSleepCooldown--;
            }

            this.dragoncare$tickCounter++;
            if (this.dragoncare$tickCounter >= 20) {
                this.dragoncare$tickCounter = 0;
                DragonDirtManager.tickDragon(self);
            }
        }
    }
}



