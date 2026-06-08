package com.dragoncare.client;

import com.dragoncare.DragonCare;
import com.dragoncare.dragonphone.ModDataComponents;
import com.dragoncare.item.ModItems;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = DragonCare.MOD_ID, value = Dist.CLIENT)
public final class AshSensorModelPredicate {

    private AshSensorModelPredicate() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModelPredicateProviderRegistry.register(
                    ModItems.ASH_SENSOR.get(),
                    Identifier.of(DragonCare.MOD_ID, "sensor_on"),
                    (ClampedModelPredicateProvider) (stack, world, entity, seed) -> 
                        Boolean.TRUE.equals(stack.get(ModDataComponents.SENSOR_ON.get())) ? 1.0F : 0.0F
            );

            ModelPredicateProviderRegistry.register(
                    ModItems.ASH_SENSOR.get(),
                    Identifier.of(DragonCare.MOD_ID, "ash_level"),
                    (ClampedModelPredicateProvider) (stack, world, entity, seed) -> {
                        Float level = stack.get(ModDataComponents.SENSOR_LEVEL.get());
                        return level != null ? level : 0.0F;
                    }
            );
        });
    }
}
