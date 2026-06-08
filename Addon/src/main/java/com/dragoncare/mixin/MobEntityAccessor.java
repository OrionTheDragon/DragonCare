package com.dragoncare.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the protected {@code goalSelector} field of {@link MobEntity} so we can
 *  attach the ash-avoidance goal from outside the package. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {

    @Accessor("goalSelector")
    GoalSelector dragoncare$getGoalSelector();
}
