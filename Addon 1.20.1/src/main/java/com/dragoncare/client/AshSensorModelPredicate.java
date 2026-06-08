package com.dragoncare.client;

import com.dragoncare.DragonCare;

import com.dragoncare.item.ModItems;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = DragonCare.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AshSensorModelPredicate {

    private AshSensorModelPredicate() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModelPredicateProviderRegistry.register(
                    ModItems.ASH_SENSOR.get(),
                    Identifier.of(DragonCare.MOD_ID, "sensor_on"),
                    (ClampedModelPredicateProvider) (stack, world, entity, seed) -> {
                        net.minecraft.nbt.NbtCompound nbt = stack.getNbt();
                        return (nbt != null && nbt.getBoolean("sensor_on")) ? 1.0F : 0.0F;
                    }
            );

            ModelPredicateProviderRegistry.register(
                    ModItems.ASH_SENSOR.get(),
                    Identifier.of(DragonCare.MOD_ID, "ash_level"),
                    (ClampedModelPredicateProvider) (stack, world, entity, seed) -> {
                        net.minecraft.nbt.NbtCompound nbt = stack.getNbt();
                        return nbt != null && nbt.contains("sensor_level") ? nbt.getFloat("sensor_level") : 0.0F;
                    }
            );
        });
    }
}


