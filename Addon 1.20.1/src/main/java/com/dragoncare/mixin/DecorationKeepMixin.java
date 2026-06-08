package com.dragoncare.mixin;

import net.minecraft.entity.decoration.AbstractDecorationEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractDecorationEntity.class)
public abstract class DecorationKeepMixin {

    @Inject(method = "canStayAttached", at = @At("HEAD"), cancellable = true)
    private void dragoncare$forceStayAttached(CallbackInfoReturnable<Boolean> cir) {
        AbstractDecorationEntity self = (AbstractDecorationEntity) (Object) this;
        if (!self.getWorld().isClient) {
            // Force decorations to stay attached, mirroring the old FrameKeeperEvents behavior
            cir.setReturnValue(true);
        }
    }
}
