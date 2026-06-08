package com.dragoncare.mechanics.ai;

/**
 * Interface injected into DragonBaseEntity to manage a cooldown
 * during which the dragon is prevented from falling asleep.
 */
public interface DragonSleepPreventer {
    void dragoncare$setSleepCooldown(int ticks);
    int dragoncare$getSleepCooldown();
}
