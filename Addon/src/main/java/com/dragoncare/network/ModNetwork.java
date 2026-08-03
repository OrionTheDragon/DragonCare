package com.dragoncare.network;

import com.dragoncare.DragonCare;
import com.dragoncare.client.ClientBondCache;
import com.dragoncare.dragonphone.ModDataComponents;
import com.dragoncare.dragonphone.PhoneServerHelper;
import com.dragoncare.dragonphone.client.ClientPhoneBridge;
import com.dragoncare.dragonphone.client.ClientPhoneHudState;
import com.dragoncare.dragonphone.net.DragonListPayload;
import com.dragoncare.dragonphone.net.PhoneHudPayload;
import com.dragoncare.dragonphone.net.RefreshListPayload;
import com.dragoncare.dragonphone.net.SelectDragonPayload;
import com.dragoncare.item.ModItems;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class ModNetwork {

    private static final UUID ZERO = new UUID(0L, 0L);

    private ModNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToClient(
                BondSyncPayload.ID,
                BondSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientBondCache.put(payload.dragonId(), payload.points())
                )
        );

        registrar.playToClient(
                DragonDirtSyncPayload.ID,
                DragonDirtSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.dragoncare.client.ClientDirtCache.put(payload.dragonId(), payload.dirtLevel())
                )
        );

        registrar.playToClient(
                WoundDebugPayload.ID,
                WoundDebugPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.dragoncare.client.ClientWoundDebugState.apply(
                                payload.dragonId(), payload.mode(), payload.percent()
                        )
                )
        );

        // Server → Client: список драконов (+ опционально открыть UI телефона)
        registrar.playToClient(
                DragonListPayload.ID,
                DragonListPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientPhoneBridge.handleList(payload)
                )
        );

        // Server → Client: компактное состояние HUD-оверлея (имя + координаты выбранного дракона)
        registrar.playToClient(
                PhoneHudPayload.ID,
                PhoneHudPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientPhoneHudState.set(payload)
                )
        );

        // Client → Server: запрос обновления списка (по кнопке)
        registrar.playToServer(
                RefreshListPayload.ID,
                RefreshListPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayerEntity sp) {
                        ItemStack phone = PhoneServerHelper.findActivePhone(sp);
                        UUID selected = phone != null ? phone.get(ModDataComponents.PHONE_TRACKED.get()) : null;
                        PhoneServerHelper.sendList(sp, false, selected);
                    }
                })
        );

        // Client → Server: выбор отслеживаемого дракона
        registrar.playToServer(
                SelectDragonPayload.ID,
                SelectDragonPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayerEntity sp)) return;
                    ItemStack phone = PhoneServerHelper.findActivePhone(sp);
                    if (phone == null) return;
                    UUID newSel = ZERO.equals(payload.dragonId()) ? null : payload.dragonId();
                    if (newSel == null) {
                        phone.remove(ModDataComponents.PHONE_TRACKED.get());
                    } else {
                        phone.set(ModDataComponents.PHONE_TRACKED.get(), newSel);
                    }
                })
        );

        // Client → Server: очистка дракона щёткой
        registrar.playToServer(
                DragonBrushCleanPayload.ID,
                DragonBrushCleanPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayerEntity sp)) return;
                    if (!(sp.getWorld() instanceof ServerWorld sw)) return;
                    net.minecraft.entity.Entity entity = sw.getEntity(payload.dragonId());
                    if (!(entity instanceof DragonBaseEntity dragon)) return;

                    Hand brushHand = Hand.MAIN_HAND;
                    ItemStack brush = sp.getStackInHand(Hand.MAIN_HAND);
                    if (!brush.isOf(ModItems.DRAGON_BRUSH.get())) {
                        brush = sp.getStackInHand(Hand.OFF_HAND);
                        brushHand = Hand.OFF_HAND;
                    }

                    if (brush.isOf(ModItems.DRAGON_BRUSH.get())) {
                        int oldDirtLevel = com.dragoncare.mechanics.DragonDirtManager.getDirtLevel(sp.getServer(), dragon.getUuid());
                        // Чистка может только уменьшать грязь: зажимаем клиентское значение в [0, oldDirtLevel],
                        // иначе поддельный пакет с отрицательным newDirtLevel даёт неограниченные очки связи.
                        int newDirtLevel = Math.max(0, Math.min(payload.newDirtLevel(), oldDirtLevel));
                        com.dragoncare.mechanics.DragonDirtManager.setDirtLevel(dragon, newDirtLevel);
                        if (payload.extraDamage() > 0 && !sp.isCreative()) {
                            brush.damage(payload.extraDamage(), sp, LivingEntity.getSlotForHand(brushHand));
                        }

                        int cleanedLevels = oldDirtLevel - newDirtLevel;
                        if (cleanedLevels > 0) {
                            boolean isPerfect = (newDirtLevel == 0 && oldDirtLevel >= 3);
                            int multiplier = isPerfect ? 2 : 1;
                            int points = cleanedLevels * multiplier;
                            com.dragoncare.taming.BondManager.onFeedBypass(sp, dragon, points);

                            // Advancements
                            if (newDirtLevel == 0) {
                                if (oldDirtLevel == 1) {
                                    com.dragoncare.advancement.AchievementGranter.grant(sp, com.dragoncare.advancement.AchievementGranter.FRESH_LOOK);
                                } else if (oldDirtLevel == 5) {
                                    com.dragoncare.advancement.AchievementGranter.grant(sp, com.dragoncare.advancement.AchievementGranter.HERCULES_FEAT);
                                }
                            }
                        }
                    }
                })
        );
    }
}
