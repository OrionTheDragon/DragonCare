package com.dragoncare.mixin.compat.gecko;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.uranus.animation.Animation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

@Mixin(value = DragonBaseEntity.class, remap = false)
public abstract class DragonCareControllersMixin {
    @Shadow public float sleepProgress;
    @Shadow public abstract boolean isSleeping();
    @Shadow public abstract boolean isBreathingFire();
    @Shadow public abstract Animation getAnimation();
    @Shadow public abstract boolean isModelDead();
    @Shadow public abstract boolean isHovering();
    @Shadow public abstract boolean isFlying();

    @Inject(method = "registerControllers", at = @At("HEAD"), cancellable = true)
    private void dragoncare$replaceControllers(AnimatableManager.ControllerRegistrar controllers,
                                                CallbackInfo ci) {
        DragonBaseEntity dragon = (DragonBaseEntity) (Object) this;
        GeoAnimatable animatable = (GeoAnimatable) (Object) dragon;

        controllers.add(new AnimationController<>(animatable, "main", 10, state -> {
            boolean swimming = dragon.isTouchingWater() && this.isHovering();
            boolean flying = this.isFlying() || this.isHovering();
            if (this.isModelDead()) {
                return dragoncare$playLooping(state, "dead", 1.0F, 2);
            }
            if (swimming) {
                return dragoncare$playLooping(state, "swim", 1.0F, 2);
            }
            if (flying) {
                return dragoncare$playLooping(state,
                        this.isBreathingFire() ? "attack_blast_breath" : "flight",
                        this.isBreathingFire() ? 1.0F : 0.4F, 2);
            }
            if (this.isSleeping() || this.sleepProgress > 0.0F) {
                return dragoncare$playLooping(state, "sleeping", 1.0F, 0);
            }
            if (dragon.sitProgress > 0.0F) {
                return dragoncare$playLooping(state, "sitting", 1.0F, 2);
            }
            return dragoncare$playLooping(state, state.isMoving() ? "walk" : "ground",
                    state.isMoving() ? 0.4F : 1.0F, 2);
        }));

        controllers.add(new AnimationController<>(animatable, "action", 0, state -> {
            boolean swimming = dragon.isTouchingWater() && this.isHovering();
            boolean flying = this.isFlying() || this.isHovering();
            Animation current = this.getAnimation();
            if (!this.isModelDead() && !swimming && flying
                    && current == DragonBaseEntity.ANIMATION_FIRECHARGE) {
                return dragoncare$playAction(state, "attack_blast_charge");
            }
            if (!this.isModelDead() && !swimming && !flying) {
                String action = dragoncare$getGeckoAction(current);
                if (action != null) {
                    return dragoncare$playAction(state, action);
                }
            }
            return PlayState.STOP;
        }));
        ci.cancel();
    }

    private PlayState dragoncare$playLooping(AnimationState<?> state, String animation,
                                              float speed, int transitionLength) {
        state.getController().transitionLength(transitionLength);
        state.setControllerSpeed(speed);
        return state.setAndContinue(RawAnimation.begin()
                .thenLoop(dragoncare$geckoAnimationId(animation)));
    }

    private PlayState dragoncare$playAction(AnimationState<?> state, String animation) {
        state.getController().transitionLength(0);
        return state.setAndContinue(RawAnimation.begin()
                .thenPlay(dragoncare$geckoAnimationId(animation)));
    }

    private String dragoncare$geckoAnimationId(String animation) {
        String simpleName = getClass().getSimpleName();
        String prefix = simpleName.contains("Fire") ? "animation.firedragon.firedragon_"
                : simpleName.contains("Ice") ? "animation.icedragon."
                : "animation.lightningdragon.";
        return prefix + animation;
    }

    private static String dragoncare$getGeckoAction(Animation animation) {
        if (animation == DragonBaseEntity.ANIMATION_FIRECHARGE) return "attack_blast_charge";
        if (animation == DragonBaseEntity.ANIMATION_BITE) return "bite";
        if (animation == DragonBaseEntity.ANIMATION_SHAKEPREY) return "bite_shatter";
        if (animation == DragonBaseEntity.ANIMATION_ROAR) return "roar";
        if (animation == DragonBaseEntity.ANIMATION_EPIC_ROAR) return "epic_roar";
        if (animation == DragonBaseEntity.ANIMATION_TAILWHACK) return "tail_whip";
        if (animation == DragonBaseEntity.ANIMATION_WINGBLAST) return "wing_blast";
        return null;
    }
}
