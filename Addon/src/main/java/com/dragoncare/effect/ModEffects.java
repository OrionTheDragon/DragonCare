package com.dragoncare.effect;

import com.dragoncare.DragonCare;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.RegistryKeys;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {

    public static final DeferredRegister<StatusEffect> EFFECTS =
            DeferredRegister.create(RegistryKeys.STATUS_EFFECT, DragonCare.MOD_ID);

    public static final DeferredHolder<StatusEffect, PainkillingEffect> PAINKILLING =
            EFFECTS.register("painkilling_o", PainkillingEffect::new);
            
    public static final DeferredHolder<StatusEffect, AshPoisoningEffect> ASH_POISONING =
            EFFECTS.register("ash_poisoning_o", AshPoisoningEffect::new);
            
    public static final DeferredHolder<StatusEffect, MysteriousTabletsEffect> MYSTERIOUS_TABLETS =
            EFFECTS.register("mysterious_tablets_o", MysteriousTabletsEffect::new);
}
