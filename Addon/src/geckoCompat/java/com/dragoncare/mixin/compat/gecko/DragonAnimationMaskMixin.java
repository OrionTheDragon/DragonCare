package com.dragoncare.mixin.compat.gecko;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.keyframe.BoneAnimationQueue;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Map;

@Mixin(value = AnimationController.class, remap = false)
public abstract class DragonAnimationMaskMixin<T extends GeoAnimatable> {
    @Shadow public abstract String getName();
    @Shadow public abstract Map<String, BoneAnimationQueue> getBoneAnimationQueues();

    @Inject(method = "process", at = @At("TAIL"))
    private void dragoncare$filterBones(GeoModel<T> model, AnimationState<T> state,
                                        Map<String, GeoBone> bones,
                                        Map<String, BoneSnapshot> snapshots,
                                        double seekTime, boolean crashWhenCantFindBone,
                                        CallbackInfo ci) {
        if (!(state.getAnimatable() instanceof DragonBaseEntity dragon)
                || dragon.sitProgress <= 0.0F || !"action".equals(getName())) {
            return;
        }
        AnimationController<?> controller = (AnimationController<?>) (Object) this;
        if (controller.getCurrentAnimation() == null
                || controller.getCurrentAnimation().animation().name() == null
                || !controller.getCurrentAnimation().animation().name().contains("roar")) {
            return;
        }
        for (Map.Entry<String, BoneAnimationQueue> entry : getBoneAnimationQueues().entrySet()) {
            String boneName = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (boneName.contains("neck") || boneName.contains("head") || boneName.contains("jaw")) {
                continue;
            }
            BoneAnimationQueue queue = entry.getValue();
            queue.rotationXQueue().clear();
            queue.rotationYQueue().clear();
            queue.rotationZQueue().clear();
            queue.positionXQueue().clear();
            queue.positionYQueue().clear();
            queue.positionZQueue().clear();
            queue.scaleXQueue().clear();
            queue.scaleYQueue().clear();
            queue.scaleZQueue().clear();
        }
    }
}
