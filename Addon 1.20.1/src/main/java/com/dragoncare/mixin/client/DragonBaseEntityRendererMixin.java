package com.dragoncare.mixin.client;

import com.dragoncare.client.DragonWoundsFeatureRenderer;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.iceandfire.render.entity.RenderDragonBase;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderDragonBase.class)
public abstract class DragonBaseEntityRendererMixin
        extends MobEntityRenderer<EntityDragonBase, TabulaModel<EntityDragonBase>> {

    protected DragonBaseEntityRendererMixin(EntityRendererFactory.Context context,
                                            TabulaModel<EntityDragonBase> model,
                                            float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void dragoncare$addWoundsFeature(EntityRendererFactory.Context context,
                                             TabulaModel<EntityDragonBase> model,
                                             CallbackInfo ci) {
        this.features.add(0, new DragonWoundsFeatureRenderer(this));
    }
}
