package com.dragoncare.client;

import com.dragoncare.client.screen.BurntSheetScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * Client-only bridge that opens {@link BurntSheetScreen}. Kept in a separate
 * class so the server-side JVM never resolves {@code MinecraftClient}.
 */
public final class BurntSheetClient {

    private BurntSheetClient() {}

    public static void openScreen(ItemStack stack) {
        MinecraftClient.getInstance().setScreen(new BurntSheetScreen(stack));
    }
}
