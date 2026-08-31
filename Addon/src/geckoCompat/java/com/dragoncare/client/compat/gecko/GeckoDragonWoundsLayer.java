package com.dragoncare.client.compat.gecko;

import com.dragoncare.DragonCare;
import com.dragoncare.client.ClientWoundDebugState;
import com.dragoncare.client.WoundFrameSelector;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Locale;

public final class GeckoDragonWoundsLayer extends GeoRenderLayer<GeoAnimatable> {
    private static final Identifier[] WOUND_TEXTURES = createTextureIds();

    public GeckoDragonWoundsLayer(GeoRenderer<GeoAnimatable> renderer) {
        super(renderer);
    }

    @Override
    public void render(MatrixStack matrices, GeoAnimatable animatable, BakedGeoModel bakedModel,
                       RenderLayer renderType, VertexConsumerProvider vertexConsumers,
                       VertexConsumer vertexConsumer, float tickDelta, int light, int overlay) {
        if (!(animatable instanceof DragonBaseEntity dragon)) {
            return;
        }
        if (dragon.isModelDead() && dragon.isSkeletal()) {
            return;
        }
        int debugPercent = ClientWoundDebugState.getPercent(dragon.getUuid());
        int frame = debugPercent == ClientWoundDebugState.NO_OVERRIDE
                ? WoundFrameSelector.fromHealth(dragon.getHealth(), dragon.getMaxHealth())
                : WoundFrameSelector.fromPercent(debugPercent);
        if (frame == 0) {
            return;
        }
        RenderLayer wounds = RenderLayer.getEntityTranslucent(WOUND_TEXTURES[frame]);
        getRenderer().reRender(bakedModel, matrices, vertexConsumers, animatable, wounds,
                vertexConsumers.getBuffer(wounds), tickDelta, light, OverlayTexture.DEFAULT_UV, -1);
    }

    private static Identifier[] createTextureIds() {
        Identifier[] textures = new Identifier[WoundFrameSelector.MAX_FRAME + 1];
        for (int frame = 1; frame < textures.length; frame++) {
            String path = String.format(Locale.ROOT,
                    "textures/entity/overlay/wounds/wound_%03d.png", frame);
            textures[frame] = Identifier.of(DragonCare.MOD_ID, path);
        }
        return textures;
    }
}
