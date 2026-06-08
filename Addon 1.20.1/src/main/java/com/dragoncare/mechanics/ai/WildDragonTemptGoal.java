package com.dragoncare.mechanics.ai;

import com.dragoncare.item.ModItems;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.Ingredient;

public class WildDragonTemptGoal extends TemptGoal {
    private final EntityDragonBase dragon;

    public WildDragonTemptGoal(EntityDragonBase dragon, double speed) {
        // canBeScared = true makes them stop/run if player moves too fast (mimics Ocelot)
        super(dragon, speed, Ingredient.ofItems(ModItems.EXCEPTIONAL_DRAGON_MEAL.get()), true);
        this.dragon = dragon;
    }

    @Override
    public boolean canStart() {
        if (dragon.isSleeping()) return false;
        // Works for all Stage 1 and Stage 2 dragons (under 50 days)
        if (dragon.getAgeInDays() >= 50) return false;
        
        boolean canStart = super.canStart();
        if (canStart && this.closestPlayer != null) {
            // Must be standing still (very low velocity) or sneaking
            if (!this.closestPlayer.isSneaking() && this.closestPlayer.getVelocity().lengthSquared() > 0.01D) {
                return false;
            }
        }
        return canStart;
    }

    @Override
    public boolean shouldContinue() {
        if (dragon.getAgeInDays() >= 50) return false;
        
        boolean shouldContinue = super.shouldContinue();
        if (shouldContinue && this.closestPlayer != null) {
            if (!this.closestPlayer.isSneaking() && this.closestPlayer.getVelocity().lengthSquared() > 0.01D) {
                return false;
            }
        }
        return shouldContinue;
    }

    @Override
    public void tick() {
        super.tick();
        // Prevent the dragon from falling asleep while following the food
        if (dragon instanceof DragonSleepPreventer preventer) {
            preventer.dragoncare$setSleepCooldown(40); // Prevent sleep for 2 seconds (constantly refreshed)
        }
    }
}



