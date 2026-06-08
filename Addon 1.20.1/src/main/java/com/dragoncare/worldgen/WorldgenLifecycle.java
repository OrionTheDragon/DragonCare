package com.dragoncare.worldgen;

import com.dragoncare.DragonCare;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle bridge that loads addon schematics into memory when the server is
 * about to start and clears them on server stop.
 *
 * <p>{@link ServerStartingEvent} fires after dynamic registries are frozen, so it's
 * the earliest safe point to resolve block-state strings inside schematic palettes.</p>
 */
@EventBusSubscriber(modid = DragonCare.MOD_ID)
public final class WorldgenLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(DragonCare.MOD_ID + "/Lifecycle");

    private WorldgenLifecycle() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOG.info("[DIAG] ServerStartingEvent fired — loading addon schematics");
        AddonSchematics.loadAll(event.getServer().getRegistryManager());
        LOG.info("[DIAG] Forge schematic buckets: BASIC={}, DRAIN={}, HEART={}",
                AddonSchematics.forgeCount(AddonSchematics.Category.BASIC),
                AddonSchematics.forgeCount(AddonSchematics.Category.DRAIN),
                AddonSchematics.forgeCount(AddonSchematics.Category.HEART));
        LOG.info("[DIAG] Hunter wood variants loaded: {}",
                AddonSchematics.dragonHunterWoodTypes());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOG.info("[DIAG] ServerStoppedEvent — clearing schematics & placer state");
        AddonSchematics.clear();
        DragonHunterPlacer.clearStatic();
        DragonHunterGuildPlacer.clearStatic();
    }
}


