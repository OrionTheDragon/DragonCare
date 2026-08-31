package com.dragoncare.client;

import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class AddonClient {

    private AddonClient() {}

    public static void register(IEventBus modEventBus) {
        // In 1.20.1 Forge, config screens are handled automatically by Configured
        // when ModLoadingContext.get().registerConfig is called.
        modEventBus.addListener(AddonClient::onRegisterReloadListeners);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((SynchronousResourceReloader) resourceManager ->
                DirtTextureBlender.clearCache());
    }
}


