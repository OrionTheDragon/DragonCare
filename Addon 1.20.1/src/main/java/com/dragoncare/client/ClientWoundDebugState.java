package com.dragoncare.client;

import com.dragoncare.network.WoundDebugPayload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ClientWoundDebugState {
    public static final int NO_OVERRIDE = -1;
    private static final int TICKS_PER_PERCENT = 20;
    private static final Map<UUID, DebugState> STATES = new HashMap<>();

    private ClientWoundDebugState() {}

    public static void apply(UUID dragonId, int mode, int percent) {
        switch (mode) {
            case WoundDebugPayload.MODE_RESET -> STATES.remove(dragonId);
            case WoundDebugPayload.MODE_FIXED ->
                    STATES.put(dragonId, new DebugState(false, clampPercent(percent)));
            case WoundDebugPayload.MODE_ANIMATE ->
                    STATES.put(dragonId, new DebugState(true, 0));
            default -> {
            }
        }
    }

    public static int getPercent(UUID dragonId) {
        DebugState state = STATES.get(dragonId);
        return state != null ? state.percent : NO_OVERRIDE;
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, DebugState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            DebugState state = iterator.next().getValue();
            if (!state.animated || ++state.ticks < TICKS_PER_PERCENT) {
                continue;
            }

            state.ticks = 0;
            if (state.percent >= 100) {
                iterator.remove();
            } else {
                state.percent++;
            }
        }
    }

    public static void clear() {
        STATES.clear();
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static final class DebugState {
        private final boolean animated;
        private int percent;
        private int ticks;

        private DebugState(boolean animated, int percent) {
            this.animated = animated;
            this.percent = percent;
        }
    }
}
