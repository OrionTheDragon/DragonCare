package com.dragoncare.client.compat.tabula;

import com.dragoncare.DragonCare;
import com.dragoncare.client.ClientWoundDebugState;
import com.dragoncare.client.WoundFrameSelector;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class TabulaDragonWoundsFeature
        extends FeatureRenderer<DragonBaseEntity, TabulaModel<DragonBaseEntity>> {
    private static final Identifier[] WOUND_TEXTURES = createTextureIds();

    public TabulaDragonWoundsFeature(
            FeatureRendererContext<DragonBaseEntity, TabulaModel<DragonBaseEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                       DragonBaseEntity dragon, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
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
        getContextModel().render(matrices,
                consumers.getBuffer(RenderLayer.getEntityTranslucent(WOUND_TEXTURES[frame])),
                light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
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
