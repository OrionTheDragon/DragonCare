package com.dragoncare.client;

import com.dragoncare.DragonCare;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.iafenvoy.uranus.client.model.TabulaModel;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class DragonWoundsFeatureRenderer
        extends FeatureRenderer<EntityDragonBase, TabulaModel<EntityDragonBase>> {
    private static final int STEPS_PER_LEVEL = 32;
    private static final Identifier[] WOUND_TEXTURES = createTextureIds();

    public DragonWoundsFeatureRenderer(
            MobEntityRenderer<EntityDragonBase, TabulaModel<EntityDragonBase>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       EntityDragonBase dragon, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        if (dragon.isSkeletal()) {
            return;
        }

        int debugPercent = ClientWoundDebugState.getPercent(dragon.getUuid());
        int frame = debugPercent == ClientWoundDebugState.NO_OVERRIDE
                ? getWoundFrame(dragon.getHealth(), dragon.getMaxHealth())
                : getWoundFrameForPercent(debugPercent);
        if (frame == 0) {
            return;
        }

        this.getContextModel().render(
                matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(WOUND_TEXTURES[frame])),
                light,
                OverlayTexture.DEFAULT_UV,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
    }

    static int getWoundFrame(float health, float maxHealth) {
        if (maxHealth <= 0.0F) {
            return 0;
        }

        float healthRatio = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
        if (healthRatio >= 1.0F) {
            return 0;
        }

        if (healthRatio >= 0.66F) {
            return frameInRange((1.0F - healthRatio) / 0.34F);
        }
        if (healthRatio >= 0.33F) {
            return STEPS_PER_LEVEL + frameInRange((0.66F - healthRatio) / 0.33F);
        }
        return STEPS_PER_LEVEL * 2 + frameInRange((0.33F - healthRatio) / 0.33F);
    }

    static int getWoundFrameForPercent(int percent) {
        if (percent <= 0) {
            return 0;
        }
        int clamped = Math.min(100, percent);
        return Math.min(STEPS_PER_LEVEL * 3,
                (int) Math.ceil(clamped * (STEPS_PER_LEVEL * 3) / 100.0D));
    }

    private static int frameInRange(float progress) {
        return Math.max(1, Math.min(STEPS_PER_LEVEL, (int) Math.ceil(progress * STEPS_PER_LEVEL)));
    }

    private static Identifier[] createTextureIds() {
        Identifier[] textures = new Identifier[STEPS_PER_LEVEL * 3 + 1];
        for (int frame = 1; frame < textures.length; frame++) {
            String path = String.format(Locale.ROOT, "textures/entity/overlay/wounds/wound_%03d.png", frame);
            textures[frame] = Identifier.of(DragonCare.MOD_ID, path);
        }
        return textures;
    }
}
