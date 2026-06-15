package com.dragoncare.recipe;

import com.dragoncare.DragonCare;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModRecipeConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, DragonCare.MOD_ID);

    static {
        CONDITIONS.register("simplify_crafts", () -> SimplifyCraftsCondition.CODEC);
    }
}
