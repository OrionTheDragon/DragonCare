package com.dragoncare.mechanics.ai;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import net.minecraft.entity.ai.goal.Goal;
import java.util.EnumSet;
import java.util.List;

/**
 * AI goal that makes baby dragons (stages 1 and 2, both tamed and wild)
 * follow and stay close to older dragons of the same type.
 *
 * Requirements:
 * - Stage 1 baby (age < 25 days) follows adults starting from Stage 3 (age >= 50 days).
 * - Stage 2 juvenile (age 25-49 days) follows adults starting from Stage 4 (age >= 75 days).
 * - Strict type matching: fire to fire, ice to ice, lightning to lightning.
 */
@SuppressWarnings("this-escape")
public class BabyDragonFollowAdultGoal extends Goal {
    private final EntityDragonBase dragon;
    private final double speed;
    private EntityDragonBase targetAdult;
    private int updateCountdownTicks;
    private int checkCooldown = 0; // Throttling variable
    private boolean cachedTemptResult = false;
    private int temptCheckCooldown = 0;

    public BabyDragonFollowAdultGoal(EntityDragonBase dragon, double speed) {
        this.dragon = dragon;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!dragon.canMove() || dragon.isModelDead()) {
            return false;
        }

        // If food has priority over mother, check if any player is tempting nearby
        if (!com.dragoncare.config.AddonConfig.MOTHER_PRIORITY_OVER_FOOD.get()) {
            if (temptCheckCooldown > 0) {
                temptCheckCooldown--;
            } else {
                cachedTemptResult = isPlayerTemptingNearby();
                temptCheckCooldown = 10;
            }
            if (cachedTemptResult) {
                return false;
            }
        }

        // Only baby stages (1 and 2) follow older dragons
        int age = dragon.getAgeInDays();
        if (age >= 50) {
            return false;
        }

        // Must not have an active combat target
        if (dragon.getTarget() != null) {
            return false;
        }

        if (this.checkCooldown > 0) {
            this.checkCooldown--;
            return false;
        }

        targetAdult = findNearbySuitableAdult();
        if (targetAdult == null) {
            this.checkCooldown = 5 + this.dragon.getRandom().nextInt(5); // Throttle if no adults around
            return false;
        }

        // If mother is sleeping or resting at home, babies can roam freely around the nest/cave
        if (targetAdult.isSleeping() || targetAdult.isInSittingPose()) {
            this.checkCooldown = 10 + this.dragon.getRandom().nextInt(10); // Throttle
            return false;
        }

        double adultRadius = targetAdult.getWidth() / 2.0;
        double startDist = adultRadius + 4.0; // start following if farther than 4 blocks from edge
        
        if (dragon.squaredDistanceTo(targetAdult) <= (startDist * startDist)) {
            this.checkCooldown = 10 + this.dragon.getRandom().nextInt(10); // Throttle while hanging around
            return false;
        }
        
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (!dragon.canMove() || dragon.isModelDead() || dragon.getTarget() != null) {
            return false;
        }

        // If food has priority over mother, use cached tempt result
        if (!com.dragoncare.config.AddonConfig.MOTHER_PRIORITY_OVER_FOOD.get()) {
            if (cachedTemptResult) {
                return false;
            }
        }

        int age = dragon.getAgeInDays();
        if (age >= 50) {
            return false;
        }

        if (targetAdult == null || targetAdult.isRemoved() || targetAdult.isModelDead()) {
            return false;
        }

        // Verify target remains suitable (e.g. hasn't somehow shrunken or aged out of bounds)
        if (!isSuitableAdult(targetAdult, age)) {
            return false;
        }

        // Stop following if adult decides to sleep/sit
        if (targetAdult.isSleeping() || targetAdult.isInSittingPose()) {
            return false;
        }

        double adultRadius = targetAdult.getWidth() / 2.0;
        double stopDistSq = (adultRadius + 2.5) * (adultRadius + 2.5); // stop 2.5 blocks from edge
        double maxWanderDistSq = (adultRadius + 30.0) * (adultRadius + 30.0); // lose track if >30 blocks from edge
        
        double distSq = dragon.squaredDistanceTo(targetAdult);
        return distSq >= stopDistSq && distSq <= maxWanderDistSq;
    }

    @Override
    public void start() {
        this.updateCountdownTicks = 0;
    }

    @Override
    public void stop() {
        this.targetAdult = null;
        this.dragon.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetAdult == null) return;

        // Face the target
        dragon.getLookControl().lookAt(targetAdult, 10.0F, (float) dragon.getMaxLookPitchChange());

        if (--this.updateCountdownTicks <= 0) {
            this.updateCountdownTicks = 10 + dragon.getRandom().nextInt(10); // refresh every 10-20 ticks
            dragon.getNavigation().startMovingTo(targetAdult, this.speed);
        }
    }

    private EntityDragonBase findNearbySuitableAdult() {
        int age = dragon.getAgeInDays();
        double searchRadius = 24.0D;

        // Search for dragons of the EXACT same class (Fire/Ice/Lightning subclass)
        @SuppressWarnings("unchecked")
        List<? extends EntityDragonBase> dragons = dragon.getWorld().getEntitiesByClass(
                (Class<? extends EntityDragonBase>) dragon.getClass(),
                dragon.getBoundingBox().expand(searchRadius, 8.0D, searchRadius),
                other -> other != dragon && !other.isModelDead() && isSuitableAdult(other, age)
        );

        EntityDragonBase closest = null;
        double closestDist = Double.MAX_VALUE;

        for (EntityDragonBase other : dragons) {
            double dist = dragon.squaredDistanceTo(other);
            if (dist < closestDist) {
                closest = other;
                closestDist = dist;
            }
        }

        return closest;
    }

    private boolean isSuitableAdult(EntityDragonBase other, int babyAge) {
        int otherStage = other.getDragonStage();
        if (babyAge < 25) {
            // Stage 1 baby: follows adults starting from Stage 3 (age >= 50 days)
            return otherStage >= 3;
        } else {
            // Stage 2 juvenile: follows adults starting from Stage 4 (age >= 75 days)
            return otherStage >= 4;
        }
    }

    private boolean isPlayerTemptingNearby() {
        if (dragon.isTamed()) return false;
        
        List<net.minecraft.entity.player.PlayerEntity> players = dragon.getWorld().getEntitiesByClass(
                net.minecraft.entity.player.PlayerEntity.class,
                dragon.getBoundingBox().expand(10.0D, 3.0D, 10.0D),
                p -> !p.isSpectator() && !p.isCreative()
        );
        for (net.minecraft.entity.player.PlayerEntity p : players) {
            boolean holdsFood = p.getMainHandStack().isOf(com.dragoncare.item.ModItems.EXCEPTIONAL_DRAGON_MEAL.get())
                    || p.getOffHandStack().isOf(com.dragoncare.item.ModItems.EXCEPTIONAL_DRAGON_MEAL.get());
            if (holdsFood) {
                return true;
            }
        }
        return false;
    }
}



