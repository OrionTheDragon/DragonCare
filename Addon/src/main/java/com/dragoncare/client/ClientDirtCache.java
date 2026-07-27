package com.dragoncare.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientDirtCache {
    // ConcurrentHashMap: put() приходит из сетевого обработчика (клиентский поток),
    // get() — из рендер-потока (DragonTextureProviderMixin) и тултипов. Совпадает с ClientBondCache.
    private static final Map<UUID, Integer> cache = new ConcurrentHashMap<>();

    public static void put(UUID dragonId, int dirtLevel) {
        cache.put(dragonId, dirtLevel);
    }

    public static int get(UUID dragonId) {
        return cache.getOrDefault(dragonId, 0);
    }

    public static void clear() {
        cache.clear();
    }

    public static void clearCache() { cache.clear(); }
}
