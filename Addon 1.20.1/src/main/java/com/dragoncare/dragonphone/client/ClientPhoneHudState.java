package com.dragoncare.dragonphone.client;

import com.dragoncare.dragonphone.net.PhoneHudPayload;

import javax.annotation.Nullable;

/**
 * Клиентский кеш состояния HUD-оверлея. Хранит последний полученный с сервера
 * {@link PhoneHudPayload}; рендерер читает его каждый кадр, сервер обновляет
 * пару раз в секунду через {@code PhoneGlowTickHandler}.
 */
public final class ClientPhoneHudState {

    @Nullable
    private static volatile PhoneHudPayload current;

    private ClientPhoneHudState() {}

    public static void set(@Nullable PhoneHudPayload payload) {
        current = (payload != null && payload.active()) ? payload : null;
    }

    public static void clear() { current = null; }

    @Nullable
    public static PhoneHudPayload get() { return current; }
}


