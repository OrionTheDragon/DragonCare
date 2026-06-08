package com.dragoncare.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side store of last-known bond points for dragons whose owner is the local player. */
public final class ClientBondCache {

    private static final Map<UUID, Integer> POINTS = new ConcurrentHashMap<>();

    private ClientBondCache() {}

    public static void put(UUID dragonId, int points) {
        POINTS.put(dragonId, points);
    }

    public static int get(UUID dragonId) {
        Integer v = POINTS.get(dragonId);
        return v == null ? 0 : v;
    }

    public static boolean has(UUID dragonId) {
        return POINTS.containsKey(dragonId);
    }

    public static void clear() {
        POINTS.clear();
    }

    public static void clearCache() { POINTS.clear(); }
}


