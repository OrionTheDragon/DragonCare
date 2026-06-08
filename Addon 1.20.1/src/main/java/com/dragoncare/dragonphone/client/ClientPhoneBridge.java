package com.dragoncare.dragonphone.client;

import com.dragoncare.dragonphone.net.DragonListPayload;
import net.minecraft.client.MinecraftClient;

/**
 * Клиентский «мост» между сетевыми пакетами и {@link DragonPhoneScreen}:
 * <ul>
 *   <li>при первом получении списка с {@code openScreen=true} — открывает экран;</li>
 *   <li>в дальнейшем — обновляет данные уже открытого экрана.</li>
 * </ul>
 */
public final class ClientPhoneBridge {

    private ClientPhoneBridge() {}

    public static void handleList(DragonListPayload payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (payload.openScreen() && !(mc.currentScreen instanceof DragonPhoneScreen)) {
            mc.setScreen(new DragonPhoneScreen(payload.dragons(), payload.selected()));
        } else if (mc.currentScreen instanceof DragonPhoneScreen screen) {
            screen.updateData(payload.dragons(), payload.selected());
        }
    }
}


