package com.dragoncare.client;

import com.dragoncare.client.screen.DragonBrushScreen;
import com.iafenvoy.iceandfire.entity.EntityDragonBase;
import net.minecraft.client.MinecraftClient;

/**
 * Client-only bridge for opening the dragon cleaning QTE screen.
 * Kept separate to prevent server-side JVM class-loading exceptions.
 */
public final class DragonBrushClient {

    private DragonBrushClient() {}

    public static void openScreen(EntityDragonBase dragon, int dirtLevel) {
        MinecraftClient.getInstance().setScreen(new DragonBrushScreen(dragon, dirtLevel));
    }
}



