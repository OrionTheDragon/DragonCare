package com.dragoncare.mixin;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla Minecraft hard-caps GENERIC_MAX_HEALTH at 1024 (via {@link ClampedEntityAttribute#clamp(double)}).
 * This Mixin lifts that cap for the max-health attribute so dragons (and any other entity that needs it)
 * can reach values defined by user/mod config without being silently truncated.
 */
@Mixin(ClampedEntityAttribute.class)
public abstract class ClampedEntityAttributeMixin {

    @Mutable
    @Final
    @Shadow
    private double maxValue;

    // Constructor names are never obfuscated; remapping this selector can put
    // an external mapped owner into the refmap on newer Mixin runtimes.
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void dragoncare$liftMaxHealthCap(String translationKey, double fallback,
                                                   double min, double max, CallbackInfo ci) {
        // Match the max-health attribute robustly: both the legacy
        // "attribute.name.generic.max_health" and any mapping variant without
        // the "generic." infix end with "max_health", and no other vanilla
        // attribute does — so endsWith is safe and version-proof.
        if (translationKey != null && translationKey.endsWith("max_health")) {
            this.maxValue = 10_000_000.0D;
        }
    }
}


