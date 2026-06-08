package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.dragoncare.DragonCare;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Поддерживает реестр прирученных драконов в актуальном состоянии:
 * <ul>
 *   <li>каждые {@value #UPDATE_INTERVAL_TICKS} тиков обновляет позицию/имя/стадию;</li>
 *   <li>при смерти дракона удаляет запись из реестра.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class DragonTrackingHandler {

    private static final int UPDATE_INTERVAL_TICKS = 20; // раз в секунду — этого хватает для UI телефона

    private DragonTrackingHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof DragonBaseEntity dragon)) return;
        if (!(dragon.getWorld() instanceof ServerWorld serverWorld)) return;
        if ((serverWorld.getTime() + dragon.getId()) % UPDATE_INTERVAL_TICKS != 0) return;

        MinecraftServer server = serverWorld.getServer();
        if (dragon.getOwnerUuid() == null) {
            // Не прирученный (или питомец потерян/освобождён) — выбрасываем из
            // реестра любую устаревшую запись, чтобы карта телефона не копила
            // «мёртвые» записи. remove() — дешёвый no-op, если записи нет.
            DragonRegistryState.get(server).remove(dragon.getUuid());
            return;
        }
        DragonRegistryState.get(server).putOrUpdate(dragon, serverWorld.getTime());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof DragonBaseEntity dragon)) return;
        if (!(dragon.getWorld() instanceof ServerWorld serverWorld)) return;
        DragonRegistryState.get(serverWorld.getServer()).remove(dragon.getUuid());
    }
}
