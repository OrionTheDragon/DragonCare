package com.dragoncare.mixin.compat.gecko;

import com.dragoncare.DragonCare;
import com.dragoncare.client.compat.gecko.GeckoDragonWoundsLayer;
import com.iafenvoy.iceandfire.render.entity.DragonBaseEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mixin(value = DragonBaseEntityRenderer.class, remap = false)
public abstract class DragonBaseEntityRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dragoncare$addWoundsLayer(EntityRendererFactory.Context context, CallbackInfo ci) {
        GeoEntityRenderer renderer = (GeoEntityRenderer) (Object) this;
        GeoRenderer<GeoAnimatable> geoRenderer = (GeoRenderer<GeoAnimatable>) renderer;
        renderer.addRenderLayer(new GeckoDragonWoundsLayer(geoRenderer));
        DragonCare.LOGGER.debug("Attached DragonCare Gecko wound layer to {}", getClass().getName());
    }
}
