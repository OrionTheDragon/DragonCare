package com.dragoncare.client.sound;

import com.dragoncare.DragonCare;

import com.dragoncare.item.ModItems;
import com.dragoncare.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;

/**
 * Client-side controller for the ash-sensor looping sounds.
 *
 * <p>Each client tick this checks if the local player is holding an ash
 * sensor in auto mode with the sensor turned on. Two layers play depending
 * on the ash level, with a smooth crossfade between them:
 * <ul>
 *   <li>{@code level < 0.10}: {@link ModSounds#ASH_METER_NORMAL} idle hum,
 *       routed through Minecraft's sound system as an {@link AshSensorSoundInstance}.</li>
 *   <li>{@code level >= 0.10}: the geiger track via {@link GeigerSoundPlayer},
 *       which manages its own OpenAL source so it can seek inside the file
 *       (position = level * track length) and ramp playback speed up to 1.5x
 *       at maximum level.</li>
 * </ul>
 * Manual-mode sensors stay silent regardless of their on/off state.
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID, value = Dist.CLIENT)
public final class AshSensorSoundHandler {

    /** Ash level below which the idle hum plays; the geiger track kicks in at &ge; this. */
    private static final float GEIGER_THRESHOLD = 0.10F;

    /** Steady-state volume of the idle hum instance. */
    private static final float HUM_TARGET_VOLUME = 0.7F;
    /** Volume change per tick during the hum's fade-in/out. */
    private static final float HUM_VOLUME_FADE_PER_TICK = 0.07F;
    /** Below this volume the hum instance is stopped to avoid silent voices. */
    private static final float HUM_MIN_VOLUME = 0.01F;

    private static AshSensorSoundInstance humSound;
    private static float humVolume;

    private AshSensorSoundHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            fadeOutHum(mc);
            GeigerSoundPlayer.update(0.0F, false);
            return;
        }

        // Respect master kill-switch and the sound-only opt-out. In both cases
        // silence the sensor immediately and skip the per-tick hand scan so
        // disabling the feature actually saves cycles on the client.
        if (!com.dragoncare.config.AddonConfig.ASH_POISONING_ENABLED.get()
                || !com.dragoncare.config.AddonConfig.ASH_SENSOR_SOUND_ENABLED.get()) {
            fadeOutHum(mc);
            GeigerSoundPlayer.update(0.0F, false);
            return;
        }

        PlayerEntity player = mc.player;
        ItemStack activeStack = findActiveAutoSensor(player);
        if (activeStack == null) {
            fadeOutHum(mc);
            GeigerSoundPlayer.update(0.0F, false);
            return;
        }

        net.minecraft.nbt.NbtCompound nbt = activeStack.getNbt();
        float level = nbt != null && nbt.contains("sensor_level") ? nbt.getFloat("sensor_level") : 0.0F;
        boolean geiger = level >= GEIGER_THRESHOLD;

        GeigerSoundPlayer.update(level, geiger);

        if (geiger) {
            fadeOutHum(mc);
        } else {
            fadeInHum(mc, player);
        }
    }

    @SubscribeEvent
    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        GeigerSoundPlayer.reset();
    }

    private static void fadeInHum(MinecraftClient mc, PlayerEntity player) {
        if (humSound == null || humSound.isDone()) {
            humVolume = 0.0F;
            humSound = new AshSensorSoundInstance(player, ModSounds.ASH_METER_NORMAL.get(), humVolume);
            mc.getSoundManager().play(humSound);
        }
        humVolume = Math.min(HUM_TARGET_VOLUME, humVolume + HUM_VOLUME_FADE_PER_TICK);
        humSound.setVolume(humVolume);
    }

    private static void fadeOutHum(MinecraftClient mc) {
        if (humSound == null) return;
        humVolume = Math.max(0.0F, humVolume - HUM_VOLUME_FADE_PER_TICK);
        if (humVolume <= HUM_MIN_VOLUME) {
            mc.getSoundManager().stop(humSound);
            humSound = null;
            humVolume = 0.0F;
        } else {
            humSound.setVolume(humVolume);
        }
    }

    /**
     * Returns the first active auto-mode ash sensor the player is holding in
     * either hand, or {@code null} if none. Manual-mode sensors are skipped:
     * per the spec they must stay silent even when "on".
     */
    private static ItemStack findActiveAutoSensor(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (isActiveAutoSensor(main)) return main;
        ItemStack off = player.getOffHandStack();
        if (isActiveAutoSensor(off)) return off;
        return null;
    }

    private static boolean isActiveAutoSensor(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(ModItems.ASH_SENSOR.get())) return false;
        net.minecraft.nbt.NbtCompound nbt = stack.getNbt();
        if (nbt == null) return false;
        if (!nbt.getBoolean("sensor_on")) return false;
        // Manual mode: explicitly silent (only emits the per-press button click
        // handled in the item's use() method).
        return !nbt.getBoolean("sensor_manual");
    }
}


