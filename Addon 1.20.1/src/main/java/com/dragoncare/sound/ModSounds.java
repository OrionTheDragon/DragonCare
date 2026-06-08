package com.dragoncare.sound;

import com.dragoncare.DragonCare;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(RegistryKeys.SOUND_EVENT, DragonCare.MOD_ID);

    public static final RegistryObject<SoundEvent> USING_PILL =
            registerSound("using_pill");

    /** Idle hum of the ash sensor at low ash levels (auto mode, level &lt; 0.10). */
    public static final RegistryObject<SoundEvent> ASH_METER_NORMAL =
            registerSound("ash_meter_normal");

    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.of(Identifier.of(DragonCare.MOD_ID, name)));
    }
}


