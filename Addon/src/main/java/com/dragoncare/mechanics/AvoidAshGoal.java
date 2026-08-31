package com.dragoncare.mechanics;

import com.dragoncare.mechanics.ai.DragonAiGuards;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Flee-from-ash AI. Reuses the ash detection already performed by
 * {@link AshPoisoningSystem#onEntityTick}: when the system scanner spots ash near
 * a vulnerable mob it caches the block position, and this goal simply reacts to
 * that signal — no separate block scan happens here.
 * <p>Modeled on piglins fleeing from soul fire.</p>
 */
@SuppressWarnings("this-escape")
public class AvoidAshGoal extends Goal {

    private static final int FLEE_HORIZONTAL = 16;
    private static final int FLEE_VERTICAL = 7;
    /** Min ticks between pathfinding attempts. Prevents canStart() from re-running
     *  the expensive {@code findPathTo} every 2 ticks while ash is still cached. */
    private static final int RETRY_COOLDOWN = 20;

    private final PathAwareEntity mob;
    private final double speed;

    private Path fleePath;
    private int retryCooldown;

    public AvoidAshGoal(PathAwareEntity mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (mob instanceof com.iafenvoy.iceandfire.entity.DragonBaseEntity dragon
                && DragonAiGuards.isMovementLocked(dragon)) {
            return false;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return false;
        }

        if (!com.dragoncare.config.AddonConfig.ASH_POISONING_ENABLED.get()) return false;

        BlockPos ashPos = AshPoisoningSystem.getNearbyAshFor(mob.getUuid());
        if (ashPos == null) return false;

        retryCooldown = RETRY_COOLDOWN;

        Vec3d ashVec = Vec3d.ofCenter(ashPos);
        Vec3d fleeVec = NoPenaltyTargeting.findFrom(mob, FLEE_HORIZONTAL, FLEE_VERTICAL, ashVec);
        if (fleeVec == null) return false;

        // Don't bother fleeing toward a spot that's closer to the ash
        if (fleeVec.squaredDistanceTo(ashVec) < mob.squaredDistanceTo(ashVec)) return false;

        this.fleePath = mob.getNavigation().findPathTo(fleeVec.x, fleeVec.y, fleeVec.z, 0);
        return this.fleePath != null;
    }

    @Override
    public boolean shouldContinue() {
        if (mob instanceof com.iafenvoy.iceandfire.entity.DragonBaseEntity dragon
                && DragonAiGuards.isMovementLocked(dragon)) {
            return false;
        }
        return !mob.getNavigation().isIdle();
    }

    @Override
    public void start() {
        mob.getNavigation().startMovingAlong(fleePath, speed);
    }

    @Override
    public void stop() {
        fleePath = null;
    }
}
