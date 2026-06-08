package com.dragoncare.mechanics.ai;

import com.dragoncare.item.ModItems;
import com.dragoncare.taming.DragonTamingManager;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Makes wild (untamed) dragon babies (age &lt; 26 days) flee from survival-mode players.
 *
 * <p>Key design: Ice &amp; Fire CE's {@link com.iafenvoy.iceandfire.entity.util.dragon.IafDragonLogic}
 * calls {@code getNavigation().stop()} every tick when {@code canMove()} is false
 * (e.g. dragon is sleeping). It also puts untamed dragons to sleep when they have
 * no target. Since our addon nullifies targets for young dragons in
 * {@code LivingChangeTargetEvent}, the dragon immediately falls asleep and
 * navigation is killed each tick вЂ” making a naive flee goal useless.
 *
 * <p>Solution: This goal forcibly wakes the dragon every tick while fleeing,
 * continuously re-issues navigation commands, and applies a temporary speed
 * boost so the baby dragon actually runs fast enough to flee.
 */
public class WildDragonFleePlayerGoal extends Goal {
    private static final java.util.UUID FLEE_SPEED_MODIFIER =
            java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final EntityDragonBase dragon;
    private final double distance;
    private final double speedMultiplier;
    private PlayerEntity targetPlayer;
    private int checkCooldown = 0; // Throttling variable

    public WildDragonFleePlayerGoal(EntityDragonBase dragon, double distance, double speedMultiplier) {
        this.dragon = dragon;
        this.distance = distance;
        this.speedMultiplier = speedMultiplier;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (dragon.isSleeping()) return false;
        if (dragon.isTamed() || dragon.getAgeInDays() > 25) return false;
        if (dragon.isModelDead()) return false;

        if (this.checkCooldown > 0) {
            this.checkCooldown--;
            return false;
        }

        // Find closest survival-mode player in range
        List<PlayerEntity> players = dragon.getWorld().getEntitiesByClass(PlayerEntity.class,
                dragon.getBoundingBox().expand(distance, 3.0D, distance),
                p -> !p.isSpectator() && !p.isCreative());

        if (players.isEmpty()) {
            this.checkCooldown = 5 + this.dragon.getRandom().nextInt(5); // Throttle if no players around
            return false;
        }

        double closestDist = Double.MAX_VALUE;
        targetPlayer = null;
        MinecraftServer server = dragon.getServer();
        for (PlayerEntity p : players) {
            double d = p.squaredDistanceTo(dragon);
            // Fix logical bug where closer befriended player prevents fleeing from farther non-befriended player.
            // Check distance first, but only update closestDist if NOT befriended.
            if (d < closestDist) {
                if (server != null && DragonTamingManager.isBefriended(server, dragon.getUuid(), p.getUuid())) {
                    continue;
                }
                
                // Ocelot-like sneaking mechanic: ignore sneaking players if they are farther than 2 blocks (distSq > 4.0)
                if (p.isSneaking() && d > 4.0D) {
                    continue;
                }

                boolean holdsFood = p.getMainHandStack().isOf(ModItems.EXCEPTIONAL_DRAGON_MEAL.get())
                        || p.getOffHandStack().isOf(ModItems.EXCEPTIONAL_DRAGON_MEAL.get());
                if (holdsFood && (p.isSneaking() || p.getVelocity().lengthSquared() < 0.01)) {
                    continue; // Skip: player is tempting, not scaring
                }
                targetPlayer = p;
                closestDist = d;
            }
        }

        if (targetPlayer == null) {
            this.checkCooldown = 5 + this.dragon.getRandom().nextInt(5); // Throttle if only befriended/tempting players around
            return false;
        }

        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (dragon.isSleeping()) return false;
        if (dragon.isTamed() || dragon.getAgeInDays() > 25 || dragon.isModelDead()) return false;
        if (targetPlayer == null || targetPlayer.isRemoved() || !targetPlayer.isAlive()) return false;
        if (targetPlayer.isSpectator() || targetPlayer.isCreative()) return false;

        // Stop fleeing once the player is far enough away
        return dragon.squaredDistanceTo(targetPlayer) < (distance + 4) * (distance + 4);
    }
    @Override
    public void start() {
        if (dragon instanceof DragonSleepPreventer preventer) {
            preventer.dragoncare$setSleepCooldown(400); // 20 seconds cooldown before falling asleep again
        }
        applySpeedBoost();

        // Cry for help to mother if she's nearby
        if (this.targetPlayer != null) {
            com.dragoncare.mechanics.DragonFamilyManager.callMotherForHelp(dragon, this.targetPlayer);
        }
        navigateAway();
    }

    @Override
    public void tick() {
        navigateAway();
    }

    @Override
    public void stop() {
        removeSpeedBoost();
        this.targetPlayer = null;
    }

    /**
     * Navigate toward a point directly away from the target player.
     * Uses NoPenaltyTargeting first; falls back to a raw vector projection.
     */
    private void navigateAway() {
        if (targetPlayer == null) return;

        Vec3d fleeVec = NoPenaltyTargeting.findFrom(dragon, 16, 7, targetPlayer.getPos());
        if (fleeVec == null) {
            // Fallback: project 12 blocks directly away from the player
            Vec3d dir = dragon.getPos().subtract(targetPlayer.getPos());
            if (dir.lengthSquared() > 0.001) {
                dir = dir.normalize();
                fleeVec = dragon.getPos().add(dir.x * 12.0D, 0.0D, dir.z * 12.0D);
            }
        }

        if (fleeVec != null) {
            dragon.getNavigation().startMovingTo(fleeVec.x, fleeVec.y, fleeVec.z, this.speedMultiplier);
        }
    }

    private void applySpeedBoost() {
        var speedAttr = dragon.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(FLEE_SPEED_MODIFIER) == null) {
            // Double the current movement speed while fleeing
            double baseSpeed = speedAttr.getBaseValue();
            speedAttr.addTemporaryModifier(new EntityAttributeModifier(
                    FLEE_SPEED_MODIFIER, "wild_dragon_flee", baseSpeed, EntityAttributeModifier.Operation.ADDITION));
        }
    }

    private void removeSpeedBoost() {
        var speedAttr = dragon.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(FLEE_SPEED_MODIFIER);
        }
    }
}



