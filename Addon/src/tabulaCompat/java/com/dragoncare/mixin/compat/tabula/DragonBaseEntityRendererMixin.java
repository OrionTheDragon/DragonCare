package com.dragoncare.mixin.compat.tabula;

import com.dragoncare.DragonCare;
import com.dragoncare.client.compat.tabula.TabulaDragonWoundsFeature;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.render.entity.DragonBaseEntityRenderer;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DragonBaseEntityRenderer.class, remap = false)
public abstract class DragonBaseEntityRendererMixin
        extends MobEntityRenderer<DragonBaseEntity, TabulaModel<DragonBaseEntity>> {
    protected DragonBaseEntityRendererMixin(EntityRendererFactory.Context context,
                                             TabulaModel<DragonBaseEntity> model,
                                             float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void dragoncare$addWoundsFeature(EntityRendererFactory.Context context,
                                              TabulaModel<DragonBaseEntity> model,
                                              CallbackInfo ci) {
        addFeature(new TabulaDragonWoundsFeature(this));
        DragonCare.LOGGER.debug("Attached DragonCare Tabula wound layer to {}", getClass().getName());
    }
}
