package com.dragoncare.recipe;

import com.dragoncare.DragonCare;
import com.dragoncare.config.AddonConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.common.conditions.ICondition;

public record SimplifyCraftsCondition() implements ICondition {
    public static final Identifier ID = Identifier.of(DragonCare.MOD_ID, "simplify_crafts");
    public static final MapCodec<SimplifyCraftsCondition> CODEC = MapCodec.unit(new SimplifyCraftsCondition());

    @Override
    public boolean test(IContext context) {
        return AddonConfig.SIMPLIFY_CRAFTS.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
