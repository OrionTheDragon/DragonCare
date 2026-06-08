package com.dragoncare.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientDirtCache {
    private static final Map<UUID, Integer> cache = new HashMap<>();

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


