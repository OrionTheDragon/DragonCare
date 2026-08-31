package com.dragoncare.client;

/** Shared wound-frame calculation used by both IaF renderer families. */
public final class WoundFrameSelector {
    public static final int STEPS_PER_LEVEL = 32;
    public static final int MAX_FRAME = STEPS_PER_LEVEL * 3;

    private WoundFrameSelector() {
    }

    public static int fromHealth(float health, float maxHealth) {
        if (maxHealth <= 0.0F) {
            return 0;
        }
        float ratio = Math.max(0.0F, Math.min(1.0F, health / maxHealth));
        if (ratio >= 1.0F) {
            return 0;
        }
        if (ratio >= 0.66F) {
            return frameInRange((1.0F - ratio) / 0.34F);
        }
        if (ratio >= 0.33F) {
            return STEPS_PER_LEVEL + frameInRange((0.66F - ratio) / 0.33F);
        }
        return STEPS_PER_LEVEL * 2 + frameInRange((0.33F - ratio) / 0.33F);
    }

    public static int fromPercent(int percent) {
        if (percent <= 0) {
            return 0;
        }
        int clamped = Math.min(100, percent);
        return Math.min(MAX_FRAME, (int) Math.ceil(clamped * MAX_FRAME / 100.0D));
    }

    private static int frameInRange(float progress) {
        return Math.max(1, Math.min(STEPS_PER_LEVEL,
                (int) Math.ceil(progress * STEPS_PER_LEVEL)));
    }
}
