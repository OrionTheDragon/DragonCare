package com.dragoncare.dragonphone.client;

import com.dragoncare.DragonCare;
import com.dragoncare.item.ModItems;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Регистрирует item-property {@code dragoncare:on} для модели «Драконьего телефона»,
 * чтобы при включении предмета JSON-overrides переключался на текстуру _on.
 *
 * <p>Метод {@code ModelPredicateProviderRegistry.register} в 1.21.1 yarn — отложенный
 * вызов внутри {@link FMLClientSetupEvent} нужен, потому что предмет регистрируется
 * на mod-bus раньше клиентских ресурсов.</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PhoneItemModelPredicate {

    private PhoneItemModelPredicate() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModelPredicateProviderRegistry.register(
                ModItems.DRAGON_PHONE.get(),
                Identifier.of(DragonCare.MOD_ID, "on"),
                (ClampedModelPredicateProvider) (stack, world, entity, seed) ->
                        stack.hasNbt() && stack.getNbt().getBoolean("phone_on") ? 1.0F : 0.0F
        ));
    }
}


