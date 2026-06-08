package com.dragoncare.taming;

/** Per-dragon affection bond record. */
public final class BondData {
    public int points = 0;
    /** First server-tick of the current 10-minute feeding window. */
    public long windowStartTick = 0L;
    /** Number of feedings credited inside the current window (cap = 18 = 36 / 2). */
    public int feedsInWindow = 0;
    /** Server-tick at which the next +1 passive bond should be paid out (lazy). */
    public long lastPassiveTick = 0L;
    /** Server-tick of the last credited feed (used to dedup double-fired interact events). Not persisted. */
    public transient long lastFeedTick = -1L;
}


