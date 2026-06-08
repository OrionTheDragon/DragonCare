package com.dragoncare.mechanics.ai;

import com.dragoncare.mechanics.DragonFamilyManager;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumSet;
import java.util.List;

/**
 * AI goal that forces a parent dragon to stay close to their babies.
 * If they wander or fly too far away, they will stop what they are doing and return.
 */
public class ParentProtectBabiesGoal extends Goal {

    private final DragonBaseEntity parent;
    private final double maxDistance;
    private final double speed;
    private DragonBaseEntity targetBaby;
    private int checkCooldown = 0;
    private int pathingTimeout = 0;
    private int pathUpdateTicks = 0;

    public ParentProtectBabiesGoal(DragonBaseEntity parent, double maxDistance, double speed) {
        this.parent = parent;
        this.maxDistance = maxDistance;
        this.speed = speed;
        // Controlling MOVE allows it to override wandering and soaring
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (parent.getDragonStage() < 3) return false;
        if (!parent.canMove() || parent.isModelDead()) return false;
        
        // Don't interrupt active combat
        if (parent.getTarget() != null) return false;

        // Don't interrupt sleep unless a baby woke them up (handled elsewhere)
        if (parent.isSleeping()) return false;

        if (checkCooldown > 0) {
            checkCooldown--;
            return false;
        }

        List<DragonBaseEntity> babies = DragonFamilyManager.getLoadedBabies(parent);
        if (babies.isEmpty()) {
            checkCooldown = 20 + parent.getRandom().nextInt(20); // Throttle checking if no babies are loaded
            return false;
        }

        double maxDistSq = maxDistance * maxDistance;
        double limitDistSq = 128.0D * 128.0D; // If the baby is beyond 128 blocks, they consider it lost
        double maxDistFromAny = 0;
        DragonBaseEntity farthestBaby = null;

        for (DragonBaseEntity baby : babies) {
            if (baby.isAlive() && baby.getWorld() == parent.getWorld()) {
                double distSq = parent.squaredDistanceTo(baby);
                if (distSq > maxDistFromAny && distSq < limitDistSq) {
                    maxDistFromAny = distSq;
                    farthestBaby = baby;
                }
            }
        }

        // If they are farther than the max allowed distance from ANY of their babies, return to the farthest one
        if (maxDistFromAny > maxDistSq && farthestBaby != null) {
            this.targetBaby = farthestBaby;
            return true;
        }

        checkCooldown = 10 + parent.getRandom().nextInt(10);
        return false;
    }

    @Override
    public boolean shouldContinue() {
        if (!parent.canMove() || parent.isModelDead() || parent.getTarget() != null) return false;
        if (targetBaby == null || !targetBaby.isAlive() || targetBaby.getWorld() != parent.getWorld()) return false;

        // Stop returning when they get close enough (e.g., within 10 blocks) or if the pathing times out
        return parent.squaredDistanceTo(targetBaby) > 100.0D && pathingTimeout > 0;
    }

    @Override
    public void start() {
        if (parent.isFlying() || parent.isHovering()) {
            // Force landing sequence if they are in the air
            parent.setFlying(false);
            parent.setHovering(false);
        }
        this.pathingTimeout = 200; // Give them 10 seconds to try and reach the baby
        this.pathUpdateTicks = 0;
        parent.getNavigation().startMovingTo(targetBaby, this.speed);
    }

    @Override
    public void stop() {
        this.targetBaby = null;
        parent.getNavigation().stop();
        // Give them a short break before evaluating again so they don't stutter if stuck
        this.checkCooldown = 20 + parent.getRandom().nextInt(20); 
    }

    @Override
    public void tick() {
        if (targetBaby != null) {
            this.pathingTimeout--;
            // Update path periodically
            if (--this.pathUpdateTicks <= 0) {
                this.pathUpdateTicks = 20;
                parent.getNavigation().startMovingTo(targetBaby, this.speed);
            }
        }
    }
}
