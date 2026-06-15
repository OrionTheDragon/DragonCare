package com.dragoncare.recipe;

import com.dragoncare.DragonCare;
import com.dragoncare.config.AddonConfig;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public class SimplifyCraftsCondition implements ICondition {
    public static final Identifier ID = new Identifier(DragonCare.MOD_ID, "simplify_crafts");

    @Override
    public Identifier getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return AddonConfig.SIMPLIFY_CRAFTS.get();
    }

    public static class Serializer implements IConditionSerializer<SimplifyCraftsCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public void write(JsonObject json, SimplifyCraftsCondition value) {
        }

        @Override
        public SimplifyCraftsCondition read(JsonObject json) {
            return new SimplifyCraftsCondition();
        }

        @Override
        public Identifier getID() {
            return SimplifyCraftsCondition.ID;
        }
    }
}
