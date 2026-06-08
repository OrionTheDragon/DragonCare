package com.dragoncare.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class AddonClient {

    private AddonClient() {}

    public static void registerConfigScreen() {
        // In 1.20.1 Forge, config screens are handled automatically by Configured
        // when ModLoadingContext.get().registerConfig is called.
    }
}


