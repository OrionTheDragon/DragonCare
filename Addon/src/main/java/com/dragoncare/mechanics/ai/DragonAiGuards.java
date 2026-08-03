package com.dragoncare.mechanics.ai;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;

public final class DragonAiGuards {
    private DragonAiGuards() {}

    public static boolean isMovementLocked(DragonBaseEntity dragon) {
        return dragon.isModelDead()
                || dragon.isInSittingPose()
                || dragon.isSleeping()
                || !dragon.canMove();
    }
}
