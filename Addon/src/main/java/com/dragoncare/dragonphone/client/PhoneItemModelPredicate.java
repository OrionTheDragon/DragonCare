package com.dragoncare.dragonphone.client;

import com.dragoncare.DragonCare;
import com.dragoncare.item.ModItems;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Регистрирует item-property {@code dragoncare:on} для модели «Драконьего телефона»,
 * чтобы при включении предмета JSON-overrides переключался на текстуру _on.
 *
 * <p>Метод {@code ModelPredicateProviderRegistry.register} в 1.21.1 yarn — отложенный
 * вызов внутри {@link FMLClientSetupEvent} нужен, потому что предмет регистрируется
 * на mod-bus раньше клиентских ресурсов.</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID, value = Dist.CLIENT)
public final class PhoneItemModelPredicate {

    private PhoneItemModelPredicate() {}

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModelPredicateProviderRegistry.register(
                ModItems.DRAGON_PHONE.get(),
                Identifier.of(DragonCare.MOD_ID, "on"),
                (ClampedModelPredicateProvider) (stack, world, entity, seed) ->
                        Boolean.TRUE.equals(stack.get(com.dragoncare.dragonphone.ModDataComponents.PHONE_ON.get())) ? 1.0F : 0.0F
        ));
    }
}
