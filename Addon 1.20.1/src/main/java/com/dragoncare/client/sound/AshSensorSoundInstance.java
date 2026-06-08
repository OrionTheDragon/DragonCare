package com.dragoncare.client.sound;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/**
 * Looping idle hum that follows the player when an ash sensor is held in
 * auto mode and the ash level is below the geiger threshold. The elevated
 * geiger sound is played independently by {@link GeigerSoundPlayer} and is
 * not represented as a {@code SoundInstance}.
 *
 * <p>The handler ({@link AshSensorSoundHandler}) updates the volume each
 * client tick to fade the hum in and out smoothly. The instance is
 * self-stopping: if the player vanishes from the client world it calls
 * {@link #setDone()}.
 */
public class AshSensorSoundInstance extends MovingSoundInstance {

    private final PlayerEntity player;

    public AshSensorSoundInstance(PlayerEntity player, SoundEvent event, float initialVolume) {
        super(event, SoundCategory.PLAYERS, Random.create());
        this.player = player;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = initialVolume;
        this.pitch = 1.0F;
        this.relative = true;
        this.attenuationType = AttenuationType.NONE;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    /** Setter for volume — exposed so the tick handler can fade in/out. */
    public void setVolume(float volume) {
        this.volume = volume;
    }

    /** Current volume (for fade bookkeeping). */
    public float getVolume() {
        return this.volume;
    }

    @Override
    public void tick() {
        if (!player.isAlive() || player.isRemoved()) {
            this.setDone();
            return;
        }
        // Keep the source coordinates aligned with the player. Combined with
        // relative=true + AttenuationType.NONE this gives a constant-volume
        // headphone effect regardless of motion.
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}


