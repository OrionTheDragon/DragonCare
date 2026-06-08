package com.dragoncare.mixin;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.config.AddonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DragonBaseEntity.class)
public abstract class DragonAgingMixin {

    /**
     * Prevents ICE AND FIRE CE from calling setAgingDisabled(true) on wild,
     * neutral, and tamed dragons, controlled by separate config flags.
     *
     * CE calls setAgingDisabled(true) on tamed dragons after hatching/loading,
     * which permanently stops their passive growth. We intercept that here.
     */
    @Inject(method = "setAgingDisabled", at = @At("HEAD"), cancellable = true, remap = false)
    private void dragoncare$preventAgingDisable(boolean isAgingDisabled, CallbackInfo ci) {
        if (!isAgingDisabled) return; // only intercept calls that DISABLE aging
        DragonBaseEntity self = (DragonBaseEntity) (Object) this;

        try {
            if (self.isTamed()) {
                // Tamed dragons: check TAMED_DRAGONS_AGE
                if (AddonConfig.TAMED_DRAGONS_AGE.get()) {
                    ci.cancel();
                }
            } else {
                // Wild / neutral (befriended but not tamed): check WILD_DRAGONS_AGE
                if (AddonConfig.WILD_DRAGONS_AGE.get()) {
                    ci.cancel();
                }
            }
        } catch (Throwable ignored) {
            // Config not fully loaded (e.g. early world gen) — default: allow aging
            ci.cancel();
        }
    }
}
