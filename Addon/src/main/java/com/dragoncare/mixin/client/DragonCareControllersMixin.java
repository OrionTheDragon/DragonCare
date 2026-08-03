package com.dragoncare.mixin.client;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
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

    @Shadow
    public float sleepProgress;

    @Shadow
    public abstract boolean isSleeping();

    @Shadow
    public abstract boolean isBreathingFire();

    @Shadow
    public abstract com.iafenvoy.uranus.animation.Animation getAnimation();

    @Shadow
    public abstract boolean isModelDead();

    @Shadow
    public abstract boolean isHovering();

    @Shadow
    public abstract boolean isFlying();

    @Shadow
    public abstract boolean isInWater();

    @Inject(method = "registerControllers", at = @At("HEAD"), remap = false, cancellable = true)
    public void dragoncare$replaceControllers(AnimatableManager.ControllerRegistrar controllers, CallbackInfo ci) {
        DragonBaseEntity dragon = (DragonBaseEntity) (Object) this;

        // Controller 1: "main" (Looping states: sitting, sleeping, flying, walking, swimming, dead)
        controllers.add(new AnimationController<>(dragon, "main", 10, state -> {
            boolean isDead = this.isModelDead();
            boolean isSwim = this.isInWater() && this.isHovering();
            boolean isFlight = this.isFlying() || this.isHovering();
            
            if (isDead) {
                return dragoncare$playLooping(state, "dead", 1.0f, 2);
            } else if (isSwim) {
                return dragoncare$playLooping(state, "swim", 1.0f, 2);
            } else if (isFlight) {
                if (this.isBreathingFire()) {
                    return dragoncare$playLooping(state, "attack_blast_breath", 1.0f, 2);
                }
                return dragoncare$playLooping(state, "flight", 0.4f, 2);
            } else {
                // GROUND
                if (this.isSleeping() || this.sleepProgress > 0.0f) {
                    return dragoncare$playLooping(state, "sleeping", 1.0f, 0);
                }
                if (dragon.sitProgress > 0.0f) {
                    return dragoncare$playLooping(state, "sitting", 1.0f, 2);
                }
                return dragoncare$playLooping(state, state.isMoving() ? "walk" : "ground", state.isMoving() ? 0.4f : 1.0f, 2);
            }
        }));

        // Controller 2: "action" (One-off states: roar, bite, etc.)
        controllers.add(new AnimationController<>(dragon, "action", 0, state -> {
            boolean isDead = this.isModelDead();
            boolean isSwim = this.isInWater() && this.isHovering();
            boolean isFlight = this.isFlying() || this.isHovering();
            
            com.iafenvoy.uranus.animation.Animation currentAnimation = this.getAnimation();

            if (!isDead && !isSwim && isFlight) {
                if (currentAnimation == DragonBaseEntity.ANIMATION_FIRECHARGE) {
                    return dragoncare$playAction(state, "attack_blast_charge");
                }
            } else if (!isDead && !isSwim && !isFlight) { // GROUND
                String action = dragoncare$getGeckoAction(currentAnimation);
                if (action != null) {
                    return dragoncare$playAction(state, action);
                }
            }
            return PlayState.STOP;
        }));

        ci.cancel();
    }

    private PlayState dragoncare$playLooping(AnimationState<?> state, String animation, float speed, int transitionLength) {
        state.getController().transitionLength(transitionLength);
        state.setControllerSpeed(speed);
        return state.setAndContinue(RawAnimation.begin().thenLoop(dragoncare$geckoAnimationId(animation)));
    }

    private PlayState dragoncare$playAction(AnimationState<?> state, String animation) {
        state.getController().transitionLength(0);
        return state.setAndContinue(RawAnimation.begin().thenPlay(dragoncare$geckoAnimationId(animation)));
    }

    private String dragoncare$geckoAnimationId(String animation) {
        String prefix = this.getClass().getSimpleName().contains("Fire") ? "animation.firedragon.firedragon_" : 
                       (this.getClass().getSimpleName().contains("Ice") ? "animation.icedragon." : "animation.lightningdragon.");
        return prefix + animation;
    }

    private String dragoncare$getGeckoAction(com.iafenvoy.uranus.animation.Animation animation) {
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
