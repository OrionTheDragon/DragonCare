package com.dragoncare.mixin.client;

import com.dragoncare.DragonCare;
import com.dragoncare.client.DragonWoundsFeatureRenderer;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.render.entity.DragonBaseEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mixin(DragonBaseEntityRenderer.class)
public abstract class DragonBaseEntityRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dragoncare$addWoundsLayer(EntityRendererFactory.Context context, CallbackInfo ci) {
        GeoEntityRenderer<DragonBaseEntity> renderer =
                (GeoEntityRenderer<DragonBaseEntity>) (Object) this;
        renderer.addRenderLayer(new DragonWoundsFeatureRenderer<>(renderer));
        DragonCare.LOGGER.debug("Attached DragonCare Geo wound layer to {}", getClass().getName());
    }
}
