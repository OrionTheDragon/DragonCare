package com.dragoncare.effect;

import com.dragoncare.DragonCare;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class ModEffects {

    public static final DeferredRegister<StatusEffect> EFFECTS =
            DeferredRegister.create(RegistryKeys.STATUS_EFFECT, DragonCare.MOD_ID);

    public static final RegistryObject<PainkillingEffect> PAINKILLING =
            EFFECTS.register("painkilling_o", PainkillingEffect::new);
            
    public static final RegistryObject<AshPoisoningEffect> ASH_POISONING =
            EFFECTS.register("ash_poisoning_o", AshPoisoningEffect::new);
            
    public static final RegistryObject<MysteriousTabletsEffect> MYSTERIOUS_TABLETS =
            EFFECTS.register("mysterious_tablets_o", MysteriousTabletsEffect::new);
}


