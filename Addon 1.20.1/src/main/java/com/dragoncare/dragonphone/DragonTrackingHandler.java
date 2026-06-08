package com.dragoncare.dragonphone;

import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import com.dragoncare.DragonCare;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * РџРѕРґРґРµСЂР¶РёРІР°РµС‚ СЂРµРµСЃС‚СЂ РїСЂРёСЂСѓС‡РµРЅРЅС‹С… РґСЂР°РєРѕРЅРѕРІ РІ Р°РєС‚СѓР°Р»СЊРЅРѕРј СЃРѕСЃС‚РѕСЏРЅРёРё:
 * <ul>
 *   <li>РєР°Р¶РґС‹Рµ {@value #UPDATE_INTERVAL_TICKS} С‚РёРєРѕРІ РѕР±РЅРѕРІР»СЏРµС‚ РїРѕР·РёС†РёСЋ/РёРјСЏ/СЃС‚Р°РґРёСЋ;</li>
 *   <li>РїСЂРё СЃРјРµСЂС‚Рё РґСЂР°РєРѕРЅР° СѓРґР°Р»СЏРµС‚ Р·Р°РїРёСЃСЊ РёР· СЂРµРµСЃС‚СЂР°.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class DragonTrackingHandler {

    private static final int UPDATE_INTERVAL_TICKS = 20; // СЂР°Р· РІ СЃРµРєСѓРЅРґСѓ вЂ” СЌС‚РѕРіРѕ С…РІР°С‚Р°РµС‚ РґР»СЏ UI С‚РµР»РµС„РѕРЅР°

    private DragonTrackingHandler() {}

    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityDragonBase dragon)) return;
        if (!(dragon.getWorld() instanceof ServerWorld serverWorld)) return;
        if ((serverWorld.getTime() + dragon.getId()) % UPDATE_INTERVAL_TICKS != 0) return;

        MinecraftServer server = serverWorld.getServer();
        if (dragon.getOwnerUuid() == null) {
            // РќРµ РїСЂРёСЂСѓС‡РµРЅРЅС‹Р№ (РёР»Рё РїРёС‚РѕРјРµС† РїРѕС‚РµСЂСЏРЅ/РѕСЃРІРѕР±РѕР¶РґС‘РЅ) вЂ” РІС‹Р±СЂР°СЃС‹РІР°РµРј РёР·
            // СЂРµРµСЃС‚СЂР° Р»СЋР±СѓСЋ СѓСЃС‚Р°СЂРµРІС€СѓСЋ Р·Р°РїРёСЃСЊ, С‡С‚РѕР±С‹ РєР°СЂС‚Р° С‚РµР»РµС„РѕРЅР° РЅРµ РєРѕРїРёР»Р°
            // В«РјС‘СЂС‚РІС‹РµВ» Р·Р°РїРёСЃРё. remove() вЂ” РґРµС€С‘РІС‹Р№ no-op, РµСЃР»Рё Р·Р°РїРёСЃРё РЅРµС‚.
            DragonRegistryState.get(server).remove(dragon.getUuid());
            return;
        }
        DragonRegistryState.get(server).putOrUpdate(dragon, serverWorld.getTime());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityDragonBase dragon)) return;
        if (!(dragon.getWorld() instanceof ServerWorld serverWorld)) return;
        DragonRegistryState.get(serverWorld.getServer()).remove(dragon.getUuid());
    }
}



